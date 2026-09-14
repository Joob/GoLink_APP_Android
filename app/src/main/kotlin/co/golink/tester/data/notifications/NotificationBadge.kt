package co.golink.tester.data.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.golink.tester.ui.i18n.tr

/**
 * Badge com o número de notificações por ler no ícone da app.
 *
 * O Android não tem API para pintar o ícone do launcher directamente (ao
 * contrário do iOS): o badge é derivado das notificações activas da app. Por
 * isso publicamos UMA notificação-resumo com `setNumber()`, que o launcher usa
 * como contador. O aspecto final depende do launcher — Samsung/Xiaomi mostram o
 * número, o Pixel mostra só um ponto — e nada disto é garantido pelo framework.
 *
 * Canal com IMPORTANCE_LOW e silencioso: isto é um contador, não um alerta. Se
 * um dia houver push (FCM), esse sim deve alertar, num canal próprio.
 */
object NotificationBadge {
    const val CHANNEL_BADGE = "notifications_badge"
    const val BADGE_NOTIFICATION_ID = 7100

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_BADGE) != null) return
        val channel = NotificationChannel(
            CHANNEL_BADGE,
            "Notificações por ler".tr(),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Mostra o número de notificações por ler no ícone da app.".tr()
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** Publica (ou remove, se `count` for 0) o badge com o número por ler. */
    // A permissão é verificada em canPostNotifications(), mas o lint não segue
    // a chamada através do helper.
    @SuppressLint("MissingPermission")
    fun update(context: Context, count: Int) {
        if (count <= 0) {
            clear(context)
            return
        }
        if (!canPostNotifications(context)) return
        ensureChannel(context)
        val text = if (count == 1) {
            "Tens 1 notificação por ler.".tr()
        } else {
            "Tens %d notificações por ler.".tr().format(count)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_BADGE)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("GoLink")
            .setContentText(text)
            .setNumber(count) // é isto que o launcher lê para o badge
            .setAutoCancel(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(BADGE_NOTIFICATION_ID, notification)
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(BADGE_NOTIFICATION_ID)
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
