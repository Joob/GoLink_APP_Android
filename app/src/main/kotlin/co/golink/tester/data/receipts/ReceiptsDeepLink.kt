package co.golink.tester.data.receipts

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Toque numa notificação local dos Recibos → painel a abrir (consumido pelo AppNavHost).
 * O painel vem dos extras da nossa própria PendingIntent, mas é validado na mesma.
 */
object ReceiptsDeepLink {
    const val EXTRA = "receipts_pane"

    private val SAFE = Regex("^(spending(/(day|week|month|year)/\\d{4}-\\d{2}-\\d{2})?|all(/[A-Za-z0-9-]{1,64})?|budgets|budget/[a-z]{3,20}|irs|efatura|scan|trash|recat/[A-Za-z0-9-]{1,64})$")

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun from(intent: Intent?) {
        val pane = intent?.getStringExtra(EXTRA) ?: return
        intent.removeExtra(EXTRA)
        if (SAFE.matches(pane)) _pending.value = pane
    }

    fun consume() {
        _pending.value = null
    }
}
