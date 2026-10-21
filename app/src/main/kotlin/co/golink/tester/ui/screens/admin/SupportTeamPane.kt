package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.SupportTeamAgent
import co.golink.tester.domain.admin.SupportTeamAgentRequest
import co.golink.tester.domain.admin.SupportTeamCandidate
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsDialog
import co.golink.tester.ui.screens.settings.SettingsField
import co.golink.tester.ui.screens.settings.SettingsNotice
import co.golink.tester.ui.screens.settings.SettingsOutlinedButton
import co.golink.tester.ui.screens.settings.SettingsPrimaryButton
import co.golink.tester.ui.screens.support.categoryLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SupportTeamViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val agents: List<SupportTeamAgent> = emptyList(),
        val categories: List<String> = listOf("billing", "technical", "other"),
        val isLoading: Boolean = true,
        val error: String? = null,
        /** Folha aberta: null = fechada; agente sem id = novo membro. */
        val editing: SupportTeamAgent? = null,
        val saving: Boolean = false,
        val formError: String? = null,
        val candidates: List<SupportTeamCandidate> = emptyList(),
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = it.agents.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.supportTeam()
                .onSuccess { r -> _state.update { it.copy(agents = r.data, categories = r.categories.ifEmpty { it.categories }, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun edit(agent: SupportTeamAgent?) = _state.update { it.copy(editing = agent, formError = null, candidates = emptyList()) }

    fun search(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 2) return _state.update { it.copy(candidates = emptyList()) }
        searchJob = viewModelScope.launch {
            delay(300)
            repository.searchTeamCandidates(q.trim()).onSuccess { l -> _state.update { it.copy(candidates = l) } }
        }
    }

    fun save(request: SupportTeamAgentRequest) {
        val id = _state.value.editing?.idString?.ifBlank { null }
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            (if (id == null) repository.addAgent(request) else repository.updateAgent(id, request))
                .onSuccess {
                    _state.update { it.copy(saving = false, editing = null, toast = if (id == null) "Membro adicionado".tr() else "Alterações guardadas".tr()) }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(saving = false, formError = t.message) } }
        }
    }

    fun remove(agent: SupportTeamAgent) = viewModelScope.launch {
        repository.removeAgent(agent.idString)
            .onSuccess { _state.update { it.copy(editing = null, toast = "Membro removido".tr()) }; load() }
            .onFailure { t -> _state.update { it.copy(formError = t.message) } }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

@Composable
fun SupportTeamPane(viewModel: SupportTeamViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    state.editing?.let { agent ->
        AgentSheet(agent, state, viewModel)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Agentes que respondem aos tickets, com o nome público, âmbito e permissões de cada um.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            when {
                state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                state.error != null -> item { AdminError(state.error!!, viewModel::load, Modifier.fillMaxWidth()) }
                state.agents.isEmpty() -> item { AdminEmpty("Ainda não há membros na equipa.".tr()) }
                else -> items(state.agents, key = { it.idString }) { a ->
                    AdminListRow(
                        title = a.display_name ?: a.account_name ?: "—",
                        subtitle = listOfNotNull(a.title, a.email).joinToString(" · "),
                        leading = { AdminAvatar(a.avatar) },
                        onClick = { viewModel.edit(a) },
                        footer = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                if (a.is_active) AdminBadge("Ativo".tr(), SettingsAccent) else AdminBadge("Inativo".tr(), AdminColors.Neutral)
                                AdminBadge(if (a.scope == "assigned") "Só atribuídos".tr() else "Todos os tickets".tr(), AdminColors.Info)
                                if (a.open_assigned > 0) AdminBadge("%d abertos".tr().format(a.open_assigned), AdminColors.Warning)
                            }
                        },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { viewModel.edit(SupportTeamAgent()) },
            icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
            text = { Text("Adicionar membro".tr()) },
            containerColor = SettingsAccent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentSheet(agent: SupportTeamAgent, state: SupportTeamViewModel.UiState, vm: SupportTeamViewModel) {
    val isNew = agent.idString.isBlank()
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(agent.display_name.orEmpty()) }
    var title by remember { mutableStateOf(agent.title.orEmpty()) }
    var active by remember { mutableStateOf(agent.is_active) }
    var scope by remember { mutableStateOf(agent.scope) }
    var categories by remember { mutableStateOf(agent.categories.orEmpty().toSet()) }
    var canAssign by remember { mutableStateOf(agent.can_assign) }
    var canDelete by remember { mutableStateOf(agent.can_delete) }
    var canRequestDeletion by remember { mutableStateOf(agent.can_request_deletion) }
    var canViewEmail by remember { mutableStateOf(agent.can_view_user_email) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { vm.edit(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isNew) "Adicionar membro".tr() else (agent.email ?: ""), style = MaterialTheme.typography.titleMedium)
            if (isNew) {
                SettingsField("Email do utilizador".tr(), email, { email = it.trim(); vm.search(it) }, keyboardType = KeyboardType.Email)
                state.candidates.take(6).forEach { c ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = !c.is_member && !c.is_suspended) { email = c.email.orEmpty(); vm.search("") }
                            .padding(vertical = 4.dp),
                    ) {
                        AdminAvatar(c.avatar, size = 28)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name ?: c.email.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            Text(c.email.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when {
                            c.is_member -> AdminBadge("Já é membro".tr(), AdminColors.Neutral)
                            c.is_suspended -> AdminBadge("Suspenso".tr(), AdminColors.Danger)
                        }
                    }
                }
            }
            SettingsField("Nome público".tr(), name, { name = it.take(60) }, supportingText = "É o nome que o utilizador vê nas respostas.".tr())
            SettingsField("Cargo".tr(), title, { title = it.take(60) })
            SwitchRow("Ativo".tr(), active) { active = it }
            Text("Âmbito".tr(), style = MaterialTheme.typography.labelMedium)
            AdminSegmented(listOf("all" to "Todos os tickets".tr(), "assigned" to "Só atribuídos".tr()), scope, { scope = it })
            Text("Categorias (nenhuma = todas)".tr(), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { c ->
                    FilterChip(
                        selected = c in categories,
                        onClick = { categories = if (c in categories) categories - c else categories + c },
                        label = { Text(categoryLabel(c)) },
                    )
                }
            }
            Text("Permissões".tr(), style = MaterialTheme.typography.labelMedium)
            SwitchRow("Atribuir tickets".tr(), canAssign) { canAssign = it }
            SwitchRow("Apagar tickets".tr(), canDelete) { canDelete = it }
            SwitchRow("Pedir eliminação de conta".tr(), canRequestDeletion) { canRequestDeletion = it }
            SwitchRow("Ver email dos utilizadores".tr(), canViewEmail) { canViewEmail = it }
            state.formError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
            SettingsPrimaryButton(
                if (isNew) "Adicionar".tr() else "Guardar".tr(),
                onClick = {
                    vm.save(
                        SupportTeamAgentRequest(
                            email = email.takeIf { isNew },
                            display_name = name.trim().ifBlank { null },
                            title = title.trim().ifBlank { null },
                            is_active = active,
                            scope = scope,
                            categories = categories.toList(),
                            can_assign = canAssign,
                            can_delete = canDelete,
                            can_request_deletion = canRequestDeletion,
                            can_view_user_email = canViewEmail,
                        ),
                    )
                },
                enabled = !isNew || email.contains('@'),
                loading = state.saving,
            )
            if (!isNew) {
                SettingsOutlinedButton("Remover da equipa".tr(), onClick = { confirmRemove = true }, destructive = true)
            }
        }
    }
    if (confirmRemove) {
        SettingsDialog(
            title = "Remover da equipa".tr(),
            message = "Os tickets abertos atribuídos a este membro ficam sem responsável.".tr(),
            confirmText = "Remover".tr(),
            destructive = true,
            onConfirm = { confirmRemove = false; vm.remove(agent) },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
