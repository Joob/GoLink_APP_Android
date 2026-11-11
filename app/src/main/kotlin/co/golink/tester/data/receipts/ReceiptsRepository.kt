package co.golink.tester.data.receipts

import android.util.LruCache
import co.golink.tester.data.encryption.CalendarCrypto
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.data.encryption.Envelope
import com.goterl.lazysodium.interfaces.SecretBox
import co.golink.tester.data.pass.PassCrypto
import co.golink.tester.data.pass.int
import co.golink.tester.data.pass.str
import co.golink.tester.data.pass.with
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

/** Orçamento que passou agora um limite (para a notificação local, com valores). */
data class BudgetAlert(
    val cat: String,
    val kind: String,
    val spent: Long,
    val budget: Long,
    val daysLeft: Int,
    val receiptId: String?,
    val merchant: String?,
    val total: Long?,
)

/**
 * Recibos E2E — espelho de resources/js/store/modules/receipts.js. K_rcpt (selada à
 * pubkey E2E) cifra o meta (agregado, orçamentos, alertas avisados) e cada recibo;
 * a foto é outro blob. Tudo é decifrado e agregado no aparelho; o servidor só vê ids,
 * datas, o ano fiscal e o tamanho da foto. Sync de 5 s pelo /sync (só com o ecrã aberto).
 */
@Singleton
class ReceiptsRepository @Inject constructor(
    private val http: ReceiptsHttp,
    private val keys: E2EKeyManager,
    private val cache: ReceiptsCache,
    private val prefs: ReceiptsPrefs,
) {
    companion object {
        private val SERVER_FIELDS = setOf("id", "revision", "deleted_at", "created_at", "updated_at", "locked", "has_photo", "year")
        private const val MAX_PHOTO_CACHE = 40
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ReceiptsState())
    val state: StateFlow<ReceiptsState> = _state.asStateFlow()

    private val _alerts = MutableSharedFlow<List<BudgetAlert>>(extraBufferCapacity = 4)
    val alerts: SharedFlow<List<BudgetAlert>> = _alerts.asSharedFlow()

    @Volatile private var k: ByteArray? = null
    @Volatile private var gen = 0
    private var cursor: String? = null
    private var metaSeen: String? = null
    private var stampVi: String? = null
    private var stampVv: String? = null
    private var writeSeq = 0
    private val touchedAt = HashMap<String, Int>()
    private val loadLock = Mutex()
    private val alertLock = Mutex()
    private val patchLock = Mutex()

    /** Linhas do servidor tal como vieram (cifradas) — é o que vai para a cache em disco. */
    private val rawItems = LinkedHashMap<String, JsonObject>()
    private var vaultRaw: JsonObject? = null
    private var persistJob: Job? = null

    /** Fotos decifradas (JPEG) em LRU; o que está no ecrã é o mais recente. */
    private val photos = LruCache<String, ByteArray>(MAX_PHOTO_CACHE)
    private val photoJobs = HashMap<String, Deferred<ByteArray>>()

    init {
        keys.addOnLock {
            wipe()
            _state.value = ReceiptsState()
            cache.clear()
        }
    }

    val hasKey: Boolean get() = k != null

    private fun markLocal(vararg ids: String) = synchronized(touchedAt) {
        writeSeq++
        ids.forEach { touchedAt[it] = writeSeq }
    }

    private fun wipe() {
        k?.fill(0)
        k = null
        gen++
        cursor = null
        metaSeen = null
        stampVi = null
        stampVv = null
        synchronized(touchedAt) { touchedAt.clear() }
        synchronized(rawItems) { rawItems.clear() }
        vaultRaw = null
        persistJob?.cancel()
        photos.evictAll()
        synchronized(photoJobs) { photoJobs.clear() }
    }

    // ---- Abrir ----------------------------------------------------------------

    /**
     * Abre (ou cria) o cofre e decifra os recibos. Mostra primeiro a cache cifrada do
     * aparelho (se houver) e depois só pede ao servidor o que mudou desde então.
     */
    suspend fun load(force: Boolean = false) = loadLock.withLock {
        val s = _state.value
        if (s.loaded && !force) return@withLock
        val g = gen
        _state.update { it.copy(loading = !it.loaded, failed = false) }
        if (!keys.unlocked.value && k == null) {
            _state.update { it.copy(needsKeys = true, loading = false) }
            return@withLock
        }
        if (!s.loaded) restoreFromCache()
        if (g != gen) return@withLock
        try {
            var vault = http.get("/vault")["data"] as? JsonObject
            if (g != gen) return@withLock
            if (vault == null) vault = createVault(g) ?: return@withLock
            if (k == null) k = keys.openFileDataKey(vault.str("wrapped_key")!!)
            applyVault(vault)
            if (g != gen) return@withLock
            loadItems()
            if (g != gen) return@withLock
            _state.update { it.copy(loaded = true, loading = false, needsKeys = false, failed = false) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Sem rede mas com cache: continua a mostrar o que havia.
            if (g == gen) _state.update { it.copy(loading = false, failed = !it.loaded) }
        }
    }

    private suspend fun createVault(g: Int): JsonObject? {
        val key = CalendarCrypto.genKey()
        return try {
            val res = http.post(
                "/vault",
                JsonObject(
                    mapOf(
                        "wrapped_key" to JsonPrimitive(keys.sealForSelf(key)),
                        "meta_enc" to JsonPrimitive(PassCrypto.encryptJson(ReceiptsMeta().toJson(), key)),
                    ),
                ),
            )
            if (g != gen) null else res["data"] as? JsonObject ?: throw IllegalStateException("vault")
        } catch (e: ReceiptsHttpException) {
            // Outro aparelho criou o cofre ao mesmo tempo: usa o dele.
            if (e.status != 409) throw e
            http.get("/vault")["data"] as? JsonObject ?: throw e
        } finally {
            key.fill(0)
        }
    }

    /** Cache em disco → estado (sem rede). Precisa das chaves E2E em memória. */
    private suspend fun restoreFromCache(): Boolean {
        val snap = withContext(Dispatchers.IO) { cache.read() } ?: return false
        val g = gen
        return runCatching {
            if (k == null) k = keys.openFileDataKey(snap.vault.str("wrapped_key")!!)
            applyVault(snap.vault)
            val items = withContext(Dispatchers.Default) { snap.items.map { decryptItem(it, null) } }
            if (g != gen) return false
            synchronized(rawItems) {
                rawItems.clear()
                snap.items.forEach { r -> r.str("id")?.let { rawItems[it] = r } }
            }
            cursor = snap.cursor
            _state.update { it.copy(items = items, loaded = true, loading = false, needsKeys = false) }
            true
        }.getOrDefault(false)
    }

    /** Para o worker semanal: abre com a chave do aparelho e a cache; tenta a rede. */
    suspend fun openForBackground(): Boolean {
        if (k == null && !keys.unlocked.value && !keys.restoreFromDevice()) return false
        load()
        return _state.value.loaded
    }

    private fun applyVault(vault: JsonObject) {
        vaultRaw = vault
        val settings = ReceiptsSettings.from(vault["settings"] as? JsonObject)
        prefs.weeklyEnabled = settings.weekly
        _state.update { it.copy(settings = settings) }
        val metaEnc = vault.str("meta_enc")
        if (metaEnc == metaSeen && metaSeen != null) return
        val key = k ?: return
        var meta = ReceiptsMeta()
        var unreadable = false
        if (metaEnc != null) {
            try {
                meta = ReceiptsMeta.from(PassCrypto.decryptJson(metaEnc, key))
            } catch (e: Exception) {
                unreadable = true
            }
        }
        metaSeen = metaEnc
        _state.update { it.copy(meta = meta, metaLocked = unreadable) }
        schedulePersist()
    }

    private fun decryptItem(raw: JsonObject, previous: Receipt?): Receipt {
        val id = raw.str("id")!!
        val revision = raw.int("revision") ?: 1
        val enc = raw.str("payload_enc").orEmpty()
        val deletedAt = raw.str("deleted_at")
        val updatedAt = raw.str("updated_at")
        val hasPhoto = (raw["has_photo"] as? JsonPrimitive)?.contentOrNull == "true"
        if (previous != null && previous.revision == revision && previous.enc == enc) {
            return previous.copy(deletedAt = deletedAt, updatedAt = updatedAt, hasPhoto = hasPhoto)
        }
        val year = raw.int("year")
        return try {
            val payload = PassCrypto.decryptJson(enc, k!!) as JsonObject
            Receipt(id, revision, year, hasPhoto, deletedAt, raw.str("created_at"), updatedAt, enc, payload, locked = false)
        } catch (e: Exception) {
            Receipt(id, revision, year, hasPhoto, deletedAt, raw.str("created_at"), updatedAt, enc, JsonObject(emptyMap()), locked = true)
        }
    }

    /** 1.ª vez: tudo (páginas de 500). Depois: só o que mudou desde o último `now` + ids vivos. */
    private suspend fun loadItems() {
        if (k == null) return
        val g = gen
        val startSeq = synchronized(touchedAt) { writeSeq }
        val since = if (_state.value.loaded) cursor else null
        val base = if (since != null) mapOf<String, Any?>("since" to since) else emptyMap()
        val rows = mutableListOf<JsonObject>()
        var first: JsonObject? = null
        var after: String? = null
        do {
            val res = http.get("/items", if (after != null) base + ("after" to after) else base)
            if (k == null || g != gen) return
            if (first == null) first = res
            (res["data"] as? JsonArray).orEmpty().mapNotNullTo(rows) { it as? JsonObject }
            after = res.str("next")
        } while (after != null)
        val data = first ?: return

        val current = _state.value.items
        val prev = current.associateBy { it.id }
        val fresh = withContext(Dispatchers.Default) { rows.map { decryptItem(it, prev[it.str("id")]) } }
        if (k == null || g != gen) return

        fun mineAfter(id: String) = synchronized(touchedAt) { (touchedAt[id] ?: 0) > startSeq }
        val alive: Set<String> = if (since != null) {
            (data["ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        } else fresh.map { it.id }.toSet()
        val merged = LinkedHashMap<String, Receipt>()
        current.filter { it.id in alive || mineAfter(it.id) }.forEach { merged[it.id] = it }
        var changed = merged.size != current.size
        val rawById = rows.associateBy { it.str("id") }
        for (it in fresh) {
            if (mineAfter(it.id)) continue
            val old = merged[it.id]
            if (old != null && old.revision > it.revision) continue
            // Revisão nova vinda de outro aparelho (pode ser foto trocada): larga a foto da cache.
            if (old != null && old.revision != it.revision) photos.remove(it.id)
            if (old == null || old.revision != it.revision || old.isTrashed != it.isTrashed || old.hasPhoto != it.hasPhoto) changed = true
            merged[it.id] = it
            rawById[it.id]?.let { r -> synchronized(rawItems) { rawItems[it.id] = r } }
        }
        synchronized(rawItems) { rawItems.keys.retainAll(merged.keys) }
        cursor = data.str("now")
        if (since == null) changed = true
        if (changed) _state.update { it.copy(items = merged.values.toList(), trashDays = data.int("trash_days") ?: 30) }
        schedulePersist()
    }

    /** Sync de 5 s: recarrega só a parte do carimbo que mudou (vi = recibos, vv = cofre). */
    suspend fun sync() {
        if (k == null || !_state.value.loaded) return
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

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(1500)
            val vault = vaultRaw ?: return@launch
            val items = synchronized(rawItems) { rawItems.values.toList() }
            withContext(Dispatchers.IO) { cache.write(vault, items, cursor) }
        }
    }

    // ---- Escrever ---------------------------------------------------------------

    private fun payloadOf(p: JsonObject): JsonObject = JsonObject(p.filterKeys { it !in SERVER_FIELDS && !it.startsWith("_") })

    private fun yearOf(p: JsonObject): Int? = p.str("date")?.take(4)?.toIntOrNull()?.takeIf { it in 2000..2100 }

    private fun localItem(raw: JsonObject, payload: JsonObject) = Receipt(
        id = raw.str("id")!!, revision = raw.int("revision") ?: 1, year = raw.int("year"),
        hasPhoto = (raw["has_photo"] as? JsonPrimitive)?.contentOrNull == "true",
        deletedAt = raw.str("deleted_at"), createdAt = raw.str("created_at"), updatedAt = raw.str("updated_at"),
        enc = raw.str("payload_enc").orEmpty(), payload = payload, locked = false,
    )

    private fun upsert(item: Receipt, raw: JsonObject?) {
        if (raw != null) synchronized(rawItems) { rawItems[item.id] = raw }
        _state.update { s ->
            val i = s.items.indexOfFirst { it.id == item.id }
            s.copy(items = if (i >= 0) s.items.toMutableList().also { it[i] = item } else s.items + item)
        }
        schedulePersist()
    }

    /** 409: mostra já a versão do servidor e avisa o formulário. */
    private fun conflict(e: ReceiptsHttpException): Nothing {
        (e.body?.get("data") as? JsonObject)?.let { fresh ->
            if (k != null) {
                val item = decryptItem(fresh, null)
                photos.remove(item.id)
                upsert(item, fresh)
            }
        }
        throw ReceiptsConflictException()
    }

    private fun writeBody(payload: JsonObject, key: ByteArray, revision: Int?): JsonObject {
        val m = mutableMapOf<String, JsonElement>(
            "payload_enc" to JsonPrimitive(PassCrypto.encryptJson(payload, key)),
            "year" to (yearOf(payload)?.let(::JsonPrimitive) ?: JsonNull),
        )
        if (revision != null) m["revision"] = JsonPrimitive(revision)
        return JsonObject(m)
    }

    /**
     * Grava um recibo novo ou editado e, se vier, a foto (JPEG em claro, cifrada aqui).
     * `baseRevision` = revisão que o formulário abriu (409 → [ReceiptsConflictException]).
     * Se a foto falhar depois de o recibo ficar gravado → [ReceiptPhotoException] com o
     * recibo, para repetir só a foto com [retryPhoto].
     */
    suspend fun save(id: String?, payload: JsonObject, baseRevision: Int?, photo: ByteArray?): Receipt {
        val key = k ?: throw IllegalStateException("locked")
        val prev = id?.let { _state.value.item(it) }
        if (prev?.locked == true) throw IllegalStateException("unreadable")
        val next = payloadOf(payload)
        val res = try {
            if (prev != null) http.patch("/items/${prev.id}", writeBody(next, key, baseRevision ?: prev.revision))
            else http.post("/items", writeBody(next, key, null))
        } catch (e: ReceiptsHttpException) {
            if (e.status == 409 && prev != null) conflict(e)
            throw e
        }
        val raw = res["data"] as JsonObject
        val saved = localItem(raw, next)
        markLocal(saved.id)
        upsert(saved, raw)
        checkAlertsLater(next.str("date")?.take(7), saved.id)
        if (photo == null) return saved
        return try {
            uploadPhoto(saved, next, photo)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw ReceiptPhotoException(saved, e)
        }
    }

    /** Repete só a foto de um recibo já gravado. */
    suspend fun retryPhoto(id: String, photo: ByteArray): Receipt {
        val item = _state.value.item(id) ?: throw IllegalStateException("gone")
        return uploadPhoto(item, item.payload, photo)
    }

    private suspend fun uploadPhoto(item: Receipt, payload: JsonObject, photo: ByteArray): Receipt {
        val key = k ?: throw IllegalStateException("locked")
        // nonce(24) ‖ secretbox, em binário (sem base64: −33 % de tráfego e de quota).
        val sealed = withContext(Dispatchers.Default) { Envelope.wrapSecretKey(photo, key).let { it.nonce + it.ciphertext } }
        val up = http.putBytes("/items/${item.id}/photo", sealed)
        val raw = up["data"] as JsonObject
        // O servidor sobe a revisão (os outros aparelhos largam a foto antiga).
        val saved = localItem(raw, payload)
        markLocal(saved.id)
        upsert(saved, raw)
        photos.put(saved.id, photo)
        return saved
    }

    /** Alteração pequena (categoria, validado, várias atividades). */
    suspend fun patch(id: String, vararg changes: Pair<String, JsonElement?>) = patchLock.withLock {
        // Em fila: 2 toques seguidos (chip, ✓) leem a revisão já gravada em vez de dar 409.
        val key = k ?: return@withLock
        val prev = _state.value.item(id) ?: return@withLock
        if (prev.locked) return@withLock
        val next = payloadOf(prev.payload).with(*changes)
        val res = try {
            http.patch("/items/$id", writeBody(next, key, prev.revision))
        } catch (e: ReceiptsHttpException) {
            if (e.status == 409) conflict(e)
            throw e
        }
        val raw = res["data"] as JsonObject
        markLocal(id)
        upsert(localItem(raw, next), raw)
        if (changes.any { it.first == "spend_cat" }) checkAlertsLater(next.str("date")?.take(7), id)
    }

    private fun markDeleted(id: String, deletedAt: String?) {
        markLocal(id)
        val item = _state.value.item(id) ?: return
        val raw = synchronized(rawItems) { rawItems[id] }
        upsert(item.copy(deletedAt = deletedAt), raw?.with("deleted_at" to (deletedAt?.let(::JsonPrimitive) ?: JsonNull)))
    }

    suspend fun trash(id: String) {
        http.delete("/items/$id")
        markDeleted(id, Instant.now().toString())
    }

    suspend fun restore(id: String) {
        http.post("/items/$id/restore")
        markDeleted(id, null)
    }

    suspend fun deleteForever(id: String) {
        http.delete("/items/$id/force")
        markLocal(id)
        photos.remove(id)
        synchronized(rawItems) { rawItems.remove(id) }
        _state.update { s -> s.copy(items = s.items.filter { it.id != id }) }
        schedulePersist()
    }

    suspend fun emptyTrash() {
        http.delete("/items/trash")
        val ids = _state.value.trash.map { it.id }
        markLocal(*ids.toTypedArray())
        ids.forEach { photos.remove(it) }
        synchronized(rawItems) { ids.forEach(rawItems::remove) }
        _state.update { s -> s.copy(items = s.active) }
        schedulePersist()
    }

    // ---- Fotos --------------------------------------------------------------------

    /** Foto decifrada (JPEG). `cache = false` para exportações (não empurra as do ecrã). */
    suspend fun photo(id: String, cache: Boolean = true): ByteArray {
        photos.get(id)?.let { return it }
        val key = k ?: throw IllegalStateException("locked")
        val g = gen
        val job = synchronized(photoJobs) {
            photoJobs.getOrPut(id) {
                scope.async {
                    val bytes = http.getBytes("/items/$id/photo")
                    val n = SecretBox.NONCEBYTES
                    require(bytes.size > n + SecretBox.MACBYTES) { "photo" }
                    withContext(Dispatchers.Default) { Envelope.unwrapSecretKey(bytes.copyOfRange(n, bytes.size), bytes.copyOfRange(0, n), key) }
                }.also { d -> d.invokeOnCompletion { synchronized(photoJobs) { if (photoJobs[id] === d) photoJobs.remove(id) } } }
            }
        }
        val bytes = job.await()
        if (g != gen) throw IllegalStateException("locked")
        if (cache) photos.put(id, bytes)
        return bytes
    }

    // ---- Meta / definições ------------------------------------------------------------

    /** `update(meta)` sobre a versão mais recente do servidor; 409 → aplica outra vez (até 4). */
    suspend fun saveMeta(update: (ReceiptsMeta) -> ReceiptsMeta): ReceiptsMeta {
        val key = k ?: throw IllegalStateException("locked")
        repeat(4) {
            // Também depois de um 409: se o meta novo do servidor não abre, nunca gravar por cima.
            if (_state.value.metaLocked) throw IllegalStateException("unreadable")
            val meta = update(_state.value.meta)
            val metaEnc = PassCrypto.encryptJson(meta.toJson(), key)
            try {
                val res = http.patch("/vault", JsonObject(mapOf("meta_enc" to JsonPrimitive(metaEnc), "meta_base" to JsonPrimitive(PassCrypto.sha256Hex(metaSeen)))))
                val vault = res["data"] as? JsonObject
                metaSeen = vault?.str("meta_enc") ?: metaEnc
                if (vault != null) vaultRaw = vault
                // Reler do JSON garante o mesmo estado que os outros aparelhos vão ver.
                _state.update { it.copy(meta = ReceiptsMeta.from(meta.toJson())) }
                schedulePersist()
                return _state.value.meta
            } catch (e: ReceiptsHttpException) {
                val fresh = e.body?.get("data") as? JsonObject
                if (e.status != 409 || fresh == null) throw e
                metaSeen = null
                applyVault(fresh)
            }
        }
        throw ReceiptsConflictException()
    }

    suspend fun saveSettings(alertClose: Boolean? = null, alertOver: Boolean? = null, weekly: Boolean? = null) {
        val before = _state.value.settings
        val body = buildMap<String, JsonElement> {
            alertClose?.let { put("alert_close", JsonPrimitive(it)) }
            alertOver?.let { put("alert_over", JsonPrimitive(it)) }
            weekly?.let { put("weekly", JsonPrimitive(it)) }
        }
        if (body.isEmpty()) return
        _state.update {
            it.copy(settings = it.settings.copy(alertClose = alertClose ?: it.settings.alertClose, alertOver = alertOver ?: it.settings.alertOver, weekly = weekly ?: it.settings.weekly))
        }
        try {
            val res = http.patch("/vault", JsonObject(mapOf("settings" to JsonObject(body))))
            (res["data"] as? JsonObject)?.let { v ->
                vaultRaw = v
                val s = ReceiptsSettings.from(v["settings"] as? JsonObject)
                prefs.weeklyEnabled = s.weekly
                _state.update { it.copy(settings = s) }
                schedulePersist()
            }
        } catch (e: Exception) {
            // Volta ao que estava (só as chaves deste pedido).
            _state.update {
                it.copy(settings = it.settings.copy(
                    alertClose = if (alertClose != null) before.alertClose else it.settings.alertClose,
                    alertOver = if (alertOver != null) before.alertOver else it.settings.alertOver,
                    weekly = if (weekly != null) before.weekly else it.settings.weekly,
                ))
            }
            throw e
        }
    }

    // ---- Alertas de orçamento ---------------------------------------------------------

    private fun checkAlertsLater(ym: String?, receiptId: String?) {
        if (ym == null) return
        scope.launch { runCatching { checkAlerts(ym, receiptId) } }
    }

    /**
     * Orçamentos que passaram agora um limite → marca no meta (dedup com a web),
     * aviso genérico na Inbox (sem valores) e evento local com valores.
     * Um de cada vez: o seguinte só lê `alerted` depois de o anterior gravar.
     */
    suspend fun checkAlerts(ym: String, receiptId: String?): List<BudgetAlert> = alertLock.withLock {
        if (k == null || !Regex("^\\d{4}-\\d{2}$").matches(ym)) return@withLock emptyList()
        val today = LocalDate.now()
        if (ym != Budgets.currentYm(today)) return@withLock emptyList()
        val s = _state.value
        val due = Budgets.pendingAlerts(s.active, s.meta.budgets, ym, s.meta.alerted, s.settings)
        if (due.isEmpty()) return@withLock emptyList()
        val keep = (0..2).map { Budgets.currentYm(today.withDayOfMonth(1).minusMonths(it.toLong())) }.toSet()
        saveMeta { meta ->
            val alerted = meta.alerted.toMutableMap()
            due.forEach { alerted[it.key] = 1 }
            meta.copy(alerted = alerted.filterKeys { it.take(7) in keep })
        }
        for (kind in due.map { it.kind }.toSet()) {
            scope.launch { runCatching { http.post("/alert", JsonObject(mapOf("kind" to JsonPrimitive(kind)))) } }
        }
        val cur = _state.value
        val receipt = receiptId?.let { cur.item(it) }
        val prog = Budgets.monthProgress(ym, today)
        val out = due.map { a ->
            val budget = Budgets.budgetForMonth(cur.active, cur.meta.budgets, a.cat, ym)
            BudgetAlert(
                cat = a.cat, kind = a.kind,
                spent = Budgets.spentIn(cur.active, a.cat, ym), budget = budget, daysLeft = prog.left,
                receiptId = receipt?.takeIf { it.spendCat == a.cat }?.id,
                merchant = receipt?.takeIf { it.spendCat == a.cat }?.title,
                total = receipt?.takeIf { it.spendCat == a.cat }?.total,
            )
        }
        _alerts.tryEmit(out)
        out
    }
}
