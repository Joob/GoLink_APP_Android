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
import co.golink.tester.domain.files.freeFileName
import co.golink.tester.domain.files.OfficeFormat
import co.golink.tester.domain.files.isOfficeFile
import co.golink.tester.domain.files.withOfficeExtension
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
    val emptyingDeleted: Int = 0,
    val emptyingTotal: Int = 0,
    // Toaster do flush em "concluído" (fecha sozinho).
    val emptyingDone: Boolean = false,
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

/** Ecrã de pesquisa: [tab] = filtro do servidor (null = Tudo; files|folders|shared|team|mobile_backup). */
data class SearchSheetState(
    val open: Boolean = false,
    val query: String = "",
    val tab: String? = null,
    val chips: List<String> = emptyList(),
    val results: List<BrowseItem> = emptyList(),
    val recents: List<BrowseItem> = emptyList(),
    val loading: Boolean = false,
    val recentsLoading: Boolean = false,
)

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

    // Feed da Atividade recente (redesign igual à web).
    private val _feed = MutableStateFlow(ActivityFeedUiState())
    val feed: StateFlow<ActivityFeedUiState> = _feed.asStateFlow()
    private var feedJob: Job? = null

    private val _shareState = MutableStateFlow<ShareDialogUiState?>(null)
    val shareState: StateFlow<ShareDialogUiState?> = _shareState.asStateFlow()

    val uploads: StateFlow<List<UploadTask>> = uploadManager.tasks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // E2E Fase 2: decifra os nomes das favoritas (re-emite ao desbloquear).
    val favouriteFolders: StateFlow<List<BrowseItem.Folder>> =
        kotlinx.coroutines.flow.combine(sessionManager.state, e2eKeys.unlocked) { s, _ ->
            val list = if (s is AuthState.Authenticated) s.user.favouriteFolders else emptyList()
            // Trancado: o servidor só tem o placeholder; mostra "Protegido" em vez de "•".
            list.map { f ->
                e2eKeys.openNameOrNull(f.nameEncrypted)?.let { f.copy(name = it) }
                    ?: if (f.nameEncrypted.isNullOrEmpty()) f else f.copy(name = "Protegido".tr())
            }
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
        // Config do servidor logo no arranque: com sessão já iniciada não se passa
        // pelo login/landing, e sem ela o tipo de faturação ficava "none" (sem
        // "Melhorar plano" no cartão de armazenamento nem o grupo Faturação).
        viewModelScope.launch { configRepository.load() }
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
                    // Itens novos podem ter ficado selados ao dono (Team Folder).
                    teamsRepository.invalidateClearedNames()
                    loadCurrent()
                }
            }
        }
        viewModelScope.launch {
            // Recarga silenciosa: mantém seleção e scroll (e não duplica o load do unlock).
            favouriteFolders.collect { list ->
                if (_state.value.mode == BrowseMode.Favourites) handleSimple(Result.success(list), BrowseMode.Favourites)
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
        val mode = _state.value.mode
        val newMode = when {
            mode is BrowseMode.TeamFolder -> BrowseMode.TeamFolder(id = item.id, name = item.name)
            mode is BrowseMode.SharedWithMe -> BrowseMode.SharedWithMe(id = item.id, name = item.name)
            // Links públicos: uma pasta de equipa abre no seu contexto (badge, nomes em claro).
            mode == BrowseMode.Shared && item.isTeamFolder ->
                if (item.ownerId == null || item.ownerId == currentUserId()) BrowseMode.TeamFolder(id = item.id, name = item.name)
                else BrowseMode.SharedWithMe(id = item.id, name = item.name)
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

    /** Atalho do drawer: abre a favorita a partir de Favoritos, seja qual for a secção atual. */
    fun openFavourite(folder: BrowseItem.Folder) {
        _state.update {
            it.copy(
                mode = BrowseMode.Folder(id = folder.id, name = folder.name),
                crumbs = listOf(Crumb(null, "Favoritos".tr(), origin = BrowseMode.Favourites), Crumb(folder.id, folder.name)),
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
    fun openShared() = switchTo(BrowseMode.Shared, "Links públicos".tr())
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
    /** Faturação: none | fixed | metered (decide o grupo Faturação do admin). */
    val subscriptionType: StateFlow<String> = configRepository.config
        .map { it?.payments?.type ?: "none" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "none")
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
        if (_state.value.searchQuery.isBlank() && query.isNotBlank()) warmSearch()
        _state.update { it.copy(searchQuery = query, searchChips = parsed.chips) }
        searchJob?.cancel()
        if (query.isBlank()) return
        // Mínimo 2 chars + debounce 300ms: 1 carácter é um LIKE quase-full-scan
        // no servidor sem valor para o utilizador (alinha com o Spotlight da Web).
        // Com filtros reconhecidos ("pdf") a pesquisa já faz sentido.
        if (!parsed.hasFilters && query.trim().length < 2) return
        searchJob = viewModelScope.launch {
            delay(200)
            // Os resultados anteriores ficam visíveis até chegarem os novos.
            _state.update { it.copy(mode = BrowseMode.SearchResults(query), isLoading = true, error = null) }
            try {
                repository.searchProgressive(query).collect { items ->
                    if (_state.value.searchQuery == query) _state.update { it.copy(items = items) }
                }
                if (_state.value.searchQuery == query) _state.update { it.copy(isLoading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (_state.value.searchQuery == query) _state.update { it.copy(isLoading = false, error = t.message) }
            }
        }
    }

    /** Chamado ao abrir a pesquisa: aquece o índice de nomes cifrados. */
    fun warmSearch() {
        viewModelScope.launch { runCatching { repository.warmSearchIndex() } }
    }

    fun clearSearch() {
        searchJob?.cancel()
        openRoot()
    }

    // ── Ecrã de pesquisa (paridade com o popup da web): separadores + Recentes ──

    private val _searchSheet = MutableStateFlow(SearchSheetState())
    val searchSheet: StateFlow<SearchSheetState> = _searchSheet.asStateFlow()
    private var sheetSearchJob: Job? = null
    private var sheetRecentsJob: Job? = null

    fun openSearchSheet() {
        _searchSheet.value = SearchSheetState(open = true)
        warmSearch()
        loadSheetRecents()
    }

    fun closeSearchSheet() {
        sheetSearchJob?.cancel()
        sheetRecentsJob?.cancel()
        _searchSheet.value = SearchSheetState()
    }

    fun setSheetQuery(query: String) {
        val parsed = co.golink.tester.domain.search.SearchQueryParser.parse(query)
        _searchSheet.update { it.copy(query = query, chips = parsed.chips) }
        runSheetSearch()
    }

    fun setSheetTab(tab: String?) {
        if (_searchSheet.value.tab == tab) return
        _searchSheet.update { it.copy(tab = tab, results = emptyList()) }
        loadSheetRecents()
        runSheetSearch()
    }

    private fun runSheetSearch() {
        sheetSearchJob?.cancel()
        val st = _searchSheet.value
        val query = st.query
        val tab = st.tab
        val parsed = co.golink.tester.domain.search.SearchQueryParser.parse(query)
        // Mínimo 2 chars (ou filtros reconhecidos), como o Spotlight da web.
        if (query.isBlank() || (!parsed.hasFilters && query.trim().length < 2)) {
            _searchSheet.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        sheetSearchJob = viewModelScope.launch {
            delay(250)
            _searchSheet.update { it.copy(loading = true) }
            val current = { _searchSheet.value.let { it.query == query && it.tab == tab } }
            try {
                repository.searchProgressive(query, tab).collect { items ->
                    if (current()) _searchSheet.update { it.copy(results = items) }
                }
                if (current()) _searchSheet.update { it.copy(loading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (current()) _searchSheet.update { it.copy(loading = false, results = emptyList()) }
            }
        }
    }

    private fun loadSheetRecents() {
        sheetRecentsJob?.cancel()
        val tab = _searchSheet.value.tab
        sheetRecentsJob = viewModelScope.launch {
            _searchSheet.update { it.copy(recentsLoading = true) }
            val items = repository.recentOpened(tab).getOrDefault(emptyList())
            if (_searchSheet.value.tab == tab) _searchSheet.update { it.copy(recents = items, recentsLoading = false) }
        }
    }

    /** Pasta escolhida na pesquisa: abre no seu contexto (equipa minha / partilhada comigo / Drive). */
    fun openSearchFolder(folder: BrowseItem.Folder) {
        val ownedByMe = folder.ownerId == null || isOwnedByMe(folder.ownerId)
        val (mode, root) = when {
            !folder.isTeamFolder -> BrowseMode.Folder(id = folder.id, name = folder.name) to Crumb(null, "Ficheiros".tr())
            ownedByMe -> BrowseMode.TeamFolder(id = folder.id, name = folder.name) to
                Crumb(null, "Pastas de equipa".tr(), origin = BrowseMode.TeamFolder(id = null, name = "Pastas de equipa".tr()))
            else -> BrowseMode.SharedWithMe(id = folder.id, name = folder.name) to
                Crumb(null, "Partilhado comigo".tr(), origin = BrowseMode.SharedWithMe(id = null, name = "Partilhado comigo".tr()))
        }
        closeSearchSheet()
        _state.update {
            it.copy(mode = mode, crumbs = listOf(root, Crumb(folder.id, folder.name)), searchQuery = "", searchChips = emptyList())
        }
        loadCurrent()
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        loadCurrent()
    }

    fun prepareViewer(
        files: List<BrowseItem.File>,
        startId: String,
        fromTrash: Boolean = _state.value.mode == BrowseMode.Trash,
        // Membro can-view (ou raiz do "Partilhado comigo", onde a permissão por ficheiro não se
        // sabe — como no menu): o viewer não oferece editar/mover/apagar.
        readOnlyIds: Set<String> = _state.value.let { st ->
            val sharedRoot = (st.mode as? BrowseMode.SharedWithMe)?.let { it.id == null } == true
            if (st.readOnly || sharedRoot) files.mapTo(HashSet()) { it.id } else emptySet()
        },
        // De outro dono (owner_id; servidor antigo → Partilhado comigo): sem partilhar (exige ser o dono).
        notOwnedIds: Set<String> = currentUserId().let { me ->
            val shared = _state.value.mode is BrowseMode.SharedWithMe
            files.filter { f -> f.ownerId?.let { it != me } ?: shared }.mapTo(HashSet()) { it.id }
        },
    ) {
        fileViewerSession.files = files
        fileViewerSession.startId = startId
        fileViewerSession.fromTrash = fromTrash
        fileViewerSession.readOnlyIds = readOnlyIds
        fileViewerSession.notOwnedIds = notOwnedIds
    }

    fun downloadFile(item: BrowseItem.File) {
        downloader.downloadFile(item)
            .onSuccess { _state.update { it.copy(toast = "Download iniciado: %s".tr().format(item.name)) } }
            .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
    }

    fun downloadItem(item: BrowseItem) {
        when (item) {
            is BrowseItem.File -> downloadFile(item)
            is BrowseItem.Folder -> downloader.downloadFolder(item)
                .onSuccess { _state.update { it.copy(toast = "A preparar zip: %s".tr().format(item.name)) } }
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
                .onSuccess { _state.update { it.copy(toast = "A preparar zip (%d)".tr().format(items.size)) } }
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
        val parentId = currentParentId()
        viewModelScope.launch {
            // Em equipa/partilhado o nome fica em claro (os membros têm de o ler).
            filesRepository.createFolder(name.trim(), parentId, encryptName = isPrivateNameContext() && isPrivateTarget(parentId), color = color)
                .onSuccess {
                    _state.update { it.copy(toast = "Pasta criada".tr()) }
                    // Como em createFolderIn: a árvore (Mover, isPrivateTarget) tem de a ver.
                    teamsRepository.invalidateClearedNames()
                    loadNavigationTree()
                    loadCurrent()
                }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    /** Cria um ficheiro .txt vazio na pasta atual (o nome ganha .txt se faltar). */
    fun createTextFile(name: String, openEditor: Boolean = true) {
        val parentId = currentParentId()
        val filename = freeNameHere(withTextExtension(name))
        // Sem toast: quem confirma é o painel de uploads (e a lista recarrega no
        // completedTick) — anunciar "criado" aqui mentia se o upload falhasse.
        val (taskId, job) = uploadManager.createTextFile(filename, "", parentId, trackCreated = openEditor)
        // "Abrir o editor depois de criar" desmarcado: só cria.
        if (openEditor) openWhenCreated(taskId, job, isText = true)
    }

    // Nome já usado na pasta → "notas (1).txt": criar nunca substitui.
    private fun freeNameHere(filename: String): String =
        freeFileName(filename, state.value.items.filterIsInstance<BrowseItem.File>().map { it.name })

    /** Formatos convertidos no servidor (odt) só se o ficheiro não for cifrado. */
    val canCreateServerConverted: Boolean get() = !uploadManager.encryptsUploads

    /** Documento ou folha de cálculo vazios no formato escolhido, a partir dos modelos em assets/office. */
    fun createDocument(format: OfficeFormat, name: String, openEditor: Boolean = true) {
        val parentId = currentParentId()
        val filename = freeNameHere(withOfficeExtension(name, format))
        val asset = format.asset ?: return
        if (format.serverConverted && !canCreateServerConverted) return
        val bytes = runCatching { appContext.assets.open(asset).use { it.readBytes() } }.getOrElse { t ->
            _state.update { it.copy(toast = failText(t)) }
            return
        }
        val (taskId, job) = uploadManager.createDocument(filename, bytes, format.mimeType, parentId, trackCreated = openEditor)
        if (openEditor) openWhenCreated(taskId, job, isText = false)
    }

    /**
     * Abre o ficheiro que ESTE envio criou (pelo id, nunca pelo nome — um
     * homónimo antigo abria em edição). Falhou, conflito ou cancelado → nada.
     * A recarga da lista corre em background, por isso esperamos que o ficheiro
     * novo apareça antes de pedir ao ecrã que abra o viewer em modo de edição.
     * [isText]: criado pelo "Novo ficheiro de texto" (um .csv abre no editor de texto).
     */
    private fun openWhenCreated(taskId: String, job: Job, isText: Boolean) {
        viewModelScope.launch {
            job.join()
            val id = uploadManager.takeCreated(taskId)?.id ?: return@launch
            val file = withTimeoutOrNull(15_000) {
                _state
                    .map { s -> s.items.filterIsInstance<BrowseItem.File>().firstOrNull { it.id == id } }
                    .filterNotNull()
                    .first()
            } ?: return@launch

            if (file.isOfficeFile() && !isText) {
                // O viewer abre-o no editor de documentos.
                prepareViewer(listOf(file), file.id)
            } else {
                prepareViewer(
                    state.value.items.filterIsInstance<BrowseItem.File>().filter { it.isViewable() },
                    file.id,
                )
                fileViewerSession.startInEditMode = true
            }
            _state.update { it.copy(openFileRequest = file.id) }
        }
    }

    fun consumeOpenFileRequest() = _state.update { it.copy(openFileRequest = null) }

    fun createFolderIn(name: String, parentId: String?) {
        viewModelScope.launch {
            filesRepository.createFolder(name.trim(), parentId, encryptName = isPrivateTarget(parentId))
                .onSuccess {
                    _state.update { it.copy(toast = "Pasta criada".tr()) }
                    teamsRepository.invalidateClearedNames()
                    loadNavigationTree()
                    if (currentParentId() == parentId) loadCurrent()
                }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    // Decide pela pasta de DESTINO (o seletor da árvore pode apontar para uma
    // Team Folder estando em modo privado, ou vice-versa). Raiz = espaço privado.
    // Desconhecida na árvore → recorre ao modo do ecrã.
    private fun isPrivateTarget(parentId: String?): Boolean {
        if (parentId == null) return true
        for (section in state.value.navigationTree) {
            val path = findNavPath(section.folders, parentId) ?: continue
            return path.none { it.team_folder == true }
        }
        return isPrivateNameContext()
    }

    private fun teamRootOf(folderId: String): String? {
        for (section in state.value.navigationTree) {
            val path = findNavPath(section.folders, folderId) ?: continue
            return path.firstOrNull { it.team_folder == true }?.id
        }
        return null
    }

    private fun findNavPath(nodes: List<co.golink.tester.domain.browse.NavFolder>, id: String): List<co.golink.tester.domain.browse.NavFolder>? {
        for (n in nodes) {
            if (n.id == id) return listOf(n)
            findNavPath(n.folders, id)?.let { return listOf(n) + it }
        }
        return null
    }

    // E2E Fase 2: só ciframos nomes no espaço privado do utilizador — não em
    // pastas de equipa / partilhas / resultados de pesquisa.
    /** Pasta onde o botão + cria/carrega: Ficheiros, Pastas de equipa ou Partilhado comigo. */
    private fun currentParentId(): String? = when (val m = state.value.mode) {
        is BrowseMode.Folder -> m.id
        is BrowseMode.TeamFolder -> m.id
        is BrowseMode.SharedWithMe -> m.id
        else -> null
    }

    private fun isPrivateNameContext(): Boolean = when (state.value.mode) {
        is BrowseMode.Folder, BrowseMode.Latest, BrowseMode.Favourites, BrowseMode.Trash -> true
        else -> false
    }

    fun rename(item: BrowseItem, newName: String, appearance: co.golink.tester.domain.files.FolderAppearance? = null) {
        viewModelScope.launch {
            // Pela localização do item (em Recentes/Favoritos pode estar numa equipa).
            val private = !(item is BrowseItem.Folder && item.isTeamFolder) && isPrivateTarget(item.parentId)
            filesRepository.rename(item, newName.trim(), encryptName = private, appearance = appearance)
                .onSuccess { _state.update { it.copy(toast = "Renomeado".tr()) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    fun delete(item: BrowseItem, permanent: Boolean = false) {
        if (permanent) _state.update { it.copy(processing = "A eliminar…".tr()) }
        viewModelScope.launch {
            filesRepository.delete(listOf(item), permanent)
                .onSuccess { _state.update { it.copy(processing = null, toast = if (permanent) "Eliminado".tr() else "Movido para o lixo".tr()) }; reloadAfterRemoving(item) }
                .onFailure { t ->
                    android.util.Log.e("BrowseVM", "delete failed", t)
                    _state.update { it.copy(processing = null, toast = "Falha: %s".tr().format(t.message ?: t::class.java.simpleName)) }
                }
        }
    }

    fun move(item: BrowseItem, toFolderId: String?) {
        viewModelScope.launch {
            filesRepository.move(listOf(item), toFolderId)
                .onSuccess { _state.update { it.copy(toast = "Movido".tr()) }; teamsRepository.invalidateClearedNames(); reloadAfterRemoving(item) }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    /** Apagou/moveu a pasta onde se está (menu do cabeçalho): sobe um nível. */
    private fun reloadAfterRemoving(item: BrowseItem) {
        val crumbs = _state.value.crumbs
        when {
            crumbs.lastOrNull()?.id != item.id -> loadCurrent()
            crumbs.size > 1 -> goToCrumb(crumbs.size - 2)
            else -> openRoot()
        }
    }

    /** Arrastar para uma pasta: avisa com o nome do destino. */
    fun moveToFolder(items: List<BrowseItem>, toFolderId: String?, folderName: String) {
        val targets = items.filter { it.id != toFolderId }
        if (targets.isEmpty()) return
        if (state.value.selectMode) exitSelectMode()
        viewModelScope.launch {
            filesRepository.move(targets, toFolderId)
                .onSuccess { _state.update { it.copy(toast = "Movido para %s".tr().format(folderName)) }; teamsRepository.invalidateClearedNames(); loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    // Nomes (em claro) dos ficheiros já visíveis na pasta: com nomes cifrados é
    // o cliente que deteta o conflito, o servidor só vê o placeholder.
    private fun visibleFileNames(): Map<String, String> =
        state.value.items.filterIsInstance<BrowseItem.File>().associate { it.name to it.id }

    fun upload(uri: Uri) = uploadMany(listOf(uri))

    fun uploadMany(uris: List<Uri>) {
        val parentId = currentParentId()
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

    /**
     * Restrições da conta (faturação/espaço), como na web: recusa antes de abrir
     * o diálogo em vez de um "HTTP 401" no fim. Criar ficheiros conta como envio.
     */
    fun allowCreateFolder(): Boolean = allowed({ it.canCreateFolder }, "Criação de pastas temporariamente desativada".tr())

    fun allowUpload(): Boolean = allowed({ it.canUpload }, "Envio temporariamente desativado".tr())

    private fun allowed(check: (co.golink.tester.domain.user.User) -> Boolean, title: String): Boolean {
        val user = (sessionManager.state.value as? AuthState.Authenticated)?.user ?: return true
        if (check(user)) return true
        _state.update { it.copy(uploadBlocked = UploadBlock(title, "Reveja o espaço disponível e as definições de faturação.".tr())) }
        return false
    }

    fun dismissUploadBlocked() = _state.update { it.copy(uploadBlocked = null) }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(java.util.Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1L shl 20 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1L shl 10 -> String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    // Recebe a lista já separada pelo diálogo: voltar a juntar e partir por
    // vírgulas cortava URLs válidos (ex. ?size=800,600).
    fun remoteUpload(input: List<String>) {
        val urls = input
            .map { it.trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        if (urls.isEmpty()) {
            _state.update { it.copy(toast = "Insira pelo menos um URL válido".tr()) }
            return
        }
        val parentId = currentParentId()
        viewModelScope.launch {
            filesRepository.remoteUpload(urls, parentId)
                .onSuccess { _state.update { it.copy(toast = "%d URL(s) na fila".tr().format(urls.size)) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
        }
    }

    fun createTeamFolder(name: String, invitations: List<TeamInvitation>, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            teamsRepository.createTeamFolder(name.trim(), invitations)
                .onSuccess {
                    onDone(null)
                    _state.update { it.copy(toast = "Pasta de equipa criada".tr()) }
                    uploadManager.invalidateMemberKeys()
                    // Fora de Pastas de equipa a nova pasta não aparece: ir para lá.
                    if ((_state.value.mode as? BrowseMode.TeamFolder)?.id == null && _state.value.mode is BrowseMode.TeamFolder) loadCurrent() else openTeamFolders()
                }
                .onFailure { t -> onDone(failText(t)) }
        }
    }

    fun convertToTeamFolder(folderId: String, invitations: List<TeamInvitation>, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            teamsRepository.convertToTeamFolder(folderId, invitations)
                .onSuccess {
                    onDone(null)
                    _state.update { it.copy(toast = "Pasta convertida".tr()) }
                    uploadManager.invalidateMemberKeys()
                    loadCurrent()
                    // Como a web (clearTeamFolderNames): sem isto os membros veem "•".
                    teamsRepository.clearEncryptedNames(folderId)
                }
                .onFailure { t -> onDone(failText(t)) }
        }
    }

    // folderId nulo = usa a pasta actual (fluxo do botão +). O menu "…" de uma
    // pasta passa o id dessa pasta. Ao criar, copiamos o link para a área de
    // transferência, como faz a Web.
    fun createFileRequest(name: String?, email: String?, notes: String?, folderId: String? = null, expiresInDays: Int? = null) {
        val effectiveFolder = folderId ?: currentParentId()
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
        val parentId = currentParentId()
        viewModelScope.launch {
            // Percorrer a pasta (SAF) é lento em pastas grandes: em IO.
            val (rootName, entries) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                treeDisplayName(treeUri) to collectFilesFromTree(treeUri)
            }
            if (entries.isEmpty()) {
                _state.update { it.copy(toast = "Pasta vazia ou sem ficheiros".tr()) }
                return@launch
            }
            if (!canUpload(entries.map { it.first })) return@launch
            // Mantém a estrutura: cria a pasta raiz e as subpastas antes de enviar
            // (antes ia tudo solto para a pasta atual e os nomes repetidos colidiam).
            val encryptName = isPrivateNameContext() && isPrivateTarget(parentId)
            val folderIds = mutableMapOf<String, String>()
            val targets = runCatching {
                // Pais antes dos filhos: ordenar por caminho garante "A" antes de "A/B".
                entries.map { (_, dir) -> if (dir.isEmpty()) rootName else "$rootName/$dir" }
                    .flatMap { full -> full.split('/').indices.map { i -> full.split('/').take(i + 1).joinToString("/") } }
                    .distinct().sortedBy { it.count { c -> c == '/' } }
                    .forEach { path ->
                        val parent = if (path.contains('/')) folderIds.getValue(path.substringBeforeLast('/')) else parentId
                        folderIds[path] = filesRepository.createFolder(path.substringAfterLast('/'), parent, encryptName = encryptName).getOrThrow().id
                    }
                entries.map { (uri, dir) -> uri to folderIds.getValue(if (dir.isEmpty()) rootName else "$rootName/$dir") }
            }.getOrElse { t ->
                _state.update { it.copy(toast = failText(t)) }
                loadCurrent()
                return@launch
            }
            // A permissão persistente é da árvore: guarda-a para retomar após fechar a app.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                targets.forEach { (uri, folderId) -> uploadManager.enqueueWithId(uri, folderId, treeUri = treeUri) }
            }
            loadCurrent()
            _state.update { it.copy(toast = "%d ficheiro(s) na fila".tr().format(entries.size)) }
        }
    }

    private fun treeDisplayName(treeUri: Uri): String {
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return appContext.contentResolver.query(rootUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?.takeIf { it.isNotBlank() } ?: "Pasta"
    }

    /** Ficheiros da árvore com o caminho relativo da pasta onde estão ("" = raiz). */
    private fun collectFilesFromTree(treeUri: Uri): List<Pair<Uri, String>> {
        val out = mutableListOf<Pair<Uri, String>>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        walkTree(treeUri, rootDocId, "", out)
        return out
    }

    private fun walkTree(treeUri: Uri, docId: String, path: String, out: MutableList<Pair<Uri, String>>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        appContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0)
                val mime = cursor.getString(1)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val name = cursor.getString(2)?.replace('/', '_') ?: childId
                    walkTree(treeUri, childId, if (path.isEmpty()) name else "$path/$name", out)
                } else {
                    out += DocumentsContract.buildDocumentUriUsingTree(treeUri, childId) to path
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
    private fun failText(t: Throwable): String {
        val msg = t.message.orEmpty()
        if (msg.startsWith("HTTP 403")) return "Não tens permissão de edição nesta pasta".tr()
        // "HTTP 4xx: {json}" dos repositórios: mostra a mensagem do servidor, não o JSON.
        val body = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(msg.substringAfter(": ", "")) as kotlinx.serialization.json.JsonObject
        }.getOrNull()
        val serverMsg = body?.let { o ->
            val errors = o["errors"] as? kotlinx.serialization.json.JsonObject
            val first = (errors?.values?.firstOrNull() as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
            ((first ?: o["message"]) as? kotlinx.serialization.json.JsonPrimitive)?.content
        } ?: return "Falha: %s".tr().format(msg)
        return when {
            serverMsg.contains("members limit", ignoreCase = true) -> "Atingiste o limite de membros do teu plano.".tr()
            serverMsg.contains("action is not allowed", ignoreCase = true) -> "Esta ação não é permitida no teu plano.".tr()
            else -> "Falha: %s".tr().format(serverMsg)
        }
    }

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

    /** Duplicados: saem já do resumo e vão para o lixo (reversível) num só pedido. */
    fun trashDuplicates(files: List<BrowseItem.File>) {
        if (files.isEmpty()) return
        val ids = files.map { it.id }.toSet()
        _feed.update { st ->
            val s = st.summary ?: return@update st
            val groups = s.duplicates
                .map { g ->
                    val keep = g.files.indices.filter { g.files[it].id !in ids }
                    val folders = keep.mapNotNull { g.fileFolders.getOrNull(it) }
                    g.copy(files = keep.map { g.files[it] }, fileFolders = folders, folders = folders.distinct())
                }
                .filter { it.files.size > 1 }
            st.copy(summary = s.copy(duplicates = groups))
        }
        viewModelScope.launch {
            filesRepository.delete(files, false)
                .onSuccess { _state.update { it.copy(toast = "Movido para o lixo".tr()) }; loadCurrent() }
                .onFailure { t -> _state.update { it.copy(toast = failText(t)) }; loadCurrent() }
        }
    }

    /** "Abrir pasta" de uma cópia duplicada (sem pasta-mãe → O meu Drive). */
    fun openDuplicateFolder(parentId: String?, name: String) {
        if (parentId == null) {
            openRoot()
            return
        }
        val root = _state.value.crumbs.firstOrNull()?.takeIf { it.origin != null }
            ?: Crumb(null, "Atividade".tr(), origin = BrowseMode.Latest)
        _state.update {
            it.copy(mode = BrowseMode.Folder(id = parentId, name = name), crumbs = listOf(root, Crumb(parentId, name)), searchQuery = "", searchChips = emptyList())
        }
        loadCurrent()
    }

    fun currentUserId(): String? = (sessionManager.state.value as? AuthState.Authenticated)?.user?.id

    /** Dono conhecido e sou eu (sem sessão ou sem dono → não). */
    fun isOwnedByMe(ownerId: String?): Boolean = ownerId != null && ownerId == currentUserId()

    /**
     * Itens da Atividade que não posso editar/mover/apagar. Nos lotes decide o
     * can_edit de cada ficheiro: o do lote é falso se um só não for editável, e
     * tirava a edição aos meus ficheiros num lote misto.
     */
    fun activityReadOnlyIds(): Set<String> = _feed.value.entries.flatMapTo(HashSet()) { e ->
        val editable = e.editable()
        (if (editable) emptyList() else listOfNotNull(e.base.item?.id)) +
            e.files.filterNot { f -> e.fileCanEdit[f.id] ?: editable }.map { it.id }
    }

    /** Itens da Atividade de outro dono: sem partilhar/copiar link (exige ser o dono). */
    fun activityNotOwnedIds(): Set<String> {
        val me = currentUserId() ?: return emptySet()
        return _feed.value.entries
            .filter { e -> (e.base.itemOwnerId ?: e.folderOwnerId)?.let { it != me } == true }
            .flatMapTo(HashSet()) { e -> listOfNotNull(e.base.item?.id) + e.files.map { it.id } }
    }

    /** Ficheiros visualizáveis da Atividade (deslizar no viewer), sem os que já estão no lixo. */
    fun activityViewableFiles(): List<BrowseItem.File> = _feed.value.entries
        .flatMap { e -> (if (e.base.itemTrashed) emptyList() else listOfNotNull(e.base.item)) + e.files.filterNot { it.id in e.trashedFileIds } }
        .filterIsInstance<BrowseItem.File>()
        .filter { it.isViewable() }
        .distinctBy { it.id }

    // Servidor sem can_edit: só o dono (num lote, o dono da pasta). Sessão em transição → decide o servidor.
    private fun co.golink.tester.domain.activity.FeedEntry.editable(): Boolean = canEdit ?: run {
        val me = currentUserId() ?: return@run true
        val owner = base.itemOwnerId ?: folderOwnerId
        owner == null || owner == me
    }

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

    suspend fun onlineMembers(folderId: String): Set<String>? = teamsRepository.onlineMembers(folderId)

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
                .onSuccess { _state.update { it.copy(processing = null, toast = "Restaurado".tr()) } }
                .onFailure { t -> _state.update { it.copy(processing = null, toast = failText(t)) } }
        }
    }

    fun restore(item: BrowseItem) {
        _state.update { it.copy(processing = "A restaurar…".tr()) }
        viewModelScope.launch {
            trashRepository.restore(listOf(item))
                .onSuccess { _state.update { it.copy(processing = null, toast = "Restaurado".tr()) } }
                .onFailure { t ->
                    android.util.Log.e("BrowseVM", "restore failed", t)
                    _state.update { it.copy(processing = null, toast = "Falha: %s".tr().format(t.message ?: t::class.java.simpleName)) }
                }
        }
    }

    // Esvazia o lixo em lotes: a cada lote actualiza o progresso e recarrega a
    // lista em silêncio (sem spinner) para os itens sumirem por trás do blur.
    private var emptyTrashRun = 0

    // Esvazia o lixo em lotes. O progresso conta pelo `remaining` do servidor
    // (o `deleted` conta 1 por árvore de pasta, não é comparável com o total).
    // A lista recarrega no máximo a cada RELOAD_MS e no fim, com o filtro/ordem
    // e as estatísticas do cabeçalho (loadPaged), em vez de a cada lote.
    fun emptyTrash() {
        if (_state.value.isEmptyingTrash) return
        val run = ++emptyTrashRun
        _state.update {
            it.copy(
                isEmptyingTrash = true, emptyingProgress = 0, emptyingDeleted = 0, emptyingTotal = 0, emptyingDone = false,
                selectMode = false, selectedIds = emptySet(),
            )
        }
        viewModelScope.launch {
            val batch = 50
            var total = 0
            var remaining = Int.MAX_VALUE
            var failed = false
            var lastReload = System.currentTimeMillis()
            try {
                while (remaining > 0) {
                    val res = trashRepository.dumpBatch(batch).getOrElse { t ->
                        android.util.Log.e("BrowseVM", "emptyTrash failed", t)
                        _state.update { it.copy(toast = "Falha: %s".tr().format(t.message ?: t::class.java.simpleName)) }
                        failed = true
                        null
                    } ?: break
                    remaining = res.remaining
                    // Itens que chegam ao lixo a meio alargam o total (nunca "60 / 50").
                    total = maxOf(total, res.deleted + res.remaining, remaining)
                    val done = (total - remaining).coerceIn(0, total)
                    val pct = if (total > 0) done * 100 / total else 100
                    _state.update { it.copy(emptyingProgress = pct.coerceIn(0, 99), emptyingDeleted = done, emptyingTotal = total) }
                    // Lote sem progresso com itens por apagar: pára e avisa (sem loop infinito).
                    if (res.deleted == 0 && remaining > 0) {
                        _state.update { it.copy(toast = "Alguns itens não puderam ser eliminados".tr()) }
                        failed = true
                        break
                    }
                    if (remaining > 0 && System.currentTimeMillis() - lastReload >= RELOAD_MS) {
                        reloadTrashItems()
                        lastReload = System.currentTimeMillis()
                    }
                }
                if (!failed) _state.update { it.copy(emptyingProgress = 100, emptyingDeleted = total, emptyingTotal = total, emptyingDone = true) }
                reloadTrashItems()
            } finally {
                // Blur sai logo; o toaster fica em "concluído" uns segundos.
                _state.update { it.copy(isEmptyingTrash = false) }
                if (!failed) delay(3000)
                // Só limpa se nenhum flush novo começou entretanto.
                if (run == emptyTrashRun) {
                    _state.update { it.copy(emptyingProgress = 0, emptyingDeleted = 0, emptyingTotal = 0, emptyingDone = false) }
                }
            }
        }
    }

    // Recarga silenciosa (sem skeleton) com o filtro/ordem atuais e as stats do
    // cabeçalho; os itens apagados desaparecem por trás do blur.
    private suspend fun reloadTrashItems() {
        if (_state.value.mode != BrowseMode.Trash) return
        // Repõe as páginas que já estavam abertas (paginado, sem skeleton).
        loadPaged(BrowseMode.Trash, _state.value.currentPage.coerceAtLeast(1), silent = true)
    }

    // Duplo toque na estrela enquanto o pedido/refreshUser não termina: ignora.
    private val favouriteInFlight = mutableSetOf<String>()

    fun toggleFavourite(folder: BrowseItem.Folder) {
        if (!favouriteInFlight.add(folder.id)) return
        val isFavourite = isFavourite(folder)
        viewModelScope.launch {
            try {
                val result = if (isFavourite) favouritesRepository.remove(folder.id)
                else favouritesRepository.add(folder.id)
                result
                    .onSuccess {
                        val refreshed = sessionManager.refreshUser()
                        _state.update {
                            it.copy(toast = if (refreshed.isFailure) failText(refreshed.exceptionOrNull()!!)
                            else if (isFavourite) "Removido dos favoritos".tr() else "Adicionado aos favoritos".tr())
                        }
                    }
                    .onFailure { t -> _state.update { it.copy(toast = failText(t)) } }
            } finally {
                favouriteInFlight.remove(folder.id)
            }
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
                // A lista vem do utilizador: refresca-o (pull-to-refresh, após apagar/mover/renomear).
                // Uma lista nova chega pelo coletor de favouriteFolders; aqui só se monta quando
                // não muda (o coletor não emite) ou falha — então com a cache e o erro.
                BrowseMode.Favourites -> {
                    val before = favouriteFolders.value
                    val refreshed = sessionManager.refreshUser()
                    if (refreshed.isFailure || favouriteFolders.value == before) {
                        handleSimple(Result.success(favouriteFolders.value.toList<BrowseItem>()), mode)
                    }
                    refreshed.exceptionOrNull()?.let { t -> _state.update { it.copy(toast = failText(t)) } }
                }
                is BrowseMode.SearchResults -> handleSimple(repository.search(mode.query), mode)
                else -> loadPaged(mode, pagesToReload, silent)
            }
        }
    }

    // Carrega uma única página da fonte correspondente ao modo actual.
    // Devolve null para modos não paginados (Favoritos/Pesquisa).
    private suspend fun fetchPage(mode: BrowseMode, page: Int): Result<PagedItems>? = when (mode) {
        is BrowseMode.Folder -> repository.listFolder(mode.id, page = page, query = browseQuery())
        BrowseMode.Latest -> repository.listLatest(page = page)
        BrowseMode.Trash -> trashRepository.listPaged(browseQuery(), page = page)
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

    // Carregamento paginado preguiçoso: a abertura normal pede só a página 1; as
    // seguintes chegam à medida que o utilizador faz scroll (loadMore). Antes
    // carregava TODAS as páginas de imediato em cascata — dezenas de pedidos em
    // série ao abrir uma pasta grande, a causa principal do "lento a carregar".
    // Numa recarga silenciosa pedimos as primeiras `pages` páginas para repor o
    // que já estava visível, sem spinner e sem mexer no scroll.
    // Dono: nomes selados à sua chave → em claro para os membros (como na web).
    // Na raiz são as próprias Team Folders; dentro, a pasta aberta (o servidor
    // sobe até à raiz da equipa). Um de cada vez, depois de a lista aparecer.
    private fun clearTeamFolderNames(mode: BrowseMode.TeamFolder, items: List<BrowseItem>) {
        val ids = mode.id?.let { listOf(it) } ?: items.filterIsInstance<BrowseItem.Folder>().map { it.id }
        if (ids.isEmpty()) return
        // Dentro de uma equipa as subpastas partilham o throttle pela raiz (a
        // Team Folder na árvore de navegação; senão a própria pasta).
        val rootKey = mode.id?.let { id -> teamRootOf(id) ?: id }
        viewModelScope.launch {
            var cleared = 0
            for (id in ids) cleared += teamsRepository.clearEncryptedNames(id, rootKey ?: id)
            if (cleared > 0 && mode == _state.value.mode) loadCurrent(silent = true)
        }
    }

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
                if (mode is BrowseMode.TeamFolder) clearTeamFolderNames(mode, all)
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
        // Pastas pessoais e Partilhado comigo têm endpoint de fingerprint; evitar
        // interferir durante carregamentos ou enquanto o utilizador selecciona itens.
        if (s.isLoading || s.isLoadingMore || s.isRefreshing) return
        if (s.selectedIds.isNotEmpty()) return

        val signature = when (mode) {
            is BrowseMode.Folder -> repository.folderFingerprint(mode.id)
            // Edições/uploads de outros membros aparecem logo (modified, tamanho…).
            is BrowseMode.SharedWithMe -> teamsRepository.sharedWithMeFingerprint(mode.id)
            // Sem endpoint de fingerprint: assinatura da 1.ª página (links criados/revogados/expirados).
            BrowseMode.Shared -> repository.listShared(page = 1, query = browseQuery()).map { p ->
                "${p.total}|" + p.items.joinToString(",") { "${it.id}:${it.share?.token}:${it.share?.active}" }
            }
            else -> return
        }.getOrNull() ?: return
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
                    // Favoritos: ordem manual do servidor (favourite_folder.position), como no drawer e na web.
                    it.copy(items = if (mode == BrowseMode.Favourites) items else sorted(items), isLoading = false, isRefreshing = false, currentPage = 1, lastPage = 1)
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
        _state.update { it.copy(sortMode = mode, items = if (it.mode == BrowseMode.Favourites) it.items else sortedWith(it.items, mode)) }
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
        private const val RELOAD_MS = 1500L
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
