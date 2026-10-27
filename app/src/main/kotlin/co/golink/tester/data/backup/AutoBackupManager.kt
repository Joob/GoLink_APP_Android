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
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Progresso da execução actual do worker: itens processados / total e os bytes
 * correspondentes, para mostrar "X MB de Y MB" da execução inteira (e não só
 * dos ficheiros que ainda estão na lista em memória).
 */
data class BackupRunProgress(
    val done: Int = 0,
    val total: Int = 0,
    val doneBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AutoBackupPreferences,
    private val uploadManager: UploadManager,
    private val logger: AppLogger,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Um só worker de cada vez, seja qual for o pedido (periódico, tick ou
    // botão): têm nomes únicos diferentes e o WorkManager não os serializa
    // entre si. O segundo a chegar sai logo sem tocar nos cursores.
    private val runLock = Mutex()

    /** Há um worker a varrer/enviar neste momento. */
    val isRunning: Boolean get() = runLock.isLocked

    internal fun tryBeginRun(): Boolean = runLock.tryLock()

    internal fun finishRun() {
        if (runLock.isLocked) runLock.unlock()
    }

    // O ecrã calculava "X de Y" a partir da lista de tarefas em memória, que o
    // worker poda a cada lote (pruneFinishedBackups) — a contagem encolhia e
    // saltava. Este flow é a fonte de verdade: o total vem do scan inicial e
    // done avança uma vez por item processado (sucesso, conflito ou falha).
    private val _runProgress = MutableStateFlow(BackupRunProgress())
    val runProgress: StateFlow<BackupRunProgress> = _runProgress.asStateFlow()

    internal fun startRun(total: Int, totalBytes: Long = 0L) {
        _runProgress.value = BackupRunProgress(done = 0, total = total, doneBytes = 0L, totalBytes = totalBytes)
    }

    internal fun recordProcessed(bytes: Long = 0L) {
        _runProgress.update {
            it.copy(
                done = (it.done + 1).coerceAtMost(it.total),
                doneBytes = (it.doneBytes + bytes.coerceAtLeast(0L)).coerceAtMost(it.totalBytes),
            )
        }
    }

    internal fun endRun() {
        _runProgress.value = BackupRunProgress()
    }

    fun enable() {
        preferences.enabled = true
        // O servidor pode ainda ter "desligado" (pausa da web, ou este aparelho
        // desligado antes): o worker confirma o ligar antes de ler a flag.
        preferences.pendingServerEnable = true
        logger.log("AutoBackup", "Backup automático activado")
        // O runNow() arma a cadeia; o oneshot de 1 min do reschedule() era logo
        // substituído. A correr (runLock), o finally do worker agenda o tick.
        reschedule(armTick = false)
        runNow()
    }

    // "Tick" de ~1 minuto: o WorkManager periódico tem mínimo de 15 min, por
    // isso para verificar de minuto a minuto usamos uma cadeia de OneTimeWork
    // que se reagenda a si própria ao fim de cada execução (ver o worker). O
    // periódico de 30 min fica como rede de segurança se o Doze matar a cadeia.
    //
    // O tick e o "Fazer backup agora" partilham o nome único WORK_ONESHOT: o
    // tick usa KEEP (não perturba um já pendente); o botão usa REPLACE, mas só
    // quando nada está a correr (ver runNow). A exclusão entre workers é o
    // runLock.
    fun scheduleNextTick() {
        if (!preferences.enabled) return
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(TICK_MINUTES, TimeUnit.MINUTES)
            .setConstraints(buildConstraints())
            .build()
        workManager.enqueueUniqueWork(
            AutoBackupWorker.WORK_ONESHOT,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Agenda o próximo tick quando o worker [workId] sair de RUNNING. Chamado do
     * finally do próprio worker: aí o WORK_ONESHOT ainda está RUNNING e um
     * enqueue com KEEP era ignorado — a cadeia de 1 min morria ao fim do
     * primeiro tick e só sobrava o periódico de 30 min.
     */
    fun scheduleNextTickAfter(workId: UUID) {
        scope.launch {
            runCatching {
                workManager.getWorkInfoByIdFlow(workId).first { it == null || it.state != WorkInfo.State.RUNNING }
                scheduleNextTick()
            }.onFailure { logger.log("AutoBackup", "Reagendar tick falhou: ${it.message}") }
        }
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

    fun reschedule(armTick: Boolean = true) {
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
        // Re-arma a cadeia de 1 min com as novas condições. Um tick pendente
        // tem as condições antigas: substitui-o (a correr, o finally do worker
        // agenda o seguinte já com as novas).
        if (armTick && !isRunning) enqueueOneshot(buildConstraints(), delayMinutes = TICK_MINUTES)
    }

    /**
     * Corre já. [userInitiated] = botão "Fazer backup agora": basta haver rede
     * (o utilizador pediu explicitamente), mas respeita "só Wi-Fi". Activar,
     * "Procurar de novo" e "Tentar de novo" seguem todas as condições — antes
     * enviavam a galeria inteira por dados móveis/bateria.
     *
     * Nunca substitui um worker a correr: o REPLACE cancelava-o, mas os uploads
     * já entregues ao UploadManager continuavam e o novo worker voltava a
     * enviá-los (duplicados, o servidor não os deteta com E2E).
     */
    fun runNow(userInitiated: Boolean = false) {
        if (!preferences.enabled || isRunning) return
        val constraints = if (userInitiated) {
            Constraints.Builder()
                .setRequiredNetworkType(if (preferences.allowCellular) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .build()
        } else {
            buildConstraints()
        }
        enqueueOneshot(constraints, delayMinutes = 0L)
    }

    private fun enqueueOneshot(constraints: Constraints, delayMinutes: Long) {
        // The worker promotes itself to a foreground service inside doWork()
        // so it can keep uploading even when the app is backgrounded.
        //
        // We deliberately do NOT use setExpedited() — on Android 14+ that asks
        // the framework to start a `shortService` foreground service before
        // doWork() runs, and the system can throw ForegroundServiceStart-
        // NotAllowedException synchronously from background contexts. That
        // surfaces as an app crash. Plain OneTimeWork + REPLACE is dispatched
        // promptly enough for a user-initiated tap, and once doWork() calls
        // setForeground() we're protected from Doze.
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
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
        const val TICK_MINUTES = 1L
    }
}
