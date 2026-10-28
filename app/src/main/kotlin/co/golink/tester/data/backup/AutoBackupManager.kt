package co.golink.tester.data.backup

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import co.golink.tester.data.AppLogger
import co.golink.tester.data.upload.UploadManager
import co.golink.tester.data.upload.UploadTask
import co.golink.tester.domain.settings.MobileBackupSettingRequest
import co.golink.tester.network.SettingsApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.withLock

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
    private val settingsApi: SettingsApi,
    private val logger: AppLogger,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Um só worker de cada vez, seja qual for o pedido (periódico, tick ou
    // botão): têm nomes únicos diferentes e o WorkManager não os serializa
    // entre si. O segundo a chegar sai logo sem tocar nos cursores.
    private val runLock = Mutex()

    // POSTs do estado deste aparelho em série: um "desligar" atrasado (repetição
    // do pendingServerDisable) não chega ao servidor depois de um "ligar".
    private val serverLock = Mutex()

    // Uma sincronização de cada vez e no máximo uma por SYNC_MIN_INTERVAL_MS:
    // Definições e o ecrã do backup chamam-na ao abrir, um a seguir ao outro.
    private val syncLock = Mutex()
    @Volatile private var lastSyncAt = 0L

    /** Há um worker a varrer/enviar neste momento. */
    val isRunning: Boolean get() = runLock.isLocked

    internal fun tryBeginRun(): Boolean = runLock.tryLock()

    internal fun finishRun() {
        if (runLock.isLocked) runLock.unlock()
    }

    // Há um "Fazer backup agora" à espera (ENQUEUED). O reschedule() de uma
    // mudança de definições substituía-o (REPLACE) por um tick de 1 min com
    // "só a carregar" — o backup pedido nunca corria. Volatile: é limpo pelo
    // worker quando arranca.
    @Volatile private var pendingUserRun = false

    internal fun userRunStarted() { pendingUserRun = false }

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
        pendingUserRun = false
        preferences.devicePaused = false
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
        // tem as condições antigas: substitui-o (a correr, o worker verifica as
        // condições antes de cada ficheiro e o finally agenda o seguinte já com
        // as novas). Um "Fazer backup agora" pendente continua a sê-lo, só com
        // a nova regra de rede.
        if (armTick && !isRunning) {
            if (pendingUserRun) runNow(userInitiated = true)
            else enqueueOneshot(buildConstraints(), delayMinutes = TICK_MINUTES)
        }
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
        // Um "Fazer backup agora" pendente não perde as suas condições por
        // outro pedido (activar, "Tentar de novo") que o substitui.
        val user = userInitiated || pendingUserRun
        pendingUserRun = user
        enqueueOneshot(buildConstraints(requireCharging = !user), delayMinutes = 0L, userInitiated = user)
    }

    private fun enqueueOneshot(constraints: Constraints, delayMinutes: Long, userInitiated: Boolean = false) {
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
            .setInputData(workDataOf(AutoBackupWorker.KEY_USER_INITIATED to userInitiated))
            .build()
        workManager.enqueueUniqueWork(
            AutoBackupWorker.WORK_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    // Wi-Fi e dados móveis são toggles independentes — ambos podem estar
    // activos. UNMETERED ≈ Wi-Fi, METERED ≈ dados móveis, CONNECTED = ambos.
    // Única fonte da regra de rede: constraints e verificação a meio da execução.
    private fun networkType(): NetworkType = when {
        preferences.allowWifi && preferences.allowCellular -> NetworkType.CONNECTED
        preferences.allowCellular -> NetworkType.METERED
        else -> NetworkType.UNMETERED
    }

    private fun buildConstraints(requireCharging: Boolean = true): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(networkType())
            .setRequiresCharging(requireCharging && preferences.chargingOnly)
            .build()

    /**
     * As condições actuais ainda deixam enviar? As constraints do WorkManager
     * só contam ao arrancar: mudar para "só Wi-Fi" ou "só a carregar" a meio
     * deixava o worker a enviar a galeria por dados móveis/bateria. O worker
     * chama isto antes de cada ficheiro. [userInitiated] ignora o carregamento,
     * tal como as constraints do botão.
     */
    internal fun conditionsMet(userInitiated: Boolean): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return false
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val networkOk = when (networkType()) {
            NetworkType.METERED -> !unmetered
            NetworkType.UNMETERED -> unmetered
            else -> true
        }
        if (!networkOk) return false
        if (userInitiated || !preferences.chargingOnly) return true
        // EXTRA_PLUGGED como o WorkManager (isCharging é false com a bateria cheia).
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    /**
     * Acerta o estado local com o servidor: repete a pausa deste aparelho que
     * ficou por confirmar, desliga se a web pausou a conta e guarda a pausa só
     * deste telemóvel (device_paused) para o ecrã a mostrar.
     */
    suspend fun syncServerState() {
        if (System.currentTimeMillis() - lastSyncAt < SYNC_MIN_INTERVAL_MS) return
        if (!syncLock.tryLock()) return
        try {
            lastSyncAt = System.currentTimeMillis()
            if (preferences.pendingServerDisable && !preferences.enabled) pushDeviceState()
            if (!preferences.enabled || preferences.pendingServerEnable) return
            val response = settingsApi.getMobileBackupSetting()
            val body = response.body()
            if (!response.isSuccessful || body == null || preferences.pendingServerEnable) return
            if (!body.mobile_backup_enabled) {
                preferences.lastError = ACCOUNT_PAUSED_ERROR
                disable()
                return
            }
            setDevicePaused(body.device_paused)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            logger.log("AutoBackup", "Sincronizar estado falhou: ${t.message}")
        } finally {
            syncLock.unlock()
        }
    }

    /**
     * Envia ao servidor o estado local deste aparelho (ligado/desligado) lido
     * já dentro do lock, e só limpa o pendente se o utilizador não mudou de
     * ideias entretanto. Devolve se o POST foi aceite.
     */
    suspend fun pushDeviceState(): Boolean = serverLock.withLock {
        val enabled = preferences.enabled
        val ok = try {
            settingsApi.setMobileBackupEnabled(MobileBackupSettingRequest(enabled = enabled, device = true)).isSuccessful
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            logger.log("AutoBackup", "Enviar estado do aparelho falhou: ${t.message}")
            false
        }
        if (ok && preferences.enabled == enabled) {
            if (enabled) preferences.pendingServerEnable = false else preferences.pendingServerDisable = false
        }
        ok
    }

    internal fun setDevicePaused(paused: Boolean) {
        val was = preferences.devicePaused
        preferences.devicePaused = paused
        // Retomado na web: o aviso de pausa deixado pelo worker já não se aplica.
        if (was && !paused && preferences.lastError == DEVICE_PAUSED_ERROR) preferences.clearError()
    }

    /** "Retomar" no telemóvel: levanta a pausa deste aparelho e corre já. */
    suspend fun resumeDevice(): Boolean {
        if (!preferences.enabled) return false
        val ok = pushDeviceState()
        if (ok) {
            setDevicePaused(false)
            preferences.clearError()
            runNow(userInitiated = true)
        }
        return ok
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

    companion object {
        private const val PERIOD_MINUTES = 30L
        private const val TICK_MINUTES = 1L
        private const val SYNC_MIN_INTERVAL_MS = 5_000L
        internal const val DEVICE_PAUSED_ERROR = "Backup deste telemóvel em pausa na Web."
        internal const val ACCOUNT_PAUSED_ERROR = "Backups automáticos pausados a partir da Web."
        // Erros passageiros (o próximo tick resolve): não pedem atenção no cartão.
        internal const val OFFLINE_ERROR = "Sem ligação ao servidor"
        internal const val FAILED_FILES_ERROR = "ficheiro(s) falharam"
    }
}
