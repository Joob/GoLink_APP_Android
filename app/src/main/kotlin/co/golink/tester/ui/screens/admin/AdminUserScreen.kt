package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.UserStorageData
import co.golink.tester.domain.user.User
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsCard
import co.golink.tester.ui.screens.settings.SettingsDialog
import co.golink.tester.ui.screens.settings.SettingsField
import co.golink.tester.ui.screens.settings.SettingsNotice
import co.golink.tester.ui.screens.settings.SettingsOutlinedButton
import co.golink.tester.ui.screens.settings.SettingsPrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AdminUserViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: AdminRepository,
) : ViewModel() {
    val userId: String = checkNotNull(savedState["id"])

    data class UiState(
        val user: User? = null,
        val storage: UserStorageData? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        /** Ação em curso (chave do botão) — desativa os restantes. */
        val busy: String? = null,
        val toast: String? = null,
        val fieldError: String? = null,
        /** Passo 2 (código) pendente: "email" | "delete". */
        val awaitingCode: String? = null,
        val deleted: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.user(userId)
                .onSuccess { u -> _state.update { it.copy(user = u, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
            repository.userStorage(userId).onSuccess { s -> _state.update { it.copy(storage = s) } }
        }
    }

    private fun run(key: String, success: String?, after: () -> Unit = {}, block: suspend () -> Result<*>) {
        _state.update { it.copy(busy = key, fieldError = null) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(busy = null, toast = success) }; after() }
                .onFailure { t -> _state.update { it.copy(busy = null, fieldError = t.message ?: "Erro".tr()) } }
        }
    }

    fun changeRole(role: String) = run("role", "Função atualizada".tr(), ::load) { repository.changeRole(userId, role) }

    fun changeCapacity(gb: Int) = run("capacity", "Quota atualizada".tr()) {
        repository.changeCapacity(userId, gb).onSuccess { s -> _state.update { it.copy(storage = s) } }
    }

    fun resetPassword() = run("reset", "Link de reposição enviado".tr()) { repository.resetPassword(userId) }

    fun requestEmail(email: String, password: String) = run("email", "Código enviado para o teu email".tr(), {
        _state.update { it.copy(awaitingCode = "email") }
    }) { repository.requestEmailChange(userId, email, password) }

    fun confirmEmail(code: String) = run("email-confirm", "Email alterado".tr(), {
        _state.update { it.copy(awaitingCode = null) }; load()
    }) { repository.confirmEmailChange(userId, code) }

    fun suspend(unit: String?, value: Int?, reason: String?) =
        run("suspend", "Conta suspensa".tr(), ::load) { repository.suspendUser(userId, unit, value, reason) }

    fun unsuspend() = run("unsuspend", "Suspensão levantada".tr(), ::load) { repository.unsuspendUser(userId) }

    fun requestDelete(name: String, password: String) = run("delete", "Código enviado para o teu email".tr(), {
        _state.update { it.copy(awaitingCode = "delete") }
    }) { repository.requestUserDeletion(userId, name, password) }

    fun confirmDelete(name: String, code: String) = run("delete-confirm", "Eliminação iniciada".tr(), {
        _state.update { it.copy(awaitingCode = null, deleted = true) }
    }) { repository.deleteUser(userId, name, code) }

    fun cancelCode() = _state.update { it.copy(awaitingCode = null, fieldError = null) }
    fun clearError() = _state.update { it.copy(fieldError = null) }
    fun consumeToast() = _state.update { it.copy(toast = null) }
}

private enum class UserTab { Details, Storage, Billing, Security, Suspend, Delete }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserScreen(
    onBack: () -> Unit,
    accessViewModel: AdminAccessViewModel = hiltViewModel(),
    viewModel: AdminUserViewModel = hiltViewModel(),
) {
    val access by accessViewModel.access.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subscriptionType by accessViewModel.subscriptionType.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(UserTab.Details) }

    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.user?.name ?: "Utilizador".tr()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr()) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AdminGuard(access, "users") {
                val user = state.user
                when {
                    state.isLoading && user == null -> AdminLoading()
                    user == null -> AdminError(state.error ?: "Erro".tr(), viewModel::load)
                    else -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        UserHeader(user, isSelf = access?.user?.id == user.id)
                        AdminSegmented(
                            options = listOf(
                                UserTab.Details to "Detalhes".tr(),
                                UserTab.Storage to "Armazenamento".tr(),
                            ) + (if (subscriptionType != "none") listOf(UserTab.Billing to "Subscrição".tr()) else emptyList()) + listOf(
                                UserTab.Security to "Password e email".tr(),
                                UserTab.Suspend to "Suspensão".tr(),
                                UserTab.Delete to "Eliminar".tr(),
                            ),
                            selected = tab,
                            onSelect = { tab = it; viewModel.clearError() },
                        )
                        when (tab) {
                            UserTab.Details -> DetailsTab(user, state, viewModel)
                            UserTab.Storage -> StorageTab(state, viewModel)
                            UserTab.Billing -> UserBillingTab(subscriptionType)
                            UserTab.Security -> SecurityTab(user, state, viewModel)
                            UserTab.Suspend -> SuspendTab(user, state, viewModel)
                            UserTab.Delete -> DeleteTab(user, state, viewModel)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHeader(user: User, isSelf: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AdminAvatar(user.avatar, size = 52)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                AdminBadge(if (user.role == "admin") "Admin" else "Utilizador".tr(), if (user.role == "admin") AdminColors.Info else AdminColors.Neutral)
                if (user.isSuspended) AdminBadge("Suspenso".tr(), AdminColors.Danger)
                if (isSelf) AdminBadge("Tu".tr(), SettingsAccent)
            }
        }
    }
}

@Composable
private fun ErrorNotice(state: AdminUserViewModel.UiState) {
    state.fieldError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
}

@Composable
private fun DetailsTab(user: User, state: AdminUserViewModel.UiState, vm: AdminUserViewModel) {
    SettingsCard(title = "Informação da conta".tr()) {
        AdminInfoRow("Nome".tr(), user.name)
        AdminInfoRow("Email".tr(), user.email)
        AdminInfoRow("Email verificado".tr(), if (user.emailVerified) "Sim".tr() else "Não".tr())
        AdminInfoRow("Plano".tr(), user.planName)
        AdminInfoRow("2FA", if (user.twoFactorEnabled) "Ativo".tr() else "Inativo".tr())
        AdminInfoRow("Telefone".tr(), user.phoneNumber)
        AdminInfoRow("Cidade".tr(), user.city)
    }
    var role by remember(user.role) { mutableStateOf(user.role) }
    SettingsCard(title = "Função".tr(), description = "Os administradores têm acesso a toda a administração.".tr()) {
        AdminSegmented(listOf("user" to "Utilizador".tr(), "admin" to "Admin"), role, { role = it })
        ErrorNotice(state)
        SettingsPrimaryButton(
            "Guardar função".tr(), onClick = { vm.changeRole(role) },
            enabled = role != user.role, loading = state.busy == "role",
        )
    }
}

@Composable
private fun StorageTab(state: AdminUserViewModel.UiState, vm: AdminUserViewModel) {
    val s = state.storage ?: return AdminLoading(Modifier.fillMaxWidth().height(120.dp))
    val a = s.attributes
    SettingsCard(title = "Utilização".tr()) {
        Text("${a.used ?: "—"} / ${a.capacity ?: "—"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(
            progress = { (a.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
            color = if (a.percentage >= 90) AdminColors.Danger else SettingsAccent,
            modifier = Modifier.fillMaxWidth(),
        )
        s.meta?.let { m ->
            listOf(
                "Imagens".tr() to m.images, "Vídeos".tr() to m.videos, "Áudio".tr() to m.audios,
                "Documentos".tr() to m.documents, "Outros".tr() to m.others,
            ).forEach { (label, slice) -> AdminInfoRow(label, slice?.used) }
            m.traffic?.let {
                AdminInfoRow("Download (45 dias)".tr(), it.download)
                AdminInfoRow("Upload (45 dias)".tr(), it.upload)
            }
        }
    }
    if (a.has_limit) {
        var gb by remember { mutableStateOf("") }
        SettingsCard(title = "Quota de armazenamento".tr(), description = "Novo limite em GB.".tr()) {
            SettingsField("GB", gb, { v -> gb = v.filter(Char::isDigit).take(9) }, keyboardType = KeyboardType.Number, placeholder = a.capacity)
            ErrorNotice(state)
            SettingsPrimaryButton(
                "Guardar quota".tr(), onClick = { gb.toIntOrNull()?.let(vm::changeCapacity) },
                enabled = (gb.toIntOrNull() ?: 0) > 0, loading = state.busy == "capacity",
            )
        }
    }
}

@Composable
private fun SecurityTab(user: User, state: AdminUserViewModel.UiState, vm: AdminUserViewModel) {
    SettingsCard(title = "Repor password".tr(), description = "Envia ao utilizador um email com o link para definir uma nova password.".tr()) {
        SettingsOutlinedButton("Enviar link de reposição".tr(), onClick = vm::resetPassword, enabled = state.busy == null)
    }
    SettingsCard(
        title = "Alterar email".tr(),
        description = "Confirma com a tua password; o código de 6 dígitos vai para o TEU email. As sessões do utilizador são terminadas.".tr(),
    ) {
        if (user.role == "admin") {
            SettingsNotice("O email de administradores não pode ser alterado aqui.".tr(), AdminColors.Warning)
            return@SettingsCard
        }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        if (state.awaitingCode == "email") {
            SettingsField("Código".tr(), code, { v -> code = v.filter(Char::isDigit).take(6) }, keyboardType = KeyboardType.NumberPassword)
            ErrorNotice(state)
            SettingsPrimaryButton("Confirmar".tr(), onClick = { vm.confirmEmail(code) }, enabled = code.length == 6, loading = state.busy == "email-confirm")
            SettingsOutlinedButton("Cancelar".tr(), onClick = vm::cancelCode)
        } else {
            SettingsField("Novo email".tr(), email, { email = it.trim() }, keyboardType = KeyboardType.Email)
            SettingsField("A tua password".tr(), password, { password = it }, isPassword = true)
            ErrorNotice(state)
            SettingsPrimaryButton(
                "Enviar código".tr(), onClick = { vm.requestEmail(email, password) },
                enabled = email.contains('@') && password.isNotEmpty(), loading = state.busy == "email",
            )
        }
    }
}

/** Unidades aceites pelo backend (SuspendUserRequest); null = indefinida. */
private val SUSPEND_UNITS = listOf(
    null to "Indefinida",
    "minutes" to "Minutos",
    "hours" to "Horas",
    "days" to "Dias",
    "weeks" to "Semanas",
)

@Composable
private fun SuspendTab(user: User, state: AdminUserViewModel.UiState, vm: AdminUserViewModel) {
    SettingsCard(title = "Suspensão".tr(), description = "Uma conta suspensa não consegue entrar nem usar a API.".tr()) {
        when {
            user.role == "admin" -> SettingsNotice("Os administradores não podem ser suspensos".tr(), AdminColors.Warning)
            user.isSuspended -> {
                Text(
                    user.suspendedUntil?.let { "${"Suspenso até".tr()} ${formatAdminDate(it)}" } ?: "Suspenso indefinidamente".tr(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                user.suspendedReason?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ErrorNotice(state)
                SettingsPrimaryButton("Levantar suspensão".tr(), onClick = vm::unsuspend, loading = state.busy == "unsuspend")
            }
            else -> {
                var unit by remember { mutableStateOf<String?>(null) }
                var value by remember { mutableStateOf("") }
                var reason by remember { mutableStateOf("") }
                Text("Duração".tr(), style = MaterialTheme.typography.labelMedium)
                AdminSegmented(SUSPEND_UNITS.map { (k, l) -> k to l.tr() }, unit, { unit = it })
                if (unit != null) {
                    SettingsField("Quantidade".tr(), value, { v -> value = v.filter(Char::isDigit).take(4) }, keyboardType = KeyboardType.Number)
                }
                SettingsField("Motivo (opcional)".tr(), reason, { reason = it.take(255) })
                ErrorNotice(state)
                SettingsPrimaryButton(
                    "Suspender".tr(),
                    onClick = { vm.suspend(unit, value.toIntOrNull(), reason) },
                    // O backend exige value sempre que unit vier preenchido.
                    enabled = unit == null || (value.toIntOrNull() ?: 0) > 0,
                    loading = state.busy == "suspend",
                )
            }
        }
    }
}

@Composable
private fun DeleteTab(user: User, state: AdminUserViewModel.UiState, vm: AdminUserViewModel) {
    SettingsCard(
        title = "Eliminar conta".tr(),
        description = "A conta é bloqueada de imediato e todos os ficheiros são apagados. Não pode ser desfeito.".tr(),
    ) {
        if (user.role == "admin") {
            SettingsNotice("Os administradores não podem ser eliminados aqui.".tr(), AdminColors.Warning)
            return@SettingsCard
        }
        var name by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf(false) }
        SettingsField(
            "${"Escreve o nome para confirmar".tr()}: ${user.name}", name, { name = it },
            isError = name.isNotEmpty() && name != user.name,
        )
        if (state.awaitingCode == "delete") {
            SettingsField("Código".tr(), code, { v -> code = v.filter(Char::isDigit).take(6) }, keyboardType = KeyboardType.NumberPassword)
            ErrorNotice(state)
            SettingsOutlinedButton(
                "Eliminar definitivamente".tr(), onClick = { confirm = true }, destructive = true,
                enabled = code.length == 6 && name == user.name && state.busy == null,
            )
            SettingsOutlinedButton("Cancelar".tr(), onClick = vm::cancelCode)
        } else {
            SettingsField("A tua password".tr(), password, { password = it }, isPassword = true)
            ErrorNotice(state)
            SettingsOutlinedButton(
                "Enviar código".tr(), onClick = { vm.requestDelete(name, password) }, destructive = true,
                enabled = name == user.name && password.isNotEmpty() && state.busy == null,
            )
        }
        if (confirm) {
            SettingsDialog(
                title = "Eliminar conta".tr(),
                message = user.email,
                confirmText = "Eliminar".tr(),
                destructive = true,
                onConfirm = { confirm = false; vm.confirmDelete(name, code) },
                onDismiss = { confirm = false },
            )
        }
    }
}
