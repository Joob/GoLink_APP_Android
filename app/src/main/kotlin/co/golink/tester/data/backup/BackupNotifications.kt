package co.golink.tester.data.backup

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import co.golink.tester.ui.i18n.tr

object BackupNotifications {
    const val CHANNEL_PROGRESS = "auto_backup_progress"
    const val CHANNEL_RESULT = "auto_backup_result"

    const val PROGRESS_NOTIFICATION_ID = 7001
    const val RESULT_NOTIFICATION_ID = 7002
    // ID próprio: o aviso de E2E trancada não pode ser substituído pelo resultado
    // de um backup anterior nem desaparecer com ele — fica até o unlock.
    const val LOCKED_NOTIFICATION_ID = 7003

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_PROGRESS) == null) {
            val progress = NotificationChannel(
                CHANNEL_PROGRESS,
                "Backup automático",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mostra o progresso do backup automático em curso."
                setShowBadge(false)
            }
            manager.createNotificationChannel(progress)
        }
        if (manager.getNotificationChannel(CHANNEL_RESULT) == null) {
            val result = NotificationChannel(
                CHANNEL_RESULT,
                "Resultado do backup",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Avisa quando o backup terminou ou falhou."
            }
            manager.createNotificationChannel(result)
        }
    }

    fun buildProgress(
        context: Context,
        title: String,
        text: String,
        indeterminate: Boolean,
        progress: Int = 0,
        max: Int = 0,
    ): Notification {
        ensureChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent(context))
        if (indeterminate) {
            builder.setProgress(0, 0, true)
        } else if (max > 0) {
            builder.setProgress(max, progress.coerceIn(0, max), false)
        }
        return builder.build()
    }

    // A ForegroundInfo de 2 argumentos deixa foregroundServiceType = 0 (NONE)
    // e o WorkManager passa esse valor tal-e-qual ao startForeground() de
    // 3 argumentos no API 29+ — nunca cai no tipo do manifest. No Android 14+
    // (targetSdk 34+) o tipo NONE faz o framework lançar
    // MissingForegroundServiceTypeException na main thread do
    // SystemForegroundService, matando o processo inteiro (era a causa dos
    // crashes ao entrar na app / activar o backup). Passamos sempre dataSync,
    // que corresponde ao <service> do manifest e à permissão
    // FOREGROUND_SERVICE_DATA_SYNC.
    fun foregroundInfo(context: Context, notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }

    // A permissão é verificada em canPostNotifications(), mas o lint não segue
    // a chamada através do helper.
    @SuppressLint("MissingPermission")
    fun showResult(
        context: Context,
        title: String,
        text: String,
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, notification)
    }

    fun cancelProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
    }

    /**
     * Backup adiado por a encriptação estar trancada. Toca na notificação para
     * abrir a app e introduzir a passphrase. Não é `ongoing` (o utilizador pode
     * dispensá-la) mas repõe-se no tick seguinte enquanto continuar trancada.
     */
    @SuppressLint("MissingPermission") // ver nota em showResult()
    fun showE2ELocked(context: Context) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val title = "Backup automático em pausa".tr()
        val text = "Desbloqueia a passphrase de encriptação para o backup automático continuar.".tr()
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(LOCKED_NOTIFICATION_ID, notification)
    }

    /** Chamada após o unlock: o aviso deixou de fazer sentido. */
    fun cancelE2ELocked(context: Context) {
        NotificationManagerCompat.from(context).cancel(LOCKED_NOTIFICATION_ID)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, 0, launchIntent, flags)
    }
}
