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
) : ViewModel() {

    data class UiState(
        val files: List<BrowseItem.File> = emptyList(),
        val currentIndex: Int = 0,
        val zoom: Float = 1f,
        val rotation: Float = 0f,
        val textContent: String? = null,
        val textLoading: Boolean = false,
        val textError: String? = null,
        val toast: String? = null,
        val closeRequested: Boolean = false,
        val navigationTree: List<NavigationSection> = emptyList(),
    )

    private val _state = MutableStateFlow(
        UiState(
            files = session.files,
            currentIndex = session.files.indexOfFirst { it.id == session.startId }.coerceAtLeast(0),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _shareState = MutableStateFlow<ShareDialogUiState?>(null)
    val shareState: StateFlow<ShareDialogUiState?> = _shareState.asStateFlow()

    init {
        maybeLoadText()
    }

    val current: BrowseItem.File? get() = _state.value.files.getOrNull(_state.value.currentIndex)

    fun authedUrlFor(file: BrowseItem.File): String {
        val backend = backendUrlHolder.current.trimEnd('/')
        val token = tokenStore.token
        val tokenParam = if (!token.isNullOrBlank()) "?token=${android.net.Uri.encode(token)}" else ""
        val basename = file.basename.ifBlank { file.name }
        val encoded = android.net.Uri.encode(basename, "/")
        return "$backend/file/$encoded$tokenParam"
    }

    fun authToken(): String? = tokenStore.token

    fun goTo(index: Int) {
        val s = _state.value
        if (index < 0 || index >= s.files.size || index == s.currentIndex) return
        _state.update { it.copy(currentIndex = index, zoom = 1f, rotation = 0f, textContent = null, textError = null) }
        maybeLoadText()
    }

    fun next() {
        val s = _state.value
        if (s.currentIndex < s.files.lastIndex) {
            _state.update { it.copy(currentIndex = it.currentIndex + 1, zoom = 1f, rotation = 0f, textContent = null, textError = null) }
            maybeLoadText()
        }
    }

    fun prev() {
        val s = _state.value
        if (s.currentIndex > 0) {
            _state.update { it.copy(currentIndex = it.currentIndex - 1, zoom = 1f, rotation = 0f, textContent = null, textError = null) }
            maybeLoadText()
        }
    }

    fun setZoom(z: Float) = _state.update { it.copy(zoom = z.coerceIn(0.2f, 8f)) }
    fun zoomIn() = setZoom(_state.value.zoom * 1.25f)
    fun zoomOut() = setZoom(_state.value.zoom / 1.25f)
    fun resetView() = _state.update { it.copy(zoom = 1f, rotation = 0f) }
    fun rotateLeft() = _state.update { it.copy(rotation = ((it.rotation - 90f) % 360f + 360f) % 360f) }
    fun rotateRight() = _state.update { it.copy(rotation = (it.rotation + 90f) % 360f) }

    fun download() {
        current?.let { file ->
            downloader.downloadFile(file) // passa file.encrypted -> decifra ao guardar
                .onSuccess { _state.update { it.copy(toast = "Download iniciado: ${file.name}") } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    // ----- E2E preview -----

    /** Data key do ficheiro (null se não cifrado ou E2E bloqueado). */
    private suspend fun dataKeyFor(file: BrowseItem.File): ByteArray? {
        if (!file.encrypted || !e2eKeyManager.isUnlocked) return null
        val wrapped = runCatching { userEncryptionApi.fileKey(file.id).body()?.wrapped_data_key }.getOrNull() ?: return null
        return runCatching { e2eKeyManager.openFileDataKey(wrapped) }.getOrNull()
    }

    /** Decifra bytes de imagem já descarregados (ciphertext) — usado no ImageViewer. */
    suspend fun decryptImageBytes(file: BrowseItem.File, cipher: ByteArray): ByteArray? = withContext(Dispatchers.Default) {
        val key = dataKeyFor(file) ?: return@withContext null
        runCatching { co.golink.tester.data.encryption.EncryptedFileCodec.decryptFull(cipher, key) }.getOrNull()
    }

    /**
     * Descarrega + decifra um media cifrado para um ficheiro local (seek nativo).
     * Devolve null se não cifrado. v1: decifra o ficheiro todo antes de tocar.
     */
    suspend fun decryptedMediaFile(file: BrowseItem.File): java.io.File? = withContext(Dispatchers.IO) {
        val key = dataKeyFor(file) ?: return@withContext null
        val url = authedUrlForDownload(file)
        val out = java.io.File(appContext.cacheDir, "e2e_view_${file.id}.tmp")
        runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body ?: error("vazio")
                body.byteStream().use { input ->
                    out.outputStream().use { o ->
                        co.golink.tester.data.encryption.EncryptedFileCodec.decryptStream(input, o, key)
                    }
                }
            }
            out
        }.getOrElse { out.delete(); null }
    }

    private fun authedUrlForDownload(file: BrowseItem.File): String {
        val backend = backendUrlHolder.current.trimEnd('/')
        val token = tokenStore.token
        val tokenParam = if (!token.isNullOrBlank()) "?token=${android.net.Uri.encode(token)}" else ""
        return "$backend/api/file/${android.net.Uri.encode(file.id)}/download$tokenParam"
    }

    fun isEncrypted(file: BrowseItem.File): Boolean = file.encrypted

    // ----- Sharing -----

    fun openShareDialog() {
        val file = current ?: return
        _shareState.value = ShareDialogUiState(item = file, share = file.share)
    }

    fun closeShareDialog() {
        _shareState.value = null
    }

    fun createShare(password: String?, permission: String?, expirationDays: Int?) {
        val cur = _shareState.value ?: return
        _shareState.value = cur.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.create(cur.item, password, permission, expirationDays, null)
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    applyShareToCurrent(info)
                    _state.update { it.copy(toast = "Partilha criada".tr()) }
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: ${t.message}") }
                }
        }
    }

    fun updateCurrentShare(password: String?, permission: String?, expirationDays: Int?) {
        val cur = _shareState.value ?: return
        val token = cur.share?.token ?: return
        _shareState.value = cur.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.update(
                token = token,
                protected = !password.isNullOrBlank(),
                password = password?.takeIf { it.isNotBlank() },
                permission = permission,
                expirationDays = expirationDays,
            )
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    applyShareToCurrent(info)
                    _state.update { it.copy(toast = "Partilha actualizada".tr()) }
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: ${t.message}") }
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
                    applyShareToCurrent(null)
                    _state.update { it.copy(toast = "Partilha revogada".tr()) }
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: ${t.message}") }
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
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
            _shareState.update { it?.copy(sendingEmail = false) }
        }
    }

    /** Keep the open file's share state in sync so the menu label updates. */
    private fun applyShareToCurrent(info: ShareInfo?) {
        val idx = _state.value.currentIndex
        val file = _state.value.files.getOrNull(idx) ?: return
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
                        _state.update { it.copy(files = newList, toast = "Movido", closeRequested = true) }
                    } else {
                        val newIdx = _state.value.currentIndex.coerceAtMost(newList.lastIndex)
                        _state.update { it.copy(files = newList, currentIndex = newIdx, toast = "Movido", zoom = 1f, rotation = 0f, textContent = null) }
                        maybeLoadText()
                    }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun createFolderIn(name: String, parentId: String?) {
        viewModelScope.launch {
            filesRepository.createFolder(name.trim(), parentId)
                .onSuccess { _state.update { it.copy(toast = "Pasta criada".tr()) }; loadNavigationTree() }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
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
                        _state.update { it.copy(files = newList, toast = "Renomeado") }
                        session.files = newList
                    } else {
                        _state.update { it.copy(toast = "Renomeado") }
                    }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
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
                        _state.update { it.copy(files = newList, currentIndex = newIdx, toast = "Movido para o lixo".tr(), zoom = 1f, rotation = 0f, textContent = null) }
                        maybeLoadText()
                    }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
    fun acknowledgeClose() = _state.update { it.copy(closeRequested = false) }

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
                        val bytes = resp.body?.bytes() ?: ByteArray(0)
                        // Cap to ~1MB to avoid blowing memory
                        val capped = if (bytes.size > 1_048_576) bytes.copyOfRange(0, 1_048_576) else bytes
                        String(capped, Charsets.UTF_8)
                    }
                }
            }
            result
                .onSuccess { txt -> _state.update { it.copy(textContent = txt, textLoading = false) } }
                .onFailure { t -> _state.update { it.copy(textLoading = false, textError = t.message ?: "erro") } }
        }
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
