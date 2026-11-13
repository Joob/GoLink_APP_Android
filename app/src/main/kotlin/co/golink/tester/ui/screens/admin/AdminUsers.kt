package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.AdminUserCounts
import co.golink.tester.domain.admin.AdminUserItem
import co.golink.tester.domain.admin.CreateUserRequest
import co.golink.tester.domain.admin.DeletedUserRow
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsField
import co.golink.tester.ui.screens.settings.SettingsNotice
import co.golink.tester.ui.screens.settings.SettingsPrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UsersTab(val status: String?) { All(null), Active("active"), Suspended("suspended"), Deleted(null) }

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val tab: UsersTab = UsersTab.All,
        val search: String = "",
        val users: List<AdminUserItem> = emptyList(),
        val deleted: List<DeletedUserRow> = emptyList(),
        val counts: AdminUserCounts? = null,
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val page: Int = 1,
        val error: String? = null,
        val toast: String? = null,
        val showCreate: Boolean = false,
        val isCreating: Boolean = false,
        val createError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null
    /** Pedido de lista em curso: um load novo cancela-o (troca de separador/pesquisa). */
    private var loadJob: Job? = null

    init { load() }

    fun setTab(tab: UsersTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab, users = emptyList(), deleted = emptyList()) }
        load()
    }

    /** Pesquisa com atraso de 350 ms (como o DatatableWrapper da web). */
    fun setSearch(value: String) {
        _state.update { it.copy(search = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load()
        }
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null, page = 1) }
        loadJob = viewModelScope.launch { fetch(1) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMore || loadJob?.isActive == true) return
        _state.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch { fetch(s.page + 1) }
    }

    private suspend fun fetch(page: Int) {
        val s = _state.value
        // Resultado de um filtro que já não é o atual (ou pedido cancelado): ignora.
        val filterChanged = { _state.value.tab != s.tab || _state.value.search != s.search }
        val failed: (Throwable) -> Unit = { t ->
            _state.update { it.copy(isLoading = false, isLoadingMore = false, error = if (page == 1) "Não foi possível carregar (%s)".tr().format(t.message.orEmpty()) else it.error) }
        }
        if (s.tab == UsersTab.Deleted) {
            val result = repository.deletedUsers(page, s.search)
            if (!currentCoroutineContext().isActive || filterChanged()) return
            result
                .onSuccess { (rows, more) ->
                    _state.update {
                        it.copy(
                            deleted = if (page == 1) rows else (it.deleted + rows).distinctBy { r -> r.id.toString() },
                            hasMore = more, page = page, isLoading = false, isLoadingMore = false,
                        )
                    }
                }
                .onFailure(failed)
        } else {
            val result = repository.users(page, s.tab.status, s.search)
            if (!currentCoroutineContext().isActive || filterChanged()) return
            result
                .onSuccess { p ->
                    _state.update {
                        it.copy(
                            // Uma conta nova entre páginas desloca o offset: sem dedupe o LazyColumn rebenta com chaves repetidas.
                            users = if (page == 1) p.items else (it.users + p.items).distinctBy { u -> u.id },
                            counts = p.counts ?: it.counts,
                            hasMore = p.hasMore, page = page, isLoading = false, isLoadingMore = false,
                        )
                    }
                }
                .onFailure(failed)
        }
    }

    fun cancelDeletion(userId: String) = viewModelScope.launch {
        repository.cancelScheduledDeletion(userId)
            .onSuccess { _state.update { it.copy(toast = "Eliminação cancelada".tr()) }; load() }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun openCreate(show: Boolean) = _state.update { it.copy(showCreate = show, createError = null) }

    fun create(request: CreateUserRequest) {
        _state.update { it.copy(isCreating = true, createError = null) }
        viewModelScope.launch {
            repository.createUser(request)
                .onSuccess {
                    _state.update { it.copy(isCreating = false, showCreate = false, toast = "Utilizador criado".tr()) }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(isCreating = false, createError = t.message) } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

@Composable
fun AdminUsersPane(
    onOpenUser: (String) -> Unit,
    viewModel: AdminUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    if (state.showCreate) {
        CreateUserSheet(
            isSubmitting = state.isCreating,
            error = state.createError,
            onDismiss = { viewModel.openCreate(false) },
            onSubmit = viewModel::create,
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = AdminListPadding,
        ) {
            adminBlock("header") {
                AdminPageHeader("Contas registadas, suspensas e eliminadas.".tr()) {
                    AdminButton("Criar".tr(), onClick = { viewModel.openCreate(true) }, icon = Icons.Outlined.PersonAdd)
                }
            }
            adminBlock("toolbar") {
                val c = state.counts
                AdminToolbar(state.search, viewModel::setSearch, "Pesquisar por nome ou email".tr()) {
                    AdminSegmented(
                        options = listOf(
                            UsersTab.All to "Todos".tr(),
                            UsersTab.Active to "Ativos".tr(),
                            UsersTab.Suspended to "Suspensos".tr(),
                            UsersTab.Deleted to "Eliminados".tr(),
                        ),
                        selected = state.tab,
                        onSelect = viewModel::setTab,
                        counts = if (c == null) emptyMap() else mapOf(
                            UsersTab.All to c.all, UsersTab.Active to c.active,
                            UsersTab.Suspended to c.suspended, UsersTab.Deleted to c.deleted,
                        ),
                    )
                }
            }
            when {
                state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                state.error != null -> item { AdminError(state.error!!, viewModel::load, Modifier.fillMaxWidth()) }
                state.tab == UsersTab.Deleted -> {
                    if (state.deleted.isEmpty()) item { AdminEmptyState("Sem resultados.".tr(), icon = Icons.Outlined.PersonOff) }
                    adminListGroup(state.deleted, key = { "d" + it.id.toString() }) { row ->
                        DeletedUserRowItem(row, onOpen = row.userIdString?.takeIf { row.state == "scheduled" }?.let { id -> { onOpenUser(id) } }, onCancel = { row.userIdString?.let(viewModel::cancelDeletion) })
                    }
                }
                else -> {
                    if (state.users.isEmpty()) item { AdminEmptyState("Sem resultados.".tr(), icon = Icons.Outlined.PersonSearch) }
                    adminListGroup(state.users, key = { it.id }) { u -> UserRowItem(u) { onOpenUser(u.id) } }
                }
            }
            if (!state.isLoading && state.hasMore) item { AdminLoadMore(state.isLoadingMore, viewModel::loadMore) }
        }
        androidx.compose.material3.SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun UserRowItem(u: AdminUserItem, onClick: () -> Unit) {
    val tone = when {
        u.deletionScheduledFor != null -> AdminTone.Danger
        u.isSuspended -> AdminTone.Warning
        else -> AdminTone.Success
    }
    AdminListItem(
        title = u.name,
        subtitle = u.email,
        leading = { AdminAvatar(u.avatar, size = 36, name = u.name, tone = tone) },
        onClick = onClick,
        trailing = {
            // Estado à direita, como a coluna "Estado" da tabela da web.
            when {
                u.deletionScheduledFor != null -> AdminStatusPill("Eliminação agendada".tr(), AdminTone.Danger)
                u.isSuspended -> AdminStatusPill("Suspenso".tr(), AdminTone.Warning)
                else -> AdminStatusPill("Ativo".tr(), AdminTone.Success)
            }
        },
        footer = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                if (u.role == "admin") AdminStatusPill("Admin", AdminTone.Info)
                u.plan?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
            if (u.storagePercent != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    AdminProgressBar(u.storagePercent, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(u.storage.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            } else if (!u.storage.isNullOrBlank()) {
                Text(u.storage, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            u.lastLoginAt?.let {
                Text("${"Último acesso".tr()}: ${formatAdminDate(it) ?: it}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        },
    )
}

@Composable
private fun DeletedUserRowItem(row: DeletedUserRow, onOpen: (() -> Unit)?, onCancel: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val (label, tone) = when (row.state) {
        "scheduled" -> "Agendada".tr() to AdminTone.Warning
        "deleting" -> "Em curso".tr() to AdminTone.Info
        else -> "Eliminada".tr() to AdminTone.Danger
    }
    val by = when (row.requested_by) {
        "admin" -> "Pedido do admin".tr()
        "support" -> "Via suporte".tr()
        "inactivity" -> "Por inatividade".tr()
        else -> "Pedido do utilizador".tr()
    }
    AdminListItem(
        title = row.name ?: "—",
        subtitle = listOfNotNull(row.email, row.plan).joinToString(" · "),
        leading = { AdminAvatar(row.avatar, size = 36, name = row.name, tone = tone) },
        onClick = onOpen,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                AdminStatusPill(label, tone)
                AdminCount(by)
            }
            val date = if (row.state == "scheduled") row.scheduled_for else row.deleted_at
            formatAdminDate(date)?.let {
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        },
        trailing = if (row.state == "scheduled") { { TextButton(onClick = { confirm = true }) { Text("Cancelar".tr(), color = AdminTone.Danger.solid) } } } else null,
    )
    if (confirm) {
        co.golink.tester.ui.screens.settings.SettingsDialog(
            title = "Cancelar eliminação".tr(),
            message = "A conta volta a ficar ativa e a eliminação agendada é anulada.".tr(),
            confirmText = "Cancelar eliminação".tr(),
            onConfirm = { confirm = false; onCancel() },
            onDismiss = { confirm = false },
            dismissText = "Fechar".tr(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateUserSheet(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (CreateUserRequest) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("user") }
    // Criar um admin exige step-up: password do admin atual ou passkey (change_role).
    var adminPassword by remember { mutableStateOf("") }
    var passkeyToken by remember { mutableStateOf<String?>(null) }
    val stepUpOk = role != "admin" || passkeyToken != null || adminPassword.isNotEmpty()
    val valid = name.isNotBlank() && email.contains('@') && password.length >= 6 && password == confirm && stepUpOk
    // Erro do servidor: a passkey é de uso único/expira, pede de novo.
    LaunchedEffect(error) { if (error != null) { passkeyToken = null; adminPassword = "" } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Criar utilizador".tr(), style = MaterialTheme.typography.titleMedium)
            SettingsField("Nome".tr(), name, { name = it.take(255) })
            SettingsField("Email".tr(), email, { email = it.trim() }, keyboardType = KeyboardType.Email)
            SettingsField("Password".tr(), password, { password = it }, isPassword = true, supportingText = "Mínimo 6 caracteres".tr())
            SettingsField(
                "Confirmar password".tr(), confirm, { confirm = it }, isPassword = true,
                isError = confirm.isNotEmpty() && confirm != password,
            )
            Text("Função".tr(), style = MaterialTheme.typography.labelMedium)
            AdminSegmented(listOf("user" to "Utilizador".tr(), "admin" to "Admin"), role, { role = it })
            if (role == "admin") {
                Text(
                    "Os administradores têm acesso total à plataforma. Confirma com a tua password.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                co.golink.tester.ui.screens.settings.PasskeyConfirmRow(
                    purpose = "change_role",
                    confirmed = passkeyToken != null,
                    onToken = { passkeyToken = it },
                )
                if (passkeyToken == null) {
                    SettingsField("A tua password".tr(), adminPassword, { adminPassword = it }, isPassword = true)
                }
            }
            error?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
            SettingsPrimaryButton(
                "Criar".tr(),
                onClick = {
                    val admin = role == "admin"
                    onSubmit(
                        CreateUserRequest(
                            name.trim(), email, password, confirm, role,
                            admin_password = adminPassword.takeIf { admin && passkeyToken == null && it.isNotEmpty() },
                            passkey_token = passkeyToken.takeIf { admin },
                        ),
                    )
                },
                enabled = valid,
                loading = isSubmitting,
            )
        }
    }
}
