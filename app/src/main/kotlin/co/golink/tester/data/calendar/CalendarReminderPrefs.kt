package co.golink.tester.data.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.golink.tester.MainActivity
import co.golink.tester.ui.i18n.I18n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferências dos lembretes no aparelho (sem conteúdo E2E, por isso em claro):
 * - mostrar o título no ecrã de bloqueio (desligado por omissão);
 * - "lembretes em pausa": o worker não conseguiu a chave E2E, por isso os alarmes
 *   só cobrem até [coverageEnd] (última sync boa + janela). Aviso no Calendário e,
 *   perto do fim da cobertura, no máximo uma notificação silenciosa por 24 h.
 * Apagadas no logout (CalendarAlarmStore.clear).
 */
object CalendarReminderPrefs {
    private const val FILE = "calendar_reminders"
    private const val KEY_LOCK_TITLE = "lock_screen_title"
    private const val KEY_LAST_OK = "last_ok_at"
    private const val KEY_HAD_ALARMS = "had_alarms"
    private const val KEY_PAUSED_SINCE = "paused_since"
    private const val KEY_LAST_NOTICE = "pause_notice_at"

    private const val DAY_MS = 24 * 3600_000L

    /** Igual a CalendarAlarmScheduler.WINDOW_S: até onde a última sync agendou. */
    internal const val WINDOW_MS = 7 * DAY_MS

    const val STATUS_CHANNEL = "calendar_status"
    private const val PAUSE_TAG = "cal-paused"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Título no ecrã de bloqueio ----

    fun showTitleOnLockScreen(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_LOCK_TITLE, false) }.getOrDefault(false)

    fun setShowTitleOnLockScreen(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_TITLE, value).apply()
    }

    // ---- Lembretes em pausa ----

    private val _paused = MutableStateFlow<Long?>(null)
    @Volatile private var loaded = false

    /** Fim da cobertura dos alarmes agendados enquanto em pausa; null = não está em pausa. */
    fun pausedUntil(context: Context): StateFlow<Long?> {
        if (!loaded) {
            loaded = true
            _paused.value = computePausedUntil(prefs(context))
        }
        return _paused.asStateFlow()
    }

    private fun computePausedUntil(p: SharedPreferences): Long? {
        if (p.getLong(KEY_PAUSED_SINCE, 0L) == 0L || !p.getBoolean(KEY_HAD_ALARMS, false)) return null
        val lastOk = p.getLong(KEY_LAST_OK, 0L).takeIf { it > 0 } ?: return null
        return lastOk + WINDOW_MS
    }

    /** Sync com a chave: os alarmes estão em dia. */
    fun onSynced(context: Context, hadAlarms: Boolean) {
        val p = prefs(context)
        p.edit()
            .putLong(KEY_LAST_OK, System.currentTimeMillis())
            .putBoolean(KEY_HAD_ALARMS, hadAlarms)
            .remove(KEY_PAUSED_SINCE)
            .remove(KEY_LAST_NOTICE)
            .apply()
        loaded = true
        _paused.value = null
        runCatching { NotificationManagerCompat.from(context).cancel(PAUSE_TAG, 0) }
    }

    /**
     * O worker não teve a chave (sem "manter desbloqueado" e sem a app aberta).
     * Só conta para quem tem lembretes; a notificação vem nas últimas 24 h da cobertura.
     */
    fun onKeyMissing(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_HAD_ALARMS, false) || p.getLong(KEY_LAST_OK, 0L) == 0L) return
        val now = System.currentTimeMillis()
        if (p.getLong(KEY_PAUSED_SINCE, 0L) == 0L) p.edit().putLong(KEY_PAUSED_SINCE, now).apply()
        loaded = true
        _paused.value = computePausedUntil(p)
        val end = p.getLong(KEY_LAST_OK, 0L) + WINDOW_MS
        if (now >= end - DAY_MS && now - p.getLong(KEY_LAST_NOTICE, 0L) >= DAY_MS) {
            if (notifyPaused(context)) p.edit().putLong(KEY_LAST_NOTICE, now).apply()
        }
    }

    fun clear(context: Context) {
        runCatching { prefs(context).edit().clear().apply() }
        loaded = true
        _paused.value = null
        runCatching { NotificationManagerCompat.from(context).cancel(PAUSE_TAG, 0) }
    }

    private fun notifyPaused(context: Context): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager != null && manager.getNotificationChannel(STATUS_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(STATUS_CHANNEL, I18n.t("Estado dos lembretes"), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            PAUSE_TAG.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(CalendarAlarmStore.EXTRA_OPEN_CALENDAR, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(I18n.t("Lembretes do calendário em pausa"))
            .setContentText(I18n.t("Abre o Calendário para voltares a receber os lembretes."))
            .setStyle(NotificationCompat.BigTextStyle().bigText(I18n.t("Abre o Calendário para voltares a receber os lembretes.")))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return runCatching { nm.notify(PAUSE_TAG, 0, n) }.isSuccess
    }
}
