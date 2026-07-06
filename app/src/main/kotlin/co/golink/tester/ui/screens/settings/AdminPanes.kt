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
import androidx.compose.ui.text.font.FontWeight
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

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatRow(left: String, value: String, right: String, value2: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(left, value, modifier = Modifier.weight(1f))
        StatCard(right, value2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AdminSectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RankedSection(title: String, items: List<RankedItem>, max: Int = 8) {
    if (items.isEmpty()) return
    AdminSectionTitle(title)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            items.take(max).forEach { item ->
                InfoLine(item.label ?: "—", "${item.value}  ·  ${item.percentage}%")
            }
        }
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

@Composable
fun DashboardPane(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.isLoading -> AdminLoading()
        state.error != null -> AdminError(state.error!!, viewModel::load)
        else -> {
            val d = state.data ?: return
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AdminSectionTitle("Utilizadores".tr())
                StatRow("Total", d.users.total.toString(), "Online", d.users.online.toString())
                Spacer(Modifier.height(12.dp))
                StatRow("Convidados online".tr(), d.users.guests.toString(), "Premium", d.users.usersPremiumTotal.toString())

                AdminSectionTitle("Armazenamento e tráfego".tr())
                StatRow("Em uso".tr(), d.disk.used ?: "—", "Ganhos", d.app.earnings ?: "—")
                Spacer(Modifier.height(12.dp))
                StatRow("Upload total".tr(), d.disk.upload.total ?: "—", "Download total".tr(), d.disk.download.total ?: "—")

                AdminSectionTitle("Aplicação".tr())
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoLine("Versão".tr(), d.app.version ?: "—")
                        InfoLine("Licença".tr(), d.app.license ?: "—")
                        InfoLine("Cron", if (d.app.cron.isRunning) "A correr".tr() else "Parado")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ===========================================================================
// Analytics
// ===========================================================================

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val data: AnalyticsResponse? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.analytics()
                .onSuccess { d -> _state.update { it.copy(data = d, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }
}

@Composable
fun AnalyticsPane(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.isLoading -> AdminLoading()
        state.error != null -> AdminError(state.error!!, viewModel::load)
        else -> {
            val d = state.data ?: return
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AdminSectionTitle("Visitas (${d.range ?: "7d"})")
                StatRow("Visitantes", d.cards.visitors.toString(), "Visitas", d.cards.visits.toString())
                Spacer(Modifier.height(12.dp))
                StatRow("Novos", d.behavior.new.toString(), "Recorrentes", d.behavior.returning.toString())

                RankedSection("Top países".tr(), d.countries)
                RankedSection("Dispositivos", d.devices)
                RankedSection("Browsers", d.browsers)
                RankedSection("Sistemas", d.os)
                RankedSection("Referências".tr(), d.referrers)

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

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
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

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
    when {
        state.isLoading -> AdminLoading()
        state.error != null -> AdminError(state.error!!, viewModel::load)
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.users, key = { it.id }) { u -> UserRow(u) }
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

@Composable
private fun UserRow(u: AdminUserItem) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
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
            }
            Spacer(Modifier.width(8.dp))
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
