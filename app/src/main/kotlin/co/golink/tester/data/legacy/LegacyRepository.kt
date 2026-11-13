package co.golink.tester.data.legacy

import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.legacy.LegacyDataResponse
import co.golink.tester.domain.legacy.LegacyEventDto
import co.golink.tester.domain.legacy.LegacyFullDto
import co.golink.tester.domain.legacy.LegacyGuardian
import co.golink.tester.domain.legacy.LegacyItem
import co.golink.tester.domain.legacy.LegacyMeta
import co.golink.tester.domain.legacy.LegacyRecipient
import co.golink.tester.domain.legacy.LegacyVaultDto
import co.golink.tester.network.LegacyApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import retrofit2.Response

/** Escritas só neste estado do cofre (EDITABLE da web). */
val LEGACY_EDITABLE = setOf("draft", "armed", "disarmed")

data class LegacyState(
    val loaded: Boolean = false,
    val vault: LegacyVaultDto? = null,
    val guardians: List<LegacyGuardian> = emptyList(),
    val recipients: List<LegacyRecipient> = emptyList(),
    val items: List<LegacyItem> = emptyList(),
    val guardingCount: Int = 0,
) {
    // Lidos várias vezes por recomposição (barra, segmentos, gaveta): filtra uma vez por estado.
    val messages: List<LegacyItem> by lazy(LazyThreadSafetyMode.PUBLICATION) { items.filter { it.dto.type == "message" } }
    val vaultItems: List<LegacyItem> by lazy(LazyThreadSafetyMode.PUBLICATION) { items.filter { it.dto.type != "message" } }
    val editable: Boolean get() = vault != null && vault.status in LEGACY_EDITABLE
    val keysToConfirm: List<LegacyGuardian> get() = guardians.filter { LegacyCrypto.keyState(it) in setOf("unverified", "changed") }
}

/** Erro da API (`message` = legacy_*) ou do cliente. */
class LegacyException(val code: String, val status: Int = 0) : Exception(code)

/** Uma linha do Activity log, já aberta (selada à chave pública do dono). */
data class LegacyEventRow(val id: String, val category: String, val kind: String, val data: JsonObject, val createdAt: String)

data class LegacyEventsPage(val rows: List<LegacyEventRow>, val cursor: String?)

/**
 * Legacy no Android — espelho de store/modules/legacy.js. K_vault e o que dela sai
 * ficam só em memória e saem com o bloqueio E2E.
 *
 * Tudo o que lê ou escreve o cofre corre em série ([turn]): cada escrita tem de partir
 * do que a anterior deixou (o `inTurn` da web) e um GET nunca pinta por cima de uma
 * escrita mais recente.
 */
@Singleton
class LegacyRepository @Inject constructor(
    private val api: LegacyApi,
    private val keys: E2EKeyManager,
    private val filesApi: co.golink.tester.network.FilesApi,
) {
    private val _state = MutableStateFlow(LegacyState())
    val state: StateFlow<LegacyState> = _state.asStateFlow()

    private val turn = Mutex()
    private val cc = co.golink.tester.data.encryption.CalendarCrypto
    @Volatile private var kVault: ByteArray? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Sai com o bloqueio E2E, como o LEGACY_RESET da web.
        // Duas vezes: já (o ecrã esconde logo) e depois do que estava a meio em [turn], que
        // podia voltar a gravar a K_vault e as linhas decifradas por cima do reset.
        scope.launch {
            keys.unlocked.collect {
                if (!it) {
                    val stale = kVault
                    reset()
                    turn.withLock {
                        val later = kVault
                        if (!keys.isUnlocked) reset()
                        // As escritas correm em [turn]: já ninguém cifra com estas chaves.
                        // (Uma leitura fora do mutex só falha a decifrar.)
                        if (!keys.isUnlocked) {
                            stale?.fill(0)
                            later?.fill(0)
                        }
                    }
                }
            }
        }
    }

    /** Pastas do cofre já sincronizadas nesta sessão (como foldersSynced da web). */
    @Volatile private var foldersSynced = false

    fun reset() {
        kVault = null
        foldersSynced = false
        _state.value = LegacyState()
    }

    /** O separador Guarding conta os seus próprios pedidos. */
    fun setGuardingCount(count: Int) = _state.update { it.copy(guardingCount = count) }

    // ---- leitura ----

    suspend fun load() = withContext(Dispatchers.IO) {
        turn.withLock { reloadLocked() }
    }

    /** Decifra a resposta. Um meta ilegível não tira o resto do ecrã. */
    private fun decryptFull(full: LegacyFullDto, key: ByteArray): LegacyState = LegacyState(
        vault = full.vault,
        guardians = full.guardians.orEmpty().map { LegacyGuardian(it, meta(it.meta_enc, key)) },
        recipients = full.recipients.orEmpty().map { r ->
            LegacyRecipient(
                r,
                meta(r.meta_enc, key),
                r.code_vault?.let { runCatching { cc.decryptString(it, key) }.getOrNull() },
            )
        },
        items = full.items.orEmpty().map { LegacyItem(it, meta(it.meta_enc, key)) },
    )

    private fun meta(enc: String?, key: ByteArray): LegacyMeta {
        if (enc == null) return LegacyMeta(JsonObject(emptyMap()))
        return runCatching { LegacyMeta(LegacyCrypto.decryptJson(enc, key)) }
            .getOrElse { LegacyMeta(JsonObject(emptyMap()), unreadable = true) }
    }

    /**
     * Aplica a resposta de uma escrita (dentro de [turn]). Só com `vault` não volta a
     * decifrar as linhas; se a K_vault mudou e faltam as linhas, recarrega.
     */
    private suspend fun apply(full: LegacyFullDto?) {
        full ?: return
        val current = _state.value
        val rekeyed = current.vault != null && current.vault.sealed_vault_key != full.vault.sealed_vault_key
        if (full.items == null && full.guardians == null) {
            if (rekeyed) {
                reloadLocked()
                return
            }
            _state.update { it.copy(vault = full.vault) }
            return
        }
        val key = if (rekeyed || kVault == null) keys.openFileDataKey(full.vault.sealed_vault_key) else kVault!!
        val decrypted = decryptFull(full, key)
        swapKey(key)
        _state.update {
            it.copy(
                vault = decrypted.vault,
                guardians = if (full.guardians != null) decrypted.guardians else it.guardians,
                recipients = if (full.recipients != null) decrypted.recipients else it.recipients,
                items = if (full.items != null) decrypted.items else it.items,
            )
        }
    }

    /** Dentro de [turn]. A chave vem sempre da resposta: outro dispositivo pode ter feito rekey. */
    private suspend fun reloadLocked() {
        val body = api.vault().bodyOrThrow()
        val full = body.data ?: run {
            swapKey(null)
            _state.value = LegacyState(loaded = true, guardingCount = body.guarding_count)
            return
        }
        val key = keys.openFileDataKey(full.vault.sealed_vault_key)
        val decrypted = decryptFull(full, key)
        swapKey(key)
        _state.value = decrypted.copy(loaded = true, guardingCount = body.guarding_count)
    }

    /** Dentro de [turn]: troca a K_vault e apaga a anterior da memória. */
    private fun swapKey(key: ByteArray?) {
        val old = kVault
        kVault = key
        if (old != null && old !== key) old.fill(0)
    }

    /** Escrita no cofre: em série; um 409 recarrega (esta cópia ficou para trás) e o erro segue. */
    private suspend fun write(request: suspend () -> Response<LegacyDataResponse>) = withContext(Dispatchers.IO) {
        turn.withLock {
            val response = request()
            if (response.code() == 409) runCatching { reloadLocked() }
            apply(response.bodyOrThrow().data)
        }
    }

    // ---- cofre ----

    suspend fun create() = withContext(Dispatchers.IO) {
        turn.withLock {
            val vaultKeys = LegacyCrypto.createVaultKeys(keys::sealForSelf)
            val body = api.createVault(buildJsonObject {
                put("sealed_vault_key", vaultKeys.sealedVaultKey)
                put("vault_check", vaultKeys.vaultCheck)
            }).bodyOrThrow()
            kVault = vaultKeys.kVault
            apply(body.data)
            _state.update { it.copy(loaded = true) }
        }
    }

    /** arm | disarm | resume | pause (body com `until` = yyyy-MM-dd). */
    suspend fun vaultAction(action: String, body: JsonObject = JsonObject(emptyMap())) = write { api.vaultAction(action, body) }

    suspend fun saveSettings(patch: JsonObject) = write { api.updateVault(patch) }

    /** "Ainda estou aqui". @return se o check-in contou. */
    suspend fun checkinNow(): Boolean = withContext(Dispatchers.IO) {
        turn.withLock {
            val body = api.checkinNow(buildJsonObject { put("source", "manual") }).bodyOrThrow()
            apply(body.data)
            body.counted
        }
    }

    // ---- Activity log ----

    /** Não precisa do cofre: só das chaves E2E (como na web, o registo não espera pelo GET do cofre). */
    suspend fun events(category: String?, cursor: String?): LegacyEventsPage = withContext(Dispatchers.IO) {
        val body = api.events(category, cursor).bodyOrThrow()
        LegacyEventsPage(body.data.map(::openEvent), body.next_cursor)
    }

    private fun openEvent(e: LegacyEventDto): LegacyEventRow {
        val data = keys.openNameOrNull(e.payload)
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: buildJsonObject { put("kind", "unreadable") }
        val kind = (data["kind"] as? JsonPrimitive)?.content ?: "unreadable"
        // Linhas antigas gravaram estes em `settings`; seladas, o servidor não as consegue mudar.
        val category = if (kind in VAULT_KINDS) "vault" else e.category
        return LegacyEventRow(e.id, category, kind, data, e.created_at)
    }

    /** Linha do registo selada à própria chave (o servidor só vê a categoria). Falhar não falha a ação. */
    suspend fun logEvent(category: String, kind: String, data: Map<String, String> = emptyMap()) {
        runCatching {
            val payload = buildJsonObject {
                put("kind", kind)
                data.forEach { (k, v) -> put(k, v) }
            }
            val sealed = keys.sealName(payload.toString()) ?: return
            api.logEvent(buildJsonObject {
                put("category", category)
                put("payload", sealed)
            })
        }
    }

    // ---- itens (Fase 2) ----

    /** Uma escolha do seletor do Drive. */
    data class DrivePick(val id: String, val name: String, val folder: Boolean, val mime: String?, val size: Long)

    /** Calendário de entrega: dias depois da abertura (0 ou 30) e "não antes de" (yyyy-MM-dd). */
    data class ItemTiming(val delayDays: Int, val deliverAt: String?)

    private data class ManifestFile(val id: String, val path: String, val mime: String?)

    private fun requireKey(): ByteArray = kVault ?: throw LegacyException("legacy_locked")

    /** 409: esta cópia ficou para trás (rekey ou abertura noutro lado). Recarrega e deixa o erro seguir. */
    private suspend fun <T> conflictAware(block: suspend () -> T): T = try {
        block()
    } catch (e: LegacyException) {
        if (e.status == 409) runCatching { reloadLocked() }
        throw e
    }

    /** As respostas de itens não trazem o cofre, e é isto que decide o Arm (LEGACY_WROTE). */
    private fun wroteItems(items: List<LegacyItem>) = wrote(items = items)

    private fun wrote(recipients: List<LegacyRecipient>? = null, items: List<LegacyItem>? = null) = _state.update { s ->
        val r = recipients ?: s.recipients
        val i = items ?: s.items
        s.copy(
            recipients = r,
            items = i,
            vault = s.vault?.copy(ready = s.vault.ready.copy(recipients = r.isNotEmpty(), items = i.any { it.dto.state == "sealed" })),
        )
    }

    /** K_item de cada recipient: K_rcpt deriva do código. */
    private fun buildGrants(kItem: ByteArray, recipientIds: List<String>): List<Pair<String, String>> =
        recipientIds.mapNotNull { id ->
            val recipient = _state.value.recipients.firstOrNull { it.dto.id == id } ?: return@mapNotNull null
            // Sem código, K_rcpt sairia só de valores públicos e o servidor abria o item.
            val code = recipient.code ?: throw LegacyException("legacy_recipient_code_unreadable")
            id to cc.wrapKey(kItem, LegacyCrypto.deriveRecipientKey(code, id))
        }

    private fun grantsJson(grants: List<Pair<String, String>>) = kotlinx.serialization.json.buildJsonArray {
        grants.forEach { (id, wrapped) -> add(buildJsonObject { put("recipient_id", id); put("wrapped_key", wrapped) }) }
    }

    private fun meta(dto: co.golink.tester.domain.legacy.LegacyItemDto, meta: JsonObject) = LegacyItem(dto, LegacyMeta(meta))

    suspend fun readItem(id: String): JsonObject = withContext(Dispatchers.IO) {
        val body = api.item(id).bodyOrThrow().data
        val kItem = cc.unwrapKey(body.key_vault, requireKey())
        LegacyCrypto.decryptJson(body.payload_enc, kItem)
    }

    suspend fun createItem(
        type: String,
        itemState: String,
        meta: JsonObject,
        payload: JsonObject,
        recipientIds: List<String>,
        timing: ItemTiming = ItemTiming(0, null),
    ) = withContext(Dispatchers.IO) { turn.withLock { createItemLocked(type, itemState, meta, payload, recipientIds, timing) } }

    private suspend fun createItemLocked(
        type: String,
        itemState: String,
        meta: JsonObject,
        payload: JsonObject,
        recipientIds: List<String>,
        timing: ItemTiming,
    ): Pair<co.golink.tester.domain.legacy.LegacyItemDto, ByteArray> {
        // Lidos juntos: a época enviada tem de ser a da chave e dos códigos usados.
        val key = requireKey()
        val vault = _state.value.vault ?: throw LegacyException("legacy_no_vault")
        val kItem = cc.genKey()
        val body = buildJsonObject {
            put("key_epoch", vault.key_epoch)
            put("id", java.util.UUID.randomUUID().toString())
            put("type", type)
            put("state", itemState)
            put("meta_enc", LegacyCrypto.encryptJson(meta, key))
            put("key_vault", cc.wrapKey(kItem, key))
            put("payload_enc", LegacyCrypto.encryptJson(payload, kItem))
            put("delay_days", timing.delayDays)
            put("deliver_at", timing.deliverAt)
            put("grants", grantsJson(buildGrants(kItem, recipientIds)))
        }
        val dto = conflictAware { api.createItem(body).bodyOrThrow().data }
        wroteItems(_state.value.items + meta(dto, meta))
        return dto to kItem
    }

    /** Só muda o que vier não-nulo; [timing] envia delay_days e deliver_at juntos. */
    suspend fun updateItem(
        id: String,
        meta: JsonObject? = null,
        payload: JsonObject? = null,
        recipientIds: List<String>? = null,
        itemState: String? = null,
        timing: ItemTiming? = null,
    ) = withContext(Dispatchers.IO) { turn.withLock { updateItemLocked(id, meta, payload, recipientIds, itemState, timing) } }

    private suspend fun updateItemLocked(
        id: String,
        meta: JsonObject?,
        payload: JsonObject?,
        recipientIds: List<String>?,
        itemState: String?,
        timing: ItemTiming?,
    ) {
        val key = requireKey()
        val vault = _state.value.vault ?: throw LegacyException("legacy_no_vault")
        val item = _state.value.items.firstOrNull { it.dto.id == id } ?: throw LegacyException("legacy_bad_item")
        val kItem = cc.unwrapKey(item.dto.key_vault ?: throw LegacyException("legacy_bad_item"), key)
        val body = buildJsonObject {
            put("key_epoch", vault.key_epoch)
            if (meta != null) put("meta_enc", LegacyCrypto.encryptJson(meta, key))
            if (payload != null) put("payload_enc", LegacyCrypto.encryptJson(payload, kItem))
            if (recipientIds != null) put("grants", grantsJson(buildGrants(kItem, recipientIds)))
            if (itemState != null) put("state", itemState)
            if (timing != null) {
                put("delay_days", timing.delayDays)
                put("deliver_at", timing.deliverAt)
            }
        }
        val dto = conflictAware { api.updateItem(id, body).bodyOrThrow().data }
        wroteItems(_state.value.items.map { if (it.dto.id == id) LegacyItem(dto, if (meta != null) LegacyMeta(meta) else it.meta) else it })
    }

    /**
     * Dá itens a quem ainda não os tem (anexos de mensagens): só acrescenta, nunca
     * revoga. Quem já os tem não volta a ser embrulhado.
     */
    suspend fun grantItems(itemIds: List<String>, recipientIds: List<String>) = withContext(Dispatchers.IO) {
        turn.withLock {
            val key = requireKey()
            val vault = _state.value.vault ?: throw LegacyException("legacy_no_vault")
            val grants = mutableListOf<Triple<String, String, String>>()
            for (item in _state.value.items.filter { it.dto.id in itemIds }) {
                val missing = recipientIds.filter { it !in item.dto.recipient_ids }
                if (missing.isEmpty()) continue
                val kItem = cc.unwrapKey(item.dto.key_vault ?: continue, key)
                buildGrants(kItem, missing).forEach { (rid, wrapped) -> grants += Triple(item.dto.id, rid, wrapped) }
            }
            if (grants.isEmpty()) return@withLock
            val body = buildJsonObject {
                put("key_epoch", vault.key_epoch)
                put("grants", kotlinx.serialization.json.buildJsonArray {
                    grants.forEach { (itemId, rid, wrapped) ->
                        add(buildJsonObject { put("item_id", itemId); put("recipient_id", rid); put("wrapped_key", wrapped) })
                    }
                })
            }
            conflictAware { api.addGrants(body).bodyOrThrow() }
            wroteItems(_state.value.items.map { item ->
                val added = grants.filter { it.first == item.dto.id }.map { it.second }
                if (added.isEmpty()) item else item.copy(dto = item.dto.copy(recipient_ids = (item.dto.recipient_ids + added).distinct()))
            })
        }
    }

    suspend fun removeItem(id: String) = withContext(Dispatchers.IO) {
        turn.withLock {
            val item = _state.value.items.firstOrNull { it.dto.id == id }
            conflictAware { api.deleteItem(id).bodyOrThrow() }
            wroteItems(_state.value.items.filter { it.dto.id != id })
            logEvent("vault", "item_removed", mapOf("name" to (item?.meta?.str("name") ?: item?.meta?.str("subject") ?: "")))
        }
    }

    /**
     * Ficheiros e pastas do Drive, um item cada. Fica em rascunho até ter as chaves todas:
     * a meio não conta para armar nem chega a ninguém.
     * @return ficheiros sem encriptação, que ficaram de fora
     */
    suspend fun addDriveItems(
        entries: List<DrivePick>,
        recipientIds: List<String>,
        delayDays: Int,
        onProgress: (Int, Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        turn.withLock {
            var skipped = 0
            try {
                // Listar tudo primeiro: o progresso é sobre a escolha inteira, não por pasta.
                val listings = entries.map { entry ->
                    entry to if (entry.folder) listFolder(entry.id, null) else listOf(ManifestFile(entry.id, entry.name, entry.mime)) to 0
                }
                val total = listings.sumOf { it.second.first.size }
                var base = 0
                onProgress(0, total)
                for ((entry, listing) in listings) {
                    val (files, notEncrypted) = listing
                    val meta = buildJsonObject {
                        put("name", entry.name)
                        put("folder_id", if (entry.folder) entry.id else null)
                        put("password", !entry.folder && PASSWORD_NAME.containsMatchIn(entry.name))
                    }
                    val (item, kItem) = createItemLocked(
                        if (entry.folder) "folder" else "file",
                        "draft",
                        meta,
                        buildJsonObject { put("name", entry.name) },
                        recipientIds,
                        ItemTiming(delayDays, null),
                    )
                    val discard: suspend () -> Unit = { runCatching { api.deleteItem(item.id) } }
                    try {
                        val offset = base
                        base += files.size
                        val missing = attachFiles(item.id, kItem, files) { done -> onProgress(offset + done, total) }
                        skipped += notEncrypted + missing
                        if (!entry.folder && missing > 0) {
                            discard()
                            continue
                        }
                        updateItemLocked(item.id, null, null, null, "sealed", null)
                    } catch (e: Exception) {
                        discard()
                        throw e
                    }
                    logEvent("vault", "item_added", mapOf("name" to entry.name))
                }
            } finally {
                runCatching { reloadLocked() }
            }
            skipped
        }
    }

    /** @return quantos ficaram de fora por não terem chave E2E */
    private suspend fun attachFiles(itemId: String, kItem: ByteArray, listing: List<ManifestFile>, onProgress: (Int) -> Unit): Int {
        var processed = 0
        var done = 0
        for (batch in listing.chunked(FILE_KEYS_BATCH)) {
            val resp = filesApi.fileEncryptionKeysBatch(co.golink.tester.domain.encryption.FileIdsBody(batch.map { it.id }))
            if (!resp.isSuccessful) throw LegacyException("http_${resp.code()}", resp.code())
            val wrapped = resp.body()?.keys.orEmpty().associate { it.file_id to it.wrapped_data_key }
            val rows = batch.mapNotNull { file ->
                // Conta também os que ficam de fora (sem chave E2E): o progresso não pára neles.
                if (++processed % 25 == 0) onProgress(processed)
                val sealed = wrapped[file.id] ?: return@mapNotNull null
                val dataKey = keys.openFileDataKey(sealed)
                done++
                buildJsonObject {
                    put("file_id", file.id)
                    put("wrapped_key", cc.wrapKey(dataKey, kItem))
                    put("meta_enc", LegacyCrypto.encryptJson(buildJsonObject { put("path", file.path); put("mime", file.mime) }, kItem))
                }
            }
            for (chunk in rows.chunked(FILE_UPLOAD_BATCH)) {
                api.addFiles(itemId, buildJsonObject { put("files", kotlinx.serialization.json.JsonArray(chunk)) }).bodyOrThrow()
            }
        }
        onProgress(listing.size)
        return listing.size - done
    }

    /** Ficheiros E2E de uma pasta, com o caminho decifrado. Com [missingFor], só os que esse item ainda não tem. */
    private suspend fun listFolder(folderId: String, missingFor: String?): Pair<List<ManifestFile>, Int> {
        val data = api.folderManifest(folderId, missingFor).bodyOrThrow().data
        if (data.files.isEmpty()) return emptyList<ManifestFile>() to data.skipped
        fun open(row: co.golink.tester.domain.legacy.LegacyManifestRow) = keys.openNameOrNull(row.name_encrypted) ?: row.name.orEmpty()
        val folders = data.folders.associate { it.id to (it.parent_id to open(it)) }
        fun pathOf(id: String?): List<String> {
            val parts = ArrayDeque<String>()
            var at = id
            while (at != null && at != folderId && at in folders) {
                val (parent, name) = folders.getValue(at)
                parts.addFirst(name)
                at = parent
            }
            return parts
        }
        return data.files.map { ManifestFile(it.id, (pathOf(it.parent_id) + open(it)).joinToString("/"), it.mimetype) } to data.skipped
    }

    private val syncing = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Ficheiros novos das pastas do cofre: sem a chave deles, nunca chegariam a ninguém.
     * Uma vez por sessão, e só marca feito se tudo correu bem. Lista fora de [turn] (as
     * escritas não esperam pelos manifestos) e só prende o turno para acrescentar.
     */
    suspend fun syncFolders() = withContext(Dispatchers.IO) {
        if (foldersSynced || !syncing.compareAndSet(false, true)) return@withContext
        try {
            val vault = _state.value.vault ?: return@withContext
            if (vault.status !in LEGACY_EDITABLE || vault.rekey_required) return@withContext
            var ok = true
            for (snapshot in _state.value.items.filter { it.dto.type == "folder" && it.meta.str("folder_id") != null }) {
                try {
                    val fresh = try {
                        listFolder(snapshot.meta.str("folder_id")!!, snapshot.dto.id).first
                    } catch (e: LegacyException) {
                        // A pasta foi apagada: o que já lá estava continua no item.
                        if (e.status == 404) continue else throw e
                    }
                    if (fresh.isEmpty()) continue
                    turn.withLock {
                        // Relido dentro do turno: um rekey entretanto trocou o key_vault.
                        val item = _state.value.items.firstOrNull { it.dto.id == snapshot.dto.id } ?: return@withLock
                        if (_state.value.vault?.status !in LEGACY_EDITABLE) return@withLock
                        val kItem = cc.unwrapKey(item.dto.key_vault ?: return@withLock, requireKey())
                        attachFiles(item.dto.id, kItem, fresh) {}
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("Legacy", "sincronizar pasta falhou", e)
                    ok = false
                }
            }
            if (ok) foldersSynced = true
        } finally {
            syncing.set(false)
        }
    }

    // ---- guardiões, recipients e rekey (Fase 3) ----


    /** Parte de um guardião pronta a enviar (sealGuardians da web). */
    private data class PlacedGuardian(
        val g: LegacyGuardian,
        val slot: Int,
        val shareVault: String?,
        val sealedShare: String?,
        val sealedPubkey: String?,
        val challenge: LegacyCrypto.Challenge?,
    )

    /** Parte de cada guardião + blob selado às chaves fixadas pelo dono, com o roster completo. */
    private fun sealGuardians(rows: List<LegacyGuardian>, key: ByteArray, vaultId: String, epoch: Int, threshold: Int, resplit: Boolean): List<PlacedGuardian> {
        val shares = if (resplit) LegacyCrypto.splitVaultKey(key, threshold, rows.size)
        else rows.map { cc.openBytes(it.dto.share_vault ?: throw LegacyException("legacy_share_missing"), key) }
        try {
            val placed = rows.mapIndexed { i, g ->
                Triple(g, if (resplit) i + 1 else (g.dto.slot ?: (i + 1)), if (resplit) cc.sealBytes(shares[i], key) else g.dto.share_vault)
            }
            val roster = placed.mapNotNull { (g, slot, _) ->
                LegacyCrypto.pinnedKey(g)?.takeIf { g.dto.status == "accepted" }?.let { LegacyCrypto.RosterEntry(slot, g.dto.id, it) }
            }
            return placed.mapIndexed { i, (g, slot, shareVault) ->
                val publicKey = LegacyCrypto.pinnedKey(g)
                if (g.dto.status != "accepted" || publicKey == null) return@mapIndexed PlacedGuardian(g, slot, shareVault, null, null, null)
                // Selar a uma chave nova não prova que o guardião a tem: é preciso outro desafio.
                val unproven = !g.dto.sealed || g.dto.sealed_pubkey != publicKey
                PlacedGuardian(
                    g, slot, shareVault,
                    LegacyCrypto.sealGuardianBlob(vaultId, epoch, slot, shares[i], threshold, placed.size, roster, publicKey),
                    publicKey,
                    if (unproven) LegacyCrypto.createChallenge(publicKey) else null,
                )
            }
        } finally {
            shares.forEach { it.fill(0) }
        }
    }

    /** Dentro de [turn]. A época enviada tem de ser a da chave e das linhas usadas. */
    private suspend fun pushGuardiansLocked(rows: List<LegacyGuardian>, threshold: Int, resplit: Boolean) {
        // Sem a chave fixada, a pessoa perdia a parte sem ninguém dar por isso.
        if (rows.any { it.meta.unreadable && it.dto.sealed }) throw LegacyException("legacy_guardian_unreadable")
        val key = requireKey()
        val vault = _state.value.vault ?: throw LegacyException("legacy_no_vault")
        val sealed = if (rows.isEmpty()) emptyList() else sealGuardians(rows, key, vault.id, vault.key_epoch + 1, threshold, resplit)
        val body = conflictAware {
            api.syncGuardians(buildJsonObject {
                put("key_epoch", vault.key_epoch)
                put("threshold", threshold)
                put("guardians", kotlinx.serialization.json.buildJsonArray {
                    sealed.forEach { p ->
                        add(buildJsonObject {
                            put("id", p.g.dto.id)
                            put("email", p.g.dto.email)
                            put("slot", p.slot)
                            put("meta_enc", p.g.dto.meta_enc)
                            put("share_vault", p.shareVault)
                            put("sealed_share", p.sealedShare)
                            put("sealed_pubkey", p.sealedPubkey)
                            put("sealed_challenge", p.challenge?.sealedChallenge)
                            put("challenge_hash", p.challenge?.challengeHash)
                        })
                    }
                })
            }).bodyOrThrow()
        }
        apply(body.data)
    }

    private fun personMeta(name: String, relation: String) = buildJsonObject { put("name", name); put("relation", relation) }

    /** @return false se o guardião ficou gravado mas não entrou como recipient. */
    suspend fun addGuardian(email: String, name: String, relation: String): Boolean = withContext(Dispatchers.IO) {
        turn.withLock {
            val key = requireKey()
            val s = _state.value
            val vault = s.vault ?: throw LegacyException("legacy_no_vault")
            val meta = personMeta(name, relation)
            val row = LegacyGuardian(
                co.golink.tester.domain.legacy.LegacyGuardianDto(
                    id = java.util.UUID.randomUUID().toString(),
                    email = email,
                    status = "invited",
                    meta_enc = LegacyCrypto.encryptJson(meta, key),
                ),
                LegacyMeta(meta),
            )
            val rows = s.guardians + row
            // O mínimo sobe para 2 mal haja 2 guardiões: "1 de n" tem de ser uma escolha.
            pushGuardiansLocked(rows, minOf(maxOf(vault.threshold, 2), rows.size), resplit = true)
        }
        // Quem é guardião costuma receber também: entra logo como recipient.
        if (_state.value.recipients.any { it.dto.email == email.lowercase() }) return@withContext true
        runCatching { addRecipient(email, name, relation) }.isSuccess
    }

    suspend fun removeGuardian(id: String) = withContext(Dispatchers.IO) {
        turn.withLock {
            val rows = _state.value.guardians.filter { it.dto.id != id }
            val threshold = maxOf(1, minOf(_state.value.vault?.threshold ?: 1, rows.size))
            pushGuardiansLocked(rows, threshold, resplit = true)
        }
    }

    suspend fun setThreshold(threshold: Int) = withContext(Dispatchers.IO) {
        turn.withLock { pushGuardiansLocked(_state.value.guardians, threshold, resplit = true) }
    }

    /** Só depois de o dono comparar a impressão digital: é isto que autoriza selar para esta chave. */
    suspend fun pinGuardianKey(id: String, publicKey: String) = withContext(Dispatchers.IO) {
        turn.withLock {
            val key = requireKey()
            val s = _state.value
            val guardian = s.guardians.firstOrNull { it.dto.id == id }
            if (guardian?.dto?.public_key != publicKey) throw LegacyException("legacy_key_moved")
            // meta veio {}: gravá-lo apagava o nome e a relação originais.
            if (guardian.meta.unreadable) throw LegacyException("legacy_guardian_unreadable")
            val metaJson = JsonObject(guardian.meta.json + mapOf(
                "id" to JsonPrimitive(id),
                "email" to JsonPrimitive(guardian.dto.email),
                "pinned_key" to JsonPrimitive(publicKey),
            ))
            val pinned = LegacyGuardian(guardian.dto.copy(meta_enc = LegacyCrypto.encryptJson(metaJson, key)), LegacyMeta(metaJson))
            pushGuardiansLocked(s.guardians.map { if (it.dto.id == id) pinned else it }, s.vault!!.threshold, resplit = false)
            logEvent("people", "guardian_key_pinned", mapOf("name" to (guardian.meta.str("name") ?: guardian.dto.email)))
        }
    }

    /** Estas respostas trazem public_key a null: não é a chave do guardião que mudou. */
    private fun patchGuardian(fresh: co.golink.tester.domain.legacy.LegacyGuardianDto) = _state.update { s ->
        s.copy(guardians = s.guardians.map { if (it.dto.id == fresh.id) it.copy(dto = fresh.copy(public_key = it.dto.public_key)) else it })
    }

    // Em [turn], como as outras escritas: o patch não pinta por cima de um reload ou de uma
    // escrita em curso, e um 409 (cofre mudou de estado) recarrega antes de o erro seguir.
    suspend fun reinviteGuardian(id: String) = withContext(Dispatchers.IO) {
        turn.withLock {
            val response = api.reinviteGuardian(id)
            if (response.code() == 409) runCatching { reloadLocked() }
            patchGuardian(response.bodyOrThrow().data)
        }
    }

    suspend fun reconfirmGuardian(id: String) = withContext(Dispatchers.IO) {
        turn.withLock {
            val guardian = _state.value.guardians.firstOrNull { it.dto.id == id } ?: return@withLock
            val publicKey = LegacyCrypto.pinnedKey(guardian) ?: throw LegacyException("legacy_not_accepted")
            val challenge = LegacyCrypto.createChallenge(publicKey)
            val response = api.reconfirmGuardian(id, buildJsonObject {
                put("sealed_challenge", challenge.sealedChallenge)
                put("challenge_hash", challenge.challengeHash)
            })
            if (response.code() == 409) runCatching { reloadLocked() }
            patchGuardian(response.bodyOrThrow().data)
        }
    }

    private fun wroteRecipients(recipients: List<LegacyRecipient>, items: List<LegacyItem>? = null) = wrote(recipients, items)

    suspend fun addRecipient(email: String, name: String, relation: String) = withContext(Dispatchers.IO) {
        turn.withLock {
            val key = requireKey()
            val vault = _state.value.vault ?: throw LegacyException("legacy_no_vault")
            val code = LegacyCrypto.generateCode()
            val meta = personMeta(name, relation)
            val dto = conflictAware {
                api.createRecipient(buildJsonObject {
                    put("key_epoch", vault.key_epoch)
                    put("id", java.util.UUID.randomUUID().toString())
                    put("email", email)
                    put("meta_enc", LegacyCrypto.encryptJson(meta, key))
                    put("code_vault", cc.encryptString(code, key))
                }).bodyOrThrow().data
            }
            wroteRecipients(_state.value.recipients + LegacyRecipient(dto, LegacyMeta(meta), code))
        }
    }

    suspend fun updateRecipient(id: String, email: String, name: String, relation: String) = withContext(Dispatchers.IO) {
        turn.withLock {
            val key = requireKey()
            val vault = _state.value.vault ?: throw LegacyException("legacy_no_vault")
            val meta = personMeta(name, relation)
            val dto = conflictAware {
                api.updateRecipient(id, buildJsonObject {
                    put("key_epoch", vault.key_epoch)
                    put("email", email)
                    put("meta_enc", LegacyCrypto.encryptJson(meta, key))
                }).bodyOrThrow().data
            }
            wroteRecipients(_state.value.recipients.map { if (it.dto.id == id) it.copy(dto = dto, meta = LegacyMeta(meta)) else it })
        }
    }

    suspend fun removeRecipient(id: String) = withContext(Dispatchers.IO) {
        turn.withLock {
            conflictAware { api.deleteRecipient(id).bodyOrThrow() }
            val s = _state.value
            wroteRecipients(
                s.recipients.filter { it.dto.id != id },
                s.items.map { i -> i.copy(dto = i.dto.copy(recipient_ids = i.dto.recipient_ids - id)) },
            )
        }
    }

    private fun rekeyedMeta(enc: String?, meta: LegacyMeta, key: ByteArray): String? =
        if (meta.unreadable) enc else LegacyCrypto.encryptJson(meta.json, key)

    /** Nova K_vault: chaves, códigos e embrulhos novos. K_item e os ficheiros ficam. Dentro de [turn]. */
    private suspend fun rekeyLocked() {
        val s = _state.value
        val vault = s.vault ?: return
        // Sem a chave fixada, a parte nova não tinha para quem ser selada.
        if (s.guardians.any { it.meta.unreadable && it.dto.sealed }) throw LegacyException("legacy_guardian_unreadable")
        val old = requireKey()
        val fresh = LegacyCrypto.createVaultKeys(keys::sealForSelf)
        val kVault = fresh.kVault

        val recipients = s.recipients.map { r -> Triple(r, LegacyCrypto.generateCode(), rekeyedMeta(r.dto.meta_enc, r.meta, kVault)) }
        val codes = recipients.associate { (r, code, _) -> r.dto.id to code }
        val items = s.items.map { item ->
            val kItem = cc.unwrapKey(item.dto.key_vault ?: throw LegacyException("legacy_item_key_missing"), old)
            try { buildJsonObject {
                put("id", item.dto.id)
                put("meta_enc", rekeyedMeta(item.dto.meta_enc, item.meta, kVault))
                put("key_vault", cc.wrapKey(kItem, kVault))
                put("grants", kotlinx.serialization.json.buildJsonArray {
                    item.dto.recipient_ids.forEach { rid ->
                        val code = codes[rid] ?: return@forEach
                        add(buildJsonObject {
                            put("recipient_id", rid)
                            put("wrapped_key", cc.wrapKey(kItem, LegacyCrypto.deriveRecipientKey(code, rid)))
                        })
                    }
                })
            } } finally { kItem.fill(0) }
        }
        val guardianRows = s.guardians.map { g -> g.copy(dto = g.dto.copy(meta_enc = rekeyedMeta(g.dto.meta_enc, g.meta, kVault))) }
        val guardians = if (guardianRows.isEmpty()) emptyList()
        else sealGuardians(guardianRows, kVault, vault.id, vault.key_epoch + 1, vault.threshold, resplit = true)

        val body = conflictAware {
            api.rekey(buildJsonObject {
                put("key_epoch", vault.key_epoch)
                put("sealed_vault_key", fresh.sealedVaultKey)
                put("vault_check", fresh.vaultCheck)
                put("guardians", kotlinx.serialization.json.buildJsonArray {
                    guardians.forEach { p ->
                        add(buildJsonObject {
                            put("id", p.g.dto.id)
                            put("meta_enc", p.g.dto.meta_enc)
                            put("share_vault", p.shareVault)
                            put("sealed_share", p.sealedShare)
                            put("sealed_pubkey", p.sealedPubkey)
                        })
                    }
                })
                put("recipients", kotlinx.serialization.json.buildJsonArray {
                    recipients.forEach { (r, code, metaEnc) ->
                        add(buildJsonObject {
                            put("id", r.dto.id)
                            put("meta_enc", metaEnc)
                            put("code_vault", cc.encryptString(code, kVault))
                        })
                    }
                })
                put("items", kotlinx.serialization.json.buildJsonArray { items.forEach { add(it) } })
            }).bodyOrThrow()
        }
        swapKey(kVault)
        apply(body.data)
    }

    /**
     * O trabalho que só o cliente do dono consegue fazer (legacyReconcile da web): rekey
     * pendente e partes por selar a chaves já fixadas. Uma chave nova espera pelo dono.
     */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        turn.withLock {
            val s = _state.value
            val vault = s.vault ?: return@withLock
            if (vault.rekey_required) {
                rekeyLocked()
                return@withLock
            }
            val unsealed = s.guardians.any { g ->
                val pinned = LegacyCrypto.pinnedKey(g)
                g.dto.status == "accepted" && pinned != null && g.dto.sealed_pubkey != pinned
            }
            if (unsealed && vault.status in LEGACY_EDITABLE) pushGuardiansLocked(s.guardians, vault.threshold, resplit = false)
        }
    }

    private fun <T> Response<T>.bodyOrThrow(): T = legacyBody()

    private companion object {
        val VAULT_KINDS = setOf("created", "armed", "disarmed", "revoked", "opened")
        const val FILE_KEYS_BATCH = 1000 // limite do /files/encryption-keys é 2000
        const val FILE_UPLOAD_BATCH = 500
        val PASSWORD_NAME = Regex("(password|passe|senha|contrase|\\.kdbx$|\\.1pux$|\\.opvault$)", RegexOption.IGNORE_CASE)
    }
}

private val errorJson = Json { ignoreUnknownKeys = true }

/** Corpo da resposta ou [LegacyException] com o `message` da API (legacy_*). */
internal fun <T> Response<T>.legacyBody(): T {
    if (isSuccessful) return body() ?: throw LegacyException("legacy_empty", code())
    val error = runCatching { errorJson.parseToJsonElement(errorBody()?.string().orEmpty()).jsonObject }.getOrNull()
    // Step-up de desarmar/pausar: o 422 vem em `errors` (password ou passkey_token).
    val fields = error?.get("errors") as? JsonObject
    if (code() == 422 && fields != null) {
        if ("passkey_token" in fields) throw LegacyException("legacy_step_up_expired", 422)
        if ("password" in fields) throw LegacyException("legacy_step_up_password", 422)
    }
    val message = (error?.get("message") as? JsonPrimitive)?.content
    throw LegacyException(message?.takeIf { it.isNotBlank() } ?: "http_${code()}", code())
}
