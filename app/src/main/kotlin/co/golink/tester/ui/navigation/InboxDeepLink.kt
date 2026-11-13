package co.golink.tester.ui.navigation

import android.content.Intent
import co.golink.tester.data.notifications.NotificationBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Notificação do contador tocada → abrir o Inbox assim que a app estiver autenticada. */
object InboxDeepLink {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending

    fun from(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationBadge.EXTRA_OPEN_INBOX, false) != true) return
        _pending.value = true
        // Não voltar a abrir ao recriar a atividade.
        intent.removeExtra(NotificationBadge.EXTRA_OPEN_INBOX)
    }

    fun consume() {
        _pending.value = false
    }
}
