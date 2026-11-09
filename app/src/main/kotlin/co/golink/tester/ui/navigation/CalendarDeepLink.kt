package co.golink.tester.ui.navigation

import android.content.Intent
import co.golink.tester.data.calendar.CalendarAlarmStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lembrete tocado → abrir o evento no Calendário assim que a app estiver autenticada. */
object CalendarDeepLink {
    /** [event] null: só abrir o Calendário (aviso "lembretes em pausa"). */
    data class Target(val event: String?, val date: String?)

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending

    fun from(intent: Intent?) {
        if (intent?.getBooleanExtra(CalendarAlarmStore.EXTRA_OPEN_CALENDAR, false) == true) {
            intent.removeExtra(CalendarAlarmStore.EXTRA_OPEN_CALENDAR)
            _pending.value = Target(null, null)
            return
        }
        val event = intent?.getStringExtra(CalendarAlarmStore.EXTRA_EVENT) ?: return
        // Não voltar a abrir ao recriar a atividade.
        intent.removeExtra(CalendarAlarmStore.EXTRA_EVENT)
        // A MainActivity é exported: só ids e datas com o formato dos nossos lembretes.
        if (!EVENT_ID.matches(event)) return
        val date = intent.getStringExtra(CalendarAlarmStore.EXTRA_DATE)?.takeIf { DATE.matches(it) }
        _pending.value = Target(event, date)
    }

    fun consume() {
        _pending.value = null
    }

    private val EVENT_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
}
