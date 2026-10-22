package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.AbuseReport
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Denúncias de links públicos (paridade com Support Desk > Denúncias na web).
 * Bloquear apaga o link, impede repartilhar o item e avisa o dono.
 */
@HiltViewModel
class AbuseReportsViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val status: String = "open",
        val reports: List<AbuseReport> = emptyList(),
        val page: Int = 1,
        val lastPage: Int = 1,
        val openCount: Int = 0,
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        /** "<id>:<ação>" em curso. */
        val busy: String? = null,
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load(page: Int = 1) {
        val status = _state.value.status
        _state.update { if (page == 1) it.copy(isLoading = it.reports.isEmpty(), error = null) else it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            repository.abuseReports(status, page)
                .onSuccess { r ->
                    _state.update {
                        it.copy(
                            reports = if (page == 1) r.data else it.reports + r.data,
                            page = r.meta.current_page,
                            lastPage = r.meta.last_page,
                            openCount = r.meta.open,
                            isLoading = false,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, isLoadingMore = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun setStatus(status: String) {
        _state.update { it.copy(status = status, reports = emptyList()) }
        load()
    }

    fun act(report: AbuseReport, action: String) {
        _state.update { it.copy(busy = "${report.id}:$action") }
        viewModelScope.launch {
            repository.abuseReportAction(report.id, action)
                .onSuccess {
                    val msg = when (action) {
                        "block" -> "Link bloqueado.".tr()
                        "dismiss" -> "Denúncias rejeitadas.".tr()
                        else -> "Bloqueio levantado.".tr()
                    }
                    _state.update { it.copy(busy = null, toast = msg) }
                    load()
                }
                .onFailure { t -> _state.update { it.copy(busy = null, toast = t.message) } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

internal fun abuseReasonLabel(reason: String) = when (reason) {
    "malware" -> "Malware ou vírus".tr()
    "phishing" -> "Phishing ou fraude".tr()
    "illegal" -> "Conteúdo ilegal".tr()
    "child_abuse" -> "Abuso sexual de menores".tr()
    "copyright" -> "Violação de direitos de autor".tr()
    "spam" -> "Spam"
    else -> "Outro motivo".tr()
}

private fun abuseStatusLabel(status: String) = when (status) {
    "open" -> "Abertas".tr()
    "blocked" -> "Bloqueadas".tr()
    "dismissed" -> "Rejeitadas".tr()
    "lifted" -> "Levantadas".tr()
    else -> "Todas".tr()
}

private fun priorityLabel(p: String) = when (p) {
    "high" -> "Alta".tr()
    "low" -> "Baixa".tr()
    else -> "Normal".tr()
}

@Composable
fun AbuseReportsPane(
    onOpenUser: (String) -> Unit,
    viewModel: AbuseReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    var confirm by remember { mutableStateOf<Pair<AbuseReport, String>?>(null) }
    confirm?.let { (report, action) ->
        SettingsDialog(
            title = when (action) {
                "block" -> "Bloquear link".tr()
                "dismiss" -> "Rejeitar".tr()
                else -> "Levantar bloqueio".tr()
            },
            message = when (action) {
                "block" -> "Bloquear este link? Vai ser apagado, o item não pode voltar a ser partilhado e o dono é avisado.".tr()
                "dismiss" -> "Rejeitar todas as denúncias abertas deste link?".tr()
                else -> "Levantar o bloqueio? O dono volta a poder partilhar este item.".tr()
            },
            confirmText = "Confirmar".tr(),
            destructive = action == "block",
            onConfirm = { confirm = null; viewModel.act(report, action) },
            onDismiss = { confirm = null },
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Denúncias de links públicos. Bloquear apaga o link, impede voltar a partilhar o item e avisa o dono.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AdminSegmented(
                    options = listOf("open", "blocked", "dismissed", "lifted", "all").map { it to abuseStatusLabel(it) },
                    selected = state.status,
                    onSelect = viewModel::setStatus,
                    counts = if (state.openCount > 0) mapOf("open" to state.openCount) else emptyMap(),
                )
            }
            when {
                state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                state.error != null -> item { AdminError(state.error!!, { viewModel.load() }, Modifier.fillMaxWidth()) }
                state.reports.isEmpty() -> item { AdminEmpty("Sem denúncias aqui.".tr()) }
                else -> {
                    items(state.reports, key = { it.id }) { r ->
                        AbuseReportRow(r, state.busy, onOpenUser, onAction = { action -> confirm = r to action })
                    }
                    if (state.page < state.lastPage) {
                        item { AdminLoadMore(state.isLoadingMore) { viewModel.load(state.page + 1) } }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbuseReportRow(
    r: AbuseReport,
    busy: String?,
    onOpenUser: (String) -> Unit,
    onAction: (String) -> Unit,
) {
    val uri = LocalUriHandler.current
    AdminListRow(
        title = abuseReasonLabel(r.reason),
        subtitle = listOfNotNull(
            formatAdminDate(r.created_at),
            if (r.reports_count > 1) "%d denúncias neste link".tr().format(r.reports_count) else null,
        ).joinToString(" · "),
        trailing = {
            r.ai_priority?.let { p ->
                val color = when (p) {
                    "high" -> AdminColors.Danger
                    "normal" -> AdminColors.Warning
                    else -> AdminColors.Neutral
                }
                AdminBadge("IA · " + priorityLabel(p), color)
            }
        },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.ai_summary?.let {
                    val extra = r.ai_category?.takeIf { c -> c != r.reason }?.let { c -> " (" + "A IA sugere: %s".tr().format(abuseReasonLabel(c)) + ")" }.orEmpty()
                    Text("IA: $it$extra", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                r.message?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    r.link + (r.item_type?.let { " ($it)" }.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.link_active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (r.link_active) null else TextDecoration.LineThrough,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AdminInfoRow("Dono".tr(), r.owner?.let { o -> listOfNotNull(o.name, o.email).joinToString(" · ") })
                r.reporter_email?.let { AdminInfoRow("Denunciado por".tr(), it) }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (r.link_active) TextButton(onClick = { uri.openUri(r.link) }) { Text("Abrir link".tr()) }
                    r.owner?.let { o -> TextButton(onClick = { onOpenUser(o.id) }) { Text("Ver dono".tr()) } }
                    val enabled = busy == null
                    when (r.status) {
                        "open" -> {
                            TextButton(onClick = { onAction("dismiss") }, enabled = enabled) { Text("Rejeitar".tr()) }
                            TextButton(onClick = { onAction("block") }, enabled = enabled) {
                                Text("Bloquear".tr(), color = AdminColors.Danger)
                            }
                        }
                        "blocked" -> TextButton(onClick = { onAction("lift") }, enabled = enabled) { Text("Levantar bloqueio".tr()) }
                        else -> Text(
                            abuseStatusLabel(r.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
    )
}
