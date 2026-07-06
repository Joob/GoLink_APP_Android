package co.golink.tester.ui.screens.backup

import co.golink.tester.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.backup.AutoBackupManager
import co.golink.tester.data.backup.AutoBackupPreferences
import co.golink.tester.data.backup.AutoBackupState
import co.golink.tester.data.backup.BackupCollection
import co.golink.tester.data.backup.BackupFolder
import co.golink.tester.data.backup.BackupRunProgress
import co.golink.tester.data.backup.MediaScanner
import co.golink.tester.data.upload.UploadManager
import co.golink.tester.data.upload.UploadTask
import co.golink.tester.domain.settings.MobileBackupSettingRequest
import co.golink.tester.network.SettingsApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AutoBackupViewModel @Inject constructor(
    private val preferences: AutoBackupPreferences,
    private val manager: AutoBackupManager,
    private val settingsApi: SettingsApi,
    private val uploadManager: UploadManager,
    private val mediaScanner: MediaScanner,
) : ViewModel() {

    val state: StateFlow<AutoBackupState> = preferences.state

    // Pastas do dispositivo por colecção (Camera, Screenshots, …). Carregadas
    // sob procura quando o utilizador expande uma colecção.
    private val _folders = MutableStateFlow<Map<BackupCollection, List<BackupFolder>>>(emptyMap())
    val folders: StateFlow<Map<BackupCollection, List<BackupFolder>>> = _folders.asStateFlow()

    // Colecções cuja lista de pastas está a ser lida do MediaStore.
    private val _loadingFolders = MutableStateFlow<Set<BackupCollection>>(emptySet())
    val loadingFolders: StateFlow<Set<BackupCollection>> = _loadingFolders.asStateFlow()

    fun loadFolders(collection: BackupCollection, force: Boolean = false) {
        if (!force && _folders.value.containsKey(collection)) return
        if (collection in _loadingFolders.value) return
        _loadingFolders.update { it + collection }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { mediaScanner.listFolders(collection) }
            _folders.update { it + (collection to list) }
            _loadingFolders.update { it - collection }
        }
    }

    fun toggleFolder(collection: BackupCollection, folder: String, selected: Boolean) {
        preferences.setFolderSelected(collection, folder, selected)
    }

    fun setAllFolders(collection: BackupCollection, selected: Boolean) {
        val all = _folders.value[collection].orEmpty().map { it.name }.toSet()
        preferences.setFolders(collection, if (selected) all else emptySet())
    }

    // Already-backed-up files come back from the server as instant "conflicts",
    // so during a run the task list can change dozens of times per second —
    // which the UI rendered as a rapidly flickering/scrolling list. Sampling
    // smooths that to a steady cadence without touching the upload logic; the
    // worker still uploads at full speed in the background.
    @OptIn(FlowPreview::class)
    val uploadTasks: StateFlow<List<UploadTask>> = uploadManager.tasks
        .sample(UI_SAMPLE_MS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, uploadManager.tasks.value)

    val runState: StateFlow<AutoBackupManager.RunState> = manager.runState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AutoBackupManager.RunState.Idle)

    @OptIn(FlowPreview::class)
    val runProgress: StateFlow<BackupRunProgress> = manager.runProgress
        .sample(UI_SAMPLE_MS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, manager.runProgress.value)

    fun enable() {
        // Guard every call: WorkManager.enqueue* and SharedPreferences.edit
        // are normally safe, but on some OEM builds (Samsung restricted mode,
        // Xiaomi MIUI, custom Android 14 ROMs) they can throw synchronously —
        // and an uncaught exception here would tear down the activity.
        runCatching {
            preferences.clearError()
            manager.enable()
        }.onFailure {
            preferences.lastError = "Não foi possível activar o backup: ${it.message ?: it::class.simpleName}"
        }
        // Marca a flag como activa no servidor. Antes era o worker que enviava
        // true a cada execução, mas isso sobrepunha-se ao pause remoto da Web.
        // Agora o ligar é da responsabilidade exclusiva do dispositivo, aqui.
        viewModelScope.launch {
            runCatching { settingsApi.setMobileBackupEnabled(MobileBackupSettingRequest(true)) }
        }
    }

    fun disable() {
        runCatching { manager.disable() }
        viewModelScope.launch {
            runCatching { settingsApi.setMobileBackupEnabled(MobileBackupSettingRequest(false)) }
        }
    }

    fun runNow() {
        // Refuse to disturb a worker that's already running. Pressing "Tentar
        // de novo" / "Fazer backup agora" while a worker is mid-upload would
        // trigger ExistingWorkPolicy.REPLACE, which cancels the live coroutine
        // — and the rapid cancel-then-restart cascade has historically been the
        // source of "App goes down" reports here. If something is already
        // running, let it finish; the UI will reflect progress as it goes.
        if (runState.value == AutoBackupManager.RunState.Running) return
        runCatching {
            preferences.clearError()
            manager.runNow()
        }.onFailure {
            preferences.lastError = "Não foi possível iniciar o backup: ${it.message ?: it::class.simpleName}"
        }
    }

    // "Backup Now": corrida incremental. Como o cursor é POR PASTA, uma pasta
    // acabada de seleccionar arranca do zero e é enviada por inteiro, enquanto as
    // já sincronizadas continuam do seu cursor. Deixámos de fazer resetCursors()
    // aqui — isso re-verificava TODAS as pastas contra o servidor a cada clique,
    // o que demorava imenso. Para forçar a re-verificação completa existe o cartão
    // "Voltar a verificar a galeria".
    fun backupNow() {
        if (runState.value == AutoBackupManager.RunState.Running) return
        runCatching {
            preferences.clearError()
            manager.runNow()
        }.onFailure {
            preferences.lastError = "Não foi possível iniciar o backup: ${it.message ?: it::class.simpleName}"
        }
    }

    fun dismissError() = preferences.clearError()

    // Sincroniza o estado com o servidor: se a Web carregou em "Pause backups"
    // (mobile_backup_enabled=false) desligamos localmente — o ecrã volta logo ao
    // cartão "Activar backup". Chamado ao abrir/retomar o ecrã e em polling curto.
    fun syncServerEnabledState() {
        if (!preferences.enabled) return
        viewModelScope.launch {
            runCatching {
                val response = settingsApi.getMobileBackupSetting()
                if (response.isSuccessful && response.body()?.mobile_backup_enabled == false) {
                    manager.disable()
                }
            }
        }
    }

    private var syncJob: kotlinx.coroutines.Job? = null

    /** Enquanto o ecrã está visível, sonda o estado remoto de ~8 em 8 s. */
    fun startServerStateSync() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            while (isActive) {
                syncServerEnabledState()
                kotlinx.coroutines.delay(8_000)
            }
        }
    }

    fun stopServerStateSync() {
        syncJob?.cancel()
        syncJob = null
    }

    // Wi-Fi e dados móveis são independentes, mas pelo menos um tem de ficar
    // activo — desligar o último é ignorado (o switch volta atrás sozinho
    // porque o estado não muda).
    fun setAllowWifi(value: Boolean) {
        if (!value && !preferences.allowCellular) return
        preferences.allowWifi = value
        manager.reschedule()
    }

    fun setAllowCellular(value: Boolean) {
        if (!value && !preferences.allowWifi) return
        preferences.allowCellular = value
        manager.reschedule()
    }

    fun setChargingOnly(value: Boolean) {
        preferences.chargingOnly = value
        manager.reschedule()
    }

    fun setIncludeImages(value: Boolean) {
        preferences.includeImages = value
    }

    fun setIncludeVideos(value: Boolean) {
        preferences.includeVideos = value
    }

    fun setIncludeAudios(value: Boolean) {
        preferences.includeAudios = value
    }

    fun setIncludeDocuments(value: Boolean) {
        preferences.includeDocuments = value
    }

    fun setIncludeDownloads(value: Boolean) {
        preferences.includeDownloads = value
    }

    fun resetCursors() {
        preferences.resetCursors()
        manager.runNow()
    }

    fun cancelTask(id: String) = uploadManager.cancel(id)
    fun retryTask(id: String) = uploadManager.retry(id)
    fun retryFailed() = uploadManager.retryFailed()
    fun dismissTasks() = uploadManager.clearFinished()
    fun overwriteTask(id: String) = uploadManager.overwriteConflict(id)
    fun skipTask(id: String) = uploadManager.skipConflict(id)

    private companion object {
        // UI refresh cadence for the live upload list/progress. ~400ms is fast
        // enough to feel live but slow enough to stop the flicker.
        const val UI_SAMPLE_MS = 400L
    }
}
