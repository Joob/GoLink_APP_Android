package co.golink.tester.ui.screens.viewer

import co.golink.tester.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.TokenStore
import co.golink.tester.data.browse.BrowseRepository
import co.golink.tester.data.config.BackendUrlHolder
import co.golink.tester.data.download.FileDownloader
import co.golink.tester.data.files.FilesRepository
import co.golink.tester.data.share.ShareRepository
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.NavigationSection
import co.golink.tester.domain.browse.ShareInfo
import co.golink.tester.domain.files.isEditableText
import co.golink.tester.domain.files.isOfficeFile
import co.golink.tester.ui.screens.browse.ShareDialogUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** O que o ExoPlayer precisa para tocar um ficheiro: cifrado ou não, sai da mesma forma. */
data class PlayableMedia(
    val url: String,
    val dataSourceFactory: androidx.media3.datasource.DataSource.Factory,
)

@HiltViewModel
class FileViewerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val session: FileViewerSession,
    private val backendUrlHolder: BackendUrlHolder,
    private val tokenStore: TokenStore,
    @Named("authed") private val httpClient: OkHttpClient,
    private val downloader: FileDownloader,
    private val filesRepository: FilesRepository,
    private val browseRepository: BrowseRepository,
    private val shareRepository: ShareRepository,
    private val e2eKeyManager: co.golink.tester.data.encryption.E2EKeyManager,
    private val userEncryptionApi: co.golink.tester.network.UserEncryptionApi,
    private val trashRepository: co.golink.tester.data.trash.TrashRepository,
) : ViewModel() {

    /** Aberto a partir do Lixo: só ver; restaurar ou eliminar de vez. */
    val fromTrash: Boolean = session.fromTrash
    private val readOnlyIds: Set<String> = session.readOnlyIds
    private val notOwnedIds: Set<String> = session.notOwnedIds

    /** Pode editar/mover/apagar o ficheiro (não can-view nem de outro dono). */
    fun canEdit(file: BrowseItem.File): Boolean = !fromTrash && file.id !in readOnlyIds

    /** Partilhar: só o dono (o servidor recusa os outros com 403). */
    fun canShare(file: BrowseItem.File): Boolean = !fromTrash && file.id !in notOwnedIds

    data class UiState(
        val files: List<BrowseItem.File> = emptyList(),
        val currentIndex: Int = 0,
        val zoom: Float = 1f,
        val rotation: Float = 0f,
        val textContent: String? = null,
        val textLoading: Boolean = false,
        val textError: String? = null,
        // Só o início do ficheiro foi lido (acima de TEXT_CAP): sem edição.
        val textTruncated: Boolean = false,
        val toast: String? = null,
        val closeRequested: Boolean = false,
        val navigationTree: List<NavigationSection> = emptyList(),
        // Editor de texto embutido.
        val editing: Boolean = false,
        val draft: String = "",
        val saving: Boolean = false,
    ) {
        val dirty: Boolean get() = editing && draft != (textContent ?: "")
    }

    // Documentos abrem-se sozinhos no editor: um csv na lista de um txt, ao deslizar,
    // punha o editor a abrir o ficheiro inicial (o editor lê o startId).
    private val browsable = session.files.filterNot {
        it.id != session.startId && it.isOfficeFile()
    }

    private val _state = MutableStateFlow(
        UiState(
            files = browsable,
            currentIndex = browsable.indexOfFirst { it.id == session.startId }.coerceAtLeast(0),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _shareState = MutableStateFlow<ShareDialogUiState?>(null)
    val shareState: StateFlow<ShareDialogUiState?> = _shareState.asStateFlow()

    init {
        maybeLoadText()
        // "Recentes" da pesquisa (como o FilePreview da web); do Lixo não conta.
        if (!session.fromTrash) {
            session.files.firstOrNull { it.id == session.startId }?.let { file ->
                viewModelScope.launch { browseRepository.recordRecentOpened(file) }
            }
        }
    }

    val current: BrowseItem.File? get() = _state.value.files.getOrNull(_state.value.currentIndex)

    /** Subtítulo de contexto (anexos do calendário); consumido para não passar a outras aberturas. */
    val contextLabel: String? = session.contextLabel.also { session.contextLabel = null }

    /** Criado como ficheiro de texto (ex.: .csv): abre no editor de texto, não na folha de cálculo. */
    val opensAsText: Boolean = session.startInEditMode

    // Sem ?token= no URL (ficava nos access logs): todos os pedidos levam o
    // header Authorization, que é o único que o servidor lê.
    fun authedUrlFor(file: BrowseItem.File): String {
        val backend = backendUrlHolder.current.trimEnd('/')
        val basename = file.basename.ifBlank { file.name }
        val encoded = android.net.Uri.encode(basename, "/")
        return "$backend/file/$encoded"
    }

    fun authToken(): String? = tokenStore.token

    fun goTo(index: Int) {
        val s = _state.value
        if (index < 0 || index >= s.files.size || index == s.currentIndex) return
        _state.update { it.copy(currentIndex = index, zoom = 1f, rotation = 0f, textContent = null, textError = null, textTruncated = false, editing = false, draft = "") }
        maybeLoadText()
    }

    fun next() {
        val s = _state.value
        if (s.currentIndex < s.files.lastIndex) {
            _state.update { it.copy(currentIndex = it.currentIndex + 1, zoom = 1f, rotation = 0f, textContent = null, textError = null, textTruncated = false, editing = false, draft = "") }
            maybeLoadText()
        }
    }

    fun prev() {
        val s = _state.value
        if (s.currentIndex > 0) {
            _state.update { it.copy(currentIndex = it.currentIndex - 1, zoom = 1f, rotation = 0f, textContent = null, textError = null, textTruncated = false, editing = false, draft = "") }
            maybeLoadText()
        }
    }

    fun setZoom(z: Float) = _state.update { it.copy(zoom = z.coerceIn(1f, 8f)) }
    fun zoomIn() = setZoom(_state.value.zoom * 1.25f)
    fun zoomOut() = setZoom(_state.value.zoom / 1.25f)
    fun resetView() = _state.update { it.copy(zoom = 1f, rotation = 0f) }
    fun rotateLeft() = _state.update { it.copy(rotation = ((it.rotation - 90f) % 360f + 360f) % 360f) }
    fun rotateRight() = _state.update { it.copy(rotation = (it.rotation + 90f) % 360f) }

    fun download() {
        current?.let { file ->
            downloader.downloadFile(file) // passa file.encrypted -> decifra ao guardar
                .onSuccess { _state.update { it.copy(toast = "Download iniciado: %s".tr().format(file.name)) } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) } }
        }
    }

    // ----- E2E preview -----

    /**
     * Bitmaps já descodificados (por id), para voltar atrás no swipe sem
     * descarregar/descodificar de novo. 1/8 da heap da app.
     */
    val bitmapCache = object : android.util.LruCache<String, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt(),
    ) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount / 1024
    }

    /** Data keys já abertas nesta sessão do viewer (evita 1 pedido à API por página). */
    private val dataKeys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    override fun onCleared() {
        super.onCleared()
        bitmapCache.evictAll()
        dataKeys.values.forEach { it.fill(0) }
        dataKeys.clear()
    }

    /** Data key do ficheiro (null se não cifrado ou E2E bloqueado). */
    private suspend fun dataKeyFor(file: BrowseItem.File): ByteArray? {
        if (!file.encrypted || !e2eKeyManager.isUnlocked) return null
        dataKeys[file.id]?.let { return it }
        val resp = runCatching { userEncryptionApi.fileKey(file.id) }.getOrNull()
        // Sem linha de chave para esta conta (ex.: membro de Team Folder a quem o
        // dono ainda não concedeu acesso): diz porquê em vez de ficar em branco.
        if (resp?.code() == 404) {
            _state.update { it.copy(toast = "Ainda não tens a chave de encriptação deste ficheiro. Numa pasta de equipa, o dono tem de conceder o acesso de encriptação.".tr()) }
            return null
        }
        val wrapped = resp?.body()?.wrapped_data_key ?: return null
        val key = runCatching { e2eKeyManager.openFileDataKey(wrapped) }.getOrNull() ?: return null
        dataKeys[file.id] = key
        return key
    }

    /** Decifra bytes de imagem já descarregados (ciphertext) — usado no ImageViewer. */
    suspend fun decryptImageBytes(file: BrowseItem.File, cipher: ByteArray): ByteArray? = withContext(Dispatchers.Default) {
        val key = dataKeyFor(file) ?: return@withContext null
        runCatching { co.golink.tester.data.encryption.EncryptedFileCodec.decryptFull(cipher, key) }.getOrNull()
    }

    /** URL + origem de dados para o ExoPlayer. null se a data key não abrir (E2E bloqueado). */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun playableMedia(file: BrowseItem.File): PlayableMedia? {
        val token = tokenStore.token
        if (!file.encrypted) {
            val headers = if (!token.isNullOrBlank()) mapOf("Authorization" to "Bearer $token") else emptyMap()
            return PlayableMedia(
                url = authedUrlFor(file),
                dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(httpClient)
                    .setDefaultRequestProperties(headers),
            )
        }
        val key = withContext(Dispatchers.IO) { dataKeyFor(file) } ?: return null
        val url = authedUrlForDownload(file)
        return PlayableMedia(
            url = url,
            dataSourceFactory = co.golink.tester.data.encryption.EncryptedMediaDataSource.Factory(httpClient, url, token, key),
        )
    }

    private fun authedUrlForDownload(file: BrowseItem.File): String {
        val backend = backendUrlHolder.current.trimEnd('/')
        return "$backend/api/file/${android.net.Uri.encode(file.id)}/download"
    }

    fun isEncrypted(file: BrowseItem.File): Boolean = file.encrypted

    // ----- Sharing -----

    /** [file]: o editor de documentos pode ter passado para uma cópia (doc → docx). */
    fun openShareDialog(file: BrowseItem.File? = null) {
        val target = file ?: current ?: return
        _shareState.value = ShareDialogUiState(item = target, share = target.share)
    }

    /** Para os Detalhes atualizarem visualizações/downloads com a folha aberta. */
    suspend fun fetchShare(token: String): Result<ShareInfo?> = shareRepository.get(token)

    fun closeShareDialog() {
        _shareState.value = null
    }

    fun createShare(password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?) {
        val cur = _shareState.value ?: return
        _shareState.value = cur.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.create(cur.item, password, permission, expirationMinutes, downloadLimit, viewLimit, null)
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    applyShareToCurrent(info, cur.item.id)
                    _state.update { it.copy(toast = "Partilha criada".tr()) }
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) }
                }
        }
    }

    fun updateCurrentShare(password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?, protected: Boolean) {
        val cur = _shareState.value ?: return
        val token = cur.share?.token ?: return
        _shareState.value = cur.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.update(
                token = token,
                protected = protected,
                password = password?.takeIf { it.isNotBlank() },
                permission = permission,
                expirationMinutes = expirationMinutes,
                downloadLimit = downloadLimit,
                viewLimit = viewLimit,
                passwordTipAllowed = cur.share?.passwordTipAllowed ?: false,
                passwordTip = cur.share?.passwordTip,
            )
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    applyShareToCurrent(info, cur.item.id)
                    _state.update { it.copy(toast = "Partilha actualizada".tr()) }
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) }
                }
        }
    }

    fun revokeCurrentShare() {
        val cur = _shareState.value ?: return
        val token = cur.share?.token ?: return
        _shareState.value = cur.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.revoke(token)
                .onSuccess {
                    _shareState.value = null
                    applyShareToCurrent(null, cur.item.id)
                    _state.update { it.copy(toast = "Partilha revogada".tr()) }
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) }
                }
        }
    }

    fun fetchQrCode() {
        val token = _shareState.value?.share?.token ?: return
        _shareState.update { it?.copy(loadingQr = true) }
        viewModelScope.launch {
            shareRepository.qrCode(token)
                .onSuccess { svg -> _shareState.update { it?.copy(qrSvg = svg, loadingQr = false) } }
                .onFailure { _shareState.update { it?.copy(loadingQr = false) } }
        }
    }

    fun setEmailDialogVisible(visible: Boolean) {
        _shareState.update { it?.copy(emailDialogVisible = visible) }
    }

    fun sendShareEmail(emails: List<String>) {
        val token = _shareState.value?.share?.token ?: return
        if (emails.isEmpty()) return
        _shareState.update { it?.copy(sendingEmail = true, emailDialogVisible = false) }
        viewModelScope.launch {
            shareRepository.sendByEmail(token, emails)
                .onSuccess { _state.update { it.copy(toast = "Email enviado".tr()) } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) } }
            _shareState.update { it?.copy(sendingEmail = false) }
        }
    }

    /** Keep the open file's share state in sync so the menu label updates. */
    private fun applyShareToCurrent(info: ShareInfo?, itemId: String) {
        val idx = _state.value.currentIndex
        val file = _state.value.files.getOrNull(idx) ?: return
        // A folha pode ser de outro ficheiro (cópia criada pelo editor).
        if (itemId != file.id) return
        val newList = _state.value.files.toMutableList()
        newList[idx] = file.copy(share = info)
        session.files = newList
        _state.update { it.copy(files = newList) }
    }

    fun loadNavigationTree() {
        viewModelScope.launch {
            browseRepository.navigation()
                .onSuccess { sections -> _state.update { it.copy(navigationTree = sections) } }
        }
    }

    fun move(toFolderId: String?) {
        val file = current ?: return
        viewModelScope.launch {
            filesRepository.move(listOf(file), toFolderId)
                .onSuccess {
                    val newList = _state.value.files.toMutableList().also { it.removeAt(_state.value.currentIndex) }
                    session.files = newList
                    if (newList.isEmpty()) {
                        _state.update { it.copy(files = newList, toast = "Movido".tr(), closeRequested = true) }
                    } else {
                        val newIdx = _state.value.currentIndex.coerceAtMost(newList.lastIndex)
                        _state.update { it.copy(files = newList, currentIndex = newIdx, toast = "Movido".tr(), zoom = 1f, rotation = 0f, textContent = null, textTruncated = false, editing = false, draft = "") }
                        maybeLoadText()
                    }
                }
                .onFailure { t ->
                    val (message, retryable) = co.golink.tester.data.files.MoveManager.describeFailure(t)
                    _state.update { it.copy(toast = if (retryable) "Falha: %s".tr().format(message) else message.tr()) }
                }
        }
    }

    fun createFolderIn(name: String, parentId: String?) {
        viewModelScope.launch {
            filesRepository.createFolder(name.trim(), parentId)
                .onSuccess { _state.update { it.copy(toast = "Pasta criada".tr()) }; loadNavigationTree() }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) } }
        }
    }

    fun rename(newName: String) {
        val file = current ?: return
        viewModelScope.launch {
            filesRepository.rename(file, newName.trim())
                .onSuccess { updated ->
                    if (updated is BrowseItem.File) {
                        val newList = _state.value.files.toMutableList()
                        newList[_state.value.currentIndex] = updated
                        _state.update { it.copy(files = newList, toast = "Renomeado".tr()) }
                        session.files = newList
                    } else {
                        _state.update { it.copy(toast = "Renomeado".tr()) }
                    }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) } }
        }
    }

    /** Lixo: repõe o ficheiro no sítio de onde veio e fecha o viewer. */
    fun restoreFromTrash() {
        val file = current ?: return
        viewModelScope.launch {
            trashRepository.restore(listOf(file))
                .onSuccess { _state.update { it.copy(toast = "Restaurado".tr(), closeRequested = true) } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) } }
        }
    }

    /** Lixo: apaga de vez e fecha o viewer. */
    fun deleteForever() {
        val file = current ?: return
        viewModelScope.launch {
            filesRepository.delete(listOf(file), permanent = true)
                .onSuccess {
                    trashRepository.notifyChanged()
                    _state.update { it.copy(toast = "Eliminado para sempre".tr(), closeRequested = true) }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) } }
        }
    }

    fun delete() {
        val file = current ?: return
        viewModelScope.launch {
            filesRepository.delete(listOf(file), permanent = false)
                .onSuccess {
                    val newList = _state.value.files.toMutableList().also { it.removeAt(_state.value.currentIndex) }
                    session.files = newList
                    if (newList.isEmpty()) {
                        _state.update { it.copy(files = newList, toast = "Movido para o lixo".tr(), closeRequested = true) }
                    } else {
                        val newIdx = _state.value.currentIndex.coerceAtMost(newList.lastIndex)
                        _state.update { it.copy(files = newList, currentIndex = newIdx, toast = "Movido para o lixo".tr(), zoom = 1f, rotation = 0f, textContent = null, textTruncated = false, editing = false, draft = "") }
                        maybeLoadText()
                    }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: %s".tr().format(t.message.orEmpty())) } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
    fun acknowledgeClose() = _state.update { it.copy(closeRequested = false) }

    /**
     * Só ficheiros de texto com extensão gravável e, quando cifrados, com a
     * chave E2E desbloqueada — sem ela não há como cifrar o conteúdo novo.
     * O servidor volta a validar (can-edit, extensão, quota).
     */
    fun canEditText(): Boolean {
        val file = current ?: return false
        if (!canEdit(file)) return false
        if (!file.isEditableText()) return false
        if (file.encrypted && !e2eKeyManager.isUnlocked) return false
        // Gravar um texto cortado reescrevia o ficheiro só com o início.
        if (_state.value.textTruncated) return false
        return _state.value.textContent != null
    }

    fun startEditing() {
        if (!canEditText()) return
        _state.update { it.copy(editing = true, draft = it.textContent ?: "") }
    }

    fun updateDraft(value: String) = _state.update { it.copy(draft = value) }

    fun cancelEditing() = _state.update { it.copy(editing = false, draft = "") }

    fun saveText() {
        val file = current ?: return
        val s = _state.value
        if (!s.editing || s.saving) return
        val text = s.draft

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            filesRepository.updateTextContent(file, text)
                .onSuccess {
                    _state.update {
                        it.copy(
                            saving = false,
                            editing = false,
                            draft = "",
                            textContent = text,
                            toast = "Ficheiro guardado".tr(),
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(saving = false, toast = "Falha: %s".tr().format(t.message.orEmpty())) }
                }
        }
    }

    fun maybeLoadText() {
        val file = current ?: return
        if (!file.isTextLike()) return
        if (_state.value.textContent != null || _state.value.textLoading) return
        val url = authedUrlFor(file)
        val token = tokenStore.token
        _state.update { it.copy(textLoading = true, textError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder().url(url).apply {
                        if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                    }.build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val body = resp.body ?: return@use "" to false
                        // Lê no máximo TEXT_CAP+1 bytes (um .log de centenas de MB dava OOM).
                        // E2E: decifra em stream e para logo que passa o limite.
                        val out = CappedOutput(TEXT_CAP + 1)
                        try {
                            body.byteStream().use { input ->
                                if (file.encrypted) {
                                    val key = dataKeyFor(file) ?: error("E2E bloqueado ou sem chave deste ficheiro")
                                    co.golink.tester.data.encryption.EncryptedFileCodec.decryptStream(input, out, key)
                                } else {
                                    val buf = ByteArray(64 * 1024)
                                    while (true) {
                                        val r = input.read(buf)
                                        if (r < 0) break
                                        out.write(buf, 0, r)
                                    }
                                }
                            }
                        } catch (_: CappedOutput.Full) {
                        }
                        val bytes = out.bytes()
                        val truncated = bytes.size > TEXT_CAP
                        var end = minOf(bytes.size, TEXT_CAP)
                        // Não cortar a meio de um carácter UTF-8.
                        if (truncated) while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
                        String(bytes, 0, end, Charsets.UTF_8) to truncated
                    }
                }
            }
            result
                .onSuccess { (txt, truncated) ->
                    _state.update { it.copy(textContent = txt, textTruncated = truncated, textLoading = false) }
                    // Ficheiro acabado de criar pelo menu "+": abre já em edição.
                    if (session.startInEditMode) {
                        session.startInEditMode = false
                        startEditing()
                    }
                }
                .onFailure { t -> _state.update { it.copy(textLoading = false, textError = t.message ?: "erro") } }
        }
    }

    /** Guarda até [limit] bytes; ao passar lança [Full] para parar a leitura/decifra. */
    private class CappedOutput(private val limit: Int) : java.io.OutputStream() {
        class Full : java.io.IOException("cap")
        private val buf = java.io.ByteArrayOutputStream()
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            val room = limit - buf.size()
            buf.write(b, off, minOf(len, room))
            if (len >= room) throw Full()
        }
        fun bytes(): ByteArray = buf.toByteArray()
    }

    private companion object {
        const val TEXT_CAP = 1_048_576
    }
}

fun BrowseItem.File.isImageLike(): Boolean {
    val mt = mimetype?.lowercase().orEmpty()
    val nm = name.lowercase()
    return type == "image" || mt.startsWith("image/") ||
        nm.endsWith(".jpg") || nm.endsWith(".jpeg") || nm.endsWith(".png") ||
        nm.endsWith(".gif") || nm.endsWith(".webp")
}

fun BrowseItem.File.isPdfLike(): Boolean {
    val mt = mimetype?.lowercase().orEmpty()
    val nm = name.lowercase()
    return mt == "application/pdf" || nm.endsWith(".pdf")
}

fun BrowseItem.File.isTextLike(): Boolean {
    val mt = mimetype?.lowercase().orEmpty()
    val nm = name.lowercase()
    return mt.startsWith("text/") || nm.endsWith(".txt") || nm.endsWith(".md") ||
        nm.endsWith(".json") || nm.endsWith(".csv") || nm.endsWith(".log") ||
        nm.endsWith(".xml") || nm.endsWith(".html")
}

fun BrowseItem.File.isVideoLike(): Boolean {
    val mt = mimetype?.lowercase().orEmpty()
    val nm = name.lowercase()
    return type == "video" || mt.startsWith("video/") ||
        nm.endsWith(".mp4") || nm.endsWith(".mkv") || nm.endsWith(".mov") ||
        nm.endsWith(".avi") || nm.endsWith(".webm") || nm.endsWith(".3gp")
}

fun BrowseItem.File.isAudioLike(): Boolean {
    val mt = mimetype?.lowercase().orEmpty()
    val nm = name.lowercase()
    // O backend devolve type == "audio" (getFileType) e o mimetype tanto pode vir
    // como "audio/mpeg" como num token curto ("mpeg", "mp3", …).
    return type == "audio" || mt.startsWith("audio/") ||
        mt in setOf("mp3", "mpeg", "wav", "flac", "aac", "ogg", "oga", "m4a") ||
        nm.endsWith(".mp3") || nm.endsWith(".wav") || nm.endsWith(".flac") ||
        nm.endsWith(".aac") || nm.endsWith(".ogg") || nm.endsWith(".m4a")
}

fun BrowseItem.File.isViewable(): Boolean =
    isImageLike() || isPdfLike() || isTextLike() || isVideoLike() || isAudioLike()
