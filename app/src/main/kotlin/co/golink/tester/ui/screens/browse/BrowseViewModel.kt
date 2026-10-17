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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
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

private typealias VP = co.golink.tester.data.settings.ViewPreferences

/** Níveis 0–1 são lista; 2–4 grelha. */
fun viewModeFor(zoomLevel: Int): ViewMode = if (zoomLevel >= VP.ZOOM_GRID) ViewMode.GRID else ViewMode.LIST

/** Colunas da grelha por nível (2 → 2, 3 → 3, 4 → 5). */
fun gridColumnsFor(zoomLevel: Int): Int = when (zoomLevel) { 3 -> 3; 4 -> 5; else -> 2 }
enum class SortMode { ALPHA_ASC, ALPHA_DESC, DATE_DESC, DATE_ASC }

data class BrowseUiState(
    val mode: BrowseMode = BrowseMode.Folder(id = null, name = "Ficheiros".tr()),
    val crumbs: List<Crumb> = emptyList(),
    val items: List<BrowseItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val error: String? = null,
    val searchQuery: String = "",
    /** Filtros reconhecidos na pesquisa (parser local, sem IA), para mostrar. */
    val searchChips: List<String> = emptyList(),
    val toast: String? = null,
    val navigationTree: List<NavigationSection> = emptyList(),
    val processing: String? = null,
    val viewMode: ViewMode = ViewMode.LIST,
    /** Zoom por pinça: 0 lista grande, 1 lista, 2–4 grelha de 2/3/5 (por secção). */
    val zoomLevel: Int = 1,
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
    // "Partilhado comigo" com permissão só de leitura (can-view): sem editar,
    // mover nem apagar — o servidor recusa (403), como na web.
    val readOnly: Boolean = false,
    // Lote recusado antes de enviar (limite de envio ou falta de espaço) — popup, como na web.
    val uploadBlocked: UploadBlock? = null,
    // Chips do cabeçalho (como na web): filtro por tipo no servidor (video|image|file),
    // que pertence ao modo/pasta onde foi escolhido, e as contagens da pasta.
    val typeFilter: String? = null,
    val typeFilterMode: BrowseMode? = null,
    val stats: co.golink.tester.domain.browse.BrowseStats? = null,
    // Pasta aberta (meta.root): cor, nome, tamanho e data no cabeçalho.
    val root: BrowseItem.Folder? = null,
)

data class UploadBlock(val title: String, val message: String)

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

/**
 * Elemento do caminho. [origin]: só no primeiro, quando se entrou a partir de
 * um separador/lista (Partilhado, Favoritos, Atividade, Lixo, …) — voltar a
 * esse elemento repõe o separador em vez de abrir a raiz dos Ficheiros.
 */
data class Crumb(val id: String?, val name: String, val origin: BrowseMode? = null)

/** Atividade recente (modo Latest): uma página por secção, pela ordem do ecrã. */
/** Atividade recente (feed): separador all|my_uploads|shared_with_me|team. */
data class ActivityFeedUiState(
    val entries: List<co.golink.tester.domain.activity.FeedEntry> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val tab: String = "all",
    val summary: co.golink.tester.domain.activity.ActivitySummary? = null,
)

data class RecentActivityUiState(
    val sections: Map<co.golink.tester.domain.activity.ActivitySection, co.golink.tester.domain.activity.ActivityPage> = emptyMap(),
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
    val loadingMore: Set<co.golink.tester.domain.activity.ActivitySection> = emptySet(),
    val error: String? = null,
)

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
    private val activityRepository: co.golink.tester.data.activity.ActivityRepository,
    private val settingsRepository: co.golink.tester.data.settings.SettingsRepository,
    private val configRepository: co.golink.tester.data.config.ConfigRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private val _activity = MutableStateFlow(RecentActivityUiState())

    // Feed da Atividade recente (redesign igual à web).
    private val _feed = MutableStateFlow(ActivityFeedUiState())
    val feed: StateFlow<ActivityFeedUiState> = _feed.asStateFlow()
    private var feedJob: Job? = null
    val activity: StateFlow<RecentActivityUiState> = _activity.asStateFlow()
    private var activityJob: Job? = null

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
        // Zoom guardado por secção: ao trocar de secção (Files, Lixo…) repõe o seu nível.
        viewModelScope.launch {
            _state.map { zoomScope(it.mode) }.distinctUntilChanged().collect { scope ->
                val level = viewPreferences.zoomLevel(scope, defaultZoom())
                _state.update { it.copy(zoomLevel = level, viewMode = viewModeFor(level)) }
            }
        }
        openRoot()
        viewModelScope.launch {
            // Restaurado / apagado de vez a partir do preview do Lixo.
            trashRepository.changes.collect {
                if (_state.value.mode == BrowseMode.Trash) reloadTrashItems()
            }
        }
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
        val crumb = Crumb(id = null, name = "Ficheiros".tr())
        _state.update {
            it.copy(
                mode = BrowseMode.Folder(id = null, name = crumb.name),
                crumbs = listOf(crumb),
                searchQuery = "", searchChips = emptyList(),
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
                searchQuery = "", searchChips = emptyList(),
            )
        }
        loadCurrent()
    }

    fun openFolderById(id: String, name: String) {
        _state.update {
            it.copy(
                mode = BrowseMode.Folder(id = id, name = name),
                crumbs = listOf(Crumb(null, "Ficheiros".tr()), Crumb(id, name)),
                searchQuery = "", searchChips = emptyList(),
            )
        }
        loadCurrent()
    }

    fun goToCrumb(index: Int) {
        val crumbs = _state.value.crumbs.take(index + 1)
        val last = crumbs.lastOrNull() ?: return openRoot()
        // Primeiro elemento de um separador (ex.: Partilhado): repor o separador.
        last.origin?.let { origin ->
            _state.update { it.copy(mode = origin, crumbs = crumbs, searchQuery = "", searchChips = emptyList()) }
            loadCurrent()
            return
        }
        val newMode = when (_state.value.mode) {
            is BrowseMode.TeamFolder -> BrowseMode.TeamFolder(id = last.id, name = last.name)
            is BrowseMode.SharedWithMe -> BrowseMode.SharedWithMe(id = last.id, name = last.name)
            else -> BrowseMode.Folder(id = last.id, name = last.name)
        }
        _state.update {
            it.copy(
                mode = newMode,
                crumbs = crumbs,
                searchQuery = "", searchChips = emptyList(),
            )
        }
        loadCurrent()
    }

    fun openLatest() = switchTo(BrowseMode.Latest, "Atividade".tr())
    fun openShared() = switchTo(BrowseMode.Shared, "Partilhado".tr())
    fun openFavourites() = switchTo(BrowseMode.Favourites, "Favoritos".tr())
    fun openTrash() {
        // Dias do aviso de retenção (config do servidor; carregado uma vez).
        viewModelScope.launch { configRepository.load() }
        switchTo(BrowseMode.Trash, "Lixo".tr())
    }

    /** Dias até o servidor apagar de vez o que está no lixo (aviso no topo do Lixo). */
    val trashRetentionDays: StateFlow<Int> = configRepository.config
        .map { it?.trash?.retentionDays ?: 30 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 30)
    fun openTeamFolders() = switchTo(BrowseMode.TeamFolder(id = null, name = "Pastas de equipa".tr()), "Pastas de equipa".tr())
    fun openSharedWithMe() = switchTo(BrowseMode.SharedWithMe(id = null, name = "Partilhado comigo".tr()), "Partilhado comigo".tr())

    private fun switchTo(mode: BrowseMode, name: String) {
        _state.update {
            it.copy(
                mode = mode,
                crumbs = listOf(Crumb(null, name, origin = mode)),
                searchQuery = "", searchChips = emptyList(),
            )
        }
        loadCurrent()
    }

    fun setSearchQuery(query: String) {
        val parsed = co.golink.tester.domain.search.SearchQueryParser.parse(query)
        _state.update { it.copy(searchQuery = query, searchChips = parsed.chips) }
        searchJob?.cancel()
        if (query.isBlank()) return
        // Mínimo 2 chars + debounce 300ms: 1 carácter é um LIKE quase-full-scan
        // no servidor sem valor para o utilizador (alinha com o Spotlight da Web).
        // Com filtros reconhecidos ("pdf") a pesquisa já faz sentido.
        if (!parsed.hasFilters && query.trim().length < 2) return
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

    fun prepareViewer(files: List<BrowseItem.File>, startId: String, fromTrash: Boolean = _state.value.mode == BrowseMode.Trash) {
        fileViewerSession.files = files
        fileViewerSession.startId = startId
        fileViewerSession.fromTrash = fromTrash
    }

    fun downloadFile(item: BrowseItem.File) {
        downloader.downloadFile(item)
            .onSuccess { _state.update { it.copy(toast = "Download iniciado: ${item.name}") } }
            .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
    }

    fun downloadItem(item: BrowseItem) {
        when (item) {
            is BrowseItem.File -> downloadFile(item)
            is BrowseItem.Folder -> downloader.downloadFolder(item)
                .onSuccess { _state.update { it.copy(toast = "A preparar zip: ${item.name}") } }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
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
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    fun moveSelected(toFolderId: String?) {
        val targets = selectedItems()
        exitSelectMode()
        targets.forEach { move(it, toFolderId) }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    fun createFolder(name: String, color: String? = null) {
        val parentId = (state.value.mode as? BrowseMode.Folder)?.id
        viewModelScope.launch {
            filesRepository.createFolder(name.trim(), parentId, color = color)
                .onSuccess { _state.update { it.copy(toast = "Pasta criada".tr()) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    /** Cria um ficheiro .txt vazio na pasta atual (o nome ganha .txt se faltar). */
    fun createTextFile(name: String, openEditor: Boolean = true) {
        val parentId = (state.value.mode as? BrowseMode.Folder)?.id
        val filename = withTextExtension(name)
        // Sem toast: quem confirma é o painel de uploads (e a lista recarrega no
        // completedTick) — anunciar "criado" aqui mentia se o upload falhasse.
        uploadManager.createTextFile(filename, "", parentId)
        // "Abrir o editor depois de criar" desmarcado: só cria.
        pendingTextFileName = if (openEditor) filename else null
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
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    // E2E Fase 2: só ciframos nomes no espaço privado do utilizador — não em
    // pastas de equipa / partilhas / resultados de pesquisa.
    private fun isPrivateNameContext(): Boolean = when (state.value.mode) {
        is BrowseMode.Folder, BrowseMode.Latest, BrowseMode.Favourites, BrowseMode.Trash -> true
        else -> false
    }

    fun rename(item: BrowseItem, newName: String, appearance: co.golink.tester.domain.files.FolderAppearance? = null) {
        viewModelScope.launch {
            filesRepository.rename(item, newName.trim(), encryptName = isPrivateNameContext(), appearance = appearance)
                .onSuccess { _state.update { it.copy(toast = "Renomeado") }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
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
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    // Nomes (em claro) dos ficheiros já visíveis na pasta: com nomes cifrados é
    // o cliente que deteta o conflito, o servidor só vê o placeholder.
    private fun visibleFileNames(): Map<String, String> =
        state.value.items.filterIsInstance<BrowseItem.File>().associate { it.name to it.id }

    fun upload(uri: Uri) = uploadMany(listOf(uri))

    fun uploadMany(uris: List<Uri>) {
        val parentId = (state.value.mode as? BrowseMode.Folder)?.id
        val names = visibleFileNames()
        viewModelScope.launch {
            if (!canUpload(uris)) return@launch
            // Ler metadados (ContentResolver) de cada ficheiro fora da main thread.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                uris.forEach { uploadManager.enqueue(it, parentId, names) }
            }
        }
    }

    /**
     * Verificações antes de enviar, como na web: nenhum ficheiro acima do limite
     * de envio (admin) e espaço para o lote inteiro (só em contas com teto).
     * Sem resposta do servidor deixa seguir — ele valida no fim. Recusa o lote
     * todo e mostra o motivo.
     */
    private suspend fun canUpload(uris: List<Uri>): Boolean {
        val files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { uploadManager.sizes(uris) }

        val limit = configRepository.load().getOrNull()?.upload
        val limitBytes = limit?.limitBytes
        if (limitBytes != null) {
            val tooBig = files.firstOrNull { it.second > limitBytes }
            if (tooBig != null) {
                val label = limit.limitLabel ?: formatBytes(limitBytes)
                _state.update {
                    it.copy(
                        uploadBlocked = UploadBlock(
                            "Ficheiro demasiado grande".tr(),
                            "%s ultrapassa o limite de envio por ficheiro (%s).".tr().format(tooBig.first, label),
                        ),
                    )
                }
                return false
            }
        }

        val storage = settingsRepository.storage().getOrNull() ?: return true
        val capacity = storage.capacityBytes ?: return true
        if (!storage.hasLimit || capacity <= 0L) return true
        val remaining = (capacity - (storage.usedBytes ?: 0L)).coerceAtLeast(0L)
        val needed = files.sumOf { it.second }
        if (needed <= remaining) return true
        _state.update {
            it.copy(
                uploadBlocked = UploadBlock(
                    "Sem espaço suficiente".tr(),
                    "Precisa de %s mas só tem %s disponível.".tr().format(formatBytes(needed), formatBytes(remaining)),
                ),
            )
        }
        return false
    }

    fun dismissUploadBlocked() = _state.update { it.copy(uploadBlocked = null) }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(java.util.Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1L shl 20 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1L shl 10 -> String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
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
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    fun createTeamFolder(name: String, invitations: List<TeamInvitation>) {
        viewModelScope.launch {
            teamsRepository.createTeamFolder(name.trim(), invitations)
                .onSuccess { _state.update { it.copy(toast = "Pasta de equipa criada".tr()) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    fun convertToTeamFolder(folderId: String, invitations: List<TeamInvitation>) {
        viewModelScope.launch {
            teamsRepository.convertToTeamFolder(folderId, invitations)
                .onSuccess { _state.update { it.copy(toast = "Pasta convertida".tr()) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    // folderId nulo = usa a pasta actual (fluxo do botão +). O menu "…" de uma
    // pasta passa o id dessa pasta. Ao criar, copiamos o link para a área de
    // transferência, como faz a Web.
    fun createFileRequest(name: String?, email: String?, notes: String?, folderId: String? = null, expiresInDays: Int? = null) {
        val effectiveFolder = folderId ?: (state.value.mode as? BrowseMode.Folder)?.id
        viewModelScope.launch {
            uploadRequestRepository.createFileRequest(name, email, notes, effectiveFolder, expiresInDays)
                .onSuccess { link ->
                    copyToClipboard("file-request", link)
                    _state.update { it.copy(toast = "Pedido criado — link copiado".tr()) }
                }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
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
            // Percorrer a pasta (SAF) é lento em pastas grandes: em IO.
            val files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { collectFilesFromTree(treeUri) }
            if (files.isEmpty()) {
                _state.update { it.copy(toast = "Pasta vazia ou sem ficheiros".tr()) }
                return@launch
            }
            if (!canUpload(files)) return@launch
            // A permissão persistente é da árvore: guarda-a para retomar após fechar a app.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                files.forEach { uploadManager.enqueueWithId(it, parentId, treeUri = treeUri) }
            }
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
     * E2E: a chave tem de viajar no fragmento (#k=) do link de partilha —
     * nunca chega ao servidor. Sem isto o destinatário recebia ciphertext que não
     * conseguia abrir (igual ao CopyShareLink da Web). Ficheiro ou pasta: uma
     * chave da partilha que abre as data keys registadas no token.
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
                            val result = e2eShareService.buildFileShareFragment(
                                item.id, share.token, wrapped, item.name,
                            )
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

    /** Texto de erro para toasts; 403 = sem permissão de edição (ex.: membro só de leitura). */
    private fun failText(t: Throwable): String =
        if (t.message?.startsWith("HTTP 403") == true) "Não tens permissão de edição nesta pasta".tr()
        else "Falha: ${t.message}"

    // ---- Atividade recente ----------------------------------------------------

    private fun loadRecentActivity(silent: Boolean = false) {
        loadFeed(silent)
    }

    /** Feed: primeira página + resumo (últimos 7 dias, duplicados). */
    private fun loadFeed(silent: Boolean) {
        feedJob?.cancel()
        if (!silent) _feed.update { it.copy(isLoading = true, error = null) }
        feedJob = viewModelScope.launch {
            val summary = async { activityRepository.summary().getOrNull() }
            activityRepository.feed(cursor = null)
                .onSuccess { page ->
                    _feed.update { it.copy(entries = page.entries, hasMore = page.hasMore, nextCursor = page.nextCursor, loaded = true, isLoading = false, error = null) }
                }
                .onFailure { t ->
                    _feed.update { it.copy(isLoading = false, error = if (it.loaded) null else t.message) }
                    if (_feed.value.loaded) _state.update { it.copy(toast = failText(t)) }
                }
            summary.await()?.let { s -> _feed.update { it.copy(summary = s) } }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadMoreFeed() {
        val st = _feed.value
        if (!st.hasMore || st.loadingMore || st.nextCursor == null) return
        _feed.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            activityRepository.feed(st.nextCursor)
                .onSuccess { page ->
                    _feed.update { cur ->
                        val seen = cur.entries.map { it.id }.toSet()
                        cur.copy(
                            entries = cur.entries + page.entries.filter { it.id !in seen },
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { t -> _feed.update { it.copy(loadingMore = false) }; _state.update { it.copy(toast = failText(t)) } }
        }
    }

    fun setActivityTab(tab: String) = _feed.update { it.copy(tab = tab) }

    @Suppress("unused")
    private fun loadRecentActivitySections(silent: Boolean = false) {
        activityJob?.cancel()
        if (!silent) _activity.update { it.copy(isLoading = true, error = null) }
        activityJob = viewModelScope.launch {
            activityRepository.recent()
                .onSuccess { sections ->
                    _activity.update { RecentActivityUiState(sections = sections, loaded = true) }
                }
                .onFailure { t ->
                    _activity.update { it.copy(isLoading = false, error = if (it.loaded) null else t.message) }
                    if (_activity.value.loaded) _state.update { it.copy(toast = failText(t)) }
                }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadMoreActivity(section: co.golink.tester.domain.activity.ActivitySection) {
        val current = _activity.value.sections[section] ?: return
        if (!current.hasMore || section in _activity.value.loadingMore) return
        _activity.update { it.copy(loadingMore = it.loadingMore + section) }
        viewModelScope.launch {
            activityRepository.recentSection(section, current.page + 1)
                .onSuccess { next ->
                    _activity.update { st ->
                        val prev = st.sections[section] ?: return@update st
                        val seen = prev.entries.map { it.id }.toSet()
                        val merged = prev.copy(
                            entries = prev.entries + next.entries.filter { it.id !in seen },
                            hasMore = next.hasMore,
                            page = next.page,
                        )
                        st.copy(sections = st.sections + (section to merged))
                    }
                }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
            _activity.update { it.copy(loadingMore = it.loadingMore - section) }
        }
    }

    /** Histórico de um item (separador Atividade dos Detalhes). */
    suspend fun itemActivity(item: BrowseItem, page: Int): Result<co.golink.tester.domain.activity.ActivityPage> =
        activityRepository.forItem(item, page)

    /** Abre uma pasta vinda da Atividade (equipa → Pastas de equipa / Partilhado comigo). */
    fun openActivityFolder(folder: BrowseItem.Folder, ownedByMe: Boolean) {
        val mode = when {
            !folder.isTeamFolder -> BrowseMode.Folder(id = folder.id, name = folder.name)
            ownedByMe -> BrowseMode.TeamFolder(id = folder.id, name = folder.name)
            else -> BrowseMode.SharedWithMe(id = folder.id, name = folder.name)
        }
        // Voltar leva de novo à Atividade (de onde se veio).
        val root = _state.value.crumbs.firstOrNull()?.takeIf { it.origin != null }
            ?: Crumb(null, "Atividade".tr(), origin = BrowseMode.Latest)
        _state.update {
            it.copy(mode = mode, crumbs = listOf(root, Crumb(folder.id, folder.name)), searchQuery = "", searchChips = emptyList())
        }
        loadCurrent()
    }

    fun currentUserId(): String? = (sessionManager.state.value as? AuthState.Authenticated)?.user?.id

    // ---- Copiar link -----------------------------------------------------------

    /**
     * Copia o link de partilha. Pastas e ficheiros cifrados precisam da chave no
     * fragmento (#k=), que só este dispositivo monta — mesmo critério da web e do
     * diálogo de partilha. Com E2E configurado mas trancado não se copia um link
     * que não abre.
     */
    fun copyShareLink(item: BrowseItem) {
        val share = item.share ?: return
        val base = share.link?.takeIf { it.isNotBlank() } ?: return
        val needsKey = item is BrowseItem.Folder || (item is BrowseItem.File && item.encrypted)
        viewModelScope.launch {
            var link = base
            if (needsKey && !base.contains("#k=") && e2eKeys.configured.value) {
                if (!e2eKeys.isUnlocked) {
                    _state.update { it.copy(toast = "Desbloqueia a encriptação para copiar este link".tr()) }
                    return@launch
                }
                val fragment = runCatching { shareKeyFragment(item, share) }.getOrNull()
                if (fragment == null) {
                    _state.update { it.copy(toast = "Não foi possível preparar a chave do link".tr()) }
                    return@launch
                }
                link = base + fragment
            }
            copyToClipboard("share-link", link)
            _state.update { it.copy(toast = "Link copiado".tr()) }
        }
    }

    /** Fragmento #k= da partilha (null se não houver chave). Avisa se a chave se perdeu. */
    private suspend fun shareKeyFragment(item: BrowseItem, share: ShareInfo): String? {
        val result = when (item) {
            is BrowseItem.File -> {
                if (!item.encrypted) return ""
                val wrapped = encryptionApi.fileKey(item.id).body()?.wrapped_data_key ?: return null
                e2eShareService.buildFileShareFragment(item.id, share.token, wrapped, item.name)
            }
            is BrowseItem.Folder -> e2eShareService.buildFolderShareFragment(item.id, share.token)
        }
        if (result.keyLost) {
            _state.update { it.copy(toast = "A chave desta partilha só existe no dispositivo onde criaste o link. Abre a conta lá uma vez para a recuperar — senão, revoga e volta a partilhar (o link antigo deixa de funcionar).".tr()) }
        }
        return result.fragment
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
                    attachShareKeyFragment()
                    _state.update { it.copy(toast = "Partilha criada".tr()) }
                    loadCurrent()
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = failText(t)) }
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
                    // O servidor devolve o link sem #k= (nunca conhece a chave): sem
                    // re-anexar, o link mostrado/copiado depois de guardar não decifrava.
                    // A chave é a MESMA de sempre (cofre do dono) — o link não muda.
                    attachShareKeyFragment()
                    _state.update { it.copy(toast = "Partilha actualizada".tr()) }
                    loadCurrent()
                }
                .onFailure { t ->
                    _shareState.update { it?.copy(isWorking = false) }
                    _state.update { it.copy(toast = failText(t)) }
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
                    _state.update { it.copy(toast = failText(t)) }
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
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
            _shareState.update { it?.copy(sendingEmail = false) }
        }
    }

    /** Pasta de equipa: o dono dissolve (volta aos ficheiros), o membro sai. */
    fun dissolveTeam(folder: BrowseItem.Folder) = teamAction(folder, "Equipa dissolvida — a pasta voltou aos teus ficheiros".tr()) {
        teamsRepository.dissolve(folder.id)
    }

    fun leaveTeam(folder: BrowseItem.Folder) = teamAction(folder, "Saíste da pasta de equipa".tr()) {
        teamsRepository.leave(folder.id)
    }

    private fun teamAction(folder: BrowseItem.Folder, success: String, call: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            call()
                .onSuccess {
                    _state.update { it.copy(toast = success) }
                    // Dentro da pasta: volta à raiz da secção.
                    if (_state.value.crumbs.size > 1) goToCrumb(0) else loadCurrent()
                }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    /** Lixo: restaurar os selecionados para o sítio de origem. */
    fun restoreSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        exitSelectMode()
        _state.update { it.copy(processing = "A restaurar…".tr()) }
        viewModelScope.launch {
            trashRepository.restore(targets)
                .onSuccess { _state.update { it.copy(processing = null, toast = "Restaurado".tr()) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(processing = null, toast = failText(t)) } }
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
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    fun isFavourite(folder: BrowseItem.Folder): Boolean =
        favouriteFolders.value.any { it.id == folder.id }

    private var loadMoreJob: Job? = null

    private fun loadCurrent(silent: Boolean = false) {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        if (_state.value.isLoadingMore) _state.update { it.copy(isLoadingMore = false) }
        val mode = _state.value.mode
        // Recarga normal: limpa baseline e mostra spinner. Recarga silenciosa
        // (despoletada pelo polling): mantém os itens e o scroll no ecrã e
        // repõe exactamente as páginas que o utilizador já tinha aberto.
        val pagesToReload = if (silent) _state.value.currentPage.coerceAtLeast(1) else 1
        // O filtro por tipo é da pasta/vista onde foi escolhido: ao mudar, volta a "Todos".
        if (_state.value.typeFilter != null && _state.value.typeFilterMode != mode) {
            _state.update { it.copy(typeFilter = null, typeFilterMode = null) }
        }
        if (!silent) {
            lastFingerprint = null
            _state.update { it.copy(items = emptyList(), isLoading = true, isLoadingMore = false, currentPage = 1, lastPage = 1, error = null, readOnly = false, stats = null, root = null) }
        }
        if (mode == BrowseMode.Latest) {
            _state.update { it.copy(isLoading = false) }
            loadRecentActivity(silent)
            return
        }
        loadJob = viewModelScope.launch {
            when (mode) {
                BrowseMode.Favourites -> handleSimple(Result.success(favouriteFolders.value.toList<BrowseItem>()), mode)
                BrowseMode.Trash -> handleTrash(trashRepository.listPaged(browseQuery()), mode)
                is BrowseMode.SearchResults -> handleSimple(repository.search(mode.query), mode)
                else -> loadPaged(mode, pagesToReload, silent)
            }
        }
    }

    // Carrega uma única página da fonte correspondente ao modo actual.
    // Devolve null para modos não paginados (Favoritos/Lixo/Pesquisa).
    private suspend fun fetchPage(mode: BrowseMode, page: Int): Result<PagedItems>? = when (mode) {
        is BrowseMode.Folder -> repository.listFolder(mode.id, page = page, query = browseQuery())
        BrowseMode.Latest -> repository.listLatest(page = page)
        BrowseMode.Shared -> repository.listShared(page = page, query = browseQuery())
        is BrowseMode.TeamFolder -> teamsRepository.listTeamFolder(mode.id, page = page, query = browseQuery())
        is BrowseMode.SharedWithMe -> teamsRepository.listSharedWithMe(mode.id, page = page, query = browseQuery())
        else -> null
    }

    // Filtro por tipo + ordenação pedidos ao servidor (paginação correta).
    private fun browseQuery(): co.golink.tester.domain.browse.BrowseQuery {
        val s = _state.value
        val (sort, direction) = when (s.sortMode) {
            SortMode.ALPHA_ASC -> "name" to "ASC"
            SortMode.ALPHA_DESC -> "name" to "DESC"
            SortMode.DATE_DESC -> "created_at" to "DESC"
            SortMode.DATE_ASC -> "created_at" to "ASC"
        }
        return co.golink.tester.domain.browse.BrowseQuery(type = s.typeFilter, sort = sort, direction = direction)
    }

    /** Chip de tipo (null = Todos). Só ficheiros desse tipo, sem pastas, como na web. */
    fun setTypeFilter(type: String?) {
        val s = _state.value
        if (s.typeFilter == type) return
        _state.update { it.copy(typeFilter = type, typeFilterMode = if (type != null) s.mode else null, selectedIds = emptySet()) }
        loadCurrent()
    }

    private fun handleTrash(result: Result<PagedItems>, mode: BrowseMode) {
        if (mode != _state.value.mode) return
        result
            .onSuccess { paged ->
                _state.update {
                    it.copy(items = sorted(paged.items), stats = paged.stats, isLoading = false, isRefreshing = false, currentPage = 1, lastPage = 1)
                }
            }
            .onFailure { t ->
                _state.update { it.copy(items = emptyList(), isLoading = false, isRefreshing = false, error = t.message) }
            }
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
                val me = currentUserId()
                val readOnly = mode is BrowseMode.SharedWithMe && mode.id != null &&
                    firstPaged.teamMembers?.firstOrNull { m -> m.id == me }?.permission == "can-view"
                _state.update {
                    it.copy(
                        items = sorted(all),
                        isLoading = false,
                        isRefreshing = false,
                        currentPage = current,
                        lastPage = last,
                        readOnly = readOnly,
                        stats = firstPaged.stats ?: it.stats,
                        root = firstPaged.root ?: it.root,
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
        // Cancelável: ao mudar de pasta, o pedido da página seguinte da anterior pára.
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
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
        setZoomLevel(if (mode == ViewMode.GRID) VP.ZOOM_GRID else VP.ZOOM_LIST)
    }

    fun toggleViewMode() = setViewMode(if (_state.value.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)

    /** Pinça: muda o nível de zoom da secção atual (e lista/grelha em conformidade). */
    fun setZoomLevel(level: Int) {
        val l = level.coerceIn(VP.ZOOM_MIN, VP.ZOOM_MAX)
        viewPreferences.setZoomLevel(zoomScope(_state.value.mode), l)
        _state.update { it.copy(zoomLevel = l, viewMode = viewModeFor(l)) }
    }

    private fun defaultZoom(): Int =
        if (viewPreferences.viewMode(VP.SCOPE_BROWSE, ViewMode.LIST) == ViewMode.GRID) VP.ZOOM_GRID else VP.ZOOM_LIST

    private fun zoomScope(mode: BrowseMode): String = "browse_" + when (mode) {
        is BrowseMode.Folder, is BrowseMode.SearchResults -> "files"
        BrowseMode.Latest -> "latest"
        BrowseMode.Shared -> "shared"
        BrowseMode.Favourites -> "favourites"
        BrowseMode.Trash -> "trash"
        is BrowseMode.TeamFolder -> "team"
        is BrowseMode.SharedWithMe -> "shared_with_me"
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
    // Respeita o filtro "só ficheiros": antes selecionava também as pastas escondidas.
    fun selectAll() = _state.update { st ->
        val visible = if (st.filesOnly) st.items.filterIsInstance<BrowseItem.File>() else st.items
        st.copy(selectedIds = visible.map { item -> item.id }.toSet())
    }
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
        // Listas paginadas: a ordem tem de vir do servidor (senão só ordenava o que já carregou).
        if (_state.value.lastPage > 1) loadCurrent()
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
            // ISO (ordenável); o createdAt é texto localizado ("16 out 2026").
            SortMode.DATE_DESC -> compareByDescending { it.createdAtIso ?: it.updatedAtIso ?: "" }
            SortMode.DATE_ASC -> compareBy { it.createdAtIso ?: it.updatedAtIso ?: "" }
        }
        return items.sortedWith(foldersFirst.then(secondary))
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
