package co.golink.tester.data.notes

import co.golink.tester.data.encryption.CalendarCrypto
import co.golink.tester.data.encryption.E2EKeyManager
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Meta cifrado de uma nota (o que a lista mostra) — EMPTY_META na web. */
data class NoteMeta(
    val title: String = "",
    val snippet: String = "",
    val tags: List<String> = emptyList(),
    val taskTotal: Int = 0,
    val taskDone: Int = 0,
    val hasFiles: Boolean = false,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("title", title)
        put("snippet", snippet)
        put("tags", JsonArray(tags.map(::JsonPrimitive)))
        put("task_total", taskTotal)
        put("task_done", taskDone)
        put("has_files", hasFiles)
    }

    companion object {
        fun from(o: JsonObject?): NoteMeta = NoteMeta(
            title = o.jstr("title").orEmpty(),
            snippet = o.jstr("snippet").orEmpty(),
            tags = (o?.get("tags") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            taskTotal = o?.get("task_total")?.jsonPrimitive?.intOrNull ?: 0,
            taskDone = o?.get("task_done")?.jsonPrimitive?.intOrNull ?: 0,
            hasFiles = o?.get("has_files")?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }
}

data class NoteMember(
    val userId: String,
    val permission: String,
    val name: String,
    val email: String?,
    val avatar: String?,
    val color: String?,
    val online: Boolean,
    val lastSeenAt: String?,
)

data class NoteItem(
    val id: String,
    val ownerId: String?,
    val version: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val deletedAt: String?,
    val commentsCount: Int,
    val filesCount: Int,
    val permission: String,
    val wrappedKey: String?,
    val notebookId: String?,
    val pinnedAt: String?,
    val members: List<NoteMember>,
    val metaEnc: String?,
    val meta: NoteMeta,
    /** K_note aberta (null = cadeado: chave ilegível ou E2E trancado). */
    val key: ByteArray?,
) {
    val locked: Boolean get() = key == null
    val canEdit: Boolean get() = permission == "owner" || permission == "edit"
    val isOwner: Boolean get() = permission == "owner"
    val pinned: Boolean get() = pinnedAt != null
    val shared: Boolean get() = members.size > 1
    val updatedInstant: Instant? get() = updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }

    // ByteArray quebra o equals gerado: a chave segue a wrapped_key.
    override fun equals(other: Any?): Boolean = other is NoteItem && other.id == id && other.version == version &&
        other.updatedAt == updatedAt && other.deletedAt == deletedAt && other.commentsCount == commentsCount &&
        other.filesCount == filesCount && other.permission == permission && other.wrappedKey == wrappedKey &&
        other.notebookId == notebookId && other.pinnedAt == pinnedAt && other.members == members &&
        other.meta == meta && other.locked == locked

    override fun hashCode(): Int = id.hashCode() * 31 + (updatedAt?.hashCode() ?: 0)
}

data class NoteNotebook(val id: String, val name: String, val color: String, val position: Int)

data class NotesState(
    val loaded: Boolean = false,
    val notes: List<NoteItem> = emptyList(),
    val notebooks: List<NoteNotebook> = emptyList(),
    val trashDays: Int = 30,
    val error: Boolean = false,
)

/** Caixa de uma nota (Checklists), já decifrada. */
data class NoteTask(
    val noteId: String,
    val uid: String,
    val text: String,
    val done: Boolean,
    val dueDate: String?,
    val assigneeId: String?,
    val noteTitle: String,
    val canEdit: Boolean,
)

/** Y.Doc de leitura abertos ao mesmo tempo no NotesCore (pesquisa, Checklists, histórico). */
private const val MAX_OPEN_CONTENT = 6

/** Versão guardada de uma nota (Histórico). */
data class NoteVersion(val id: String, val createdAt: String?, val changes: Int, val authorName: String?, val authorId: String?)

/**
 * Notes nativas: lista, cadernos, tarefas e conteúdo fora do editor — o mesmo que
 * store/modules/notes.js na web. Tudo decifrado aqui (K_note pela chave privada);
 * o servidor só vê ciphertext. Fica em memória enquanto a app vive: voltar às
 * Notes é instantâneo e o /sync (a cada 5 s) diz quando recarregar.
 */
@Singleton
class NotesRepository @Inject constructor(
    private val http: NotesHttp,
    private val keys: E2EKeyManager,
    private val core: NotesCore,
    private val keyCache: NoteKeyCache,
) {
    private val _state = MutableStateFlow(NotesState())
    val state: StateFlow<NotesState> = _state.asStateFlow()

    private val _trash = MutableStateFlow<List<NoteItem>?>(null)
    val trash: StateFlow<List<NoteItem>?> = _trash.asStateFlow()

    /** wrapped_key → K_note (abrir é crypto_box_seal_open: caro em listas grandes). */
    /** meta_enc → meta (decifrar só o que mudou). */
    private val metaCache = ConcurrentHashMap<String, NoteMeta>()
    private val loadMutex = Mutex()
    private var lastStamp: String? = null
    private val random = SecureRandom()
    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    init {
        keys.addOnLock {
            // As K_note (NoteKeyCache) são zeradas no seu próprio addOnLock.
            metaCache.clear()
            contentStamps.clear()
            // O texto decifrado das notas também vive no QuickJS: fecha os Y.Doc de leitura.
            bg.launch { closeAllContent() }
            _state.value = NotesState()
            _trash.value = null
            lastStamp = null
        }
        // Lista carregada com o E2E ainda trancado (todas 🔒): ao desbloquear recarrega,
        // senão o /sync seguinte só tomava o carimbo como base e ficavam trancadas.
        bg.launch {
            keys.unlocked.collect { open ->
                val s = _state.value
                if (open && (!s.loaded || s.notes.any { it.locked && !it.wrappedKey.isNullOrEmpty() })) refresh()
            }
        }
    }

    fun note(id: String): NoteItem? = _state.value.notes.firstOrNull { it.id == id } ?: _trash.value?.firstOrNull { it.id == id }

    // ---- lista ---------------------------------------------------------------

    suspend fun refresh(): Result<Unit> = runCatching {
        // Trancado não há chaves: a lista vinha toda 🔒. O desbloqueio recarrega.
        if (!keys.isUnlocked) return@runCatching
        loadMutex.withLock {
            coroutineScope {
                val notes = async { http.get("/notes") }
                val notebooks = async { http.get("/notebooks") }
                val n = notes.await()
                val parsed = decryptAll((n["data"] as? JsonArray).orEmpty())
                val nbs = parseNotebooks(notebooks.await())
                _state.value = NotesState(
                    loaded = true,
                    notes = parsed,
                    notebooks = nbs,
                    trashDays = n["trash_days"]?.jsonPrimitive?.intOrNull ?: _state.value.trashDays,
                )
            }
        }
    }.onFailure { if (!_state.value.loaded) _state.update { it.copy(error = true) } }

    /** Carimbo barato: true se mudou algo e a lista foi recarregada. */
    suspend fun syncIfChanged(): Boolean {
        val v = runCatching { http.get("/sync")["v"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return false
        if (v == lastStamp) return false
        // 1.º carimbo com a lista já carregada (ou a carregar, no arranque): é só a base.
        if (lastStamp == null && (_state.value.loaded || loadMutex.isLocked)) {
            lastStamp = v
            return false
        }
        // Só avança o carimbo se recarregou: uma falha volta a tentar no próximo ciclo.
        if (refresh().isFailure) return false
        lastStamp = v
        return true
    }

    suspend fun loadTrash(): Result<Unit> = runCatching {
        val res = http.get("/notes", mapOf("scope" to "trash"))
        _trash.value = decryptAll((res["data"] as? JsonArray).orEmpty())
        res["trash_days"]?.jsonPrimitive?.intOrNull?.let { d -> _state.update { it.copy(trashDays = d) } }
    }

    private suspend fun decryptAll(rows: List<kotlinx.serialization.json.JsonElement>): List<NoteItem> = withContext(Dispatchers.Default) {
        rows.mapNotNull { (it as? JsonObject)?.let(::parseNote) }
    }

    private fun openKey(wrapped: String?): ByteArray? {
        return keyCache.open(wrapped)
    }

    private fun parseNote(o: JsonObject): NoteItem? {
        val id = o.jstr("id") ?: return null
        val me = o["me"] as? JsonObject
        val wrapped = me.jstr("wrapped_key")
        val key = openKey(wrapped)
        val metaEnc = o.jstr("meta_enc")
        val meta = when {
            key == null -> NoteMeta(title = "🔒")
            metaEnc.isNullOrEmpty() -> NoteMeta()
            else -> metaCache[metaEnc] ?: runCatching {
                NoteMeta.from(Json.parseToJsonElement(CalendarCrypto.decryptString(metaEnc, key)) as? JsonObject)
            }.getOrDefault(NoteMeta()).also { metaCache[metaEnc] = it }
        }
        return NoteItem(
            id = id,
            ownerId = o.jstr("owner_id"),
            version = o["version"]?.jsonPrimitive?.intOrNull ?: 0,
            createdAt = o.jstr("created_at"),
            updatedAt = o.jstr("updated_at"),
            deletedAt = o.jstr("deleted_at"),
            commentsCount = o["comments_count"]?.jsonPrimitive?.intOrNull ?: 0,
            filesCount = o["files_count"]?.jsonPrimitive?.intOrNull ?: 0,
            permission = me.jstr("permission") ?: "view",
            wrappedKey = wrapped,
            notebookId = me.jstr("notebook_id"),
            pinnedAt = me.jstr("pinned_at"),
            members = (o["members"] as? JsonArray).orEmpty().mapNotNull { m ->
                val mo = m as? JsonObject ?: return@mapNotNull null
                NoteMember(
                    userId = mo.jstr("user_id") ?: return@mapNotNull null,
                    permission = mo.jstr("permission") ?: "view",
                    name = mo.jstr("name").orEmpty(),
                    email = mo.jstr("email"),
                    avatar = mo.jstr("avatar"),
                    color = mo.jstr("color"),
                    online = mo["online"]?.jsonPrimitive?.booleanOrNull ?: false,
                    lastSeenAt = mo.jstr("last_seen_at"),
                )
            },
            metaEnc = metaEnc,
            meta = meta,
            key = key,
        )
    }

    private suspend fun parseNotebooks(res: JsonObject): List<NoteNotebook> = withContext(Dispatchers.Default) {
        (res["data"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            NoteNotebook(
                id = o.jstr("id") ?: return@mapNotNull null,
                name = keys.openNameOrNull(o.jstr("name_encrypted")) ?: "🔒",
                color = o.jstr("color") ?: "blue",
                position = o["position"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }.sortedBy { it.position }
    }

    private fun upsert(note: NoteItem) = _state.update { s ->
        val i = s.notes.indexOfFirst { it.id == note.id }
        s.copy(notes = if (i < 0) listOf(note) + s.notes else s.notes.toMutableList().also { it[i] = note })
    }

    private fun patch(id: String, f: (NoteItem) -> NoteItem) = _state.update { s ->
        s.copy(notes = s.notes.map { if (it.id == id) f(it) else it })
    }

    suspend fun refreshOne(id: String): NoteItem? {
        val res = http.get("/notes/$id")
        val note = (res["data"] as? JsonObject)?.let(::parseNote) ?: return null
        if (note.deletedAt == null) upsert(note)
        return note
    }

    // ---- ações sobre notas -----------------------------------------------------

    /** Nota nova (K_note aleatória selada a mim, meta vazio cifrado). */
    suspend fun create(notebookId: String? = null): NoteItem {
        val key = ByteArray(32).also(random::nextBytes)
        val sealed = keys.sealForSelf(key)
        val res = http.post("/notes", buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("meta_enc", CalendarCrypto.encryptString(NoteMeta().toJson().toString(), key))
            put("wrapped_key", sealed)
            if (notebookId != null) put("notebook_id", notebookId) else put("notebook_id", JsonNull)
        })
        keyCache.put(sealed, key)
        val note = (res["data"] as? JsonObject)?.let(::parseNote) ?: error("resposta sem nota")
        upsert(note)
        return note
    }

    /** Meta derivado do documento (quem edita grava; os outros recebem pela lista). */
    suspend fun saveMeta(id: String, meta: NoteMeta) {
        val note = note(id) ?: return
        val key = note.key ?: return
        if (note.meta == meta) return
        val enc = CalendarCrypto.encryptString(meta.toJson().toString(), key)
        metaCache[enc] = meta
        patch(id) { it.copy(meta = meta, metaEnc = enc, updatedAt = Instant.now().toString()) }
        revertOnFailure(id, { it.metaEnc == enc }, { it.copy(meta = note.meta, metaEnc = note.metaEnc, updatedAt = note.updatedAt) }) {
            http.patch("/notes/$id", buildJsonObject { put("meta_enc", enc) })
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val before = note(id)?.pinnedAt
        val at = if (pinned) Instant.now().toString() else null
        patch(id) { it.copy(pinnedAt = at) }
        revertOnFailure(id, { it.pinnedAt == at }, { it.copy(pinnedAt = before) }) {
            http.patch("/notes/$id/me", buildJsonObject { put("pinned", pinned) })
        }
    }

    suspend fun moveToNotebook(id: String, notebookId: String?) {
        val before = note(id)?.notebookId
        patch(id) { it.copy(notebookId = notebookId) }
        revertOnFailure(id, { it.notebookId == notebookId }, { it.copy(notebookId = before) }) {
            http.patch("/notes/$id/me", buildJsonObject { if (notebookId != null) put("notebook_id", notebookId) else put("notebook_id", JsonNull) })
        }
    }

    /**
     * Patch otimista: se o pedido falhar, repõe o anterior (só se a nota ainda tiver
     * o valor otimista — uma mudança mais recente não é pisada) e volta a lançar.
     */
    private suspend fun revertOnFailure(
        id: String,
        stillOptimistic: (NoteItem) -> Boolean,
        revert: (NoteItem) -> NoteItem,
        call: suspend () -> Unit,
    ) {
        try {
            call()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            patch(id) { if (stillOptimistic(it)) revert(it) else it }
            throw e
        }
    }

    /** Dono: vai para o lixo. Membro: sai da nota. */
    suspend fun delete(id: String) {
        http.delete("/notes/$id")
        _state.update { s -> s.copy(notes = s.notes.filterNot { it.id == id }) }
        _trash.value = null
    }

    suspend fun restore(id: String) {
        http.post("/notes/$id/restore")
        _trash.update { it?.filterNot { n -> n.id == id } }
        refresh()
    }

    suspend fun forceDelete(id: String) {
        http.delete("/notes/$id/force")
        _trash.update { it?.filterNot { n -> n.id == id } }
    }

    /** Resultado da seleção múltipla: ids tratados, falhados, e (delete) quantas foram p/ lixo vs. saídas. */
    data class BulkResult(val done: List<String>, val failed: List<String>, val trashed: Int, val left: Int)

    /** Seleção múltipla: action = delete (dono → lixo, membro → sai) | restore | force. Um só pedido. */
    suspend fun bulk(action: String, ids: List<String>): BulkResult {
        var out = BulkResult(emptyList(), emptyList(), 0, 0)
        // O servidor aceita até 500 por pedido.
        ids.chunked(500).forEach { chunk ->
            val res = http.post("/notes/bulk", buildJsonObject {
                put("action", action)
                put("ids", buildJsonArray { chunk.forEach { add(JsonPrimitive(it)) } })
            })
            fun list(key: String) = (res[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            out = BulkResult(
                done = out.done + list("done"),
                failed = out.failed + list("failed"),
                trashed = out.trashed + (res["trashed"]?.jsonPrimitive?.intOrNull ?: 0),
                left = out.left + (res["left"]?.jsonPrimitive?.intOrNull ?: 0),
            )
        }
        val done = out.done.toSet()
        when (action) {
            "delete" -> {
                _state.update { s -> s.copy(notes = s.notes.filterNot { it.id in done }) }
                _trash.value = null
            }
            else -> _trash.update { it?.filterNot { n -> n.id in done } }
        }
        if (action == "restore" && done.isNotEmpty()) refresh()
        return out
    }

    // ---- cadernos --------------------------------------------------------------

    suspend fun createNotebook(name: String, color: String): String? {
        val sealed = keys.sealName(name) ?: error("e2e_locked")
        val res = http.post("/notebooks", buildJsonObject {
            put("name_encrypted", sealed)
            put("color", color)
        })
        reloadNotebooks()
        return (res["data"] as? JsonObject).jstr("id")
    }

    suspend fun updateNotebook(id: String, name: String?, color: String?) {
        http.patch("/notebooks/$id", buildJsonObject {
            if (name != null) put("name_encrypted", keys.sealName(name) ?: error("e2e_locked"))
            if (color != null) put("color", color)
        })
        reloadNotebooks()
    }

    suspend fun deleteNotebook(id: String) {
        http.delete("/notebooks/$id")
        refresh()
    }

    private suspend fun reloadNotebooks() {
        val nbs = parseNotebooks(http.get("/notebooks"))
        _state.update { it.copy(notebooks = nbs) }
    }

    // ---- conteúdo fora do editor (pesquisa, Checklists) -----------------------

    private val contentStamps = ConcurrentHashMap<String, String>()
    /** Notas cujo conteúdo carregado ficou incompleto (base/update ilegível ou páginas a mais). */
    private val incompleteContent: MutableSet<String> = ConcurrentHashMap.newKeySet()
    /** Um pedido de cada vez por nota (o open() do núcleo reinicia o Y.Doc). */
    private val contentLocks = ConcurrentHashMap<String, Mutex>()
    /** Y.Doc de leitura abertos, do mais antigo para o mais recente (no máximo MAX_OPEN_CONTENT). */
    private val openContent = LinkedHashSet<String>()

    private fun contentLock(id: String) = contentLocks.getOrPut(id) { Mutex() }

    /**
     * Corre `block` com o conteúdo atual da nota carregado no Y.Doc de leitura, com a
     * nota bloqueada (sem outro pedido a reabrir o doc a meio). Fecha os docs mais
     * antigos para o heap do QuickJS não crescer com cada nota lida.
     */
    private suspend fun <T> withContent(id: String, fresh: Boolean = false, block: suspend (docId: String, session: String?) -> T): T {
        val result = contentLock(id).withLock {
            val session = loadContentLocked(id, fresh)
            synchronized(openContent) { openContent.remove(id); openContent.add(id) }
            block(contentDocId(id), session)
        }
        evictContent(keep = id)
        return result
    }

    private suspend fun evictContent(keep: String) {
        while (true) {
            val victim = synchronized(openContent) {
                if (openContent.size <= MAX_OPEN_CONTENT) null else openContent.firstOrNull { it != keep }
            } ?: return
            val lock = contentLock(victim)
            if (!lock.tryLock()) return // em uso: fica para a próxima
            try {
                synchronized(openContent) { openContent.remove(victim) }
                contentStamps.remove(victim)
                runCatching { core.close(contentDocId(victim)) }
            } finally {
                lock.unlock()
            }
        }
    }

    private suspend fun closeAllContent() {
        val ids = synchronized(openContent) { openContent.toList().also { openContent.clear() } }
        for (id in ids) contentLock(id).withLock {
            contentStamps.remove(id)
            incompleteContent.remove(id)
            runCatching { core.close(contentDocId(id)) }
        }
    }

    /** Id do Y.Doc de leitura no NotesCore (o editor usa o id da nota). */
    fun contentDocId(id: String) = "c:$id"

    /**
     * Carrega o conteúdo atual (base + updates, decifrado) para o NotesCore com o id
     * contentDocId(id). Em cache enquanto updated_at|version não mudar.
     * Devolve a sessão do relay (null = nota sem conteúdo).
     */
    private suspend fun loadContentLocked(id: String, fresh: Boolean = false): String? {
        val note = note(id) ?: error("nota desconhecida")
        val key = note.key ?: error("nota sem chave")
        val stamp = "${note.updatedAt}|${note.version}"
        val docId = contentDocId(id)
        val cached = contentStamps[id]
        if (!fresh && cached != null && cached.substringBefore('#') == stamp) return cached.substringAfter('#').ifEmpty { null }

        val cipher = NoteCipher(key, id)
        val updates = ArrayList<String>()
        var after = 0L
        var session: String? = null
        var page = 0
        // Ilegíveis ou páginas por ler: o Y.Doc fica incompleto e não serve para derivar meta.
        var failures = 0
        var more = false
        while (page++ < 50) {
            val data = http.get("/notes/$id/content", mapOf("after" to after))
            val s = data["session"] as? JsonObject
            if (s == null) {
                more = false
                break
            }
            val sid = s.jstr("id")
            if (session != null && session != sid) {
                // Restauraram uma versão a meio: recomeça do zero.
                updates.clear(); after = 0; session = null; failures = 0
                continue
            }
            session = sid
            s.jstr("base")?.let { b -> runCatching { cipher.decryptToB64(b) }.getOrNull()?.let(updates::add) ?: failures++ }
            for (u in (data["updates"] as? JsonArray).orEmpty()) {
                val uo = u as? JsonObject ?: continue
                uo.jstr("payload")?.let { p -> runCatching { cipher.decryptToB64(p) }.getOrNull()?.let(updates::add) ?: failures++ }
                after = maxOf(after, uo["seq"]?.jsonPrimitive?.longOrNull ?: after)
            }
            more = data["more"]?.jsonPrimitive?.booleanOrNull == true
            if (!more) break
        }
        core.open(docId)
        if (updates.isNotEmpty()) core.applyMany(docId, updates)
        if (failures > 0 || more) {
            android.util.Log.w("NotesRepository", "conteúdo incompleto: falhas=$failures truncado=$more")
            incompleteContent += id
        } else incompleteContent -= id
        contentStamps[id] = "$stamp#${session.orEmpty()}"
        return session
    }

    /** Texto da nota para a pesquisa local (linhas legíveis). */
    suspend fun plainText(id: String): String {
        val raw = withContent(id) { docId, _ -> core.lines(docId) }
        val lines = Json.parseToJsonElement(raw) as? JsonArray
        return lines.orEmpty().joinToString("\n") { (it as? JsonObject).jstr("text").orEmpty() }
    }

    // ---- tarefas (Checklists) ------------------------------------------------

    suspend fun loadTasks(): List<NoteTask> {
        val res = http.get("/tasks")
        return withContext(Dispatchers.Default) {
            val noteInfo = HashMap<String, Triple<ByteArray, String, Boolean>?>()
            for (n in (res["notes"] as? JsonArray).orEmpty()) {
                val o = n as? JsonObject ?: continue
                val id = o.jstr("id") ?: continue
                val known = note(id)
                val key = known?.key ?: openKey(o.jstr("wrapped_key"))
                noteInfo[id] = key?.let {
                    val title = known?.meta?.title ?: o.jstr("meta_enc")?.let { enc ->
                        runCatching { NoteMeta.from(Json.parseToJsonElement(CalendarCrypto.decryptString(enc, it)) as? JsonObject).title }.getOrNull()
                    }.orEmpty()
                    Triple(it, title, o["can_edit"]?.jsonPrimitive?.booleanOrNull ?: false)
                }
            }
            (res["data"] as? JsonArray).orEmpty().mapNotNull { t ->
                val o = t as? JsonObject ?: return@mapNotNull null
                val noteId = o.jstr("note_id") ?: return@mapNotNull null
                val info = noteInfo[noteId]
                val text = info?.let { o.jstr("text_enc")?.let { enc -> runCatching { CalendarCrypto.decryptString(enc, it.first) }.getOrNull() } } ?: "🔒"
                NoteTask(
                    noteId = noteId,
                    uid = o.jstr("uid") ?: return@mapNotNull null,
                    text = text,
                    done = o["done"]?.jsonPrimitive?.booleanOrNull ?: false,
                    dueDate = o.jstr("due_date")?.take(10),
                    assigneeId = o.jstr("assignee_id"),
                    noteTitle = info?.second.orEmpty(),
                    canEdit = info?.third ?: false,
                )
            }
        }
    }

    /**
     * Marca uma caixa sem abrir a nota (como notesToggleTask na web): update cifrado
     * no relay, índice no servidor e contagens do meta.
     */
    suspend fun toggleTask(noteId: String, uid: String, done: Boolean) {
        val note = note(noteId) ?: refreshOne(noteId) ?: error("nota desconhecida")
        val key = note.key ?: error("nota sem chave")
        val derived = withContent(noteId, fresh = true) { docId, session ->
            // Y.Doc incompleto: o meta derivado apagaria título/contagens. Não grava nada (a
            // caixa volta atrás com o aviso de erro); a nota abre-se no editor para a marcar.
            if (noteId in incompleteContent) error("conteúdo da nota incompleto")
            if (session != null) {
                val update = core.setTaskChecked(docId, uid, done)
                if (update.isNotEmpty()) {
                    http.post("/notes/$noteId/collab/updates", buildJsonObject {
                        put("client", "checklists-" + UUID.randomUUID().toString().take(8))
                        put("session", session)
                        put("payload", NoteCipher(key, noteId).encryptB64(update))
                    })
                }
            }
            http.patch("/notes/$noteId/tasks/$uid", buildJsonObject { put("done", done) })
            contentStamps.remove(noteId)
            Json.parseToJsonElement(core.derive(docId)) as? JsonObject
        }
        saveMeta(noteId, NoteMeta.from(derived?.get("meta") as? JsonObject))
    }

    /** Índice de tarefas (texto cifrado com a K_note) — notesSyncTasks. */
    suspend fun syncTasks(id: String, tasks: JsonArray) {
        val key = note(id)?.key ?: return
        http.put("/notes/$id/tasks", buildJsonObject {
            put("tasks", buildJsonArray {
                tasks.forEach { el ->
                    val t = el as? JsonObject ?: return@forEach
                    add(buildJsonObject {
                        put("uid", t.jstr("uid"))
                        put("text_enc", CalendarCrypto.encryptString(t.jstr("text").orEmpty(), key))
                        put("done", t["done"]?.jsonPrimitive?.booleanOrNull ?: false)
                        put("due_date", t["due_date"]?.takeIf { it !is JsonNull } ?: JsonNull)
                        put("assignee_id", t["assignee_id"]?.takeIf { it !is JsonNull } ?: JsonNull)
                    })
                }
            })
        })
    }

    suspend fun syncFiles(id: String, files: JsonArray) {
        http.put("/notes/$id/files", buildJsonObject { put("files", files) })
    }

    // ---- versões (Histórico) -------------------------------------------------

    suspend fun loadVersions(id: String): Pair<List<NoteVersion>, Int> {
        val res = http.get("/notes/$id/versions")
        val list = (res["data"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            NoteVersion(
                id = o.jstr("id") ?: return@mapNotNull null,
                createdAt = o.jstr("created_at"),
                changes = o["changes"]?.jsonPrimitive?.intOrNull ?: 0,
                authorName = o.jstr("name"),
                authorId = o.jstr("author_id"),
            )
        }
        return list to (res["keep_days"]?.jsonPrimitive?.intOrNull ?: 90)
    }

    /** Linhas (diffLines, JSON) do estado atual da nota. */
    suspend fun currentLines(id: String): String = withContent(id) { docId, _ -> core.diffLines(docId) }

    /** Linhas (diffLines, JSON) de uma versão guardada. */
    suspend fun versionLines(id: String, versionId: String): String {
        val key = note(id)?.key ?: error("nota sem chave")
        val res = http.get("/notes/$id/versions/$versionId")
        val enc = (res["data"] as? JsonObject).jstr("state_enc") ?: error("versão vazia")
        val docId = "v:$versionId"
        core.open(docId)
        return try {
            core.apply(docId, NoteCipher(key, id).decryptToB64(enc))
            core.diffLines(docId)
        } finally {
            core.close(docId)
        }
    }

    /** [{op: '=', '-', '+', line: {text, kind, done}}] entre duas listas de diffLines. */
    suspend fun lineDiff(aJson: String, bJson: String): JsonArray =
        Json.parseToJsonElement(core.lineDiff(aJson, bJson)) as? JsonArray ?: JsonArray(emptyList())

    /** Grava o estado de um Y.Doc do núcleo como versão (state_enc cifrado). */
    suspend fun saveVersion(id: String, docId: String, changes: Int) {
        val key = note(id)?.key ?: return
        http.post("/notes/$id/versions", buildJsonObject {
            put("state_enc", NoteCipher(key, id).encryptB64(core.state(docId)))
            put("changes", changes)
        })
    }

    /** Restaurar (como a web): o atual fica no histórico; depois o índice segue a versão. */
    suspend fun restoreVersion(id: String, versionId: String) {
        withContent(id, fresh = true) { docId, _ ->
            // Sem o estado completo, o "atual" guardado no histórico ficava truncado.
            if (id in incompleteContent) error("conteúdo da nota incompleto")
            saveVersion(id, docId, 0)
        }
        http.post("/notes/$id/versions/$versionId/restore")
        contentStamps.remove(id)
        refreshOne(id)
        withContent(id, fresh = true) { docId, _ -> if (id !in incompleteContent) saveDerived(id, docId) }
    }

    /** Meta, tarefas e ficheiros derivados de um Y.Doc do núcleo. */
    suspend fun saveDerived(id: String, docId: String) {
        val derived = Json.parseToJsonElement(core.derive(docId)) as? JsonObject ?: return
        coroutineScope {
            val a = async { saveMeta(id, NoteMeta.from(derived["meta"] as? JsonObject)) }
            val b = async { syncTasks(id, derived["tasks"] as? JsonArray ?: JsonArray(emptyList())) }
            val c = async { syncFiles(id, derived["files"] as? JsonArray ?: JsonArray(emptyList())) }
            a.await(); b.await(); c.await()
        }
    }
}

internal fun JsonObject?.jstr(key: String): String? = (this?.get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
