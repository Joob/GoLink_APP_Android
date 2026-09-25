package co.golink.tester.ui.screens.settings

import co.golink.tester.ui.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.AdminUserItem
import co.golink.tester.domain.admin.AnalyticsResponse
import co.golink.tester.domain.admin.DashboardResponse
import co.golink.tester.domain.admin.InviteItem
import co.golink.tester.domain.admin.RankedItem
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ===========================================================================
// Shared building blocks
// ===========================================================================

@Composable
private fun AdminLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun AdminError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp)) { Text("Tentar de novo".tr()) }
    }
}

// ===========================================================================
// Dashboard
// ===========================================================================

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val data: DashboardResponse? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.dashboard()
                .onSuccess { d -> _state.update { it.copy(data = d, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }
}

// DashboardPane vive em DashboardPane.kt (gráficos), a usar este ViewModel.

// ===========================================================================
// Analytics
// ===========================================================================

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val data: AnalyticsResponse? = null,
        val range: String = "7d",
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    /** Troca de janela temporal (24h/7d/30d/90d) sem perder o que já está no ecrã. */
    fun setRange(range: String) {
        if (_state.value.range == range) return
        _state.update { it.copy(range = range) }
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.analytics(_state.value.range)
                .onSuccess { d -> _state.update { it.copy(data = d, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }
}

// AnalyticsPane vive em AnalyticsPane.kt (gráficos), a usar este ViewModel.

// ===========================================================================
// Users (read-only paginated list)
// ===========================================================================

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val users: List<AdminUserItem> = emptyList(),
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val error: String? = null,
        val page: Int = 1,
        val actionUser: AdminUserItem? = null,
        val isSubmitting: Boolean = false,
        val actionError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun openActions(user: AdminUserItem) = _state.update { it.copy(actionUser = user, actionError = null) }

    fun closeActions() = _state.update { it.copy(actionUser = null, actionError = null) }

    /** [unit] a null = suspensão indefinida. */
    fun suspend(unit: String?, value: Int?, reason: String?) {
        val user = _state.value.actionUser ?: return
        submit { repository.suspendUser(user.id, unit, value, reason) }
    }

    fun unsuspend() {
        val user = _state.value.actionUser ?: return
        submit { repository.unsuspendUser(user.id) }
    }

    private fun submit(block: suspend () -> Result<Unit>) {
        _state.update { it.copy(isSubmitting = true, actionError = null) }
        viewModelScope.launch {
            block()
                .onSuccess {
                    _state.update { it.copy(isSubmitting = false, actionUser = null) }
                    load()
                }
                .onFailure { t ->
                    _state.update { it.copy(isSubmitting = false, actionError = t.message ?: "Erro".tr()) }
                }
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null, page = 1) }
        viewModelScope.launch {
            repository.users(1)
                .onSuccess { (list, more) -> _state.update { it.copy(users = list, hasMore = more, isLoading = false, page = 1) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMore) return
        val next = s.page + 1
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            repository.users(next)
                .onSuccess { (list, more) -> _state.update { it.copy(users = it.users + list, hasMore = more, isLoadingMore = false, page = next) } }
                .onFailure { _state.update { it.copy(isLoadingMore = false) } }
        }
    }
}

@Composable
fun AdminUsersPane(viewModel: AdminUsersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    state.actionUser?.let { user ->
        SuspendUserSheet(
            user = user,
            isSubmitting = state.isSubmitting,
            error = state.actionError,
            onDismiss = viewModel::closeActions,
            onSuspend = viewModel::suspend,
            onUnsuspend = viewModel::unsuspend,
        )
    }

    when {
        state.isLoading -> AdminLoading()
        state.error != null -> AdminError(state.error!!, viewModel::load)
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.users, key = { it.id }) { u ->
                    UserRow(u, onClick = { viewModel.openActions(u) })
                }
                if (state.hasMore) {
                    item {
                        OutlinedButton(
                            onClick = viewModel::loadMore,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Text("Carregar mais".tr())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Unidades aceites pelo backend (SuspendUserRequest). */
private val SUSPEND_UNITS = listOf(
    null to "Indefinida",
    "minutes" to "Minutos",
    "hours" to "Horas",
    "days" to "Dias",
    "weeks" to "Semanas",
)

/**
 * Ações de suspensão de um utilizador. Administradores não podem ser suspensos
 * (o servidor recusa com 403) — aqui mostra-se o aviso em vez do formulário.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuspendUserSheet(
    user: AdminUserItem,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSuspend: (String?, Int?, String?) -> Unit,
    onUnsuspend: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var unit by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val isAdmin = user.role == "admin"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                isAdmin -> Text(
                    "Os administradores não podem ser suspensos".tr(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                user.isSuspended -> {
                    Text(
                        user.suspendedUntil
                            ?.let { "${"Suspenso até".tr()} ${formatSuspendedUntil(it)}" }
                            ?: "Suspenso indefinidamente".tr(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    user.suspendedReason?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onUnsuspend,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Levantar suspensão".tr())
                        }
                    }
                }

                else -> {
                    Text("Duração".tr(), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SUSPEND_UNITS.forEach { (key, label) ->
                            FilterChip(
                                selected = unit == key,
                                onClick = { unit = key },
                                label = { Text(label.tr(), style = MaterialTheme.typography.bodySmall) },
                            )
                        }
                    }
                    if (unit != null) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { new -> value = new.filter { it.isDigit() }.take(4) },
                            label = { Text("Quantidade".tr()) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(255) },
                        label = { Text("Motivo (opcional)".tr()) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onSuspend(unit, value.toIntOrNull(), reason) },
                        // O backend exige value sempre que unit vier preenchido.
                        enabled = !isSubmitting && (unit == null || (value.toIntOrNull() ?: 0) > 0),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Suspender".tr())
                        }
                    }
                }
            }

            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * O backend envia ISO-8601 UTC (toISOString). Mostra-se em hora local no
 * formato dd/MM/yyyy HH:mm; se o parse falhar devolve o original.
 */
private fun formatSuspendedUntil(iso: String): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.parse(iso))
}.getOrDefault(iso)

@Composable
private fun UserRow(u: AdminUserItem, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            if (u.avatar != null) {
                AsyncImage(
                    model = u.avatar,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(u.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (u.isSuspended) {
                    val detail = listOfNotNull(
                        u.suspendedUntil?.let { "${"Até".tr()} ${formatSuspendedUntil(it)}" },
                        u.suspendedReason?.takeIf { it.isNotBlank() },
                    ).joinToString(" — ")
                    if (detail.isNotEmpty()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (u.isSuspended) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.error,
                ) {
                    Text(
                        if (u.suspendedUntil != null) "Suspenso".tr() else "Suspenso ∞".tr(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Badge(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(u.role, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

// ===========================================================================
// Invite registers (list + create + delete)
// ===========================================================================

@HiltViewModel
class InviteRegistersViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val invites: List<InviteItem> = emptyList(),
        val isLoading: Boolean = true,
        val isSubmitting: Boolean = false,
        val error: String? = null,
        val feedback: String? = null,
        val newEmail: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.inviteRegisters(1)
                .onSuccess { (list, _) -> _state.update { it.copy(invites = list, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun setEmail(value: String) = _state.update { it.copy(newEmail = value, feedback = null) }

    fun createInvite() {
        val email = _state.value.newEmail.trim()
        if (email.isBlank()) return
        _state.update { it.copy(isSubmitting = true, feedback = null) }
        viewModelScope.launch {
            repository.createInvite(email)
                .onSuccess {
                    _state.update { it.copy(isSubmitting = false, newEmail = "", feedback = "Convite enviado".tr()) }
                    reload()
                }
                .onFailure { t -> _state.update { it.copy(isSubmitting = false, feedback = "Erro: ${t.message}") } }
        }
    }

    fun deleteInvite(id: String) = viewModelScope.launch {
        repository.deleteInvite(id)
            .onSuccess { _state.update { it.copy(invites = it.invites.filterNot { i -> i.id == id }, feedback = "Convite removido".tr()) } }
            .onFailure { t -> _state.update { it.copy(feedback = "Erro ao remover: ${t.message}") } }
    }

    private fun reload() {
        viewModelScope.launch {
            repository.inviteRegisters(1).onSuccess { (list, _) -> _state.update { it.copy(invites = list) } }
        }
    }
}

@Composable
fun InviteRegistersPane(viewModel: InviteRegistersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<InviteItem?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.newEmail,
                onValueChange = viewModel::setEmail,
                label = { Text("Email a convidar".tr()) },
                singleLine = true,
                enabled = !state.isSubmitting,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = viewModel::createInvite,
                enabled = !state.isSubmitting && state.newEmail.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                } else {
                    Text("Convidar")
                }
            }
        }

        state.feedback?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))

        when {
            state.isLoading -> AdminLoading()
            state.error != null -> AdminError(state.error!!, viewModel::load)
            state.invites.isEmpty() -> Text(
                "Sem convites.".tr(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.invites, key = { it.id }) { invite ->
                    InviteRow(invite, onDelete = { pendingDelete = invite })
                }
            }
        }
    }

    pendingDelete?.let { invite ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remover convite".tr()) },
            text = { Text("Remover o convite de ${invite.email}?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteInvite(invite.id); pendingDelete = null }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar".tr()) }
            },
        )
    }
}

@Composable
private fun InviteRow(invite: InviteItem, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(invite.email, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    append(invite.status.ifBlank { "—" })
                    invite.inviterName?.let { append(" · "); append(it) }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
