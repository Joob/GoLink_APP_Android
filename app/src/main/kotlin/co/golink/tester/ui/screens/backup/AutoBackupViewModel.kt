package co.golink.tester.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.backup.AutoBackupManager
import co.golink.tester.data.backup.AutoBackupPreferences
import co.golink.tester.data.backup.AutoBackupState
import co.golink.tester.data.backup.BackupRunProgress
import co.golink.tester.data.upload.UploadManager
import co.golink.tester.data.upload.UploadTask
import co.golink.tester.domain.settings.MobileBackupSettingRequest
import co.golink.tester.network.SettingsApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AutoBackupViewModel @Inject constructor(
    private val preferences: AutoBackupPreferences,
    private val manager: AutoBackupManager,
    private val settingsApi: SettingsApi,
    private val uploadManager: UploadManager,
) : ViewModel() {

    val state: StateFlow<AutoBackupState> = preferences.state

    val uploadTasks: StateFlow<List<UploadTask>> = uploadManager.tasks

    val runState: StateFlow<AutoBackupManager.RunState> = manager.runState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AutoBackupManager.RunState.Idle)

    val runProgress: StateFlow<BackupRunProgress> = manager.runProgress

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

    fun dismissError() = preferences.clearError()

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
}
