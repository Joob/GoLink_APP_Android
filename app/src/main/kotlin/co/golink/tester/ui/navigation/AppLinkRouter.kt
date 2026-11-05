package co.golink.tester.ui.navigation

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App Links dos emails (golink.co / tester.golink.co) → destino na app. O
 * AppNavHost consome o alvo quando o estado permite (sessão, fora do splash).
 * Os links já declarados como navDeepLink (password, registo, Legacy-*) só
 * passam por aqui com a app aberta (onNewIntent); no arranque trata-os o NavHost.
 */
object AppLinkRouter {
    sealed interface Target {
        /** Confirmar email: o URL completo (a assinatura cobre host e query). */
        data class VerifyEmail(val url: String) : Target
        /** [requireAuth]: reaberto depois do login (o ecrã pediu para iniciar sessão). */
        data class TeamInvite(val id: String, val requireAuth: Boolean = false) : Target
        /** [kind]: files | team | shared. */
        data class Folder(val id: String?, val kind: String) : Target
        data class Calendar(val event: String?) : Target
        data class Ticket(val id: String, val admin: Boolean) : Target
        data object NewTicket : Target
        data object Billing : Target
        data class Legacy(val pane: String) : Target
        data class Nav(val uri: Uri) : Target
    }

    private const val TTL_MS = 10 * 60_000L
    private val HOSTS = setOf("golink.co", "tester.golink.co")
    private val NAV_PREFIXES = listOf("/create-new-password", "/register/", "/legacy-open/", "/legacy-invite/", "/legacy-checkin/")

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending
    @Volatile private var requestedAt = 0L

    fun from(intent: Intent?, fromNewIntent: Boolean) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme != "https" || uri.host !in HOSTS) return
        val target = parse(uri, fromNewIntent) ?: return
        set(target)
        // Não voltar a abrir ao recriar a atividade.
        intent.data = null
    }

    fun set(target: Target) {
        requestedAt = SystemClock.elapsedRealtime()
        _pending.value = target
    }

    /** @return o alvo, se ainda vale (um login desistido não salta para lá muito depois). */
    fun consume(): Target? {
        val target = _pending.value
        _pending.value = null
        return target?.takeIf { SystemClock.elapsedRealtime() - requestedAt <= TTL_MS }
    }

    private fun parse(uri: Uri, fromNewIntent: Boolean): Target? {
        val path = uri.path.orEmpty()
        if (NAV_PREFIXES.any { path.startsWith(it) }) return if (fromNewIntent) Target.Nav(uri) else null
        val s = uri.pathSegments
        fun at(i: Int) = s.getOrNull(i)
        return when {
            at(0) == "api" && at(1) == "user" && at(2) == "verify" && at(3) != null -> Target.VerifyEmail(uri.toString())
            at(0) == "team-folder-invitation" && at(1) != null -> Target.TeamInvite(at(1)!!)
            at(0) == "platform" && at(1) == "files" -> Target.Folder(at(2), "files")
            at(0) == "platform" && at(1) == "team-folders" -> Target.Folder(at(2), "team")
            at(0) == "calendar" -> Target.Calendar(uri.getQueryParameter("event"))
            at(0) == "support" && at(1) == "tickets" && at(2) == "new" -> Target.NewTicket
            at(0) == "support" && at(1) == "tickets" && at(2) != null -> Target.Ticket(at(2)!!, admin = false)
            at(0) == "admin" && at(1) == "support" && at(2) == "tickets" && at(3) != null -> Target.Ticket(at(3)!!, admin = true)
            at(0) == "user" && at(1) == "settings" && at(2) == "billing" -> Target.Billing
            at(0) == "legacy" -> Target.Legacy(at(1) ?: "overview")
            else -> null
        }
    }
}
