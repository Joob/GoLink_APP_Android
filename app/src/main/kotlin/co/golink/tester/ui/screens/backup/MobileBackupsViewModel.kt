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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.ShareInfo
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
    val zoomLevel: Int = 1,
    val sortMode: SortMode = SortMode.DATE_DESC,
    // Folha "Ordenar e vista" (igual ao browser).
    val foldersFirst: Boolean = true,
    val groupByMonth: Boolean = true,
    // Cartões dos telemóveis (o deste aparelho vem marcado `current`).
    val summary: co.golink.tester.domain.backup.BackupSummary? = null,
    val pausingDevice: Boolean = false,
    // Telemóvel cujos backups se mostram (device_id no browse); null = todos
    // (só se o resumo falhar). Abre no próprio aparelho, como a web.
    val selectedDeviceId: String? = null,
    // Scroll infinito: há mais páginas de ficheiros no nível / a pedir a seguinte.
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
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
    private val moveManager: co.golink.tester.data.files.MoveManager,
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
                zoomLevel = viewPreferences.zoomLevel(
                    co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP,
                    if (viewPreferences.viewMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, ViewMode.LIST) == ViewMode.GRID) 2 else 1,
                ),
                viewMode = viewPreferences.viewMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, ViewMode.LIST),
                sortMode = viewPreferences.sortMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, SortMode.DATE_DESC),
                foldersFirst = viewPreferences.foldersFirst(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP),
                groupByMonth = viewPreferences.groupByMonth(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP),
            )
        }
        // A lista e as contagens só arrancam depois de o resumo escolher o
        // telemóvel — senão apareciam as pastas de todos misturadas.
        loadSummary()
        observeUploadsForCounts()
        viewModelScope.launch { moveManager.completedTick.collect { if (it > 0) refresh() } }
    }

    // Uploads manuais concluídos recarregam logo (no máximo um a cada 3 s).
    // Os do backup automático já mudam a assinatura do syncLoop: recarregar
    // também aqui duplicava os ~6 pedidos do refresh a cada ficheiro enviado.
    private fun observeUploadsForCounts() {
        viewModelScope.launch {
            uploadManager.completedTick
                .filter { it > 0 }
                .conflate()
                .collect {
                    refresh()
                    delay(3_000)
                }
        }
    }

    // false até o primeiro resumo (ou a sua falha) definir o telemóvel.
    private var deviceResolved = false

    /** [refreshCounts]: atualizar também os badges (do resumo, ou count_only em servidores antigos). */
    fun loadSummary(refreshCounts: Boolean = false) {
        viewModelScope.launch {
            repository.mobileBackupSummary()
                .onSuccess { summary ->
                    // Lido só agora: um selectDevice() feito durante o pedido manda.
                    val previous = _state.value.selectedDeviceId
                    val ids = summary.devices.map { it.id } + listOfNotNull(summary.legacy?.id)
                    val chosen = previous?.takeIf { it in ids }
                        ?: summary.devices.firstOrNull { it.current }?.id
                        ?: ids.firstOrNull()
                    _state.update { it.copy(summary = summary, selectedDeviceId = chosen) }
                    if (!deviceResolved || chosen != previous) showDevice()
                    else if (refreshCounts) loadCounts()
                }
                .onFailure {
                    if (!deviceResolved) showDevice()
                    // O resumo guardado está velho: os badges vêm do count_only.
                    else if (refreshCounts) loadCounts(fromSummary = false)
                }
        }
    }

    /** Ficheiros por separador do telemóvel escolhido, vindos do resumo (null: servidor antigo). */
    private fun summaryCounts(): Map<MobileBackupTab, Int>? {
        val s = _state.value
        val summary = s.summary ?: return null
        val device = (summary.devices + listOfNotNull(summary.legacy)).firstOrNull { it.id == s.selectedDeviceId } ?: return null
        val counts = device.counts ?: return null
        return MobileBackupTab.entries.associateWith { counts[it.apiKey] ?: 0 }
    }

    /** Escolher outro telemóvel nos cartões: volta à raiz e recarrega. */
    fun selectDevice(id: String) {
        if (id == _state.value.selectedDeviceId) return
        _state.update { it.copy(selectedDeviceId = id, counts = emptyMap()) }
        showDevice()
    }

    private fun showDevice() {
        deviceResolved = true
        _state.update { it.copy(items = emptyList(), error = null, folderStack = emptyList(), selectMode = false, selectedIds = emptySet()) }
        load()
        loadCounts()
    }

    /** Pausar / retomar só este telemóvel (o mesmo botão do cartão na web). */
    fun setDevicePaused(deviceId: String, paused: Boolean) {
        if (_state.value.pausingDevice) return
        _state.update { it.copy(pausingDevice = true) }
        viewModelScope.launch {
            repository.pauseBackupDevice(deviceId, paused)
                .onSuccess {
                    _state.update { it.copy(toast = (if (paused) "Backup deste telemóvel em pausa" else "Backup retomado").tr()) }
                    loadSummary()
                }
                .onFailure { _state.update { it.copy(toast = "Não foi possível alterar o backup.".tr()) } }
            _state.update { it.copy(pausingDevice = false) }
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
    fun toggleViewMode() = setViewMode(if (_state.value.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
    fun setViewMode(mode: ViewMode) {
        viewPreferences.setViewMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, mode)
        setZoomLevel(if (mode == ViewMode.GRID) 2 else 1)
    }
    /** Pinça: nível de zoom (0 lista grande … 4 grelha de 5). */
    fun setZoomLevel(level: Int) {
        val l = level.coerceIn(0, 4)
        viewPreferences.setZoomLevel(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, l)
        _state.update { it.copy(zoomLevel = l, viewMode = co.golink.tester.ui.screens.browse.viewModeFor(l)) }
    }
    fun setSortMode(mode: SortMode) {
        viewPreferences.setSortMode(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, mode)
        val partial = _state.value.hasMore
        _state.update { it.copy(sortMode = mode) }
        // Só com páginas por carregar: a ordem do servidor muda as páginas seguintes.
        if (partial && mode != lastServerSort) load()
    }

    fun setFoldersFirst(value: Boolean) {
        viewPreferences.setFoldersFirst(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, value)
        _state.update { it.copy(foldersFirst = value) }
    }
    fun setGroupByMonth(value: Boolean) {
        viewPreferences.setGroupByMonth(co.golink.tester.data.settings.ViewPreferences.SCOPE_BACKUP, value)
        _state.update { it.copy(groupByMonth = value) }
    }
    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet()) }

    fun enterSelectMode() = _state.update { it.copy(selectMode = true) }
    fun exitSelectMode() = _state.update { it.copy(selectMode = false, selectedIds = emptySet()) }
    fun selectAll() = _state.update { it.copy(selectedIds = it.items.map { item -> item.id }.toSet()) }
    // Quadrado do título de uma secção: atua só sobre os ids dela (ver BrowseViewModel).
    fun toggleSectionSelection(ids: Collection<String>) = _state.update { st ->
        if (ids.isEmpty()) return@update st
        val next = if (ids.none { it in st.selectedIds }) st.selectedIds + ids else st.selectedIds - ids.toSet()
        if (next.isEmpty()) st.copy(selectMode = false, selectedIds = emptySet())
        else st.copy(selectMode = true, selectedIds = next)
    }
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
        startMove(targets, destinationId)
    }

    val moveJobs = moveManager.jobs

    fun retryMove(id: String) = moveManager.retry(id)

    fun dismissMove(id: String) = moveManager.dismiss(id)

    /** Painel "A mover…" (um pedido por lote); a lista recarrega no completedTick. */
    private fun startMove(items: List<BrowseItem>, destinationId: String?) {
        val name = co.golink.tester.data.files.MoveManager.folderName(_state.value.navigationTree, destinationId)
            ?: (if (destinationId == null) "O meu Drive".tr() else "pasta".tr())
        moveManager.move(items, destinationId, name)
    }

    fun deleteSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        exitSelectMode()
        deleteOptimistic(targets)
    }

    // Ids a sair: escondidos logo da lista (e de recargas a meio) até o servidor responder.
    private val pendingDeletes = mutableSetOf<String>()

    /** Apagar sai logo do ecrã; se falhar, volta com o recarregamento. */
    private fun deleteOptimistic(targets: List<BrowseItem>) {
        val ids = targets.map { it.id }.toSet()
        pendingDeletes += ids
        _state.update { it.copy(items = it.items.filterNot { i -> i.id in ids }) }
        viewModelScope.launch {
            val result = filesRepository.delete(targets, permanent = false)
            pendingDeletes -= ids
            result
                .onSuccess { _state.update { it.copy(toast = "Movido para o lixo".tr()) } }
                .onFailure { t -> _state.update { it.copy(toast = FAILED_TOAST.tr()) } }
            refresh()
        }
    }

    fun refresh() {
        // Called on every ON_RESUME. If a list load is already running, don't
        // start another — just let it finish (avoids the resume->reload loop).
        // A correr: marca para recarregar no fim (senão perdia-se o upload/apagar
        // que aconteceu a meio do pedido).
        if (loadJob?.isActive == true) reloadPending = true else reload()
        // Os badges vêm no resumo (1 pedido em vez de 1 + 4 count_only).
        loadSummary(refreshCounts = true)
    }

    private fun loadCounts(fromSummary: Boolean = true) {
        if (!deviceResolved) return
        if (fromSummary) summaryCounts()?.let { counts ->
            _state.update { it.copy(counts = counts) }
            return
        }
        val deviceId = _state.value.selectedDeviceId
        // Servidor sem `counts` no resumo: um total por separador (count_only).
        MobileBackupTab.entries.forEach { tab ->
            viewModelScope.launch {
                repository.countMobileBackup(tab.apiKey, deviceId)
                    .onSuccess { total -> _state.update { it.copy(counts = it.counts + (tab to total)) } }
            }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    // O viewer lê os ficheiros do FileViewerSession (preenchido antes de
    // navegar). Sem isto, abrir um ficheiro daqui mostrava a sessão antiga do
    // browser — ou nada.
    fun prepareViewer(start: BrowseItem.File) {
        // Pela ordem do ecrã: _state.items vem como o servidor o devolveu.
        fileViewerSession.files = sortBackupItems(_state.value.items, _state.value.sortMode)
            .filterIsInstance<BrowseItem.File>()
            .filter { it.isViewable() }
        fileViewerSession.startId = start.id
        fileViewerSession.fromTrash = false
        fileViewerSession.readOnlyIds = emptySet()
        fileViewerSession.notOwnedIds = emptySet()
    }

    fun download(item: BrowseItem.File) {
        downloader.downloadFile(item)
            .onSuccess { _state.update { it.copy(toast = "Download iniciado: %s".tr().format(item.name)) } }
            .onFailure { t -> _state.update { it.copy(toast = FAILED_TOAST.tr()) } }
    }

    // Download genérico (ficheiro ou pasta) — usado pelo menu "…" das pastas.
    fun downloadItem(item: BrowseItem) {
        when (item) {
            is BrowseItem.File -> download(item)
            is BrowseItem.Folder -> downloader.downloadFolder(item)
                .onSuccess { _state.update { it.copy(toast = "A preparar zip: %s".tr().format(item.name)) } }
                .onFailure { t -> _state.update { it.copy(toast = FAILED_TOAST.tr()) } }
        }
    }

    fun rename(item: BrowseItem, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == item.name) return
        viewModelScope.launch {
            filesRepository.rename(item, trimmed)
                .onSuccess {
                    _state.update { it.copy(toast = "Renomeado".tr()) }
                    reload()
                }
                .onFailure { t -> _state.update { it.copy(toast = FAILED_TOAST.tr()) } }
        }
    }

    fun delete(item: BrowseItem) = deleteOptimistic(listOf(item))

    fun convertToTeamFolder(folderId: String, invitations: List<TeamInvitation>, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            teamsRepository.convertToTeamFolder(folderId, invitations)
                .onSuccess {
                    onDone(null)
                    _state.update { it.copy(toast = "Pasta convertida".tr()) }
                    reload()
                    teamsRepository.clearEncryptedNames(folderId)
                }
                .onFailure { t -> onDone(FAILED_TOAST.tr()) }
        }
    }

    fun createFileRequest(name: String?, email: String?, notes: String?, folderId: String?, expiresInDays: Int? = null) {
        viewModelScope.launch {
            uploadRequestRepository.createFileRequest(name, email, notes, folderId, expiresInDays)
                .onSuccess { link ->
                    runCatching {
                        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("file-request", link))
                    }
                    _state.update { it.copy(toast = "Pedido criado — link copiado".tr()) }
                }
                .onFailure { t -> _state.update { it.copy(toast = FAILED_TOAST.tr()) } }
        }
    }

    fun loadNavigationTree() {
        viewModelScope.launch {
            repository.navigation()
                .onSuccess { sections -> _state.update { it.copy(navigationTree = sections) } }
                .onFailure { t -> _state.update { it.copy(toast = FAILED_TOAST.tr()) } }
        }
    }

    fun move(item: BrowseItem, destinationId: String?) = startMove(listOf(item), destinationId)

    // ---- Partilha (espelha o fluxo do BrowseViewModel) ----

    fun openShareDialog(item: BrowseItem) {
        _shareState.value = ShareDialogUiState(item = item, share = item.share)
    }

    /** Para os Detalhes atualizarem visualizações/downloads com a folha aberta. */
    suspend fun fetchShare(token: String): Result<ShareInfo?> = shareRepository.get(token)

    fun closeShareDialog() {
        _shareState.value = null
    }

    fun createShare(password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?) {
        val current = _shareState.value ?: return
        _shareState.value = current.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.create(current.item, password, permission, expirationMinutes, downloadLimit, viewLimit, null)
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    _state.update { it.copy(toast = "Partilha criada".tr()) }
                    reload()
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = FAILED_TOAST.tr()) }
                }
        }
    }

    fun updateCurrentShare(password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?, protected: Boolean) {
        val current = _shareState.value ?: return
        val token = current.share?.token ?: return
        _shareState.value = current.copy(isWorking = true)
        viewModelScope.launch {
            shareRepository.update(
                token = token,
                protected = protected,
                password = password?.takeIf { it.isNotBlank() },
                permission = permission,
                expirationMinutes = expirationMinutes,
                downloadLimit = downloadLimit,
                viewLimit = viewLimit,
                passwordTipAllowed = current.share?.passwordTipAllowed ?: false,
                passwordTip = current.share?.passwordTip,
            )
                .onSuccess { info ->
                    _shareState.update { it?.copy(share = info, isWorking = false) }
                    _state.update { it.copy(toast = "Partilha actualizada".tr()) }
                    reload()
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = FAILED_TOAST.tr()) }
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
                    reload()
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = FAILED_TOAST.tr()) }
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
                    _state.update { it.copy(toast = FAILED_TOAST.tr()) }
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
                .onFailure { t -> _state.update { it.copy(toast = FAILED_TOAST.tr()) } }
            _shareState.update { it?.copy(sendingEmail = false) }
        }
    }

    private var reloadPending = false

    // Páginas já carregadas do nível atual e a última que o servidor tem.
    private var loadedPages = 0
    private var lastPage = 1
    private var lastServerSort: SortMode? = null

    // A mesma ordenação no servidor que no ecrã (como o BrowseViewModel).
    private fun serverSort(mode: SortMode): Pair<String, String> = when (mode) {
        SortMode.ALPHA_ASC -> "name" to "ASC"
        SortMode.ALPHA_DESC -> "name" to "DESC"
        SortMode.DATE_DESC -> "created_at" to "DESC"
        SortMode.DATE_ASC -> "created_at" to "ASC"
        SortMode.SIZE_DESC -> "filesize" to "DESC"
        SortMode.SIZE_ASC -> "filesize" to "ASC"
        SortMode.TYPE_ASC -> "type" to "ASC"
        SortMode.TYPE_DESC -> "type" to "DESC"
    }

    /** Recarrega as páginas que já estavam à vista (fingerprint, renomear, partilhar…). */
    private fun reload() = load(pages = loadedPages)

    /**
     * Pastas + 1.ª página de ficheiros do nível (raiz ou pasta), já filtrados por
     * tipo e telemóvel no servidor; [pages] > 1 volta a pedir as que já estavam
     * carregadas (no máximo [MAX_RELOAD_PAGES]) para a lista não encolher.
     */
    private fun load(pages: Int = 1) {
        reloadPending = false
        if (!deviceResolved) {
            _state.update { it.copy(isRefreshing = false) }
            return
        }
        val current = _state.value.tab
        val deviceId = _state.value.selectedDeviceId
        val folder = _state.value.folderStack.lastOrNull()
        val sortMode = _state.value.sortMode
        val (sort, direction) = serverSort(sortMode)
        // Cancel any previous in-flight load (e.g. when switching tabs) so we
        // never have two list requests racing.
        loadJob?.cancel()
        // Only show the full-screen spinner on the first load (nothing to show
        // yet). On a refresh with data already on screen, reload silently and
        // keep the list visible — no flicker back to a spinner.
        _state.update { it.copy(isLoading = it.items.isEmpty(), error = null, loadingMore = false) }
        loadJob = viewModelScope.launch {
            val want = pages.coerceIn(1, MAX_RELOAD_PAGES)
            val acc = ArrayList<BrowseItem>()
            var last = 1
            var got = 0
            for (page in 1..want) {
                val paged = repository.listMobileBackupTreePage(current.apiKey, folder?.id, deviceId, page, sort, direction)
                    .getOrElse { t ->
                        // Cancelamento (troca de aba, navegar para o viewer e voltar,
                        // novo load a substituir este) não é um erro — não o mostres.
                        // O runCatching do repositório embrulha a CancellationException
                        // num Result.failure, daí este guard aqui.
                        if (t is kotlinx.coroutines.CancellationException) return@launch
                        _state.update { it.copy(isLoading = false, isRefreshing = false, error = t.message) }
                        return@launch
                    }
                acc += paged.items
                last = paged.lastPage
                got = page
                if (page >= last) break
            }
            // distinctBy: ids repetidos (uma página que escorregou com um upload
            // novo) rebentavam o LazyColumn ("Key was already used").
            val deduped = withContext(Dispatchers.Default) { acc.distinctBy { it.id } }
            loadedPages = got
            lastPage = last
            lastServerSort = sortMode
            _state.update {
                it.copy(
                    items = deduped.filterNot { i -> i.id in pendingDeletes },
                    isLoading = false,
                    isRefreshing = false,
                    hasMore = got < last,
                )
            }
            if (reloadPending) reload()
        }
    }

    /** Fim da lista à vista: pede a página seguinte de ficheiros do nível. */
    fun loadMore() {
        if (loadJob?.isActive == true || loadedPages == 0 || loadedPages >= lastPage) return
        val current = _state.value.tab
        val deviceId = _state.value.selectedDeviceId
        val folder = _state.value.folderStack.lastOrNull()
        val (sort, direction) = serverSort(lastServerSort ?: _state.value.sortMode)
        val next = loadedPages + 1
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            repository.listMobileBackupTreePage(current.apiKey, folder?.id, deviceId, next, sort, direction)
                .onSuccess { paged ->
                    val merged = withContext(Dispatchers.Default) {
                        (_state.value.items + paged.items.filterNot { i -> i.id in pendingDeletes }).distinctBy { it.id }
                    }
                    loadedPages = next
                    lastPage = paged.lastPage
                    _state.update { it.copy(items = merged, loadingMore = false, hasMore = next < paged.lastPage) }
                    if (reloadPending) reload()
                }
                .onFailure { t ->
                    if (t is kotlinx.coroutines.CancellationException) return@onFailure
                    // Fica o botão/scroll para tentar outra vez.
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }

    // Assinatura do backup (todos os aparelhos). No ViewModel: ao voltar ao ecrã compara com a anterior.
    private var syncStamp: String? = null

    /**
     * Enquanto o ecrã está visível: assinatura a cada 5 s; se mudou (backup de outro
     * aparelho, apagado na web, aparelho renomeado/pausado), recarrega em silêncio.
     */
    suspend fun syncLoop() {
        while (true) {
            repository.mobileBackupFingerprint().onSuccess { v ->
                val prev = syncStamp
                syncStamp = v
                if (prev != null && prev != v) syncDirty = true
            }
            // Durante um backup a assinatura muda a cada ficheiro: junta as
            // mudanças e recarrega no máximo uma vez a cada SYNC_REFRESH_MIN_MS.
            val now = System.currentTimeMillis()
            if (syncDirty && now - lastSyncRefreshAt >= SYNC_REFRESH_MIN_MS) {
                syncDirty = false
                lastSyncRefreshAt = now
                refresh()
            }
            kotlinx.coroutines.delay(5_000)
        }
    }

    private var syncDirty = false
    private var lastSyncRefreshAt = 0L
    private companion object {
        const val SYNC_REFRESH_MIN_MS = 10_000L
        const val MAX_RELOAD_PAGES = 10

        /** Toast genérico: a mensagem da exceção (inglês/técnica) fica de fora. */
        const val FAILED_TOAST = "Não foi possível concluir. Tenta de novo."
    }

    /** Puxar para baixo em cada aba: recarrega lista + contagens dessa aba. */
    fun pullRefresh() {
        _state.update { it.copy(isRefreshing = true) }
        loadSummary(refreshCounts = true)
        load()
    }
}
