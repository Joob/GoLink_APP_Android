package co.golink.tester.data.calendar

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.calendar.CalendarAlarm
import co.golink.tester.domain.calendar.Recurrence
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lembretes no aparelho (espelho de services/calendarAlerts.js, mas com
 * AlarmManager para tocarem com a app fechada). Agenda os próximos 7 dias ao
 * desbloquear, quando um evento muda e de 6 em 6 h (worker). O sino do
 * servidor continua a chegar, com texto genérico.
 */
@Singleton
class CalendarAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: CalendarRepository,
    private val keys: E2EKeyManager,
) {
    private val store = CalendarAlarmStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @OptIn(FlowPreview::class)
    fun start() {
        // Logout (único lock): os títulos decifrados não podem ficar no aparelho.
        keys.addOnLock {
            store.clear()
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
        merge(keys.unlocked.filter { it }.map { }, repo.changes)
            .debounce(1_500)
            .onEach { runCatching { sync() } }
            .launchIn(scope)
    }

    /** false: falhou a rede/servidor (o worker volta a tentar); true: feito ou nada a fazer. */
    suspend fun sync(): Boolean = mutex.withLock {
        // Worker num processo novo: a chave só está em memória se "manter
        // desbloqueado" a conseguir repor (como o E2EGate ao abrir a app).
        if (!keys.isUnlocked && !(keys.hasDeviceKey && runCatching { keys.restoreFromDevice() }.getOrDefault(false))) {
            // Os alarmes já agendados acabam ao fim da janela: avisa ("lembretes em pausa").
            CalendarReminderPrefs.onKeyMissing(context)
            return@withLock true
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            // UPDATE: as instalações antigas ganham a constraint de rede sem perder o ritmo.
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CalendarAlarmWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        // Sem K_cal os eventos de membro ficavam "locked" e os alarmes bons eram cancelados.
        // Só lê (não cria os calendários por omissão a quem nunca abriu o Calendário).
        if (repo.calendars.value.isEmpty() && repo.loadCalendars(createDefaults = false).isFailure) return@withLock false
        val now = Instant.now()
        // Alertas até 7 dias (a chave pode não voltar a estar em memória tão cedo: sem
        // "manter desbloqueado" o worker não consegue decifrar); um alerta de 1 semana
        // antes pede eventos até 7 + 7 dias.
        val horizon = now.plusSeconds(WINDOW_S)
        val fetched = repo.fetchEvents(now.minusSeconds(3600), horizon.plusSeconds(MAX_LEAD_S)).getOrElse { return@withLock false }
        // Ilegíveis agora (ex.: calendário que não abriu): mantêm os alarmes que já tinham.
        val lockedIds = fetched.filter { it.locked }.map { it.id }.toSet()
        // Convites recusados não tocam (como na web).
        val events = fetched.filter { !(it.isGuest && it.raw.me?.status == "declined") && !it.locked && !it.raw.alerts.isNullOrEmpty() }
        val zone = ZoneId.systemDefault()
        // Cada evento só até onde os seus alertas chegam (quase todos ≤ 1 dia).
        val instances = events.flatMap { ev ->
            val lead = (ev.raw.alerts.orEmpty().maxOrNull() ?: 0).coerceIn(0, (MAX_LEAD_S / 60).toInt())
            Recurrence.expand(listOf(ev), now, horizon.plusSeconds(lead * 60L + 60))
        }
        val alarms = instances.flatMap { inst ->
            inst.event.raw.alerts.orEmpty().mapNotNull { minutes ->
                val at = inst.start.minusSeconds(minutes * 60L)
                if (!at.isAfter(now) || at.isAfter(horizon)) return@mapNotNull null
                CalendarAlarm(
                    id = "${inst.key}#$minutes",
                    eventId = inst.event.id,
                    date = inst.start.atZone(zone).toLocalDate().toString(),
                    fireAt = at.toEpochMilli(),
                    startAt = inst.start.toEpochMilli(),
                    minutes = minutes,
                    allDay = inst.event.allDay,
                    title = inst.event.data.title,
                )
            }
        }
        val kept = if (lockedIds.isEmpty()) emptyList()
        else store.all().filter { it.eventId in lockedIds && it.fireAt > now.toEpochMilli() }
        // O sistema limita os alarmes por app (500): ficam os mais próximos; o resto entra nas syncs seguintes.
        val next = (alarms + kept).sortedBy { it.fireAt }.take(MAX_ALARMS)
        store.replace(next)
        CalendarReminderPrefs.onSynced(context, hadAlarms = next.isNotEmpty())
        true
    }

    fun syncAsync() = scope.launch { sync() }

    companion object {
        private const val WORK = "calendar_alarms"
        private const val WINDOW_S = 7 * 24 * 3600L
        private const val MAX_ALARMS = 200

        /** Alerta mais antecipado que o servidor aceita (CalendarEvent::MAX_ALERT_MINUTES). */
        private const val MAX_LEAD_S = 7 * 24 * 3600L
    }
}

/** Reagenda de 6 em 6 h, com rede (só quando a chave E2E está em memória). */
@HiltWorker
class CalendarAlarmWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: CalendarAlarmScheduler,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        when {
            scheduler.sync() -> Result.success()
            // Rede/servidor: tenta outra vez (com recuo), mas não para sempre.
            runAttemptCount < 3 -> Result.retry()
            else -> Result.success()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Throwable) {
        Result.success()
    }
}
