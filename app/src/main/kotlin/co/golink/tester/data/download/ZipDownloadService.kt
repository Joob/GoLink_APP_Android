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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifManager: NotificationManager

    private val jobs = ConcurrentHashMap<Int, Job>()
    private val lastNotif = ConcurrentHashMap<Int, Notification>()
    private var foregroundId = 0

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
        synchronized(this) {
            lastNotif[jobId] = first
            if (foregroundId == 0) {
                startForegroundCompat(jobId, first)
                foregroundId = jobId
            } else {
                notifManager.notify(jobId, first)
            }
        }

        if (kind == null) {
            finishJob(jobId)
            return START_NOT_STICKY
        }

        var lastUpdate = 0L
        val progress: (Long, Long) -> Unit = { loaded, total ->
            // A leitura é bloqueante (não vê o cancel da coroutine): o "Cancelar"
            // interrompe-a aqui, a cada bloco lido.
            if (jobs[jobId]?.isCancelled == true) throw kotlinx.coroutines.CancellationException("cancelado")
            // O sistema descarta notificações acima de ~5/s — 2/s chega.
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 500) {
                lastUpdate = now
                val n = buildProgress(jobId, name, loaded, total)
                lastNotif[jobId] = n
                notifManager.notify(jobId, n)
            }
        }

        jobs[jobId] = scope.launch {
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
                    )
                }
                when (outcome) {
                    is FileDownloader.Outcome.Completed -> if (outcome.failed.isEmpty()) {
                        postFinal(outcome.name, success = true, title = "Transferência concluída")
                    } else {
                        postFinal(
                            "${outcome.name} — ${outcome.failed.size} ficheiro(s) não incluídos: " +
                                outcome.failed.take(5).joinToString(", "),
                            success = false,
                            title = "Transferência incompleta",
                        )
                    }
                    is FileDownloader.Outcome.Failed ->
                        postFinal("$name — ${outcome.message}", success = false, title = "Falha na transferência")
                    // O DownloadManager mostra a sua própria notificação.
                    FileDownloader.Outcome.ServerZipEnqueued -> Unit
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                postFinal(name, success = false, title = "Transferência cancelada")
            } finally {
                finishJob(jobId)
            }
        }
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
                startForegroundCompat(next, lastNotif[next] ?: buildProgress(next, "", 0, 0))
                foregroundId = next
            }
            notifManager.cancel(jobId)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildProgress(jobId: Int, name: String, loaded: Long, total: Long): Notification {
        val percent = if (total > 0) ((loaded * 100) / total).toInt().coerceIn(0, 99) else -1
        val text = when {
            percent >= 0 -> "$percent% · ${formatBytes(loaded)} / ${formatBytes(total)}"
            loaded > 0 -> formatBytes(loaded)
            else -> ""
        }
        val cancel = PendingIntent.getService(
            this,
            jobId,
            Intent(this, ZipDownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB_ID, jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, FileDownloader.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(name.ifBlank { "A transferir…" })
            .setContentText(text.ifBlank { "A transferir…" })
            .setSubText(if (percent >= 0) "$percent%" else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancel)
            .apply {
                if (percent >= 0) setProgress(100, percent, false) else setProgress(0, 0, true)
            }
            .build()
    }

    private fun postFinal(text: String, success: Boolean, title: String) {
        val icon = if (success) android.R.drawable.stat_sys_download_done
                   else android.R.drawable.stat_notify_error
        val notif = NotificationCompat.Builder(this, FileDownloader.CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notifManager.notify(finalNotifId.getAndIncrement(), notif)
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notification)
        }
    }

    private fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> "%.0f KB".format(b / (1L shl 10).toDouble())
        else -> "$b B"
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
