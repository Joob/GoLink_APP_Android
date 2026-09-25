package co.golink.tester.ui.screens.backup

import co.golink.tester.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.backup.AutoBackupPreferences
import co.golink.tester.data.browse.BrowseRepository
import co.golink.tester.data.download.FileDownloader
import co.golink.tester.data.files.FilesRepository
import co.golink.tester.data.share.ShareRepository
import co.golink.tester.data.teams.TeamsRepository
import co.golink.tester.data.uploadrequest.UploadRequestRepository
import co.golink.tester.domain.teams.TeamInvitation
import co.golink.tester.data.upload.UploadManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.NavigationSection
import co.golink.tester.ui.screens.browse.ShareDialogUiState
import co.golink.tester.ui.screens.browse.SortMode
import co.golink.tester.ui.screens.browse.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import co.golink.tester.ui.screens.viewer.FileViewerSession
import co.golink.tester.ui.screens.viewer.isViewable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MobileBackupTab(val apiKey: String) {
    Images("photos"),
    Videos("videos"),
    // 4 divisões reais no servidor (pastas separadas por tipo): já não é preciso
    // separar áudios/ficheiros no cliente.
    Audios("audios"),
    Files("files"),
}

data class MobileBackupsState(
    val tab: MobileBackupTab = MobileBackupTab.Images,
    // Pode conter pastas (Camera, Screenshots, …) e ficheiros — o backend
    // organiza o backup em subpastas por origem e devolve ambos na listagem.
    val items: List<BrowseItem> = emptyList(),
    // Pilha de navegação dentro do separador (drill-down nas subpastas).
    val folderStack: List<BrowseItem.Folder> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val navigationTree: List<NavigationSection> = emptyList(),
    // Totais por separador, mostrados por baixo de Fotos/Vídeos/Ficheiros.
    val counts: Map<MobileBackupTab, Int> = emptyMap(),
    val selectMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    // Mensagem do overlay de progresso (eliminar/mover). null = sem overlay.
    val processing: String? = null,
    // Vista (grelha/lista) e ordenação, iguais ao browser. Por omissão os
    // uploads aparecem do mais recente para o mais antigo (DATE_DESC).
    val viewMode: ViewMode = ViewMode.LIST,
    val sortMode: SortMode = SortMode.DATE_DESC,
)

@HiltViewModel
class MobileBackupsViewModel @Inject constructor(
    private val repository: BrowseRepository,
    private val filesRepository: FilesRepository,
    private val shareRepository: ShareRepository,
    private val downloader: FileDownloader,
    private val backupPreferences: AutoBackupPreferences,
    private val fileViewerSession: FileViewerSession,
    private val uploadManager: UploadManager,
    private val viewPreferences: co.golink.tester.data.settings.ViewPreferences,
    private val teamsRepository: TeamsRepository,
    private val uploadRequestRepository: UploadRequestRepository,
    @ApplicationContext private val appContext: Context,
    notificationsRepository: co.golink.tester.data.notifications.NotificationsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MobileBackupsState())
    val state: StateFlow<MobileBackupsState> = _state.asStateFlow()

    // The in-flight list request. Tracked so that repeated refreshes (the
    // screen calls refresh() on every ON_RESUME) don't stack/restart the heavy
    // page="all" request in a loop, which left the spinner spinning forever.
    private var loadJob: Job? = null

    val unreadNotifications: StateFlow<Int> = notificationsRepository.unreadCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _shareState = MutableStateFlow<ShareDialogUiState?>(null)
    val shareState: StateFlow<ShareDialogUiState?> = _shareState.asStateFlow()

    val backupEnabled: StateFlow<Boolean> = backupPreferences.state
        .map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, backupPreferences.enabled)

    init {
        // Restaura vista/ordenação guardadas (predefinição: lista + mais recente).
        _state.update {
            it.copy(
                viewMode = viewPreferences.viewMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, ViewMode.LIST),
                sortMode = viewPreferences.sortMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, SortMode.DATE_DESC),
            )
        }
        selectTab(MobileBackupTab.Images)
        loadCounts()
        observeUploadsForCounts()
    }

    // Contagens (e lista) quase instantâneas: assim que cada upload de backup
    // termina, o completedTick dispara e recarregamos. Amostrado a 2 s para não
    // martelar a rede durante um backup grande.
    @OptIn(FlowPreview::class)
    private fun observeUploadsForCounts() {
        viewModelScope.launch {
            uploadManager.completedTick.sample(2000).collect { t ->
                if (t > 0) {
                    loadCounts()
                    load()
                }
            }
        }
    }

    fun selectTab(tab: MobileBackupTab) {
        _state.update { it.copy(tab = tab, items = emptyList(), error = null, folderStack = emptyList(), selectMode = false, selectedIds = emptySet()) }
        load()
    }

    fun openFolder(folder: BrowseItem.Folder) {
        _state.update { it.copy(folderStack = it.folderStack + folder, items = emptyList(), error = null, selectMode = false, selectedIds = emptySet()) }
        load()
    }

    /** Sobe um nível na pilha de pastas; devolve false se já está na raiz. */
    fun navigateUp(): Boolean {
        if (_state.value.folderStack.isEmpty()) return false
        _state.update { it.copy(folderStack = it.folderStack.dropLast(1), items = emptyList(), error = null, selectMode = false, selectedIds = emptySet()) }
        load()
        return true
    }

    // ---- Selecção múltipla (espelha o BrowseViewModel) ----

    // ---- Vista / ordenação (persistidas) ----
    fun toggleViewMode() = _state.update {
        val next = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        viewPreferences.setViewMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, next)
        it.copy(viewMode = next)
    }
    fun setViewMode(mode: ViewMode) {
        viewPreferences.setViewMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, mode)
        _state.update { it.copy(viewMode = mode) }
    }
    fun setSortMode(mode: SortMode) {
        viewPreferences.setSortMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, mode)
        _state.update { it.copy(sortMode = mode) }
    }

    fun enterSelectMode() = _state.update { it.copy(selectMode = true) }
    fun exitSelectMode() = _state.update { it.copy(selectMode = false, selectedIds = emptySet()) }
    fun selectAll() = _state.update { it.copy(selectedIds = it.items.map { item -> item.id }.toSet()) }
    fun toggleSelect(id: String) = _state.update {
        it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id)
    }
    fun selectedItems(): List<BrowseItem> = _state.value.items.filter { _state.value.selectedIds.contains(it.id) }

    fun downloadSelected() {
        val files = selectedItems().filterIsInstance<BrowseItem.File>()
        exitSelectMode()
        files.forEach { download(it) }
    }

    fun moveSelected(destinationId: String?) {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        exitSelectMode()
        _state.update { it.copy(processing = "A mover…".tr()) }
        viewModelScope.launch {
            filesRepository.move(targets, destinationId)
                .onSuccess {
                    _state.update { it.copy(processing = null, toast = "Movido") }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(processing = null, toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    fun deleteSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        exitSelectMode()
        _state.update { it.copy(processing = "A eliminar…".tr()) }
        viewModelScope.launch {
            filesRepository.delete(targets, permanent = false)
                .onSuccess {
                    _state.update { it.copy(processing = null, toast = "Movido para o lixo".tr()) }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(processing = null, toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    fun refresh() {
        // Called on every ON_RESUME. If a list load is already running, don't
        // start another — just let it finish (avoids the resume->reload loop).
        if (loadJob?.isActive != true) load()
        loadCounts()
    }

    private fun loadCounts() {
        // Um total por separador (count_only) — barato, sem transferir a lista.
        MobileBackupTab.entries.forEach { tab ->
            viewModelScope.launch {
                repository.countMobileBackup(tab.apiKey)
                    .onSuccess { total -> _state.update { it.copy(counts = it.counts + (tab to total)) } }
            }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    // O viewer lê os ficheiros do FileViewerSession (preenchido antes de
    // navegar). Sem isto, abrir um ficheiro daqui mostrava a sessão antiga do
    // browser — ou nada.
    fun prepareViewer(start: BrowseItem.File) {
        fileViewerSession.files = _state.value.items
            .filterIsInstance<BrowseItem.File>()
            .filter { it.isViewable() }
        fileViewerSession.startId = start.id
    }

    fun download(item: BrowseItem.File) {
        downloader.downloadFile(item)
            .onSuccess { _state.update { it.copy(toast = "Download iniciado: ${item.name}") } }
            .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
    }

    // Download genérico (ficheiro ou pasta) — usado pelo menu "…" das pastas.
    fun downloadItem(item: BrowseItem) {
        when (item) {
            is BrowseItem.File -> download(item)
            is BrowseItem.Folder -> downloader.downloadFolder(item)
                .onSuccess { _state.update { it.copy(toast = "A preparar zip: ${item.name}") } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun rename(item: BrowseItem, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == item.name) return
        viewModelScope.launch {
            filesRepository.rename(item, trimmed)
                .onSuccess {
                    _state.update { it.copy(toast = "Renomeado") }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    fun delete(item: BrowseItem) {
        _state.update { it.copy(processing = "A eliminar…".tr()) }
        viewModelScope.launch {
            filesRepository.delete(listOf(item), permanent = false)
                .onSuccess {
                    _state.update { it.copy(processing = null, toast = "Movido para o lixo".tr()) }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(processing = null, toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    fun convertToTeamFolder(folderId: String, invitations: List<TeamInvitation>) {
        _state.update { it.copy(processing = "A converter…".tr()) }
        viewModelScope.launch {
            teamsRepository.convertToTeamFolder(folderId, invitations)
                .onSuccess { _state.update { it.copy(processing = null, toast = "Pasta convertida".tr()) }; load() }
                .onFailure { t -> _state.update { it.copy(processing = null, toast = "Falha: ${t.message}") } }
        }
    }

    fun createFileRequest(name: String?, email: String?, notes: String?, folderId: String?) {
        viewModelScope.launch {
            uploadRequestRepository.createFileRequest(name, email, notes, folderId)
                .onSuccess { link ->
                    runCatching {
                        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("file-request", link))
                    }
                    _state.update { it.copy(toast = "Pedido criado — link copiado".tr()) }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
        }
    }

    fun loadNavigationTree() {
        viewModelScope.launch {
            repository.navigation()
                .onSuccess { sections -> _state.update { it.copy(navigationTree = sections) } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    fun move(item: BrowseItem, destinationId: String?) {
        _state.update { it.copy(processing = "A mover…".tr()) }
        viewModelScope.launch {
            filesRepository.move(listOf(item), destinationId)
                .onSuccess {
                    _state.update { it.copy(processing = null, toast = "Movido") }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(processing = null, toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    // ---- Partilha (espelha o fluxo do BrowseViewModel) ----

    fun openShareDialog(item: BrowseItem) {
        _shareState.value = ShareDialogUiState(item = item, share = item.share)
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
                    _state.update { it.copy(toast = "Partilha criada".tr()) }
                    load()
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
                    _state.update { it.copy(toast = "Partilha actualizada".tr()) }
                    load()
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
                    load()
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

    private fun load() {
        val current = _state.value.tab
        val folder = _state.value.folderStack.lastOrNull()
        // Cancel any previous in-flight load (e.g. when switching tabs) so we
        // never have two list requests racing.
        loadJob?.cancel()
        // Only show the full-screen spinner on the first load (nothing to show
        // yet). On a refresh with data already on screen, reload silently and
        // keep the list visible — no flicker back to a spinner.
        _state.update { it.copy(isLoading = it.items.isEmpty(), error = null) }
        loadJob = viewModelScope.launch {
            // Devolve as pastas de backup + ficheiros do nível (raiz ou pasta),
            // já filtrados por tipo no servidor. As contagens dos separadores
            // vêm de loadCounts (globais), por isso aqui não mexemos nelas.
            repository.listMobileBackupTree(current.apiKey, folder?.id)
                .onSuccess { items ->
                    // distinctBy: ids repetidos vindos do servidor rebentavam o
                    // LazyColumn ("Key was already used") e a app ia abaixo. Corre
                    // fora do main thread para não bloquear a UI em listas grandes;
                    // a ordenação final é feita na composição consoante o sortMode.
                    val deduped = withContext(Dispatchers.Default) { items.distinctBy { it.id } }
                    _state.update { it.copy(items = deduped, isLoading = false, isRefreshing = false) }
                }
                .onFailure { t ->
                    // Cancelamento (troca de aba, navegar para o viewer e voltar,
                    // novo load a substituir este) não é um erro — não o mostres.
                    // O runCatching do repositório embrulha a CancellationException
                    // num Result.failure, daí este guard aqui.
                    if (t is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.update { it.copy(isLoading = false, isRefreshing = false, error = t.message) }
                }
        }
    }

    /** Puxar para baixo em cada aba: recarrega lista + contagens dessa aba. */
    fun pullRefresh() {
        _state.update { it.copy(isRefreshing = true) }
        loadCounts()
        load()
    }
}
