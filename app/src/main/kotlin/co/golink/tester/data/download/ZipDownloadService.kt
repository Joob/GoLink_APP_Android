package co.golink.tester.data.download

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service das transferências (ficheiros e zips). Num scope normal o
 * SO mata o processo quando o utilizador sai da app e o download morria a meio
 * (a entrada MediaStore ficava presa em IS_PENDING). Aqui cada transferência
 * tem a sua notificação com a % por bytes e um botão "Cancelar"; várias podem
 * correr ao mesmo tempo e o serviço só sai de foreground quando a última acaba.
 */
@AndroidEntryPoint
class ZipDownloadService : Service() {

    @Inject lateinit var downloader: FileDownloader
    @Inject lateinit var tracker: DownloadTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifManager: NotificationManager

    private val jobs = ConcurrentHashMap<Int, Job>()
    private val lastNotif = ConcurrentHashMap<Int, Notification>()
    private var foregroundId = 0
    @Volatile private var timedOut = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getIntExtra(EXTRA_JOB_ID, -1)
            jobs[id]?.cancel()
            // Sem trabalho nenhum (ex.: serviço recriado), não fica em foreground.
            if (jobs.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }

        val kind = intent?.getStringExtra(EXTRA_KIND)
        val name = intent?.getStringExtra(EXTRA_NAME) ?: "ficheiros.zip"
        val jobId = nextJobId.getAndIncrement()

        // Tem de chamar startForeground em <5s do arranque, ANTES de qualquer rede.
        val first = buildProgress(jobId, name, 0, 0)
        val started = synchronized(this) {
            lastNotif[jobId] = first
            if (foregroundId == 0) {
                startForegroundCompat(jobId, first).also { ok -> if (ok) foregroundId = jobId }
            } else {
                notifManager.notify(jobId, first)
                true
            }
        }
        // O sistema recusou o foreground (Android 15+: tempo de dataSync esgotado,
        // ou arranque em background): falha visível em vez de matar a app.
        if (!started) {
            lastNotif.remove(jobId)
            if (kind != null) {
                tracker.start(jobId, name, isZip = kind != KIND_FILE, encrypted = intent?.getBooleanExtra(EXTRA_ENCRYPTED, false) == true)
                tracker.finish(jobId, DownloadPhase.Failed, FGS_REFUSED_MESSAGE.tr())
            }
            postFinal(FGS_REFUSED_MESSAGE.tr(), success = false, title = "Falha na transferência".tr())
            if (jobs.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }

        if (kind == null) {
            finishJob(jobId)
            return START_NOT_STICKY
        }
        tracker.start(
            jobId, name, isZip = kind != KIND_FILE,
            encrypted = intent.getBooleanExtra(EXTRA_ENCRYPTED, false),
        )

        var lastUpdate = 0L
        val progress: (Long, Long) -> Unit = { loaded, total ->
            // A leitura é bloqueante (não vê o cancel da coroutine): o "Cancelar"
            // interrompe-a aqui, a cada bloco lido.
            if (jobs[jobId]?.isCancelled == true) throw kotlinx.coroutines.CancellationException("cancelado")
            tracker.progress(jobId, loaded, total)
            // O sistema descarta notificações acima de ~5/s — 2/s chega.
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 500) {
                lastUpdate = now
                val n = buildProgress(jobId, name, loaded, total)
                lastNotif[jobId] = n
                notifManager.notify(jobId, n)
            }
        }

        // LAZY: o job tem de estar no mapa antes de correr — um download que
        // falhava logo chamava finishJob antes da atribuição e ficava órfão.
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val outcome = when (kind) {
                    KIND_FILE -> downloader.runFileDownload(
                        id = intent.getStringExtra(EXTRA_FILE_ID).orEmpty(),
                        name = name,
                        basename = intent.getStringExtra(EXTRA_BASENAME),
                        encrypted = intent.getBooleanExtra(EXTRA_ENCRYPTED, false),
                        mimetype = intent.getStringExtra(EXTRA_MIME),
                        onProgress = progress,
                    )
                    else -> downloader.runZipDownload(
                        intent.getStringExtra(EXTRA_PAYLOAD).orEmpty(),
                        name,
                        intent.getStringExtra(EXTRA_TOKEN).orEmpty(),
                        progress,
                        onEncrypted = { tracker.markEncrypted(jobId) },
                    )
                }
                when (outcome) {
                    is FileDownloader.Outcome.Completed -> if (outcome.failed.isEmpty()) {
                        tracker.finish(jobId, DownloadPhase.Done, outcome.name)
                        postFinal(outcome.name, success = true, title = "Transferência concluída".tr())
                    } else {
                        val detail = "${outcome.name} — " + "%d ficheiro(s) não incluídos: %s".tr()
                            .format(outcome.failed.size, outcome.failed.take(5).joinToString(", "))
                        // O zip existe (Done), mas o painel mostra o que ficou de fora.
                        tracker.finish(jobId, DownloadPhase.Done, detail)
                        postFinal(detail, success = false, title = "Transferência incompleta".tr())
                    }
                    is FileDownloader.Outcome.Failed -> {
                        tracker.finish(jobId, DownloadPhase.Failed, outcome.message)
                        postFinal("$name — ${outcome.message}", success = false, title = "Falha na transferência".tr())
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                tracker.finish(jobId, DownloadPhase.Cancelled)
                postFinal(
                    name, success = false,
                    title = if (timedOut) co.golink.tester.ui.i18n.I18n.t("Transferência interrompida pelo sistema. Tenta de novo.")
                    else "Transferência cancelada".tr(),
                )
            } finally {
                finishJob(jobId)
            }
        }
        jobs[jobId] = job
        job.start()
        return START_NOT_STICKY
    }

    // Tira o job; se era o que segurava o foreground passa-o a outro, e sem
    // nenhum ativo sai de foreground e termina.
    private fun finishJob(jobId: Int) = synchronized(this) {
        jobs.remove(jobId)
        lastNotif.remove(jobId)
        if (jobs.isEmpty()) {
            foregroundId = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            notifManager.cancel(jobId)
            stopSelf()
        } else {
            if (foregroundId == jobId) {
                val next = jobs.keys.first()
                if (startForegroundCompat(next, lastNotif[next] ?: buildProgress(next, "", 0, 0))) foregroundId = next
            }
            notifManager.cancel(jobId)
        }
    }

    // Android 15+: o tipo dataSync tem limite de 6 h por 24 h. Se o serviço não parar
    // logo, o sistema lança ForegroundServiceDidNotStopInTimeException (crash).
    override fun onTimeout(startId: Int, fgsType: Int) {
        timedOut = true
        jobs.values.forEach { it.cancel() }
        synchronized(this) {
            foregroundId = 0
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildProgress(jobId: Int, name: String, loaded: Long, total: Long): Notification {
        val percent = if (total > 0) ((loaded * 100) / total).toInt().coerceIn(0, 99) else -1
        val text = when {
            percent >= 0 -> "$percent% · ${co.golink.tester.ui.common.formatBytes(loaded)} / ${co.golink.tester.ui.common.formatBytes(total)}"
            loaded > 0 -> co.golink.tester.ui.common.formatBytes(loaded)
            else -> ""
        }
        val cancel = PendingIntent.getService(
            this,
            jobId,
            Intent(this, ZipDownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB_ID, jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Ecrã de bloqueio: sem o nome (decifrado) do ficheiro.
        val publicVersion = NotificationCompat.Builder(this, FileDownloader.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("A transferir…".tr())
            .apply { if (percent >= 0) setProgress(100, percent, false) else setProgress(0, 0, true) }
            .build()
        return NotificationCompat.Builder(this, FileDownloader.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentTitle(name.ifBlank { "A transferir…".tr() })
            .setContentText(text.ifBlank { "A transferir…".tr() })
            .setSubText(if (percent >= 0) "$percent%" else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar".tr(), cancel)
            .apply {
                if (percent >= 0) setProgress(100, percent, false) else setProgress(0, 0, true)
            }
            .build()
    }

    private fun postFinal(text: String, success: Boolean, title: String) {
        val icon = if (success) android.R.drawable.stat_sys_download_done
                   else android.R.drawable.stat_notify_error
        // Ecrã de bloqueio: só o título genérico, sem o nome do ficheiro.
        val publicVersion = NotificationCompat.Builder(this, FileDownloader.CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .build()
        val notif = NotificationCompat.Builder(this, FileDownloader.CHANNEL_ID)
            .setSmallIcon(icon)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notifManager.notify(finalNotifId.getAndIncrement(), notif)
    }

    /** false se o sistema recusar (ForegroundServiceStartNotAllowedException e afins). */
    private fun startForegroundCompat(id: Int, notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notification)
        }
        true
    } catch (e: Exception) {
        false
    }

    companion object {
        private val nextJobId = AtomicInteger(3000)
        private val finalNotifId = AtomicInteger(3900)

        private const val ACTION_CANCEL = "co.golink.tester.download.CANCEL"
        private const val EXTRA_JOB_ID = "job_id"
        private const val EXTRA_KIND = "kind"
        private const val KIND_FILE = "file"
        private const val KIND_ZIP = "zip"
        private const val EXTRA_PAYLOAD = "payload"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_FILE_ID = "file_id"
        private const val EXTRA_BASENAME = "basename"
        private const val EXTRA_ENCRYPTED = "encrypted"
        private const val EXTRA_MIME = "mime"
        private const val FGS_REFUSED_MESSAGE = "O Android não deixou continuar a transferência em segundo plano. Tenta de novo com a app aberta."

        /** Cancela uma transferência (botão do painel dentro da app). */
        fun cancel(context: Context, jobId: Int) {
            context.startService(
                Intent(context, ZipDownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB_ID, jobId),
            )
        }

        fun startZip(context: Context, payload: String, suggestedName: String, token: String) {
            val intent = Intent(context, ZipDownloadService::class.java).apply {
                putExtra(EXTRA_KIND, KIND_ZIP)
                putExtra(EXTRA_PAYLOAD, payload)
                putExtra(EXTRA_NAME, suggestedName)
                putExtra(EXTRA_TOKEN, token)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startFile(context: Context, id: String, name: String, basename: String?, encrypted: Boolean, mimetype: String?) {
            val intent = Intent(context, ZipDownloadService::class.java).apply {
                putExtra(EXTRA_KIND, KIND_FILE)
                putExtra(EXTRA_FILE_ID, id)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_BASENAME, basename)
                putExtra(EXTRA_ENCRYPTED, encrypted)
                putExtra(EXTRA_MIME, mimetype)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
