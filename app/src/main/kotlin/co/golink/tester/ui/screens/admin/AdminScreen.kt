package co.golink.tester.ui.screens.admin

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.domain.user.User
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.AnalyticsPane
import co.golink.tester.ui.screens.settings.DashboardPane
import co.golink.tester.ui.screens.settings.InviteRegistersPane
import co.golink.tester.ui.screens.settings.NewsAdminPane
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Quem pode abrir o quê na área de administração. Espelha o backend:
 * api/admin exige role=admin (AdminCheck); a fila de suporte aceita também
 * agentes da Support Team (SupportStaffCheck).
 */
data class AdminAccess(val user: User?) {
    val isAdmin: Boolean get() = user?.role == "admin"
    val isSupportStaff: Boolean get() = isAdmin || user?.supportStaff != null
    fun canOpen(pane: String): Boolean = isAdmin
}

@HiltViewModel
class AdminAccessViewModel @Inject constructor(
    sessionManager: SessionManager,
    configRepository: co.golink.tester.data.config.ConfigRepository,
) : ViewModel() {
    val subscriptionType: StateFlow<String> = configRepository.config
        .map { it?.payments?.type ?: "none" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "none")

    init { viewModelScope.launch { configRepository.load() } }

    val access: StateFlow<AdminAccess?> = sessionManager.state
        .map { (it as? AuthState.Authenticated)?.user?.let(::AdminAccess) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, (sessionManager.state.value as? AuthState.Authenticated)?.user?.let(::AdminAccess))
}

/** Título de cada página (igual ao menu da web). */
private fun adminTitle(pane: String): String = when (pane) {
    "dashboard" -> "Painel".tr()
    "analytics" -> "Análises".tr()
    "users" -> "Todos os utilizadores".tr()
    "invites" -> "Convites".tr()
    "audit" -> "Registo de auditoria".tr()
    "news" -> "Notícias".tr()
    "pages" -> "Páginas".tr()
    "languages" -> "Línguas".tr()
    "support-team" -> "Equipa de suporte".tr()
    "faq" -> "FAQ"
    "plans" -> "Planos".tr()
    "subscriptions" -> "Subscrições".tr()
    "discounts" -> "Descontos".tr()
    "payments" -> "Pagamentos".tr()
    "invoices" -> "Faturas".tr()
    "settings" -> "Definições".tr()
    else -> "Administração".tr()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    pane: String,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    accessViewModel: AdminAccessViewModel = hiltViewModel(),
) {
    val access by accessViewModel.access.collectAsStateWithLifecycle()
    val subscriptionType by accessViewModel.subscriptionType.collectAsStateWithLifecycle()
    // Definições: avisa antes de sair com alterações por guardar (mesma instância que o painel usa).
    val settingsVm: SystemSettingsViewModel? = if (pane == "settings") hiltViewModel() else null
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = { if ((settingsVm?.pending ?: 0) > 0) confirmLeave = true else onBack() }
    androidx.activity.compose.BackHandler(enabled = settingsVm != null) { leave() }
    if (confirmLeave) {
        co.golink.tester.ui.screens.settings.SettingsDialog(
            title = "Descartar alterações?".tr(),
            message = "Tens %d alterações por guardar.".tr().format(settingsVm?.pending ?: 0),
            confirmText = "Descartar".tr(),
            destructive = true,
            onConfirm = { confirmLeave = false; settingsVm?.discard(); onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(adminTitle(pane)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr())
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AdminGuard(access, pane) {
                when (pane) {
                    "dashboard" -> DashboardPane(onOpenUser = onOpenUser)
                    "analytics" -> AnalyticsPane()
                    "users" -> AdminUsersPane(onOpenUser = onOpenUser)
                    "invites" -> InviteRegistersPane()
                    "audit" -> AuditLogPane()
                    "news" -> NewsAdminPane()
                    "pages" -> PagesPane()
                    "languages" -> LanguagesPane()
                    "support-team" -> SupportTeamPane()
                    "faq" -> FaqPane()
                    "plans" -> PlansPane(isFixed = subscriptionType != "metered")
                    "subscriptions" -> SubscriptionsPane()
                    "discounts" -> DiscountsPane()
                    "payments" -> PaymentsPane()
                    "invoices" -> InvoicesPane()
                    "settings" -> SystemSettingsPane(settingsVm!!)
                    else -> AdminEmpty("Página não encontrada.".tr())
                }
            }
        }
    }
}

/** Bloqueia a página se a conta não tiver acesso (a rota pode ser aberta diretamente). */
@Composable
fun AdminGuard(access: AdminAccess?, pane: String, content: @Composable () -> Unit) {
    when {
        access == null -> AdminLoading()
        !access.canOpen(pane) -> AdminEmpty("Não tens acesso a esta página.".tr())
        else -> content()
    }
}
