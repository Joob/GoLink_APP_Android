package co.golink.tester.data.calendar

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.golink.tester.MainActivity
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.domain.calendar.CalendarAlarm
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Alarmes do calendário (AlarmManager). O índice vive em EncryptedSharedPreferences
 * para o receiver (que corre sem a app aberta e sem a chave E2E) conseguir
 * mostrar o título e reagendar depois de reiniciar o telemóvel. Apagado no logout.
 */
class CalendarAlarmStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CalendarAlarm.serializer())

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "calendar_alarms",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    @Synchronized
    fun all(): List<CalendarAlarm> =
        runCatching { json.decodeFromString(serializer, prefs.getString(KEY, null) ?: "[]") }.getOrDefault(emptyList())

    @Synchronized
    private fun save(list: List<CalendarAlarm>) {
        // Keystore partido (ex.: depois de um restauro): perde-se o índice, não a app.
        runCatching { prefs.edit().putString(KEY, json.encodeToString(serializer, list)).apply() }
    }

    /** Substitui o índice: cancela os que saíram, agenda os novos/alterados. */
    @Synchronized
    fun replace(next: List<CalendarAlarm>) {
        val old = all().associateBy { it.id }
        val nextById = next.associateBy { it.id }
        old.values.filter { nextById[it.id] != it }.forEach(::cancel)
        next.filter { old[it.id] != it }.forEach(::schedule)
        save(next)
    }

    /** Depois de reiniciar: os alarmes do sistema perderam-se, o índice não. */
    @Synchronized
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        val future = all().filter { it.fireAt > now }
        future.forEach(::schedule)
        save(future)
    }

    @Synchronized
    fun clear() {
        all().forEach(::cancel)
        runCatching { prefs.edit().clear().apply() }
    }

    @Synchronized
    fun consume(id: String): CalendarAlarm? {
        val list = all()
        val hit = list.firstOrNull { it.id == id } ?: return null
        save(list.filterNot { it.id == id })
        return hit
    }

    private fun pending(id: String, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, CalendarAlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(alarm: CalendarAlarm) {
        val am = alarmManager ?: return
        val pi = pending(alarm.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        // Sem permissão de alarme exato (Android 12+ revogável): fica aproximado.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.fireAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.fireAt, pi)
        }
    }

    private fun cancel(alarm: CalendarAlarm) {
        pending(alarm.id, PendingIntent.FLAG_NO_CREATE)?.let { pi ->
            alarmManager?.cancel(pi)
            pi.cancel()
        }
    }

    fun notify(alarm: CalendarAlarm) {
        ensureChannel()
        val zone = java.time.ZoneId.systemDefault()
        val whenText = if (alarm.allDay) I18n.t("Todo o dia")
        else java.time.Instant.ofEpochMilli(alarm.startAt).atZone(zone).toLocalTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        val body = if (alarm.minutes == 0) I18n.t("a começar agora (%s)").format(whenText) else I18n.t("começa às %s").format(whenText)
        val open = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_EVENT, alarm.eventId)
                .putExtra(EXTRA_DATE, alarm.date),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(alarm.title.ifBlank { I18n.t("(sem título)") })
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify("cal-${alarm.id}", 0, n) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, I18n.t("Lembretes do calendário"), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    companion object {
        private const val KEY = "alarms"
        const val CHANNEL = "calendar_alerts"
        const val ACTION_FIRE = "co.golink.tester.CALENDAR_ALARM"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_EVENT = "calendar_event"
        const val EXTRA_DATE = "calendar_date"
    }
}

/** Dispara o lembrete; depois de reiniciar/atualizar a app volta a agendar. */
class CalendarAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // EncryptedSharedPreferences + Keystore são lentos: fora da main thread.
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                val store = CalendarAlarmStore(app)
                when (intent.action) {
                    CalendarAlarmStore.ACTION_FIRE -> intent.getStringExtra(CalendarAlarmStore.EXTRA_ID)
                        ?.let(store::consume)
                        ?.let(store::notify)
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> store.rescheduleAll()
                }
            } catch (_: Throwable) {
                // Sem índice legível não há nada a mostrar nem a reagendar.
            } finally {
                pending.finish()
            }
        }.start()
    }
}
