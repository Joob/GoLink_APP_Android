package co.golink.tester.ui.navigation

import android.content.Intent
import co.golink.tester.data.calendar.CalendarAlarmStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lembrete tocado → abrir o evento no Calendário assim que a app estiver autenticada. */
object CalendarDeepLink {
    data class Target(val event: String, val date: String?)

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending

    fun from(intent: Intent?) {
        val event = intent?.getStringExtra(CalendarAlarmStore.EXTRA_EVENT) ?: return
        _pending.value = Target(event, intent.getStringExtra(CalendarAlarmStore.EXTRA_DATE))
        // Não voltar a abrir ao recriar a atividade.
        intent.removeExtra(CalendarAlarmStore.EXTRA_EVENT)
    }

    fun consume() {
        _pending.value = null
    }
}
