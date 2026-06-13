package co.golink.tester.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.backup.AutoBackupPreferences
import co.golink.tester.data.browse.BrowseRepository
import co.golink.tester.data.download.FileDownloader
import co.golink.tester.data.files.FilesRepository
import co.golink.tester.data.share.ShareRepository
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.NavigationSection
import co.golink.tester.ui.screens.browse.ShareDialogUiState
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MobileBackupTab(val apiKey: String) {
    Photos("photos"),
    Videos("videos"),
    Files("files"),
}

data class MobileBackupsState(
    val tab: MobileBackupTab = MobileBackupTab.Photos,
    // Pode conter pastas (Camera, Screenshots, …) e ficheiros — o backend
    // organiza o backup em subpastas por origem e devolve ambos na listagem.
    val items: List<BrowseItem> = emptyList(),
    // Pilha de navegação dentro do separador (drill-down nas subpastas).
    val folderStack: List<BrowseItem.Folder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val navigationTree: List<NavigationSection> = emptyList(),
    // Totais por separador, mostrados por baixo de Fotos/Vídeos/Ficheiros.
    val counts: Map<MobileBackupTab, Int> = emptyMap(),
    val selectMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
)

@HiltViewModel
class MobileBackupsViewModel @Inject constructor(
    private val repository: BrowseRepository,
    private val filesRepository: FilesRepository,
    private val shareRepository: ShareRepository,
    private val downloader: FileDownloader,
    private val backupPreferences: AutoBackupPreferences,
    private val fileViewerSession: FileViewerSession,
    notificationsRepository: co.golink.tester.data.notifications.NotificationsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MobileBackupsState())
    val state: StateFlow<MobileBackupsState> = _state.asStateFlow()

    val unreadNotifications: StateFlow<Int> = notificationsRepository.unreadCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _shareState = MutableStateFlow<ShareDialogUiState?>(null)
    val shareState: StateFlow<ShareDialogUiState?> = _shareState.asStateFlow()

    val backupEnabled: StateFlow<Boolean> = backupPreferences.state
        .map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, backupPreferences.enabled)

    init {
        selectTab(MobileBackupTab.Photos)
        loadCounts()
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
        viewModelScope.launch {
            filesRepository.move(targets, destinationId)
                .onSuccess {
                    _state.update { it.copy(toast = "Movido") }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    fun deleteSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        exitSelectMode()
        viewModelScope.launch {
            filesRepository.delete(targets, permanent = false)
                .onSuccess {
                    _state.update { it.copy(toast = "Movido para o lixo") }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    fun refresh() {
        load()
        loadCounts()
    }

    // Uma chamada leve por separador (per_page=1) só para ler meta.paginate.total.
    private fun loadCounts() {
        MobileBackupTab.entries.forEach { tab ->
            viewModelScope.launch {
                repository.listMobileBackup(tab.apiKey, page = 1, perPage = 1)
                    .onSuccess { paged ->
                        val total = paged.total ?: return@onSuccess
                        _state.update { it.copy(counts = it.counts + (tab to total)) }
                    }
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

    fun rename(item: BrowseItem.File, newName: String) {
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

    fun delete(item: BrowseItem.File) {
        viewModelScope.launch {
            filesRepository.delete(listOf(item), permanent = false)
                .onSuccess {
                    _state.update { it.copy(toast = "Movido para o lixo") }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    fun loadNavigationTree() {
        viewModelScope.launch {
            repository.navigation()
                .onSuccess { sections -> _state.update { it.copy(navigationTree = sections) } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    fun move(item: BrowseItem.File, destinationId: String?) {
        viewModelScope.launch {
            filesRepository.move(listOf(item), destinationId)
                .onSuccess {
                    _state.update { it.copy(toast = "Movido") }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message ?: "erro"}") } }
        }
    }

    // ---- Partilha (espelha o fluxo do BrowseViewModel) ----

    fun openShareDialog(item: BrowseItem) {
        _shareState.value = ShareDialogUiState(item = item, share = item.share)
    }

    fun closeShareDialog() {
        _shareState.value = null
    }

    fun createShare(password: String?, permission: String?, expirationDays: Int?) {
        val current = _shareState.value ?: return
        _shareState.value = current.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.create(current.item, password, permission, expirationDays, null)
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    _state.update { it.copy(toast = "Partilha criada") }
                    load()
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = "Falha: ${t.message}") }
                }
        }
    }

    fun updateCurrentShare(password: String?, permission: String?, expirationDays: Int?) {
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
            )
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    _state.update { it.copy(toast = "Partilha actualizada") }
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
                    _state.update { it.copy(toast = "Partilha revogada") }
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
                .onSuccess { _state.update { it.copy(toast = "Email enviado") } }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
            _shareState.update { it?.copy(sendingEmail = false) }
        }
    }

    private fun load() {
        val current = _state.value.tab
        val folder = _state.value.folderStack.lastOrNull()
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = if (folder == null) repository.listAllMobileBackup(current.apiKey)
            else repository.listAllFolder(folder.id)
            result
                .onSuccess { items ->
                    // distinctBy: ids repetidos vindos do servidor rebentavam o
                    // LazyColumn ("Key was already used") e a app ia abaixo.
                    // Pastas primeiro, como no browser.
                    val deduped = items.distinctBy { it.id }
                        .sortedBy { it is BrowseItem.File }
                    _state.update {
                        it.copy(
                            items = deduped,
                            isLoading = false,
                            counts = if (folder == null) it.counts + (current to deduped.size) else it.counts,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(isLoading = false, error = t.message) }
                }
        }
    }
}
