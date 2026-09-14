package co.golink.tester.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service para o zip de pastas / múltiplos ficheiros E2E. O decrypt +
 * zip demora e num scope normal o SO mata o processo se o utilizador sair da app
 * (a entrada MediaStore fica presa em IS_PENDING → o ficheiro nunca aparece).
 * Aqui o trabalho corre com uma notificação persistente ("A transferir…" com
 * progresso), tal como os single files, e o processo é mantido vivo até acabar.
 */
@AndroidEntryPoint
class ZipDownloadService : Service() {

    @Inject lateinit var downloader: FileDownloader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val payload = intent?.getStringExtra(EXTRA_PAYLOAD)
        val name = intent?.getStringExtra(EXTRA_NAME) ?: "ficheiros.zip"
        val token = intent?.getStringExtra(EXTRA_TOKEN) ?: ""

        // Tem de chamar startForeground em <5s do arranque, ANTES de qualquer rede.
        startForegroundCompat(buildProgress(name, 0, 0))

        if (payload.isNullOrBlank()) {
            stopSelfCompat(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            val outcome = downloader.runZipDownload(payload, name, token) { done, total ->
                notifManager.notify(FGS_NOTIF_ID, buildProgress(name, done, total))
            }
            when (outcome) {
                is FileDownloader.ZipOutcome.Completed ->
                    postFinal(outcome.name, success = true, title = "Transferência concluída")
                is FileDownloader.ZipOutcome.Failed ->
                    postFinal("$name — ${outcome.message}", success = false, title = "Falha na transferência")
                // O DownloadManager mostra a sua própria notificação.
                FileDownloader.ZipOutcome.ServerZipEnqueued -> Unit
            }
            stopSelfCompat(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildProgress(name: String, done: Int, total: Int): Notification {
        val text = if (total > 0) "$name ($done/$total)" else name
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("A transferir…")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (total > 0) setProgress(total, done, false) else setProgress(0, 0, true)
            }
            .build()
    }

    private fun postFinal(text: String, success: Boolean, title: String) {
        val icon = if (success) android.R.drawable.stat_sys_download_done
                   else android.R.drawable.stat_notify_error
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notifManager.notify(finalNotifId.getAndIncrement(), notif)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FGS_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FGS_NOTIF_ID, notification)
        }
    }

    private fun stopSelfCompat(startId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf(startId)
    }

    companion object {
        private const val CHANNEL_ID = "golink_downloads"
        private const val FGS_NOTIF_ID = 3000
        private val finalNotifId = AtomicInteger(3100)

        private const val EXTRA_PAYLOAD = "payload"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_TOKEN = "token"

        fun start(context: Context, payload: String, suggestedName: String, token: String) {
            val intent = Intent(context, ZipDownloadService::class.java).apply {
                putExtra(EXTRA_PAYLOAD, payload)
                putExtra(EXTRA_NAME, suggestedName)
                putExtra(EXTRA_TOKEN, token)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
