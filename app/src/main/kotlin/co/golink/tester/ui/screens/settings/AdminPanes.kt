package co.golink.tester.ui.screens.settings

import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.MailOutline
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
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(14.dp)) { Text("Tentar de novo".tr()) }
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
        val days: Int = 45,
        val isLoading: Boolean = true,
        val error: String? = null,
        val newbies: List<AdminUserItem> = emptyList(),
        val transactions: List<co.golink.tester.domain.admin.TransactionData> = emptyList(),
        val migration: List<co.golink.tester.domain.admin.EncryptionMigrationRow> = emptyList(),
        val migrationPage: Int = 1,
        val migrationHasMore: Boolean = false,
        val migrationLoading: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
        // Blocos secundários: falhas aqui não estragam o painel.
        viewModelScope.launch { repository.newbies().onSuccess { l -> _state.update { it.copy(newbies = l) } } }
        viewModelScope.launch { repository.latestTransactions().onSuccess { l -> _state.update { it.copy(transactions = l) } } }
        loadMigration(1)
    }

    fun setDays(days: Int) {
        if (days == _state.value.days) return
        _state.update { it.copy(days = days) }
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = it.data == null, error = null) }
        viewModelScope.launch {
            repository.dashboard(_state.value.days)
                .onSuccess { d -> _state.update { it.copy(data = d, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun loadMigration(page: Int = _state.value.migrationPage + 1) {
        if (_state.value.migrationLoading) return
        _state.update { it.copy(migrationLoading = true) }
        viewModelScope.launch {
            repository.encryptionMigration(page)
                .onSuccess { (rows, more) ->
                    _state.update { it.copy(migration = if (page == 1) rows else it.migration + rows, migrationPage = page, migrationHasMore = more, migrationLoading = false) }
                }
                .onFailure { _state.update { it.copy(migrationLoading = false) } }
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
        val tab: String = "overview",
        val isLoading: Boolean = true,
        val error: String? = null,
        val events: co.golink.tester.domain.admin.EventsResponse? = null,
        val eventsLoading: Boolean = false,
        val realtime: co.golink.tester.domain.admin.RealtimeResponse? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    /** Troca de janela temporal (24h/7d/30d/90d) sem perder o que já está no ecrã. */
    fun setRange(range: String) {
        if (_state.value.range == range) return
        _state.update { it.copy(range = range, events = null) }
        load()
        if (_state.value.tab == "events") loadEvents()
    }

    fun setTab(tab: String) {
        _state.update { it.copy(tab = tab) }
        if (tab == "events" && _state.value.events == null) loadEvents()
    }

    fun load() {
        _state.update { it.copy(isLoading = it.data == null, error = null) }
        viewModelScope.launch {
            repository.analytics(_state.value.range)
                .onSuccess { d -> _state.update { it.copy(data = d, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun loadEvents() {
        _state.update { it.copy(eventsLoading = true) }
        viewModelScope.launch {
            repository.events(_state.value.range)
                .onSuccess { e -> _state.update { it.copy(events = e, eventsLoading = false) } }
                .onFailure { _state.update { it.copy(eventsLoading = false) } }
        }
    }

    /** Chamado a cada 15 s enquanto o separador Tempo real está visível. */
    suspend fun refreshRealtime() {
        repository.realtime().onSuccess { r -> _state.update { it.copy(realtime = r) } }
    }
}

// AnalyticsPane vive em AnalyticsPane.kt (gráficos), a usar este ViewModel.

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

    fun resendInvite(id: String) = viewModelScope.launch {
        repository.resendInvite(id)
            .onSuccess { _state.update { it.copy(feedback = "Convite reenviado".tr()) }; reload() }
            .onFailure { t -> _state.update { it.copy(feedback = t.message) } }
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
        SettingsPaneHeader(androidx.compose.material.icons.Icons.Outlined.MailOutline, SettingsColors.Orange, "Registos de Convite".tr(), "Convida pessoas e acompanha os registos.".tr())
        Spacer(Modifier.height(16.dp))
        SettingsCard(title = "Novo convite".tr(), description = "A pessoa recebe um email para criar conta.".tr()) {
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
                shape = RoundedCornerShape(14.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                } else {
                    Text("Convidar")
                }
            }
        }

        state.feedback?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Convites enviados".tr())

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
                    InviteRow(invite, onResend = { viewModel.resendInvite(invite.id) }, onDelete = { pendingDelete = invite })
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
private fun InviteRow(invite: InviteItem, onResend: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = settingsCardColor(),
        border = settingsCardBorderStroke(),
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
            // Convites já usados não se reenviam (o servidor recusa com 422).
            if (invite.status != "completed") {
                IconButton(onClick = onResend) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Refresh, contentDescription = "Reenviar".tr())
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
