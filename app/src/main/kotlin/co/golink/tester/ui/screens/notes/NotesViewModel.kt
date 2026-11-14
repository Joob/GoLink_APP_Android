package co.golink.tester.ui.screens.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.data.calendar.CalendarAttachments
import co.golink.tester.data.calendar.CalendarRepository
import co.golink.tester.data.config.BackendUrlHolder
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.calendar.CalEvent
import co.golink.tester.domain.calendar.EventAttachment
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.nt
import co.golink.tester.ui.screens.viewer.FileViewerSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Menu da área (secções, cadernos e tags), enviado pela página. */
data class NotesSidebar(
    val section: String = "NotesAll",
    val notebook: String? = null,
    val tag: String? = null,
    val all: Int = 0,
    val pinned: Int = 0,
    val shared: Int = 0,
    val notebooks: List<Notebook> = emptyList(),
    val tags: List<Pair<String, Int>> = emptyList(),
    /** A página tem uma nota aberta (rota com :id). */
    val editing: Boolean = false,
    /** Rota da página quando mandou este estado (distingue eventos atrasados). */
    val route: String? = null,
) {
    data class Notebook(val id: String, val name: String, val color: String, val count: Int)
}

sealed interface NotesEvent {
    data object OpenMenu : NotesEvent
    data class Toast(val message: String) : NotesEvent
    data class OpenFile(val id: String) : NotesEvent
    data class OpenUrl(val url: String) : NotesEvent
    data class OpenEvent(val id: String) : NotesEvent
    /** A página gravou o que tinha pendente (resposta a GoLinkNotes.flushThen). */
    data class Flushed(val token: Int) : NotesEvent
}

/**
 * Lado nativo das Notes. A página /notes-embed (a área Notes da web) corre numa
 * WebView sem sessão: os pedidos HTTP (só /api/notes/…) e as chaves privadas
 * ficam aqui; a página recebe apenas a K_note de cada nota, que o Yjs precisa.
 * Protocolo em resources/js/services/notesBridge.js e notes-embed.js (repo web).
 */
@HiltViewModel
class NotesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val backendUrlHolder: BackendUrlHolder,
    @Named("authed") private val httpClient: OkHttpClient,
    private val keys: E2EKeyManager,
    private val sessionManager: SessionManager,
    private val userRepository: co.golink.tester.data.user.UserRepository,
    private val attachments: CalendarAttachments,
    private val calendar: CalendarRepository,
    private val viewerSession: FileViewerSession,
    private val browse: co.golink.tester.data.browse.BrowseRepository,
    private val json: Json,
) : ViewModel() {

    /** Rota inicial dentro das Notes (ex.: "all/<id>", "checklists"). */
    val initialPath: String = savedState.get<String>("path").orEmpty()

    /**
     * Onde a página está agora: a WebView é destruída ao abrir o viewer ou o
     * Calendário e, ao voltar, reabre aqui (na nota que estava aberta).
     */
    @Volatile
    private var currentPath: String? = null

    val notesUrl: String get() = backendUrlHolder.current.trimEnd('/') + "/notes-embed"

    private val _sidebar = MutableStateFlow(NotesSidebar())
    val sidebar: StateFlow<NotesSidebar> = _sidebar.asStateFlow()

    private val _events = MutableSharedFlow<NotesEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<NotesEvent> = _events.asSharedFlow()

    /** Seletor do Drive aberto pela página (resposta: os ficheiros escolhidos). */
    private val _drivePick = MutableStateFlow<DrivePick?>(null)
    val drivePick: StateFlow<DrivePick?> = _drivePick.asStateFlow()

    class DrivePick(val exclude: Set<String>, val result: CompletableDeferred<List<BrowseItem.File>>)

    /** Respostas à espera de take(): (instante, json). */
    private val results = ConcurrentHashMap<Int, Pair<Long, String>>()

    /** Últimas mensagens da consola da página (diagnóstico do ecrã em branco). */
    val consoleLog = java.util.concurrent.ConcurrentLinkedDeque<String>()

    fun logConsole(line: String) {
        consoleLog.addLast(line.take(400))
        while (consoleLog.size > 25) consoleLog.pollFirst()
    }

    /** A página já tem algo à vista (notas carregadas, ou o seu erro): a app tira o indicador. */
    private val _pageReady = MutableStateFlow(false)
    val pageReady: StateFlow<Boolean> = _pageReady.asStateFlow()

    /** Nova carga da página (inicial, Tentar de novo): volta a mostrar o indicador. */
    fun onPageLoading() {
        _pageReady.value = false
    }

    /** wrapped_key de notas vistas nas respostas de /api/notes/… (as únicas que open_sealed abre). */
    private val noteKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** name_encrypted vistos em /api/notes/… (os únicos que open_name abre: nomes de cadernos). */
    private val noteNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun rememberWrappedKeys(path: String, body: String) {
        // Respostas com conteúdo (relay, versões) não trazem chaves.
        if ("/collab" in path || path.endsWith("/content") || "/versions/" in path) return
        if (body.contains("\"name_encrypted\"")) {
            NAME_ENCRYPTED.findAll(body).forEach { noteNames += it.groupValues[1].replace("\\/", "/") }
        }
        if (!body.contains("\"wrapped_key\"")) return
        // Varrimento sem parse (listas grandes); base64 não tem aspas nem escapes.
        WRAPPED_KEY.findAll(body).forEach { noteKeys += it.groupValues[1].replace("\\/", "/") }
    }

    private class BridgeError(val status: Int, override val message: String? = null) : RuntimeException(message)

    fun infoJson(dark: Boolean): String {
        // Mesmo critério do ecrã (userRepository.me): sessão restaurada sem rede ainda não está Authenticated.
        val user = (sessionManager.state.value as? AuthState.Authenticated)?.user ?: userRepository.me.value
        val publicKey = keys.ownPublicKeyB64()
        if (user == null || publicKey == null) {
            android.util.Log.w("NotesWebView", "info() null: user=${user != null} publicKey=${publicKey != null}")
            return "null"
        }
        return buildJsonObject {
            put("locale", I18n.lang)
            put("dark", dark)
            put("public_key", publicKey)
            put("route", currentPath ?: initialPath)
            put("user", buildJsonObject {
                put("id", user.id)
                put("name", user.name)
                put("email", user.email)
                put("avatar", user.avatar)
            })
        }.toString()
    }

    fun take(id: Int): String = results.remove(id)?.second ?: """{"ok":false,"status":500}"""

    fun toast(message: String) {
        if (message.isNotBlank()) _events.tryEmit(NotesEvent.Toast(message))
    }

    fun request(id: Int, method: String, args: String, onDone: (Int) -> Unit) {
        sweepResults()
        viewModelScope.launch {
            val json = try {
                val a = withContext(Dispatchers.Default) {
                    runCatching { Json.parseToJsonElement(args).jsonObject }.getOrElse { JsonObject(emptyMap()) }
                }
                val data = handle(method, a)
                withContext(Dispatchers.Default) {
                    buildJsonObject {
                        put("ok", true)
                        put("data", data)
                    }.toString()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Saiu das Notes: a WebView já não existe, não há a quem responder.
                throw e
            } catch (e: BridgeError) {
                errorJson(e.status, e.message)
            } catch (e: java.io.IOException) {
                errorJson(0, "network")
            } catch (e: Exception) {
                errorJson(500, e.message)
            } catch (e: OutOfMemoryError) {
                errorJson(413, "too_large")
            }
            results[id] = System.currentTimeMillis() to json
            onDone(id)
        }
    }

    // Ids únicos por carregamento (notesBridge.js): o que a página não levar
    // (resposta da página anterior a um reload) sai daqui ao fim de 60 s.
    private fun sweepResults() {
        if (results.size < 32) return
        val limit = System.currentTimeMillis() - 60_000
        results.entries.removeIf { it.value.first < limit }
    }

    private fun errorJson(status: Int, message: String?) = buildJsonObject {
        put("ok", false)
        put("status", status)
        put("message", message ?: "error")
    }.toString()

    private suspend fun handle(method: String, args: JsonObject): JsonElement = when (method) {
        "http" -> http(args.string("method"), args.string("url"), args["body"]?.jsonPrimitive?.contentOrNull)
        "open_sealed" -> {
            if (!keys.isUnlocked) throw BridgeError(403, "e2e_locked")
            // Só chaves de notas que a API das Notes entregou (nunca a de um ficheiro do Drive).
            if (args.string("sealed") !in noteKeys) throw BridgeError(403, "unknown_key")
            val key = withContext(Dispatchers.Default) { keys.openFileDataKey(args.string("sealed")) }
            JsonPrimitive(Base64.getEncoder().encodeToString(key))
        }
        "open_sealed_many" -> {
            if (!keys.isUnlocked) throw BridgeError(403, "e2e_locked")
            val list = (args["sealed"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty().distinct()
            withContext(Dispatchers.Default) {
                buildJsonObject {
                    list.forEach { sealed ->
                        val key = if (sealed in noteKeys) runCatching { keys.openFileDataKey(sealed) }.getOrNull() else null
                        put(sealed, key?.let { JsonPrimitive(Base64.getEncoder().encodeToString(it)) } ?: JsonNull)
                    }
                }
            }
        }
        "ready" -> {
            _pageReady.value = true
            JsonNull
        }
        "open_name" -> if (args.string("sealed") !in noteNames) throw BridgeError(403, "unknown_name") else JsonPrimitive(withContext(Dispatchers.Default) { keys.openNameOrNull(args.string("sealed")) } ?: throw BridgeError(422, "unreadable"))
        "seal_name" -> JsonPrimitive(withContext(Dispatchers.Default) { keys.sealName(args.string("name")) } ?: throw BridgeError(403, "e2e_locked"))
        "drive_pick" -> drivePick(args)
        "file_links" -> fileLinks(args)
        "file_open" -> {
            fileOpen(args)
            JsonNull
        }
        "event_list" -> eventList(args)
        "event_get" -> eventGet(args.string("id"))
        "event_open" -> {
            _events.tryEmit(NotesEvent.OpenEvent(args.string("id")))
            JsonNull
        }
        "sidebar" -> {
            _sidebar.value = parseSidebar(args)
            args["route"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { currentPath = it }
            JsonNull
        }
        "flushed" -> {
            _events.tryEmit(NotesEvent.Flushed(args["token"]?.jsonPrimitive?.intOrNull ?: -1))
            JsonNull
        }
        "menu" -> {
            _events.tryEmit(NotesEvent.OpenMenu)
            JsonNull
        }
        else -> throw BridgeError(404, "unknown_method")
    }

    // ---- HTTP --------------------------------------------------------------

    /** Só a API das Notes; o token fica na app. Devolve o estado e o corpo tal como vieram. */
    private suspend fun http(method: String, url: String, body: String?): JsonElement = withContext(Dispatchers.IO) {
        // Valida o URL já normalizado pelo OkHttp (%2e%2e vira ".." e resolve-se):
        // só a API das Notes deste servidor.
        val backend = backendUrlHolder.current.trimEnd('/').toHttpUrlOrNull() ?: throw BridgeError(500, "backend")
        val target = (backend.toString().trimEnd('/') + url.substringBefore('#')).toHttpUrlOrNull() ?: throw BridgeError(400, "bad_url")
        val segments = target.pathSegments
        if (target.host != backend.host || target.port != backend.port || target.scheme != backend.scheme ||
            !target.encodedPath.startsWith("/api/notes/") ||
            // Barras codificadas (%2F/%5C) contam como separadores noutros servidores/proxies.
            target.encodedPath.contains("%2f", ignoreCase = true) || target.encodedPath.contains("%5c", ignoreCase = true) ||
            segments.dropLast(1).any { it.isEmpty() || it == "." || it == ".." } || segments.last() == ".."
        ) throw BridgeError(403, "forbidden_path")
        val verb = method.uppercase()
        if (verb !in setOf("GET", "POST", "PUT", "PATCH", "DELETE")) throw BridgeError(405, "method")
        val requestBody = when {
            verb == "GET" -> null
            body != null -> body.toRequestBody("application/json".toMediaTypeOrNull())
            verb == "DELETE" -> null
            else -> ByteArray(0).toRequestBody(null)
        }
        // O token e os cabeçalhos da app vêm do AuthInterceptor do cliente "authed".
        val request = Request.Builder().url(target).method(verb, requestBody).build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.isSuccessful) rememberWrappedKeys(target.encodedPath, text)
            buildJsonObject {
                put("status", response.code)
                put("body", text)
            }
        }
    }

    // ---- Drive -------------------------------------------------------------

    private suspend fun drivePick(args: JsonObject): JsonElement {
        val exclude = args["exclude"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty()
        val pick = DrivePick(exclude, CompletableDeferred())
        // Um seletor de cada vez: o pedido anterior fica respondido (sem ficheiros).
        _drivePick.value?.result?.complete(emptyList())
        _drivePick.value = pick
        val files = try {
            pick.result.await()
        } finally {
            if (_drivePick.value === pick) _drivePick.value = null
        }
        attachments.remember(files)
        return buildJsonArray {
            files.forEach { f ->
                add(buildJsonObject {
                    put("file_id", f.id)
                    put("name", f.name)
                    put("mime", f.mimetype.orEmpty())
                    put("size", f.filesize.orEmpty())
                    put("parent_id", f.parentId)
                })
            }
        }
    }

    fun completeDrivePick(files: List<BrowseItem.File>) {
        _drivePick.value?.result?.complete(files)
        _drivePick.value = null
    }

    suspend fun listDriveFolder(id: String?) = attachments.listFolder(id)

    private fun JsonObject.attachment() = EventAttachment(
        file_id = string("file_id"),
        name = this["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        mime = this["mime"]?.jsonPrimitive?.contentOrNull,
        parent_id = this["parent_id"]?.jsonPrimitive?.contentOrNull,
    )

    /** Links "só ver" para os outros membros; os revogados (alguém saiu) refazem-se. */
    private suspend fun fileLinks(args: JsonObject): JsonElement {
        val items = args["files"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        val list = items.map { o ->
            val a = o.attachment()
            if (o["revoked"]?.jsonPrimitive?.booleanOrNull == true) {
                attachments.findFile(a)?.let { attachments.remember(listOf(it.copy(share = null))) }
            }
            a
        }
        val (linked, tokens) = attachments.ensureLinks(list)
        return buildJsonArray {
            linked.filter { !it.share_url.isNullOrBlank() }.forEach { a ->
                add(buildJsonObject {
                    put("file_id", a.file_id)
                    put("share_url", a.share_url)
                    put("share_token", tokens[a.file_id])
                })
            }
        }
    }

    /** Abre no viewer (só para ver, como os anexos do Calendário); sem acesso usa o link. */
    private suspend fun fileOpen(args: JsonObject) {
        val a = args.attachment()
        // A página já tem o item (GET /api/notes/{id}/files), também para ficheiros
        // de outros membros, cuja pasta não conseguimos listar.
        val file = (attachments.known(a.file_id) ?: fromPageItem(args, a.file_id) ?: attachments.findFile(a))
            ?.let { f -> if (a.name.isNotBlank()) f.copy(name = a.name) else f }
        val me = (sessionManager.state.value as? AuthState.Authenticated)?.user?.id
        val shareUrl = args["share_url"]?.jsonPrimitive?.contentOrNull?.takeIf { isOwnShareUrl(it) }
        when {
            file != null -> {
                viewerSession.files = listOf(file)
                viewerSession.startId = file.id
                viewerSession.fromTrash = false
                viewerSession.contextLabel = null
                viewerSession.readOnlyIds = setOf(file.id)
                viewerSession.notOwnedIds = if (file.ownerId != null && file.ownerId != me) setOf(file.id) else emptySet()
                _events.tryEmit(NotesEvent.OpenFile(file.id))
            }
            shareUrl != null -> _events.tryEmit(NotesEvent.OpenUrl(shareUrl))
            else -> _events.tryEmit(NotesEvent.Toast(nt("no_access")))
        }
    }

    private suspend fun fromPageItem(args: JsonObject, fileId: String): BrowseItem.File? {
        val item = args["item"] as? JsonObject ?: return null
        return runCatching {
            val body = json.decodeFromJsonElement(
                co.golink.tester.domain.browse.BrowseListResponse.serializer(),
                buildJsonObject { put("data", JsonArray(listOf(item))) },
            )
            browse.decodeItems(body).filterIsInstance<BrowseItem.File>().firstOrNull { it.id == fileId }
        }.getOrNull()?.also { attachments.remember(listOf(it)) }
    }

    /** O link vem do documento partilhado: só /share/ deste servidor. */
    private fun isOwnShareUrl(url: String): Boolean {
        val u = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
        val backend = android.net.Uri.parse(backendUrlHolder.current)
        return u.scheme == "https" && u.host == backend.host && u.path.orEmpty().startsWith("/share/")
    }

    // ---- Calendário --------------------------------------------------------

    private suspend fun ensureCalendars() {
        if (calendar.calendars.value.isEmpty()) calendar.loadCalendars()
    }

    private fun CalEvent.toJson() = buildJsonObject {
        put("id", id)
        put("title", data.title)
        put("location", data.location)
        put("start", start.toString())
        put("end", end.toString())
        put("all_day", allDay)
        put("locked", locked)
    }

    private suspend fun eventList(args: JsonObject): JsonElement {
        ensureCalendars()
        val from = Instant.parse(args.string("from"))
        val to = Instant.parse(args.string("to"))
        val list = calendar.fetchEvents(from, to).getOrElse { throw BridgeError(502, it.message) }
        return JsonArray(list.filter { !it.locked }.map { it.toJson() })
    }

    private suspend fun eventGet(id: String): JsonElement {
        ensureCalendars()
        return calendar.fetchEvent(id).getOrNull()?.takeIf { !it.locked }?.toJson() ?: JsonNull
    }

    // ---- menu --------------------------------------------------------------

    private fun parseSidebar(o: JsonObject): NotesSidebar {
        fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull
        fun JsonObject.i(k: String) = this[k]?.jsonPrimitive?.intOrNull ?: 0
        val counts = o["counts"] as? JsonObject ?: JsonObject(emptyMap())
        return NotesSidebar(
            section = o.s("section") ?: "NotesAll",
            notebook = o.s("notebook"),
            tag = o.s("tag"),
            all = counts.i("all"),
            pinned = counts.i("pinned"),
            shared = counts.i("shared"),
            notebooks = (o["notebooks"] as? JsonArray).orEmpty().mapNotNull { e ->
                val nb = e as? JsonObject ?: return@mapNotNull null
                NotesSidebar.Notebook(nb.s("id") ?: return@mapNotNull null, nb.s("name").orEmpty(), nb.s("color") ?: "blue", nb.i("count"))
            },
            tags = (o["tags"] as? JsonArray).orEmpty().mapNotNull { e ->
                val t = e as? JsonObject ?: return@mapNotNull null
                (t.s("name") ?: return@mapNotNull null) to t.i("count")
            },
            editing = o["editing"]?.jsonPrimitive?.booleanOrNull == true,
            route = o.s("route"),
        )
    }

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: throw BridgeError(400, "missing_$key")

    override fun onCleared() {
        _drivePick.value?.result?.complete(emptyList())
        super.onCleared()
    }
}

private val WRAPPED_KEY = Regex("\"wrapped_key\"\\s*:\\s*\"([^\"]+)\"")
private val NAME_ENCRYPTED = Regex("\"name_encrypted\"\\s*:\\s*\"([^\"]+)\"")
