package co.golink.tester.ui.screens.editor

import co.golink.tester.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.data.auth.TokenStore
import co.golink.tester.data.config.BackendUrlHolder
import co.golink.tester.data.download.FileDownloader
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.data.encryption.EncryptedFileCodec
import co.golink.tester.data.upload.UploadManager
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.toItem
import co.golink.tester.domain.files.officeMimeFor
import co.golink.tester.network.FilesApi
import co.golink.tester.network.UserEncryptionApi
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.screens.viewer.FileViewerSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/**
 * Lado nativo do editor de documentos (docx/xlsx). A página /editor-embed corre
 * numa WebView sem sessão; tudo o que precisa de rede ou de chaves passa por
 * aqui: descarregar e decifrar, cifrar e gravar (com base_revision), versões,
 * cópias e transferências. O protocolo está em resources/js/services/editorBridge.js.
 */
private const val COPY_TIMEOUT_MS = 3 * 60_000L

@HiltViewModel
class OfficeEditorViewModel @Inject constructor(
    private val session: FileViewerSession,
    private val backendUrlHolder: BackendUrlHolder,
    private val tokenStore: TokenStore,
    @Named("authed") private val httpClient: OkHttpClient,
    private val filesApi: FilesApi,
    private val userEncryptionApi: UserEncryptionApi,
    private val e2eKeyManager: E2EKeyManager,
    private val uploadManager: UploadManager,
    private val downloader: FileDownloader,
    private val sessionManager: SessionManager,
) : ViewModel() {

    /** Muda para a cópia docx/xlsx quando um formato antigo (doc/rtf/xls) é gravado. */
    var file: BrowseItem.File? = session.files.firstOrNull { it.id == session.startId } ?: session.files.firstOrNull()
        private set

    /** Do Lixo ou com o E2E trancado: abre só para ler. */
    val canEdit: Boolean
        get() = file.let { f -> f != null && !session.fromTrash && (!f.encrypted || e2eKeyManager.isUnlocked) }

    val editorUrl: String get() = backendUrlHolder.current.trimEnd('/') + "/editor-embed"

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    // Partilhar a partir do editor: o ecrã abre a folha de partilha nativa.
    private val _shareRequests = MutableSharedFlow<BrowseItem.File>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<BrowseItem.File> = _shareRequests.asSharedFlow()

    private val _closeRequested = MutableStateFlow(false)
    val closeRequested: StateFlow<Boolean> = _closeRequested.asStateFlow()

    private val results = ConcurrentHashMap<Int, String>()
    private var dataKey: ByteArray? = null

    /** Erro para a página: status HTTP (0 = sem rede) e o JSON de erro do servidor. */
    private class BridgeError(
        val status: Int,
        override val message: String? = null,
        val body: JsonObject? = null,
    ) : RuntimeException(message)

    fun infoJson(dark: Boolean): String {
        val f = file ?: return "null"
        return buildJsonObject {
            put("file", fileJson(f))
            put("canEdit", canEdit)
            put("canComment", !session.fromTrash)
            put("canShare", !session.fromTrash)
            put("supportsVersions", true)
            put("supportsCollab", true)
            currentUser()?.let { user ->
                put("user", buildJsonObject {
                    put("id", user.id)
                    put("name", user.name)
                    put("avatar", user.avatar)
                })
            }
            put("locale", I18n.lang)
            put("dark", dark)
        }.toString()
    }

    private fun fileJson(f: BrowseItem.File): JsonObject = buildJsonObject {
        put("id", f.id)
        put("name", f.name)
        put("basename", f.basename)
        put("mimetype", f.mimetype)
        put("encrypted", f.encrypted)
        // A lista pode estar desatualizada depois de uma gravação nossa.
        put("content_revision", session.contentRevisions[f.id] ?: f.contentRevision)
        put("filesize", f.filesize)
    }

    fun take(id: Int): String = results.remove(id) ?: """{"ok":false,"status":500}"""

    fun toast(message: String) {
        if (message.isNotBlank()) _toasts.tryEmit(message)
    }

    fun requestClose() {
        _closeRequested.value = true
    }

    /** Pedido assíncrono da página: o resultado fica em [results] até ela o ir buscar com take(id). */
    fun request(id: Int, method: String, args: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            results[id] = try {
                val a = runCatching { Json.parseToJsonElement(args).jsonObject }.getOrElse { JsonObject(emptyMap()) }
                buildJsonObject {
                    put("ok", true)
                    put("data", handle(method, a))
                }.toString()
            } catch (e: BridgeError) {
                errorJson(e)
            } catch (e: java.io.IOException) {
                errorJson(BridgeError(0, "network"))
            } catch (e: Exception) {
                errorJson(BridgeError(500, e.message))
            }
            onDone(id)
        }
    }

    private suspend fun handle(method: String, args: JsonObject): JsonElement {
        val f = file ?: throw BridgeError(404)
        return when (method) {
            "load" -> JsonPrimitive(b64(loadContent(f)))
            "save" -> save(
                f,
                unb64(args.string("content")),
                args["base_revision"]?.jsonPrimitive?.intOrNull,
                args["collab_session"]?.jsonPrimitive?.contentOrNull,
                args["collab_upto"]?.jsonPrimitive?.longOrNull,
            )
            // Edição em tempo real: a cifra corre na página (com esta chave); aqui só o HTTP.
            "collab_key" -> JsonPrimitive(b64(key(f)))
            "collab_get" -> Json.parseToJsonElement(
                raw(filesApi.collabState(f.id, args.string("client"), args["after"]?.jsonPrimitive?.longOrNull ?: 0))
            )
            "collab_session", "collab_updates", "collab_compact", "collab_awareness", "collab_leave" -> Json.parseToJsonElement(
                raw(filesApi.collabPost(f.id, method.removePrefix("collab_"), args.toString().toRequestBody("application/json".toMediaTypeOrNull())))
            )
            "versions" -> {
                val body = raw(filesApi.fileVersions(f.id))
                Json.parseToJsonElement(body).jsonObject["data"] ?: JsonNull
            }
            "version_load" -> {
                val response = filesApi.downloadFileVersion(f.id, args.string("id"))
                if (!response.isSuccessful) throw httpError(response.code(), response.errorBody()?.string())
                var bytes = withContext(Dispatchers.IO) { response.body()?.bytes() ?: ByteArray(0) }
                if (args["encrypted"]?.jsonPrimitive?.booleanOrNull == true) bytes = EncryptedFileCodec.decryptFull(bytes, key(f))
                JsonPrimitive(b64(bytes))
            }
            "version_restore" -> {
                val body = raw(filesApi.restoreFileVersion(f.id, args.string("id")))
                rememberRevision(f, body)
                Json.parseToJsonElement(body)
            }
            "save_copy" -> saveCopy(f, args.string("name"), unb64(args.string("content")), args["switch"]?.jsonPrimitive?.booleanOrNull == true)
            "download" -> {
                val name = args.string("name")
                downloader.saveBytesToDownloads(name, officeMimeFor(name), unb64(args.string("content")))
                _toasts.tryEmit("Guardado em Transferências".tr())
                JsonNull
            }
            "share" -> {
                _shareRequests.tryEmit(f)
                JsonNull
            }
            // odt/doc/rtf ↔ docx (e pdf) pelo LibreOffice no servidor; nunca com E2E.
            "convert" -> {
                if (f.encrypted) throw BridgeError(422, "encrypted_not_convertible")
                JsonPrimitive(b64(convert(unb64(args.string("content")), args.string("from"), args.string("to"))))
            }
            // Comentários: o corpo já vem cifrado da página nos ficheiros E2E.
            "comments_list" -> api("GET", "file/${f.id}/comments")
            "comments_create" -> api("POST", "file/${f.id}/comments", args)
            "comments_update" -> api("PATCH", "comments/${args.string("id")}", JsonObject(args - "id"))
            "comments_resolve" -> api("POST", "comments/${args.string("id")}/resolve", JsonObject(emptyMap()))
            "comments_reopen" -> api("POST", "comments/${args.string("id")}/reopen", JsonObject(emptyMap()))
            "comments_delete" -> api("DELETE", "comments/${args.string("id")}")
            "comments_settings" -> api("PUT", "file/${f.id}/comment-settings", args)
            else -> throw BridgeError(400, "unknown_method")
        }
    }

    /**
     * Cópia na mesma pasta. Com [switch] (doc/rtf/xls gravados como docx/xlsx)
     * espera pelo envio e o editor passa a gravar na cópia.
     *
     * Se o envio demorar (fila atrás de outros), a página recebe 504 mas o envio
     * continua: o próximo save_copy reaproveita-o em vez de criar outra cópia e,
     * se já terminou, grava nele o conteúdo mais recente.
     */
    private suspend fun saveCopy(f: BrowseItem.File, name: String, bytes: ByteArray, switch: Boolean): JsonElement {
        if (!switch) {
            uploadManager.createDocument(name, bytes, officeMimeFor(name), f.parentId)
            _toasts.tryEmit("Cópia guardada".tr() + ": " + name)
            return JsonNull
        }
        val reused = pendingCopy
        val (taskId, job) = reused ?: uploadManager.createDocument(name, bytes, officeMimeFor(name), f.parentId, trackCreated = true)
            .also { pendingCopy = it }
        if (reused == null) _toasts.tryEmit("A guardar cópia…".tr())

        val finished = withTimeoutOrNull(COPY_TIMEOUT_MS) { job.join() } != null
        if (!finished) throw BridgeError(504, "upload_timeout")
        pendingCopy = null
        val created = uploadManager.takeCreated(taskId)?.toItem() as? BrowseItem.File
            ?: throw BridgeError(500, "upload_failed")
        // O nome pode vir cifrado (espaço privado): fica o que escrevemos.
        val copy = created.copy(name = name)
        switchTo(f, copy)
        // Cópia criada num pedido anterior: leva o conteúdo atual.
        if (reused != null) {
            save(copy, bytes, null, null, null)
        }
        return buildJsonObject { put("file", fileJson(copy)) }
    }

    // Envio da cópia (doc/rtf/xls) ainda a decorrer depois de a página desistir.
    private var pendingCopy: Pair<String, kotlinx.coroutines.Job>? = null

    /** Passa a editar [copy]; o viewer (partilhar, reabrir) também segue a cópia. */
    private fun switchTo(original: BrowseItem.File, copy: BrowseItem.File) {
        file = copy
        dataKey = null
        session.files = session.files.map { if (it.id == original.id) copy else it }
        if (session.startId == original.id) session.startId = copy.id
    }

    override fun onCleared() {
        // A página fechou com a cópia a meio: não reter o resultado no UploadManager.
        pendingCopy?.let { (taskId, job) -> job.invokeOnCompletion { uploadManager.takeCreated(taskId) } }
        super.onCleared()
    }

    private suspend fun convert(content: ByteArray, from: String, to: String): ByteArray = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("content", "document.$from", content.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .addFormDataPart("from", from)
            .addFormDataPart("to", to)
            .build()
        httpClient.newCall(authed("office/convert").post(body).build()).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code, response.body?.string())
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    /** Pedido JSON à API (comentários): devolve o corpo tal como a web o recebe. */
    private suspend fun api(method: String, path: String, payload: JsonObject? = null): JsonElement = withContext(Dispatchers.IO) {
        val body = payload?.toString()?.toRequestBody("application/json".toMediaTypeOrNull())
        val request = authed(path).addHeader("Accept", "application/json").method(method, body).build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw httpError(response.code, text)
            if (text.isBlank()) JsonNull else Json.parseToJsonElement(text)
        }
    }

    private fun authed(path: String): Request.Builder {
        val token = tokenStore.token
        return Request.Builder().url(backendUrlHolder.current.trimEnd('/') + "/api/" + path).apply {
            if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
        }
    }

    private suspend fun loadContent(f: BrowseItem.File): ByteArray = withContext(Dispatchers.IO) {
        val backend = backendUrlHolder.current.trimEnd('/')
        val token = tokenStore.token
        val basename = android.net.Uri.encode(f.basename.ifBlank { f.name }, "/")
        // Cache-buster: o URL do ficheiro não muda entre gravações.
        val query = buildList {
            if (!token.isNullOrBlank()) add("token=${android.net.Uri.encode(token)}")
            add("v=${System.currentTimeMillis()}")
        }.joinToString("&")
        val request = Request.Builder().url("$backend/file/$basename?$query").apply {
            if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
            addHeader("Cache-Control", "no-cache")
        }.build()
        val bytes = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw BridgeError(response.code)
            response.body?.bytes() ?: ByteArray(0)
        }
        if (f.encrypted) EncryptedFileCodec.decryptFull(bytes, key(f)) else bytes
    }

    private suspend fun save(f: BrowseItem.File, plain: ByteArray, baseRevision: Int?, collabSession: String?, collabUpto: Long?): JsonElement {
        if (!canEdit) throw BridgeError(403)
        val bytes = if (f.encrypted) EncryptedFileCodec.encrypt(plain, key(f)) else plain
        val text = "text/plain".toMediaTypeOrNull()
        val part = MultipartBody.Part.createFormData(
            "content",
            f.basename.ifBlank { f.name },
            bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
        )
        val body = raw(
            filesApi.updateDocumentContent(
                f.id,
                part,
                (if (f.encrypted) "1" else "0").toRequestBody(text),
                baseRevision?.toString()?.toRequestBody(text),
                collabSession?.toRequestBody(text),
                collabUpto?.toString()?.toRequestBody(text),
            )
        )
        rememberRevision(f, body)
        return Json.parseToJsonElement(body)
    }

    private fun rememberRevision(f: BrowseItem.File, body: String) {
        val revision = runCatching {
            Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("attributes")?.jsonObject
                ?.get("content_revision")?.jsonPrimitive?.intOrNull
        }.getOrNull() ?: return
        session.contentRevisions[f.id] = revision
    }

    private suspend fun key(f: BrowseItem.File): ByteArray {
        dataKey?.let { return it }
        if (!e2eKeyManager.isUnlocked) throw BridgeError(403, "e2e_locked").also { _toasts.tryEmit("Desbloqueia a encriptação para abrir este ficheiro".tr()) }
        val response = userEncryptionApi.fileKey(f.id)
        if (response.code() == 404) {
            _toasts.tryEmit("Ainda não tens a chave de encriptação deste ficheiro. Numa pasta de equipa, o dono tem de conceder o acesso de encriptação.".tr())
            throw BridgeError(403, "no_file_key")
        }
        val wrapped = response.body()?.wrapped_data_key ?: throw BridgeError(response.code().takeIf { it >= 400 } ?: 500)
        return e2eKeyManager.openFileDataKey(wrapped).also { dataKey = it }
    }

    private suspend fun raw(response: Response<okhttp3.ResponseBody>): String {
        val body = withContext(Dispatchers.IO) {
            if (response.isSuccessful) response.body()?.string().orEmpty() else response.errorBody()?.string().orEmpty()
        }
        if (!response.isSuccessful) throw httpError(response.code(), body)
        return body
    }

    /** Erro HTTP com o JSON do servidor (message, revision, session…) para o editor. */
    private fun httpError(code: Int, body: String?): BridgeError {
        val json = runCatching { Json.parseToJsonElement(body.orEmpty()).jsonObject }.getOrNull()
        return BridgeError(code, json?.get("message")?.jsonPrimitive?.contentOrNull, json)
    }

    private fun errorJson(e: BridgeError): String = buildJsonObject {
        put("ok", false)
        put("status", e.status)
        put("message", e.message)
        put("body", e.body ?: buildJsonObject { put("message", e.message) })
    }.toString()

    private fun currentUser() = (sessionManager.state.value as? AuthState.Authenticated)?.user

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: throw BridgeError(400, "missing_$key")

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
