package co.golink.tester.data.pass

import co.golink.tester.data.encryption.E2EKeyManager
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class PassState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** "Lock now" / auto-lock: K apagada, pede biometria ou passphrase. */
    val locked: Boolean = false,
    /** Sem K e sem chaves E2E em memória (nem biometria): é preciso o E2EGate. */
    val needsKeys: Boolean = false,
    val settings: PassSettings = PassSettings(),
    val folders: List<PassFolder> = emptyList(),
    val items: List<PassItem> = emptyList(),
    val trashDays: Int = 30,
    val health: PassHealthResult? = null,
    val healthRunning: Boolean = false,
) {
    val active: List<PassItem> get() = items.filter { !it.isTrashed }
    val trash: List<PassItem> get() = items.filter { it.isTrashed }
    fun item(id: String?) = items.firstOrNull { it.id == id }
    fun folder(id: String?) = folders.firstOrNull { it.id == id }
}

class PassConflictException : Exception("conflict")
class PassLimitException(val imported: Int) : Exception("limit")
class PassImportException(val imported: Int, cause: Throwable) : Exception("import", cause)

/**
 * Pass E2E (espelho de resources/js/store/modules/pass.js). K_pass vive só em memória
 * enquanto o cofre está aberto; lock/auto-lock apagam tudo. Sync de 5 s pelo /sync.
 */
@Singleton
class PassRepository @Inject constructor(
    private val http: PassHttp,
    private val keys: E2EKeyManager,
    private val bio: PassBiometricStore,
    private val prefs: PassPrefs,
) {
    companion object {
        private val DEFAULT_FOLDERS = listOf("Personal", "Finance", "Work", "Home")
        private val SERVER_FIELDS = setOf("id", "revision", "deleted_at", "created_at", "updated_at", "locked")
        private const val MAX_PW_HISTORY = 5
        private const val MAX_EVENTS = 30
        private const val AUTOFILL_TOUCH_MS = 24 * 60 * 60_000L
        const val MAX_ITEMS = 5000
        const val REPROMPT_GRACE_MS = 5 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(PassState(locked = bio.enabled || prefs.startsLocked()))
    val state: StateFlow<PassState> = _state.asStateFlow()

    @Volatile private var k: ByteArray? = null
    @Volatile private var gen = 0
    private var cursor: String? = null
    private var metaSeen: String? = null
    private var settingsSeen: String? = null
    private var metaLocked = false
    private var stampVi: String? = null
    private var stampVv: String? = null
    private var writeSeq = 0
    private val touchedAt = HashMap<String, Int>()
    private val loadLock = Mutex()
    private var healthJob: Job? = null
    private val healthMutex = Mutex()
    private val healthRun = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var healthFresh = false
    private var idleJob: Job? = null
    @Volatile private var lastActivity = System.currentTimeMillis()
    @Volatile private var repromptAt = 0L
    private var twofa: Set<String>? = null

    private val health = PassHealth(
        fetchRange = { prefix -> http.getText("/breach/$prefix") },
        loadTwofa = {
            twofa ?: run {
                val data = http.get("/twofa-domains")["data"] as? JsonArray
                data.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet().also { twofa = it }
            }
        },
    )

    init {
        // Logout: apaga o cofre da memória e a K guardada para biometria (pertence à conta que saiu).
        keys.addOnLock {
            wipe()
            _state.value = PassState()
            bio.clear()
            prefs.clearLockState()
        }
    }

    val hasKey: Boolean get() = k != null

    private fun markLocal(vararg ids: String) {
        writeSeq++
        ids.forEach { touchedAt[it] = writeSeq }
    }

    private fun wipe() {
        k?.fill(0)
        k = null
        gen++
        cursor = null
        metaSeen = null
        settingsSeen = null
        metaLocked = false
        stampVi = null
        stampVv = null
        touchedAt.clear()
        healthJob?.cancel()
        idleJob?.cancel()
        idleJob = null
        repromptAt = 0
        Totp.clearCache()
        health.clear()
    }

    // ---- Abrir / trancar -----------------------------------------------------

    /** Abre (ou cria) o cofre e decifra os itens. Sem efeito se trancado. */
    suspend fun load(force: Boolean = false) = loadLock.withLock {
        val s = _state.value
        if (s.locked || (s.loaded && !force)) return@withLock
        val g = gen
        _state.update { it.copy(loading = true, failed = false) }
        try {
            var vault = http.get("/vault")["data"] as? JsonObject
            if (g != gen) return@withLock
            if (vault == null) {
                if (!keys.unlocked.value) { _state.update { it.copy(needsKeys = true, loading = false) }; return@withLock }
                val key = co.golink.tester.data.encryption.CalendarCrypto.genKey()
                val folders = JsonArray(DEFAULT_FOLDERS.map { JsonObject(mapOf("id" to JsonPrimitive(PassCrypto.newFolderId()), "name" to JsonPrimitive(it))) })
                val res = http.post(
                    "/vault",
                    JsonObject(
                        mapOf(
                            "wrapped_key" to JsonPrimitive(keys.sealForSelf(key)),
                            "meta_enc" to JsonPrimitive(PassCrypto.encryptJson(JsonObject(mapOf("folders" to folders)), key)),
                        ),
                    ),
                )
                vault = res["data"] as? JsonObject ?: throw IllegalStateException("vault")
                if (g != gen) return@withLock
            }
            if (k == null) {
                if (!keys.unlocked.value) { _state.update { it.copy(needsKeys = true, loading = false) }; return@withLock }
                k = keys.openFileDataKey(vault.str("wrapped_key")!!)
            }
            applyVault(vault)
            if (g != gen) return@withLock
            loadItems()
            if (g != gen) return@withLock
            _state.update { it.copy(loaded = true, loading = false, needsKeys = false) }
            startIdleWatch()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (g == gen) _state.update { it.copy(loading = false, failed = true) }
        }
    }

    fun lock() {
        prefs.vaultLocked = true
        wipe()
        _state.value = PassState(locked = true)
    }

    /** Desbloqueado com sucesso: o lock persistido deixa de valer. */
    private fun markUnlocked() {
        prefs.vaultLocked = false
        persistActivity(System.currentTimeMillis(), force = true)
    }

    private var activityPersistedAt = 0L

    /** Guarda a última atividade em disco (no máx. a cada 30 s). */
    private fun persistActivity(now: Long, force: Boolean = false) {
        if (!force && now - activityPersistedAt < 30_000L) return
        activityPersistedAt = now
        prefs.lastActiveAt = now
    }

    /** Desbloquear com a passphrase E2E (ou abre as chaves E2E, se ainda não estavam). */
    suspend fun unlockWithSecret(secret: String): Boolean {
        // Nunca criar chaves aqui: setupOrUnlock só quando a conta já tem E2E configurada.
        val ok = when {
            keys.unlocked.value -> keys.verifyCurrentSecret(secret)
            keys.isConfigured() -> runCatching { keys.setupOrUnlock(secret); keys.unlocked.value }.getOrDefault(false)
            else -> false
        }
        if (!ok) return false
        markUnlocked()
        _state.update { it.copy(locked = false, needsKeys = false) }
        load(force = true)
        return true
    }

    /** Desbloquear com a K vinda do Keystore (biometria). Falha se não abrir o cofre. */
    suspend fun unlockWithKey(key: ByteArray): Boolean {
        val vault = http.get("/vault")["data"] as? JsonObject ?: return false
        // A K tem de abrir o meta do cofre (se o cofre foi recriado, a K guardada já não serve).
        val meta = vault.str("meta_enc")
        if (meta != null) {
            if (runCatching { PassCrypto.decryptJson(meta, key) }.isFailure) return false
        } else {
            // Sem meta: validar com o primeiro item. Sem nada com que validar, recusar
            // (fica a passphrase) em vez de aceitar uma K qualquer e mostrar tudo "🔒".
            val first = (http.get("/items")["data"] as? JsonArray).orEmpty()
                .firstNotNullOfOrNull { (it as? JsonObject)?.str("payload_enc")?.takeIf(String::isNotEmpty) }
                ?: return false
            if (runCatching { PassCrypto.decryptJson(first, key) }.isFailure) return false
        }
        wipe()
        k = key
        markUnlocked()
        _state.value = PassState(locked = false)
        load(force = true)
        return _state.value.loaded
    }

    /** Cofre aberto e carregado (o preenchimento pode entregar dados sem pedir nada). */
    val isOpen: Boolean get() = k != null && _state.value.loaded && !_state.value.locked

    /** "Outros logins…" com "lembrar": junta o pacote a login.apps (a web preserva o campo). */
    suspend fun rememberApp(id: String, packageName: String) {
        val item = _state.value.item(id) ?: return
        val login = item.payload.obj("login") ?: return
        val apps = (login["apps"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if (packageName in apps) return
        patch(id, "login" to login.with("apps" to JsonArray((apps + packageName).takeLast(20).map(::JsonPrimitive))))
    }

    /** Gravação vinda do preenchimento: novo login ou password nova num existente. */
    suspend fun saveFromAutofill(existingId: String?, name: String, url: String, username: String, password: String, packageName: String?): PassItem {
        val prev = existingId?.let { _state.value.item(it) }
        val payload = if (prev != null) {
            val login = prev.payload.obj("login") ?: JsonObject(emptyMap())
            prev.payload.with("login" to login.with("password" to JsonPrimitive(password), "username" to JsonPrimitive(username.ifEmpty { login.str("username").orEmpty() })))
        } else {
            val apps = if (url.isEmpty() && packageName != null) JsonArray(listOf(JsonPrimitive(packageName))) else null
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("login"), "name" to JsonPrimitive(name), "folder" to JsonNull, "favourite" to JsonPrimitive(false),
                    "notes" to JsonPrimitive(""),
                    "login" to PassLogin(url, username, password, "").toJson().let { if (apps != null) it.with("apps" to apps) else it },
                ),
            )
        }
        return save(prev?.id, payload, prev?.revision)
    }

    /** K atual (para guardar no Keystore ao ligar a biometria). */
    fun currentKey(): ByteArray? = k?.copyOf()

    fun touch() {
        lastActivity = System.currentTimeMillis()
        persistActivity(lastActivity)
    }

    /** Auto-lock ao fim de lock_after minutos sem atividade (0 = nunca, como na web). */
    private fun startIdleWatch() {
        lastActivity = System.currentTimeMillis()
        persistActivity(lastActivity, force = true)
        if (idleJob?.isActive == true) return
        idleJob = scope.launch {
            while (isActive) {
                delay(10_000)
                val s = _state.value
                val minutes = s.settings.lockAfter
                if (minutes > 0 && s.loaded && !s.locked && System.currentTimeMillis() - lastActivity > minutes * 60_000L) {
                    lock()
                    break
                }
            }
        }
    }

    /** Ao voltar à app: se passou o tempo, tranca já (o ticker pode não ter corrido em segundo plano). */
    fun checkIdle() {
        val s = _state.value
        val minutes = s.settings.lockAfter
        if (minutes > 0 && s.loaded && !s.locked && System.currentTimeMillis() - lastActivity > minutes * 60_000L) lock()
    }

    fun repromptValid(): Boolean = System.currentTimeMillis() - repromptAt < REPROMPT_GRACE_MS
    fun markReprompted() { repromptAt = System.currentTimeMillis() }
    suspend fun verifySecret(secret: String): Boolean = when {
        keys.unlocked.value -> keys.verifyCurrentSecret(secret)
        // Aberto por biometria com as chaves E2E fechadas: a passphrase abre-as.
        keys.isConfigured() -> runCatching { keys.setupOrUnlock(secret); keys.unlocked.value }.getOrDefault(false)
        else -> false
    }

    // ---- Cofre / itens -------------------------------------------------------

    private fun applyVault(vault: JsonObject) {
        val key = k ?: return
        val metaEnc = vault.str("meta_enc")
        val settingsObj = vault.obj("settings")
        val settingsKey = settingsObj?.toString().orEmpty()
        if (metaEnc == metaSeen && settingsKey == settingsSeen && _state.value.loaded) return
        var folders = emptyList<PassFolder>()
        var unreadable = false
        if (metaEnc != null) {
            try {
                val meta = PassCrypto.decryptJson(metaEnc, key) as JsonObject
                folders = (meta["folders"] as? JsonArray).orEmpty().mapNotNull { f ->
                    val o = f as? JsonObject ?: return@mapNotNull null
                    PassFolder(o.str("id") ?: return@mapNotNull null, o.str("name").orEmpty())
                }
            } catch (e: Exception) {
                unreadable = true
            }
        }
        metaLocked = unreadable
        metaSeen = metaEnc
        settingsSeen = settingsKey
        val parsed = PassSettings.from(settingsObj)
        prefs.lockAfterMinutes = parsed.lockAfter
        _state.update { it.copy(settings = parsed, folders = folders) }
    }

    private fun decryptItem(raw: JsonObject, previous: PassItem?): PassItem {
        val id = raw.str("id")!!
        val revision = raw.int("revision") ?: 1
        val enc = raw.str("payload_enc").orEmpty()
        val deletedAt = raw.str("deleted_at")
        val updatedAt = raw.str("updated_at")
        if (previous != null && previous.revision == revision && previous.enc == enc) {
            return previous.copy(deletedAt = deletedAt, updatedAt = updatedAt)
        }
        return try {
            val payload = PassCrypto.decryptJson(enc, k!!) as JsonObject
            PassItem(id, revision, deletedAt, raw.str("created_at"), updatedAt, enc, payload, locked = false)
        } catch (e: Exception) {
            PassItem(id, revision, deletedAt, raw.str("created_at"), updatedAt, enc, JsonObject(mapOf("type" to JsonPrimitive("note"), "name" to JsonPrimitive("🔒"))), locked = true)
        }
    }

    /** Primeira vez: tudo. Depois: só o que mudou desde o último `now` + ids vivos. */
    private suspend fun loadItems() {
        if (k == null) return
        val g = gen
        val startSeq = writeSeq
        val since = if (_state.value.loaded) cursor else null
        val data = http.get("/items", if (since != null) mapOf("since" to since) else emptyMap())
        val rows = (data["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val current = _state.value.items
        val prev = current.associateBy { it.id }
        val fresh = withContext(Dispatchers.Default) { rows.map { decryptItem(it, prev[it.str("id")]) } }
        if (k == null || g != gen) return

        fun mineAfter(id: String) = (touchedAt[id] ?: 0) > startSeq
        val alive: Set<String> = if (since != null) (data["ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet() else fresh.map { it.id }.toSet()
        val merged = LinkedHashMap<String, PassItem>()
        current.filter { it.id in alive || mineAfter(it.id) }.forEach { merged[it.id] = it }
        var changed = merged.size != current.size
        for (it in fresh) {
            if (mineAfter(it.id)) continue
            val old = merged[it.id]
            if (old != null && old.revision > it.revision) continue
            if (old == null || old.revision != it.revision || old.isTrashed != it.isTrashed) changed = true
            merged[it.id] = it
        }
        if (since == null) changed = true
        cursor = data.str("now")
        if (!changed) return
        _state.update { it.copy(items = merged.values.toList(), trashDays = data.int("trash_days") ?: 30) }
        scheduleHealth()
    }

    @Volatile private var autofillTouchedAt = 0L

    /**
     * Preenchimento com o cofre aberto: no máximo 1 sync por dia. Qualquer
     * pedido autenticado conta como atividade no servidor (contas inativas);
     * sem isto, quem só usa o autofill e nunca abre a app ficava "inativo".
     * Corre no scope do repositório: o sistema desliga o serviço logo a seguir.
     */
    fun touchFromAutofill() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (autofillTouchedAt != 0L && now - autofillTouchedAt < AUTOFILL_TOUCH_MS) return
        autofillTouchedAt = now
        scope.launch { sync() }
    }

    /** Sync de 5 s: recarrega só a parte do carimbo que mudou (vi = itens, vv = cofre). */
    suspend fun sync() {
        val s = _state.value
        if (k == null || !s.loaded || s.locked) return
        val g = gen
        runCatching {
            val stamp = http.get("/sync")
            if (g != gen) return
            val vv = stamp.str("vv")
            val vi = stamp.str("vi")
            if (vv != stampVv) {
                val vault = http.get("/vault")["data"] as? JsonObject ?: return
                if (g != gen) return
                applyVault(vault)
                stampVv = vv
            }
            if (vi != stampVi) {
                loadLock.withLock { loadItems() }
                if (g != gen) return
                stampVi = vi
            }
        }
    }

    private fun payloadOf(item: PassItem): JsonObject = JsonObject(item.payload.filterKeys { it !in SERVER_FIELDS && !it.startsWith("_") })

    private fun localItem(raw: JsonObject, payload: JsonObject) = PassItem(
        id = raw.str("id")!!, revision = raw.int("revision") ?: 1, deletedAt = raw.str("deleted_at"),
        createdAt = raw.str("created_at"), updatedAt = raw.str("updated_at"), enc = raw.str("payload_enc").orEmpty(),
        payload = payload, locked = false,
    )

    private fun upsert(item: PassItem) = _state.update { s ->
        val i = s.items.indexOfFirst { it.id == item.id }
        s.copy(items = if (i >= 0) s.items.toMutableList().also { it[i] = item } else s.items + item)
    }

    /** Histórico do item: eventos e as últimas passwords (withHistory da web). */
    private fun withHistory(next: JsonObject, prev: JsonObject?): JsonObject {
        val now = Instant.now().toString()
        fun ev(kind: String) = JsonObject(mapOf("at" to JsonPrimitive(now), "kind" to JsonPrimitive(kind)))
        val prevEvents = (prev?.get("events") as? JsonArray).orEmpty()
        val events = prevEvents.toMutableList()
        val pwHistory = (prev?.get("password_history") as? JsonArray).orEmpty().toMutableList()
        if (prev == null) events += ev("created") else {
            val oldPw = prev.obj("login")?.str("password").orEmpty()
            val newPw = next.obj("login")?.str("password").orEmpty()
            if (oldPw.isNotEmpty() && oldPw != newPw) {
                events += ev("password_changed")
                pwHistory.add(0, JsonObject(mapOf("password" to JsonPrimitive(oldPw), "until" to JsonPrimitive(now))))
            }
            val hadTotp = Totp.parse(prev.obj("login")?.str("totp")) != null
            val hasTotp = Totp.parse(next.obj("login")?.str("totp")) != null
            if (!hadTotp && hasTotp) events += ev("totp_added")
            if (hadTotp && !hasTotp) events += ev("totp_removed")
            if (prev.str("type") == "card" && prev.obj("card")?.str("number") != next.obj("card")?.str("number")) events += ev("card_changed")
            if (events.size == prevEvents.size) events += ev("edited")
        }
        return next.with(
            "events" to JsonArray(events.takeLast(MAX_EVENTS)),
            "password_history" to JsonArray(pwHistory.take(MAX_PW_HISTORY)),
            "changed_at" to JsonPrimitive(now),
        )
    }

    /**
     * Grava um item. `payload` = novo payload em claro (a partir do anterior, para
     * preservar campos desconhecidos). `baseRevision` = revisão que o formulário abriu:
     * se outro aparelho mudou entretanto, dá [PassConflictException] (409) e recarrega.
     */
    suspend fun save(id: String?, payload: JsonObject, baseRevision: Int?): PassItem {
        val key = k ?: throw IllegalStateException("locked")
        val prev = id?.let { _state.value.item(it) }
        if (prev?.locked == true) throw IllegalStateException("unreadable")
        var next = withHistory(JsonObject(payload.filterKeys { it !in SERVER_FIELDS }), prev?.let(::payloadOf))
        if (prev != null && next.obj("login")?.str("password") != prev.login?.password) next = next.with("health_ignore" to JsonObject(emptyMap()))
        val enc = PassCrypto.encryptJson(next, key)
        val res = try {
            if (prev != null) {
                http.patch("/items/${prev.id}", JsonObject(mapOf("payload_enc" to JsonPrimitive(enc), "revision" to JsonPrimitive(baseRevision ?: prev.revision))))
            } else {
                http.post("/items", JsonObject(mapOf("payload_enc" to JsonPrimitive(enc))))
            }
        } catch (e: PassHttpException) {
            if (e.status == 409 && prev != null) {
                (e.body?.get("data") as? JsonObject)?.let { fresh -> upsert(decryptItem(fresh, null)) }
                throw PassConflictException()
            }
            throw e
        }
        val saved = localItem(res["data"] as JsonObject, next)
        markLocal(saved.id)
        upsert(saved)
        scheduleHealth()
        return saved
    }

    /** Alteração pequena sem evento de histórico (favorito, mark as fixed). */
    suspend fun patch(id: String, vararg changes: Pair<String, JsonElement?>) {
        val key = k ?: return
        val prev = _state.value.item(id) ?: return
        if (prev.locked) return
        val next = payloadOf(prev).with(*changes)
        val res = try {
            http.patch("/items/$id", JsonObject(mapOf("payload_enc" to JsonPrimitive(PassCrypto.encryptJson(next, key)), "revision" to JsonPrimitive(prev.revision))))
        } catch (e: PassHttpException) {
            if (e.status == 409) {
                (e.body?.get("data") as? JsonObject)?.let { fresh -> upsert(decryptItem(fresh, null)) }
                throw PassConflictException()
            }
            throw e
        }
        markLocal(id)
        upsert(localItem(res["data"] as JsonObject, next))
        scheduleHealth()
    }

    suspend fun toggleFavourite(id: String) {
        val item = _state.value.item(id) ?: return
        patch(id, "favourite" to JsonPrimitive(!item.favourite))
    }

    suspend fun markFixed(id: String, kind: String) {
        val item = _state.value.item(id) ?: return
        val print = _state.value.health?.byItem?.get(id)?.print ?: PassHealth.passwordPrint(item.login?.password.orEmpty())
        val ignore = (item.payload.obj("health_ignore") ?: JsonObject(emptyMap())).with(kind to JsonPrimitive(print))
        patch(id, "health_ignore" to ignore)
    }

    suspend fun trash(id: String) {
        http.delete("/items/$id")
        markLocal(id)
        _state.value.item(id)?.let { upsert(it.copy(deletedAt = Instant.now().toString())) }
        scheduleHealth()
    }

    suspend fun restore(id: String) {
        http.post("/items/$id/restore")
        markLocal(id)
        _state.value.item(id)?.let { upsert(it.copy(deletedAt = null)) }
        scheduleHealth()
    }

    suspend fun deleteForever(id: String) {
        http.delete("/items/$id/force")
        markLocal(id)
        _state.update { s -> s.copy(items = s.items.filter { it.id != id }) }
    }

    suspend fun emptyTrash() {
        http.delete("/items/trash")
        val ids = _state.value.trash.map { it.id }
        markLocal(*ids.toTypedArray())
        _state.update { s -> s.copy(items = s.active) }
    }

    // ---- Pastas / preferências ----------------------------------------------

    /** Aplica `update` sobre a versão mais recente; 409 (meta_base) → junta e repete até 4 vezes. */
    suspend fun saveFolders(update: (List<PassFolder>) -> List<PassFolder>): List<PassFolder> {
        val key = k ?: throw IllegalStateException("locked")
        if (metaLocked) throw IllegalStateException("unreadable")
        repeat(4) {
            val folders = update(_state.value.folders)
            val arr = JsonArray(folders.map { JsonObject(mapOf("id" to JsonPrimitive(it.id), "name" to JsonPrimitive(it.name))) })
            val metaEnc = PassCrypto.encryptJson(JsonObject(mapOf("folders" to arr)), key)
            try {
                val res = http.patch("/vault", JsonObject(mapOf("meta_enc" to JsonPrimitive(metaEnc), "meta_base" to JsonPrimitive(PassCrypto.sha256Hex(metaSeen)))))
                metaSeen = (res["data"] as? JsonObject)?.str("meta_enc") ?: metaEnc
                _state.update { it.copy(folders = folders) }
                return folders
            } catch (e: PassHttpException) {
                val fresh = e.body?.get("data") as? JsonObject
                if (e.status != 409 || fresh == null) throw e
                metaSeen = null
                applyVault(fresh)
            }
        }
        throw PassConflictException()
    }

    suspend fun addFolder(name: String): PassFolder? {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return null
        _state.value.folders.firstOrNull { it.name.equals(clean, true) }?.let { return it }
        val folder = PassFolder(PassCrypto.newFolderId(), clean)
        val saved = saveFolders { list -> if (list.any { it.name.equals(clean, true) }) list else list + folder }
        return saved.firstOrNull { it.name.equals(clean, true) } ?: folder
    }

    suspend fun renameFolder(id: String, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        saveFolders { list -> list.map { if (it.id == id) it.copy(name = clean) else it } }
    }

    suspend fun deleteFolder(id: String) {
        saveFolders { list -> list.filter { it.id != id } }
    }

    suspend fun saveSettings(settings: PassSettings) {
        val res = http.patch(
            "/vault",
            JsonObject(
                mapOf(
                    "settings" to JsonObject(
                        mapOf(
                            "lock_after" to JsonPrimitive(settings.lockAfter),
                            "clipboard_clear" to JsonPrimitive(settings.clipboardClear),
                            "reprompt" to JsonPrimitive(settings.reprompt),
                        ),
                    ),
                ),
            ),
        )
        val s = (res["data"] as? JsonObject)?.obj("settings")
        settingsSeen = s?.toString()
        val parsed = PassSettings.from(s)
        prefs.lockAfterMinutes = parsed.lockAfter
        _state.update { it.copy(settings = parsed) }
    }

    // ---- Importação ----------------------------------------------------------

    /** Cria as pastas em falta e sobe cifrado em lotes de 200 (o 1.º leva `remaining`). */
    suspend fun import(drafts: List<PassDraft>, onProgress: (Int) -> Unit = {}): Int {
        val key = k ?: throw IllegalStateException("locked")
        if (_state.value.items.size + drafts.size > MAX_ITEMS) throw PassLimitException(0)
        val folders = _state.value.folders.toMutableList()
        val known = folders.size
        fun folderId(name: String): String? {
            val n = name.trim().take(40)
            if (n.isEmpty() || metaLocked) return null
            val f = folders.firstOrNull { it.name.equals(n, true) } ?: PassFolder(PassCrypto.newFolderId(), n).also { folders += it }
            return f.id
        }
        val now = Instant.now().toString()
        val payloads = drafts.map { d ->
            d.payload.with(
                "folder" to (folderId(d.folderName)?.let(::JsonPrimitive) ?: JsonNull),
                "events" to JsonArray(listOf(JsonObject(mapOf("at" to JsonPrimitive(now), "kind" to JsonPrimitive("imported"))))),
                "password_history" to JsonArray(emptyList()),
                "changed_at" to JsonPrimitive(now),
            )
        }
        val added = folders.drop(known)
        if (added.isNotEmpty()) saveFolders { list -> list + added.filter { a -> list.none { it.id == a.id } } }

        var done = 0
        try {
            for (start in payloads.indices step 200) {
                val slice = payloads.subList(start, minOf(start + 200, payloads.size))
                val chunk = withContext(Dispatchers.Default) { slice.map { JsonObject(mapOf("payload_enc" to JsonPrimitive(PassCrypto.encryptJson(it, key)))) } }
                val res = http.post("/items/batch", JsonObject(mapOf("items" to JsonArray(chunk), "remaining" to JsonPrimitive(payloads.size - start))))
                val created = (res["data"] as? JsonArray).orEmpty().mapIndexed { n, raw -> localItem(raw as JsonObject, slice[n]) }
                markLocal(*created.map { it.id }.toTypedArray())
                _state.update { s ->
                    val byId = s.items.associateBy { it.id }.toMutableMap()
                    created.forEach { byId[it.id] = it }
                    s.copy(items = byId.values.toList())
                }
                done += chunk.size
                onProgress(done)
            }
        } catch (e: PassHttpException) {
            // 422 sem `code` = limite de itens; com `code` (quota do cofre) segue no PassImportException.
            if (e.status == 422 && e.code == null) throw PassLimitException(done)
            throw PassImportException(done, e)
        } finally {
            if (done > 0) scheduleHealth()
        }
        return done
    }

    /** Lista portátil para o export (exporter.js toPortable). */
    fun portable(): JsonArray = JsonArray(
        _state.value.active.filter { !it.locked }.map { i ->
            val m = mutableMapOf<String, JsonElement>(
                "type" to JsonPrimitive(i.type), "name" to JsonPrimitive(i.name), "notes" to JsonPrimitive(i.notes),
                "folderName" to JsonPrimitive(_state.value.folder(i.folder)?.name.orEmpty()), "favourite" to JsonPrimitive(i.favourite),
            )
            i.payload.obj("login")?.let { m["login"] = it }
            i.payload.obj("card")?.let { m["card"] = it }
            JsonObject(m)
        },
    )

    // ---- Saúde ---------------------------------------------------------------

    fun scheduleHealth() {
        healthJob?.cancel()
        healthJob = scope.launch {
            delay(600)
            runHealth()
        }
    }

    /**
     * Análises em série (scheduleHealth corre em Default e o Recheck no viewModelScope:
     * em paralelo partilhavam o itemMemo sem lock). Só a mais recente grava o resultado
     * e desliga o healthRunning. `fresh` (Recheck) volta a perguntar ao HIBP.
     */
    suspend fun runHealth(fresh: Boolean = false) {
        if (k == null) return
        val g = gen
        // Pedido de Recheck fica pendente até alguma análise correr (uma mais recente do sync não o perde).
        if (fresh) healthFresh = true
        val run = healthRun.incrementAndGet()
        _state.update { it.copy(healthRunning = true) }
        try {
            healthMutex.withLock {
                if (run != healthRun.get()) return // já há outra mais recente na fila
                if (healthFresh) {
                    healthFresh = false
                    health.clearBreach()
                }
                val result = health.analyse(_state.value.active)
                if (g == gen && k != null && run == healthRun.get()) _state.update { it.copy(health = result) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        } finally {
            if (g == gen && run == healthRun.get()) _state.update { it.copy(healthRunning = false) }
        }
    }
}
