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
        /** /notes/... → caminho dentro das Notes ("<id>", "all/<id>", "checklists"). */
        data class Notes(val path: String) : Target
        /** /pass/... → painel ou página do Pass ("vault", "totp", "health", "generator", "settings", "trash"). */
        data class Pass(val pane: String) : Target
        /** /receipts/... → painel dos Recibos ("spending[/period/date]", "all[/id]", "budgets", "irs", "efatura", "scan", "trash"). */
        data class Receipts(val pane: String) : Target
        data class Nav(val uri: Uri) : Target
    }

    private const val TTL_MS = 10 * 60_000L
    /** Release: só golink.co (a MainActivity é exported; um intent explícito salta a verificação dos App Links). */
    private val HOSTS = if (co.golink.tester.BuildConfig.DEBUG) setOf("golink.co", "tester.golink.co") else setOf("golink.co")
    /** Ids/painéis vindos do link: só caracteres seguros (UUID, numérico, token). */
    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,128}")
    private val NAV_PREFIXES = listOf("/create-new-password", "/register/", "/legacy-open/", "/legacy-invite/", "/legacy-checkin/")

    /** https no nosso domínio (www incluído): links vindos do servidor que se podem abrir. */
    fun isOwnHttpsLink(uri: Uri): Boolean = uri.scheme == "https" && uri.host?.removePrefix("www.") in HOSTS

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
        // Presente mas com formato inválido → link ignorado.
        fun bad(v: String?) = !v.isNullOrEmpty() && !SAFE_ID.matches(v)
        if (bad(at(1)) && at(0) in setOf("team-folder-invitation", "legacy")) return null
        if (bad(at(2)) && at(0) in setOf("platform", "support")) return null
        if (bad(at(3)) && at(0) == "admin") return null
        if (bad(uri.getQueryParameter("event")) && at(0) == "calendar") return null
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
            // Rota passada à página das Notes: só segmentos simples (nada de "..", "%2F" ou URLs absolutos).
            at(0) == "notes" -> Target.Notes(
                s.drop(1).takeIf { segs -> segs.all { SAFE_ID.matches(it) } && !uri.encodedPath.orEmpty().contains('%') }
                    ?.joinToString("/").orEmpty()
            )
            at(0) == "pass" -> Target.Pass(passPane(at(1)))
            at(0) == "receipts" -> Target.Receipts(receiptsPane(uri))
            else -> null
        }
    }

    private val YMD = Regex("\\d{4}-\\d{2}-\\d{2}")

    /** Rotas da web (routesReceipts.js) → painel da app; valores do link validados. */
    fun receiptsPane(uri: Uri): String {
        val s = uri.pathSegments
        return when (val seg = s.getOrNull(1)) {
            "all" -> s.getOrNull(2)?.takeIf { SAFE_ID.matches(it) }?.let { "all/$it" } ?: "all"
            "budgets", "irs", "efatura", "scan", "trash" -> seg
            else -> {
                val period = uri.getQueryParameter("period")?.takeIf { it in setOf("day", "week", "month", "year") }
                val date = uri.getQueryParameter("date")?.takeIf { YMD.matches(it) }
                if (period != null && date != null) "spending/$period/$date" else "spending"
            }
        }
    }

    /** Rotas da web (routesPass.js) → painel da app; o resto (all, logins, edit/…) abre o cofre. */
    private fun passPane(segment: String?): String = when (segment) {
        "settings", "trash", "health", "generator" -> segment
        "2fa" -> "totp"
        else -> "vault"
    }
}
