package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.screens.settings.SettingsIconTile
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.AuditLogData
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val logs: List<AuditLogData> = emptyList(),
        val action: String = "",
        val onlyFailed: Boolean = false,
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val page: Int = 1,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    init { load() }

    fun setAction(value: String) {
        _state.update { it.copy(action = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(350); load() }
    }

    fun toggleFailed() {
        _state.update { it.copy(onlyFailed = !it.onlyFailed) }
        load()
    }

    fun clearFilters() {
        _state.update { it.copy(action = "", onlyFailed = false) }
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { fetch(1) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { fetch(s.page + 1) }
    }

    private suspend fun fetch(page: Int) {
        val s = _state.value
        repository.auditLogs(page, s.action, s.onlyFailed)
            .onSuccess { (rows, more) ->
                _state.update { it.copy(logs = if (page == 1) rows else (it.logs + rows).distinctBy { x -> x.idString }, hasMore = more, page = page, isLoading = false, isLoadingMore = false) }
            }
            .onFailure { t ->
                _state.update { it.copy(isLoading = false, isLoadingMore = false, error = if (page == 1) "Não foi possível carregar (%s)".tr().format(t.message.orEmpty()) else it.error) }
            }
    }
}

private fun statusTone(status: Int) = when {
    status >= 400 -> AdminTone.Danger
    status >= 300 -> AdminTone.Info
    else -> AdminTone.Success
}

@Composable
fun AuditLogPane(viewModel: AuditLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<AuditLogData?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = AdminListPadding) {
        adminBlock("header") {
            AdminPageHeader("Ações feitas na administração: quem, o quê e quando.".tr()) {
                if (state.onlyFailed || state.action.isNotEmpty()) {
                    AdminButton("Limpar filtros".tr(), onClick = viewModel::clearFilters, primary = false)
                }
            }
        }
        adminBlock("toolbar") {
            AdminToolbar(state.action, viewModel::setAction, "Filtrar por ação".tr()) {
                AdminSegmented(
                    listOf(false to "Todas".tr(), true to "Só falhas".tr()),
                    state.onlyFailed,
                    { if (it != state.onlyFailed) viewModel.toggleFailed() },
                )
            }
        }
        when {
            state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
            state.error != null -> item { AdminError(state.error!!, viewModel::load, Modifier.fillMaxWidth()) }
            state.logs.isEmpty() -> item { AdminEmptyState("Sem registos.".tr(), icon = Icons.Outlined.History) }
            else -> adminListGroup(state.logs, key = { it.idString }) { log ->
                val a = log.attributes
                val failed = (a.status ?: 0) >= 400
                // Linhas falhadas tingidas, como na tabela da web.
                Box(if (failed) Modifier.background(AdminTone.Danger.background().copy(alpha = 0.45f)) else Modifier) {
                    AdminListItem(
                        title = a.action ?: a.path ?: "—",
                        subtitle = listOfNotNull(a.actor_name, a.target_name).joinToString(" → ").ifBlank { null },
                        onClick = { open = log },
                        leading = {
                            SettingsIconTile(
                                if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.History,
                                if (failed) AdminTone.Danger.solid else AdminAccent,
                                size = 32,
                            )
                        },
                        trailing = { a.status?.let { AdminStatusPill(it.toString(), statusTone(it)) } },
                        footer = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 5.dp),
                            ) {
                                a.method?.let { AdminCodeChip(it) }
                                formatAdminDate(a.created_at)?.let {
                                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        },
                    )
                }
            }
        }
        if (!state.isLoading && state.hasMore) item { AdminLoadMore(state.isLoadingMore, viewModel::loadMore) }
    }

    open?.let { AuditLogSheet(it) { open = null } }
}

private val prettyJson = Json { prettyPrint = true }

private fun JsonElement?.pretty(): String? =
    this?.takeUnless { it is JsonNull }?.let { runCatching { prettyJson.encodeToString(JsonElement.serializer(), it) }.getOrNull() }
        ?.takeUnless { it == "[]" || it == "{}" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogSheet(log: AuditLogData, onDismiss: () -> Unit) {
    val a = log.attributes
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.action ?: "—", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                a.status?.let { AdminStatusPill(it.toString(), statusTone(it)) }
            }
            SettingsCard {
                AdminInfoRow("Autor".tr(), a.actor_name)
                AdminInfoRow("Alvo".tr(), a.target_name)
                AdminInfoRow("Método".tr(), a.method)
                AdminInfoRow("Caminho".tr(), a.path)
                AdminInfoRow("Estado".tr(), a.status?.toString())
                AdminInfoRow("IP", a.ip)
                AdminInfoRow("Data".tr(), formatAdminDate(a.created_at))
                AdminInfoRow("Navegador".tr(), a.user_agent)
            }
            listOf("Parâmetros".tr() to a.params.pretty(), "Dados enviados".tr() to a.payload.pretty()).forEach { (label, body) ->
                if (body != null) {
                    SettingsCard(title = label) {
                        Text(body, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        }
    }
}
