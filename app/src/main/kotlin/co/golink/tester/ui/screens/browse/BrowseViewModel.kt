package co.golink.tester.ui.screens.browse

import co.golink.tester.ui.i18n.tr
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.data.browse.BrowseRepository
import co.golink.tester.data.browse.PagedItems
import co.golink.tester.data.download.FileDownloader
import co.golink.tester.data.favourites.FavouritesRepository
import co.golink.tester.data.files.FilesRepository
import co.golink.tester.data.share.ShareRepository
import co.golink.tester.data.teams.TeamsRepository
import co.golink.tester.data.trash.TrashRepository
import co.golink.tester.data.upload.UploadManager
import co.golink.tester.domain.files.withTextExtension
import co.golink.tester.data.upload.UploadTask
import co.golink.tester.data.uploadrequest.UploadRequestRepository
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.NavigationSection
import co.golink.tester.domain.browse.ShareInfo
import co.golink.tester.domain.teams.TeamInvitation
import co.golink.tester.ui.screens.viewer.FileViewerSession
import co.golink.tester.ui.screens.viewer.isViewable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface BrowseMode {
    data class Folder(val id: String?, val name: String) : BrowseMode
    data object Latest : BrowseMode
    data object Shared : BrowseMode
    data object Favourites : BrowseMode
    data object Trash : BrowseMode
    data class SearchResults(val query: String) : BrowseMode
    data class TeamFolder(val id: String?, val name: String) : BrowseMode
    data class SharedWithMe(val id: String?, val name: String) : BrowseMode
}

enum class ViewMode { LIST, GRID }
enum class SortMode { ALPHA_ASC, ALPHA_DESC, DATE_DESC, DATE_ASC }

data class BrowseUiState(
    val mode: BrowseMode = BrowseMode.Folder(id = null, name = "Os meus ficheiros".tr()),
    val crumbs: List<Crumb> = emptyList(),
    val items: List<BrowseItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val error: String? = null,
    val searchQuery: String = "",
    val toast: String? = null,
    val navigationTree: List<NavigationSection> = emptyList(),
    val processing: String? = null,
    val viewMode: ViewMode = ViewMode.LIST,
    val sortMode: SortMode = SortMode.ALPHA_ASC,
    val selectedIds: Set<String> = emptySet(),
    val selectMode: Boolean = false,
    val filesOnly: Boolean = false,
    val isRefreshing: Boolean = false,
    // Flush do lixo: enquanto true, a lista fica com blur e os ficheiros vão
    // desaparecendo por trás à medida que cada lote é apagado; progresso 0–100.
    val isEmptyingTrash: Boolean = false,
    val emptyingProgress: Int = 0,
    // Id de um ficheiro que o ecrã deve abrir no viewer (ficheiro de texto
    // acabado de criar). O ecrã navega e limpa-o.
    val openFileRequest: String? = null,
)

data class ShareDialogUiState(
    val item: BrowseItem,
    val share: ShareInfo?,
    val qrSvg: String? = null,
    val loadingQr: Boolean = false,
    val sendingEmail: Boolean = false,
    val isWorking: Boolean = false,
    val emailDialogVisible: Boolean = false,
    // E2E: chave (#k=) da partilha ainda a ser preparada em fundo (pastas fazem
    // round-trip ao servidor). Enquanto true, a UI bloqueia copiar/QR/email para
    // não entregar um link SEM a chave.
    val keyPending: Boolean = false,
)

data class Crumb(val id: String?, val name: String)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: BrowseRepository,
    private val filesRepository: FilesRepository,
    private val shareRepository: ShareRepository,
    private val favouritesRepository: FavouritesRepository,
    private val trashRepository: TrashRepository,
    private val teamsRepository: TeamsRepository,
    private val uploadRequestRepository: UploadRequestRepository,
    private val sessionManager: SessionManager,
    private val downloader: FileDownloader,
    private val uploadManager: UploadManager,
    private val fileViewerSession: FileViewerSession,
    private val viewPreferences: co.golink.tester.data.settings.ViewPreferences,
    private val e2eKeys: co.golink.tester.data.encryption.E2EKeyManager,
    private val e2eShareService: co.golink.tester.data.encryption.E2EShareService,
    private val encryptionApi: co.golink.tester.network.UserEncryptionApi,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private val _shareState = MutableStateFlow<ShareDialogUiState?>(null)
    val shareState: StateFlow<ShareDialogUiState?> = _shareState.asStateFlow()

    val uploads: StateFlow<List<UploadTask>> = uploadManager.tasks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // E2E Fase 2: decifra os nomes das favoritas (re-emite ao desbloquear).
    val favouriteFolders: StateFlow<List<BrowseItem.Folder>> =
        kotlinx.coroutines.flow.combine(sessionManager.state, e2eKeys.unlocked) { s, _ ->
            val list = if (s is AuthState.Authenticated) s.user.favouriteFolders else emptyList()
            list.map { f -> e2eKeys.openNameOrNull(f.nameEncrypted)?.let { f.copy(name = it) } ?: f }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    // Sincronização em tempo real (polling): enquanto o ecrã está visível,
    // sondamos periodicamente o "fingerprint" da pasta actual e, se mudou
    // (upload/apagar/mover/renomear noutro dispositivo), recarregamos em
    // silêncio sem spinner nem perda de scroll.
    private var pollJob: Job? = null
    @Volatile private var lastFingerprint: String? = null

    init {
        // Restaura vista/ordenação guardadas antes do primeiro load, para a lista
        // já vir ordenada como o utilizador deixou.
        _state.update {
            it.copy(
                viewMode = viewPreferences.viewMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BROWSE, ViewMode.LIST),
                sortMode = viewPreferences.sortMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BROWSE, SortMode.ALPHA_ASC),
            )
        }
        openRoot()
        viewModelScope.launch {
            uploadManager.completedTick.collect { t ->
                if (t > 0) {
                    loadCurrent()
                    pendingTextFileName?.let { openNewTextFileWhenListed(it) }
                }
            }
        }
        viewModelScope.launch {
            favouriteFolders.collect {
                if (_state.value.mode == BrowseMode.Favourites) loadCurrent()
            }
        }
        viewModelScope.launch {
            downloader.events.collect { ev ->
                when (ev) {
                    is FileDownloader.Event.Started -> _state.update { it.copy(toast = "Download iniciado: ${ev.name}") }
                    is FileDownloader.Event.Completed -> _state.update { it.copy(toast = "Download concluído: ${ev.name}") }
                    is FileDownloader.Event.Failed -> _state.update { it.copy(toast = "Falha em ${ev.name}: ${ev.message}") }
                }
            }
        }
        // E2E Fase 2: ao desbloquear, recarrega (para decifrar os nomes que tinham
        // sido lidos trancados) e migra em background os nomes ainda em claro.
        viewModelScope.launch {
            e2eKeys.unlocked.collect { unlocked ->
                if (unlocked) {
                    launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { e2eKeys.migrateNames() } }
                    loadCurrent()
                    loadNavigationTree()
                }
            }
        }
    }

    fun openRoot() {
        val crumb = Crumb(id = null, name = "Os meus ficheiros".tr())
        _state.update {
            it.copy(
                mode = BrowseMode.Folder(id = null, name = crumb.name),
                crumbs = listOf(crumb),
                searchQuery = "",
            )
        }
        loadCurrent()
    }

    fun openFolder(item: BrowseItem.Folder) {
        val newMode = when (_state.value.mode) {
            is BrowseMode.TeamFolder -> BrowseMode.TeamFolder(id = item.id, name = item.name)
            is BrowseMode.SharedWithMe -> BrowseMode.SharedWithMe(id = item.id, name = item.name)
            else -> BrowseMode.Folder(id = item.id, name = item.name)
        }
        _state.update {
            it.copy(
                mode = newMode,
                crumbs = it.crumbs + Crumb(item.id, item.name),
                searchQuery = "",
            )
        }
        loadCurrent()
    }

    fun openFolderById(id: String, name: String) {
        _state.update {
            it.copy(
                mode = BrowseMode.Folder(id = id, name = name),
                crumbs = listOf(Crumb(null, "Os meus ficheiros".tr()), Crumb(id, name)),
                searchQuery = "",
            )
        }
        loadCurrent()
    }

    fun goToCrumb(index: Int) {
        val crumbs = _state.value.crumbs.take(index + 1)
        val last = crumbs.lastOrNull() ?: return openRoot()
        val newMode = when (_state.value.mode) {
            is BrowseMode.TeamFolder -> BrowseMode.TeamFolder(id = last.id, name = last.name)
            is BrowseMode.SharedWithMe -> BrowseMode.SharedWithMe(id = last.id, name = last.name)
            else -> BrowseMode.Folder(id = last.id, name = last.name)
        }
        _state.update {
            it.copy(
                mode = newMode,
                crumbs = crumbs,
                searchQuery = "",
            )
        }
        loadCurrent()
    }

    fun openLatest() = switchTo(BrowseMode.Latest, "Recentes")
    fun openShared() = switchTo(BrowseMode.Shared, "Partilhado comigo".tr())
    fun openFavourites() = switchTo(BrowseMode.Favourites, "Favoritos".tr())
    fun openTrash() = switchTo(BrowseMode.Trash, "Lixo".tr())
    fun openTeamFolders() = switchTo(BrowseMode.TeamFolder(id = null, name = "Pastas de equipa".tr()), "Pastas de equipa".tr())
    fun openSharedWithMe() = switchTo(BrowseMode.SharedWithMe(id = null, name = "Partilhado comigo".tr()), "Partilhado comigo".tr())

    private fun switchTo(mode: BrowseMode, name: String) {
        _state.update {
            it.copy(
                mode = mode,
                crumbs = listOf(Crumb(null, name)),
                searchQuery = "",
            )
        }
        loadCurrent()
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) return
        // Mínimo 2 chars + debounce 300ms: 1 carácter é um LIKE quase-full-scan
        // no servidor sem valor para o utilizador (alinha com o Spotlight da Web).
        if (query.trim().length < 2) return
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(mode = BrowseMode.SearchResults(query), isLoading = true, error = null) }
            repository.search(query)
                .onSuccess { items ->
                    if (_state.value.searchQuery == query) {
                        _state.update { it.copy(items = items, isLoading = false) }
                    }
                }
                .onFailure { t ->
                    if (_state.value.searchQuery == query) {
                        _state.update { it.copy(isLoading = false, error = t.message) }
                    }
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        openRoot()
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        loadCurrent()
    }

    fun notifyComingSoon() {
        _state.update { it.copy(toast = "Em breve".tr()) }
    }

    fun prepareViewer(files: List<BrowseItem.File>, startId: String) {
        fileViewerSession.files = files
        fileViewerSession.startId = startId
    }

    fun downloadFile(item: BrowseItem.File) {
        downloader.downloadFile(item)
            .onSuccess { _state.update { it.copy(toast = "Download iniciado: ${item.name}") } }
            .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
    }

    fun downloadItem(item: BrowseItem) {
        when (item) {
            is BrowseItem.File -> downloadFile(item)
            is BrowseItem.Folder -> downloader.downloadFolder(item)
                .onSuccess { _state.update { it.copy(toast = "A preparar zip: ${item.name}") } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun downloadSelected() {
        val items = selectedItems()
        exitSelectMode()
        if (items.isEmpty()) return
        // 1 item → download direto (single file ou zip da pasta). Vários itens
        // (ficheiros e/ou pastas) → SEMPRE um zip, como na web. Antes, uma
        // seleção só de ficheiros descarregava um a um em vez de zipar.
        if (items.size == 1) {
            downloadItem(items.first())
        } else {
            downloader.downloadZip(items, suggestedName = "ficheiros.zip")
                .onSuccess { _state.update { it.copy(toast = "A preparar zip (${items.size})") } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun moveSelected(toFolderId: String?) {
        val targets = selectedItems()
        exitSelectMode()
        targets.forEach { move(it, toFolderId) }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    fun createFolder(name: String) {
        val parentId = (state.value.mode as? BrowseMode.Folder)?.id
        viewModelScope.launch {
            filesRepository.createFolder(name.trim(), parentId)
                .onSuccess { _state.update { it.copy(toast = "Pasta criada".tr()) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    /** Cria um ficheiro .txt vazio na pasta atual (o nome ganha .txt se faltar). */
    fun createTextFile(name: String) {
        val parentId = (state.value.mode as? BrowseMode.Folder)?.id
        val filename = withTextExtension(name)
        // Sem toast: quem confirma é o painel de uploads (e a lista recarrega no
        // completedTick) — anunciar "criado" aqui mentia se o upload falhasse.
        uploadManager.createTextFile(filename, "", parentId)
        pendingTextFileName = filename
    }

    // Nome do ficheiro de texto acabado de criar, à espera de aparecer na lista
    // recarregada para o abrirmos no editor.
    private var pendingTextFileName: String? = null

    /**
     * A recarga da lista corre em background, por isso esperamos que o ficheiro
     * novo apareça antes de pedir ao ecrã que abra o viewer em modo de edição.
     */
    private fun openNewTextFileWhenListed(name: String) {
        viewModelScope.launch {
            val file = withTimeoutOrNull(15_000) {
                _state
                    .map { s -> s.items.filterIsInstance<BrowseItem.File>().firstOrNull { it.name == name } }
                    .filterNotNull()
                    .first()
            }
            pendingTextFileName = null
            if (file == null) return@launch

            prepareViewer(
                state.value.items.filterIsInstance<BrowseItem.File>().filter { it.isViewable() },
                file.id,
            )
            fileViewerSession.startInEditMode = true
            _state.update { it.copy(openFileRequest = file.id) }
        }
    }

    fun consumeOpenFileRequest() = _state.update { it.copy(openFileRequest = null) }

    fun createFolderIn(name: String, parentId: String?) {
        viewModelScope.launch {
            filesRepository.createFolder(name.trim(), parentId)
                .onSuccess {
                    _state.update { it.copy(toast = "Pasta criada".tr()) }
                    loadNavigationTree()
                    if ((state.value.mode as? BrowseMode.Folder)?.id == parentId) loadCurrent()
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    // E2E Fase 2: só ciframos nomes no espaço privado do utilizador — não em
    // pastas de equipa / partilhas / resultados de pesquisa.
    private fun isPrivateNameContext(): Boolean = when (state.value.mode) {
        is BrowseMode.Folder, BrowseMode.Latest, BrowseMode.Favourites, BrowseMode.Trash -> true
        else -> false
    }

    fun rename(item: BrowseItem, newName: String) {
        viewModelScope.launch {
            filesRepository.rename(item, newName.trim(), encryptName = isPrivateNameContext())
                .onSuccess { _state.update { it.copy(toast = "Renomeado") }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun delete(item: BrowseItem, permanent: Boolean = false) {
        if (permanent) _state.update { it.copy(processing = "A eliminar…".tr()) }
        viewModelScope.launch {
            filesRepository.delete(listOf(item), permanent)
                .onSuccess { _state.update { it.copy(processing = null, toast = if (permanent) "Eliminado" else "Movido para o lixo".tr()) }; loadCurrent() }
                .onFailure { t ->
                    android.util.Log.e("BrowseVM", "delete failed", t)
                    _state.update { it.copy(processing = null, toast = "Falha: ${t.message ?: t::class.java.simpleName}") }
                }
        }
    }

    fun move(item: BrowseItem, toFolderId: String?) {
        viewModelScope.launch {
            filesRepository.move(listOf(item), toFolderId)
                .onSuccess { _state.update { it.copy(toast = "Movido") }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    // Nomes (em claro) dos ficheiros já visíveis na pasta: com nomes cifrados é
    // o cliente que deteta o conflito, o servidor só vê o placeholder.
    private fun visibleFileNames(): Map<String, String> =
        state.value.items.filterIsInstance<BrowseItem.File>().associate { it.name to it.id }

    fun upload(uri: Uri) {
        val parentId = (state.value.mode as? BrowseMode.Folder)?.id
        uploadManager.enqueue(uri, parentId, visibleFileNames())
    }

    fun uploadMany(uris: List<Uri>) {
        val parentId = (state.value.mode as? BrowseMode.Folder)?.id
        val names = visibleFileNames()
        uris.forEach { uploadManager.enqueue(it, parentId, names) }
    }

    fun remoteUpload(rawInput: String) {
        val urls = rawInput.split(Regex("[\\s,]+"))
            .map { it.trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        if (urls.isEmpty()) {
            _state.update { it.copy(toast = "Insira pelo menos um URL válido".tr()) }
            return
        }
        val parentId = (state.value.mode as? BrowseMode.Folder)?.id
        viewModelScope.launch {
            filesRepository.remoteUpload(urls, parentId)
                .onSuccess { _state.update { it.copy(toast = "${urls.size} URL(s) na fila") }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun createTeamFolder(name: String, invitations: List<TeamInvitation>) {
        viewModelScope.launch {
            teamsRepository.createTeamFolder(name.trim(), invitations)
                .onSuccess { _state.update { it.copy(toast = "Pasta de equipa criada".tr()) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun convertToTeamFolder(folderId: String, invitations: List<TeamInvitation>) {
        viewModelScope.launch {
            teamsRepository.convertToTeamFolder(folderId, invitations)
                .onSuccess { _state.update { it.copy(toast = "Pasta convertida".tr()) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    // folderId nulo = usa a pasta actual (fluxo do botão +). O menu "…" de uma
    // pasta passa o id dessa pasta. Ao criar, copiamos o link para a área de
    // transferência, como faz a Web.
    fun createFileRequest(name: String?, email: String?, notes: String?, folderId: String? = null) {
        val effectiveFolder = folderId ?: (state.value.mode as? BrowseMode.Folder)?.id
        viewModelScope.launch {
            uploadRequestRepository.createFileRequest(name, email, notes, effectiveFolder)
                .onSuccess { link ->
                    copyToClipboard("file-request", link)
                    _state.update { it.copy(toast = "Pedido criado — link copiado".tr()) }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        runCatching {
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        }
    }

    fun uploadFolder(treeUri: Uri) {
        val parentId = (state.value.mode as? BrowseMode.Folder)?.id
        viewModelScope.launch {
            val files = collectFilesFromTree(treeUri)
            if (files.isEmpty()) {
                _state.update { it.copy(toast = "Pasta vazia ou sem ficheiros".tr()) }
                return@launch
            }
            files.forEach { uploadManager.enqueue(it, parentId) }
            _state.update { it.copy(toast = "${files.size} ficheiro(s) na fila") }
        }
    }

    private fun collectFilesFromTree(treeUri: Uri): List<Uri> {
        val out = mutableListOf<Uri>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        walkTree(treeUri, rootDocId, out)
        return out
    }

    private fun walkTree(treeUri: Uri, docId: String, out: MutableList<Uri>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        appContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0)
                val mime = cursor.getString(1)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walkTree(treeUri, childId, out)
                } else {
                    out += DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                }
            }
        }
    }

    fun loadNavigationTree() {
        viewModelScope.launch {
            repository.navigation()
                .onSuccess { sections ->
                    _state.update { st ->
                        // E2E Fase 2: crumbs criados com a chave trancada ficaram com o
                        // placeholder '•' — corrige-os com o nome decifrado da árvore.
                        val byId = buildMap<String, String> {
                            fun walk(f: co.golink.tester.domain.browse.NavFolder) {
                                put(f.id, f.name); f.folders.forEach(::walk)
                            }
                            sections.forEach { it.folders.forEach(::walk) }
                        }
                        val crumbs = st.crumbs.map { c ->
                            val id = c.id
                            if (c.name == "•" && id != null) c.copy(name = byId[id] ?: c.name) else c
                        }
                        st.copy(navigationTree = sections, crumbs = crumbs)
                    }
                }
        }
    }

    fun dismissCompletedUploads() = uploadManager.clearFinished()
    fun cancelUpload(taskId: String) {
        uploadManager.cancel(taskId)
        _state.update { it.copy(toast = "Upload cancelado".tr()) }
    }
    fun retryUpload(taskId: String) = uploadManager.retry(taskId)
    fun retryFailedUploads() = uploadManager.retryFailed()
    fun clearAllUploads() {
        val had = uploads.value.any { it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued }
        uploadManager.clearAll()
        if (had) {
            _state.update { it.copy(toast = "Uploads cancelados".tr()) }
            loadCurrent() // refresh de confirmação
        }
    }
    fun overwriteConflict(taskId: String) = uploadManager.overwriteConflict(taskId)
    fun skipConflict(taskId: String) = uploadManager.skipConflict(taskId)

    fun openShareDialog(item: BrowseItem) {
        _shareState.value = ShareDialogUiState(item = item, share = item.share)
        attachShareKeyFragment()
    }

    /**
     * E2E: a data key tem de viajar no fragmento (#k=) do link de partilha —
     * nunca chega ao servidor. Sem isto o destinatário recebia ciphertext que não
     * conseguia abrir (igual ao CopyShareLink da Web). FICHEIRO: a própria data
     * key; PASTA: uma chave de partilha que abre as data keys registadas no token.
     */
    private fun attachShareKeyFragment() {
        val st = _shareState.value ?: return
        val share = st.share ?: return
        val link = share.link ?: return
        if (!e2eKeys.isUnlocked || link.contains("#k=")) return
        when (val item = st.item) {
            is BrowseItem.File -> {
                if (!item.encrypted) return
                _shareState.update { it?.copy(keyPending = true) }
                viewModelScope.launch {
                    try {
                        runCatching {
                            val wrapped = encryptionApi.fileKey(item.id).body()?.wrapped_data_key ?: return@runCatching
                            // Regista também o nome cifrado com esta chave: sem isso
                            // o visitante do link vê o placeholder ('•').
                            val fragment = e2eShareService.buildFileShareFragment(
                                item.id, share.token, wrapped, item.name,
                            ) ?: return@runCatching
                            _shareState.update { cur ->
                                cur?.copy(share = cur.share?.copy(link = cur.share.link + fragment))
                            }
                        }
                    } finally {
                        _shareState.update { it?.copy(keyPending = false) }
                    }
                }
            }
            is BrowseItem.Folder -> {
                _shareState.update { it?.copy(keyPending = true) }
                viewModelScope.launch {
                    try {
                        runCatching {
                            val result = e2eShareService.buildFolderShareFragment(item.id, share.token)
                            // Chave desta partilha irrecuperável e já há links lá fora:
                            // cunhar uma nova matava-os — avisa-se em vez disso.
                            if (result.keyLost) {
                                _state.update { it.copy(toast = "A chave desta partilha só existe no dispositivo onde criaste o link. Abre a conta lá uma vez para a recuperar — senão, revoga e volta a partilhar (o link antigo deixa de funcionar).".tr()) }
                            }
                            val fragment = result.fragment ?: return@runCatching
                            _shareState.update { cur ->
                                cur?.copy(share = cur.share?.copy(link = cur.share.link + fragment))
                            }
                        }
                    } finally {
                        _shareState.update { it?.copy(keyPending = false) }
                    }
                }
            }
        }
    }

    fun closeShareDialog() {
        _shareState.value = null
    }

    fun createShare(password: String?, permission: String?, expirationDays: Int?, downloadLimit: Int?, singleView: Boolean) {
        val current = _shareState.value ?: return
        _shareState.value = current.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.create(current.item, password, permission, expirationDays, downloadLimit, singleView, null)
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    attachShareKeyFragment()
                    _state.update { it.copy(toast = "Partilha criada".tr()) }
                    loadCurrent()
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: ${t.message}") }
                }
        }
    }

    fun updateCurrentShare(password: String?, permission: String?, expirationDays: Int?, downloadLimit: Int?, singleView: Boolean) {
        val current = _shareState.value ?: return
        val token = current.share?.token ?: return
        _shareState.value = current.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.update(
                token = token,
                protected = !password.isNullOrBlank(),
                password = password?.takeIf { it.isNotBlank() },
                permission = permission,
                expirationDays = expirationDays,
                downloadLimit = downloadLimit,
                singleView = singleView,
            )
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    // O servidor devolve o link sem #k= (nunca conhece a chave): sem
                    // re-anexar, o link mostrado/copiado depois de guardar não decifrava.
                    // A chave é a MESMA de sempre (cofre do dono) — o link não muda.
                    attachShareKeyFragment()
                    _state.update { it.copy(toast = "Partilha actualizada".tr()) }
                    loadCurrent()
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: ${t.message}") }
                }
        }
    }

    fun revokeCurrentShare() {
        val current = _shareState.value ?: return
        val token = current.share?.token ?: return
        _shareState.value = current.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.revoke(token)
                .onSuccess {
                    _shareState.value = null
                    _state.update { it.copy(toast = "Partilha revogada".tr()) }
                    loadCurrent()
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: ${t.message}") }
                }
        }
    }

    fun fetchQrCode() {
        val current = _shareState.value ?: return
        val token = current.share?.token ?: return
        _shareState.value = current.copy(loadingQr = true)
        viewModelScope.launch {
            shareRepository.qrCode(token)
                .onSuccess { svg -> _shareState.update { it?.copy(qrSvg = svg, loadingQr = false) } }
                .onFailure { t ->
                    _shareState.update { it?.copy(loadingQr = false) }
                    _state.update { it.copy(toast = "Falha QR: ${t.message}") }
                }
        }
    }

    fun setEmailDialogVisible(visible: Boolean) {
        _shareState.update { it?.copy(emailDialogVisible = visible) }
    }

    fun sendShareEmail(emails: List<String>) {
        val current = _shareState.value ?: return
        val token = current.share?.token ?: return
        if (emails.isEmpty()) return
        _shareState.value = current.copy(sendingEmail = true, emailDialogVisible = false)
        viewModelScope.launch {
            shareRepository.sendByEmail(token, emails)
                .onSuccess { _state.update { it.copy(toast = "Email enviado".tr()) } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
            _shareState.update { it?.copy(sendingEmail = false) }
        }
    }

    fun restore(item: BrowseItem) {
        _state.update { it.copy(processing = "A restaurar…".tr()) }
        viewModelScope.launch {
            trashRepository.restore(listOf(item))
                .onSuccess { _state.update { it.copy(processing = null, toast = "Restaurado") }; loadCurrent() }
                .onFailure { t ->
                    android.util.Log.e("BrowseVM", "restore failed", t)
                    _state.update { it.copy(processing = null, toast = "Falha: ${t.message ?: t::class.java.simpleName}") }
                }
        }
    }

    // Esvazia o lixo em lotes: a cada lote actualiza o progresso e recarrega a
    // lista em silêncio (sem spinner) para os itens sumirem por trás do blur.
    fun emptyTrash() {
        if (_state.value.isEmptyingTrash) return
        _state.update { it.copy(isEmptyingTrash = true, emptyingProgress = 0) }
        viewModelScope.launch {
            val batch = 50
            var total: Int? = null
            var remaining = Int.MAX_VALUE
            try {
                while (remaining > 0) {
                    val res = trashRepository.dumpBatch(batch).getOrElse { t ->
                        android.util.Log.e("BrowseVM", "emptyTrash failed", t)
                        _state.update { it.copy(toast = "Falha: ${t.message ?: t::class.java.simpleName}") }
                        null
                    } ?: break
                    remaining = res.remaining
                    if (total == null) total = res.deleted + res.remaining
                    val t = total ?: 0
                    val pct = if (t > 0) (t - remaining) * 100 / t else 100
                    _state.update { it.copy(emptyingProgress = pct.coerceIn(0, 99)) }
                    if (_state.value.mode == BrowseMode.Trash) reloadTrashItems()
                    if (res.deleted == 0) break
                }
                _state.update { it.copy(emptyingProgress = 100) }
                if (_state.value.mode == BrowseMode.Trash) reloadTrashItems()
                _state.update { it.copy(toast = "Lixo esvaziado".tr()) }
            } finally {
                delay(400)
                _state.update { it.copy(isEmptyingTrash = false, emptyingProgress = 0) }
            }
        }
    }

    // Recarrega a lista do lixo sem tocar no isLoading (sem skeleton): os itens
    // já apagados desaparecem por trás do blur, mantendo a vista visível.
    private suspend fun reloadTrashItems() {
        trashRepository.list().onSuccess { items ->
            if (_state.value.mode == BrowseMode.Trash) {
                _state.update { it.copy(items = sorted(items)) }
            }
        }
    }

    fun toggleFavourite(folder: BrowseItem.Folder) {
        val isFavourite = isFavourite(folder)
        viewModelScope.launch {
            val result = if (isFavourite) favouritesRepository.remove(folder.id)
            else favouritesRepository.add(folder.id)
            result
                .onSuccess {
                    sessionManager.refreshUser()
                    _state.update { it.copy(toast = if (isFavourite) "Removido dos favoritos".tr() else "Adicionado aos favoritos".tr()) }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun isFavourite(folder: BrowseItem.Folder): Boolean =
        favouriteFolders.value.any { it.id == folder.id }

    private fun loadCurrent(silent: Boolean = false) {
        loadJob?.cancel()
        val mode = _state.value.mode
        // Recarga normal: limpa baseline e mostra spinner. Recarga silenciosa
        // (despoletada pelo polling): mantém os itens e o scroll no ecrã e
        // repõe exactamente as páginas que o utilizador já tinha aberto.
        val pagesToReload = if (silent) _state.value.currentPage.coerceAtLeast(1) else 1
        if (!silent) {
            lastFingerprint = null
            _state.update { it.copy(items = emptyList(), isLoading = true, isLoadingMore = false, currentPage = 1, lastPage = 1, error = null) }
        }
        loadJob = viewModelScope.launch {
            when (mode) {
                BrowseMode.Favourites -> handleSimple(Result.success(favouriteFolders.value.toList<BrowseItem>()), mode)
                BrowseMode.Trash -> handleSimple(trashRepository.list(), mode)
                is BrowseMode.SearchResults -> handleSimple(repository.search(mode.query), mode)
                else -> loadPaged(mode, pagesToReload, silent)
            }
        }
    }

    // Carrega uma única página da fonte correspondente ao modo actual.
    // Devolve null para modos não paginados (Favoritos/Lixo/Pesquisa).
    private suspend fun fetchPage(mode: BrowseMode, page: Int): Result<PagedItems>? = when (mode) {
        is BrowseMode.Folder -> repository.listFolder(mode.id, page = page)
        BrowseMode.Latest -> repository.listLatest(page = page)
        BrowseMode.Shared -> repository.listShared(page = page)
        is BrowseMode.TeamFolder -> teamsRepository.listTeamFolder(mode.id, page = page)
        is BrowseMode.SharedWithMe -> teamsRepository.listSharedWithMe(mode.id, page = page)
        else -> null
    }

    // Carregamento paginado preguiçoso: a abertura normal pede só a página 1; as
    // seguintes chegam à medida que o utilizador faz scroll (loadMore). Antes
    // carregava TODAS as páginas de imediato em cascata — dezenas de pedidos em
    // série ao abrir uma pasta grande, a causa principal do "lento a carregar".
    // Numa recarga silenciosa pedimos as primeiras `pages` páginas para repor o
    // que já estava visível, sem spinner e sem mexer no scroll.
    private suspend fun loadPaged(mode: BrowseMode, pages: Int, silent: Boolean) {
        val first = fetchPage(mode, 1) ?: return
        if (mode != _state.value.mode) return
        first
            .onSuccess { firstPaged ->
                val all = firstPaged.items.toMutableList()
                var current = firstPaged.currentPage
                val last = firstPaged.lastPage
                var page = 2
                while (page <= pages && page <= last) {
                    if (mode != _state.value.mode) return
                    val next = fetchPage(mode, page)?.getOrNull() ?: break
                    all += next.items
                    current = next.currentPage
                    page++
                }
                if (mode != _state.value.mode) return
                _state.update {
                    it.copy(
                        items = sorted(all),
                        isLoading = false,
                        isRefreshing = false,
                        currentPage = current,
                        lastPage = last,
                    )
                }
            }
            .onFailure { t ->
                // Numa recarga silenciosa não apagamos a vista por um erro de rede.
                if (silent) {
                    _state.update { it.copy(isRefreshing = false) }
                } else {
                    _state.update { it.copy(items = emptyList(), isLoading = false, isRefreshing = false, error = t.message) }
                }
            }
    }

    // --- Sincronização em tempo real via polling ----------------------------

    /** Chamar quando o ecrã fica visível (ON_RESUME). */
    fun startRealtimeSync() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                pollFolderOnce()
            }
        }
    }

    /** Chamar quando o ecrã deixa de estar visível (ON_PAUSE). */
    fun stopRealtimeSync() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollFolderOnce() {
        val s = _state.value
        val mode = s.mode
        // Só pastas pessoais têm endpoint de fingerprint; evitar interferir
        // durante carregamentos ou enquanto o utilizador selecciona itens.
        if (mode !is BrowseMode.Folder) return
        if (s.isLoading || s.isLoadingMore || s.isRefreshing) return
        if (s.selectedIds.isNotEmpty()) return

        val signature = repository.folderFingerprint(mode.id).getOrNull() ?: return
        if (mode != _state.value.mode) return // navegou entretanto

        val previous = lastFingerprint
        lastFingerprint = signature
        if (previous != null && previous != signature) {
            loadCurrent(silent = true)
        }
    }

    private fun handleSimple(result: Result<List<BrowseItem>>, mode: BrowseMode) {
        if (mode != _state.value.mode) return
        result
            .onSuccess { items ->
                _state.update {
                    it.copy(items = sorted(items), isLoading = false, isRefreshing = false, currentPage = 1, lastPage = 1)
                }
            }
            .onFailure { t ->
                _state.update { it.copy(items = emptyList(), isLoading = false, isRefreshing = false, error = t.message) }
            }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore) return
        if (s.currentPage >= s.lastPage) return
        val mode = s.mode
        val nextPage = s.currentPage + 1
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val result = fetchPage(mode, nextPage) ?: run {
                _state.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            if (mode != _state.value.mode) return@launch
            result
                .onSuccess { paged ->
                    _state.update {
                        it.copy(
                            items = sorted(it.items + paged.items),
                            isLoadingMore = false,
                            currentPage = paged.currentPage,
                            lastPage = paged.lastPage,
                        )
                    }
                    // Sem cascata: a página seguinte só carrega quando o scroll
                    // voltar a aproximar-se do fim da lista.
                }
                .onFailure { t ->
                    _state.update { it.copy(isLoadingMore = false, error = t.message) }
                }
        }
    }

    fun setViewMode(mode: ViewMode) {
        viewPreferences.setViewMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BROWSE, mode)
        _state.update { it.copy(viewMode = mode) }
    }

    fun toggleViewMode() = _state.update {
        val next = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        viewPreferences.setViewMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BROWSE, next)
        it.copy(viewMode = next)
    }

    fun toggleSelection(id: String) = _state.update {
        val next = it.selectedIds.toMutableSet().also { s ->
            if (!s.add(id)) s.remove(id)
        }
        it.copy(selectedIds = next)
    }

    fun startSelection() = _state.update { it.copy(selectedIds = it.selectedIds.ifEmpty { emptySet() }) }
    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet()) }
    fun enterSelectMode() = _state.update { it.copy(selectMode = true) }
    fun exitSelectMode() = _state.update { it.copy(selectMode = false, selectedIds = emptySet()) }
    fun selectAll() = _state.update { it.copy(selectedIds = it.items.map { item -> item.id }.toSet()) }
    fun toggleFilesOnly() = _state.update { it.copy(filesOnly = !it.filesOnly) }
    fun selectedItems(): List<BrowseItem> = _state.value.items.filter { _state.value.selectedIds.contains(it.id) }

    fun deleteSelected(permanent: Boolean = false) {
        val targets = selectedItems()
        exitSelectMode()
        targets.forEach { delete(it, permanent = permanent) }
    }

    fun setSortMode(mode: SortMode) {
        viewPreferences.setSortMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BROWSE, mode)
        _state.update { it.copy(sortMode = mode, items = sortedWith(it.items, mode)) }
    }

    private fun sorted(items: List<BrowseItem>): List<BrowseItem> =
        sortedWith(items, _state.value.sortMode)

    private fun sortedWith(items: List<BrowseItem>, mode: SortMode): List<BrowseItem> {
        val foldersFirst = compareBy<BrowseItem> { it !is BrowseItem.Folder }
        // CASE_INSENSITIVE_ORDER compara sem criar uma cópia em minúsculas por
        // elemento a cada comparação (o sort é refeito a cada página).
        val secondary: Comparator<BrowseItem> = when (mode) {
            SortMode.ALPHA_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.ALPHA_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.DATE_DESC -> compareByDescending { it.createdAt ?: it.updatedAt ?: "" }
            SortMode.DATE_ASC -> compareBy { it.createdAt ?: it.updatedAt ?: "" }
        }
        return items.sortedWith(foldersFirst.then(secondary))
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
