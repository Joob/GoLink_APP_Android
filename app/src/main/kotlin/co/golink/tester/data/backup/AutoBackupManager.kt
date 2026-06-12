package co.golink.tester.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.golink.tester.data.AppLogger
import co.golink.tester.data.upload.UploadManager
import co.golink.tester.data.upload.UploadTask
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** Progresso da execução actual do worker (itens processados / total). */
data class BackupRunProgress(val done: Int = 0, val total: Int = 0)

@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AutoBackupPreferences,
    private val uploadManager: UploadManager,
    private val logger: AppLogger,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    // O ecrã calculava "X de Y" a partir da lista de tarefas em memória, que o
    // worker poda a cada lote (pruneFinishedBackups) — a contagem encolhia e
    // saltava. Este flow é a fonte de verdade: o total vem do scan inicial e
    // done avança uma vez por item processado (sucesso, conflito ou falha).
    private val _runProgress = MutableStateFlow(BackupRunProgress())
    val runProgress: StateFlow<BackupRunProgress> = _runProgress.asStateFlow()

    internal fun startRun(total: Int) {
        _runProgress.value = BackupRunProgress(done = 0, total = total)
    }

    internal fun recordProcessed() {
        _runProgress.update { it.copy(done = (it.done + 1).coerceAtMost(it.total)) }
    }

    internal fun endRun() {
        _runProgress.value = BackupRunProgress()
    }

    fun enable() {
        preferences.enabled = true
        logger.log("AutoBackup", "Backup automático activado")
        reschedule()
        runNow()
    }

    fun disable() {
        preferences.enabled = false
        logger.log("AutoBackup", "Backup automático desactivado")
        workManager.cancelUniqueWork(AutoBackupWorker.WORK_NAME)
        workManager.cancelUniqueWork(AutoBackupWorker.WORK_ONESHOT)
        // Os uploads já entregues ao UploadManager correm num scope próprio e
        // sobrevivem ao cancelamento do worker — sem isto continuavam em
        // background e, ao reactivar, somavam-se à nova execução (contagens a
        // duplicar e conflitos).
        uploadManager.cancelBackups()
        BackupNotifications.cancelProgress(context)
    }

    fun reschedule() {
        if (!preferences.enabled) return
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            // Sem atraso inicial a primeira execução periódica dispara logo e
            // corre em paralelo com o oneshot do runNow() — dois workers a
            // varrer os mesmos cursores: uploads duplicados, progresso a
            // chegar a 100% e a "recomeçar com menos".
            .setInitialDelay(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(buildConstraints())
            .build()
        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun runNow() {
        if (!preferences.enabled) return
        // User-initiated run: any network is enough so it starts immediately
        // even on mobile data. The worker promotes itself to a foreground
        // service inside doWork() so it can keep uploading even when the app
        // is backgrounded.
        //
        // We deliberately do NOT use setExpedited() — on Android 14+ that asks
        // the framework to start a `shortService` foreground service before
        // doWork() runs, and the system can throw ForegroundServiceStart-
        // NotAllowedException synchronously from background contexts. That
        // surfaces as an app crash. Plain OneTimeWork + REPLACE is dispatched
        // promptly enough for a user-initiated tap, and once doWork() calls
        // setForeground() we're protected from Doze.
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            AutoBackupWorker.WORK_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun buildConstraints(): Constraints {
        // Wi-Fi e dados móveis são toggles independentes — ambos podem estar
        // activos. UNMETERED ≈ Wi-Fi, METERED ≈ dados móveis, CONNECTED = ambos.
        val networkType = when {
            preferences.allowWifi && preferences.allowCellular -> NetworkType.CONNECTED
            preferences.allowCellular -> NetworkType.METERED
            else -> NetworkType.UNMETERED
        }
        return Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresCharging(preferences.chargingOnly)
            .build()
    }

    /**
     * What the user sees as the "current run" state. We deliberately ignore
     * the periodic work entry — it sits ENQUEUED forever waiting for its 30‑min
     * trigger, and folding it in would keep the UI stuck on "Aguardar rede /
     * condições…". Instead we track:
     *
     *  - the WORK_ONESHOT entry (user pressed "Fazer backup agora" / just
     *    activated), and
     *  - whether the UploadManager currently has any active task — that way
     *    the screen flips to "A enviar…" the moment the first file starts
     *    uploading, even if the WorkInfo polling hasn't caught up yet.
     */
    val runState: Flow<RunState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(AutoBackupWorker.WORK_ONESHOT),
        uploadManager.tasks,
    ) { oneshot, tasks ->
        // Only count backup-tagged uploads — a user-initiated upload in the
        // browser must not flip the backup status to "Running".
        val hasActiveUpload = tasks.any {
            it.mobileBackup &&
                (it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued)
        }
        if (hasActiveUpload) return@combine RunState.Running
        val state = oneshot.firstOrNull()?.state
        when (state) {
            WorkInfo.State.RUNNING -> RunState.Running
            WorkInfo.State.ENQUEUED -> RunState.Waiting
            else -> RunState.Idle
        }
    }

    enum class RunState { Idle, Waiting, Running }

    private companion object {
        const val PERIOD_MINUTES = 30L
    }
}
