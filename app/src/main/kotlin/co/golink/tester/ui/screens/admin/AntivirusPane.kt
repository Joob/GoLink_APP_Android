package co.golink.tester.ui.screens.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.AntivirusOverview
import co.golink.tester.domain.admin.QuarantinedFile
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Antivírus (paridade com Admin › Definições › Antivírus na web): estado do
 * clamd, contagens e ficheiros em quarentena.
 */
@HiltViewModel
class AntivirusViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val overview: AntivirusOverview? = null,
        val files: List<QuarantinedFile> = emptyList(),
        val page: Int = 1,
        val lastPage: Int = 1,
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        val busy: String? = null,
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load(page: Int = 1) {
        _state.update { if (page == 1) it.copy(isLoading = it.overview == null, error = null) else it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            repository.antivirus(page)
                .onSuccess { r ->
                    _state.update {
                        it.copy(
                            overview = r,
                            files = if (page == 1) r.data else (it.files + r.data).distinctBy { x -> x.id },
                            page = r.meta.current_page,
                            lastPage = r.meta.last_page,
                            isLoading = false,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, isLoadingMore = false, error = "Não foi possível carregar (%s)".tr().format(t.message.orEmpty())) } }
        }
    }

    fun rescan(file: QuarantinedFile) = run(file, "rescan", "Ficheiro em fila para nova análise.".tr()) { repository.antivirusRescan(file.id) }

    fun delete(file: QuarantinedFile) = run(file, "delete", "Ficheiro apagado.".tr()) { repository.antivirusDelete(file.id) }

    private fun run(file: QuarantinedFile, action: String, done: String, call: suspend () -> Result<Unit>) {
        _state.update { it.copy(busy = "${file.id}:$action") }
        viewModelScope.launch {
            call()
                .onSuccess { _state.update { it.copy(busy = null, toast = done) }; load() }
                .onFailure { t -> _state.update { it.copy(busy = null, toast = t.message) } }
        }
    }

    /** Interruptor do admin (setting antivirus_enabled), como na web. */
    fun toggle(on: Boolean) {
        _state.update { it.copy(overview = it.overview?.copy(enabled = on), busy = "toggle") }
        viewModelScope.launch {
            repository.updateSetting("antivirus_enabled", if (on) "1" else "0")
                .onSuccess { _state.update { it.copy(busy = null) }; load() }
                .onFailure { t -> _state.update { it.copy(overview = it.overview?.copy(enabled = !on), busy = null, toast = t.message) } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

/** Comandos para desligar/ligar no servidor (iguais aos da web). */
private val SERVER_STEPS = listOf(
    "1. Parar os workers do antivírus (e impedir que voltem a arrancar)" to
        "sudo supervisorctl stop golink-antivirus:*\nsudo sed -i 's/autostart=true/autostart=false/' /etc/supervisor/conf.d/golink-antivirus.conf\nsudo supervisorctl reread && sudo supervisorctl update",
    "2. Opcional: parar o ClamAV (liberta RAM)" to
        "sudo systemctl disable --now clamav-daemon clamav-freshclam",
    "Para voltar a ligar: arranca o ClamAV, os workers e liga acima" to
        "sudo systemctl enable --now clamav-daemon clamav-freshclam\nsudo sed -i 's/autostart=false/autostart=true/' /etc/supervisor/conf.d/golink-antivirus.conf\nsudo supervisorctl reread && sudo supervisorctl update\nsudo supervisorctl start golink-antivirus:*",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AntivirusPane(
    onOpenUser: (String) -> Unit,
    viewModel: AntivirusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    var confirmDelete by remember { mutableStateOf<QuarantinedFile?>(null) }
    // Desligar deixa todos os uploads sem análise: pede confirmação (ligar não).
    var confirmDisable by remember { mutableStateOf(false) }
    if (confirmDisable) {
        SettingsDialog(
            title = "Desligar o antivírus?".tr(),
            message = "Os uploads novos deixam de ser analisados até voltares a ligar. Os ficheiros enviados entretanto não ficam em quarentena.".tr(),
            confirmText = "Desligar".tr(),
            destructive = true,
            onConfirm = { confirmDisable = false; viewModel.toggle(false) },
            onDismiss = { confirmDisable = false },
        )
    }
    confirmDelete?.let { f ->
        SettingsDialog(
            title = "Apagar definitivamente".tr(),
            message = "Apagar este ficheiro definitivamente? Não é possível desfazer.".tr(),
            confirmText = "Apagar".tr(),
            destructive = true,
            onConfirm = { confirmDelete = null; viewModel.delete(f) },
            onDismiss = { confirmDelete = null },
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = AdminListPadding) {
            when {
                state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                state.error != null -> item { AdminError(state.error!!, { viewModel.load() }, Modifier.fillMaxWidth()) }
                else -> {
                    val o = state.overview!!
                    adminBlock("header") {
                        AdminPageHeader("Os ficheiros não cifrados são analisados em segundo plano. Os ficheiros cifrados ponta a ponta não podem ser lidos e não são analisados.".tr())
                    }
                    adminBlock("engine", bottom = 16) {
                        AdminSection(
                            "Antivírus ligado".tr(),
                            icon = Icons.Outlined.Shield,
                            tint = if (o.configured && o.enabled && o.version != null) AdminAccent else AdminTone.Danger.solid,
                            description = "Desligado, os uploads não são analisados nem entram novas análises na fila.".tr(),
                            actions = if (o.configured) {
                                {
                                    co.golink.tester.ui.screens.settings.SettingsSwitch(
                                        checked = o.enabled,
                                        onCheckedChange = { on -> if (on) viewModel.toggle(true) else confirmDisable = true },
                                        enabled = state.busy == null,
                                    )
                                }
                            } else null,
                        ) {
                            when {
                                !o.configured -> AdminStatusPill("CLAMAV_ENABLED=false", AdminTone.Danger)
                                !o.enabled -> AdminStatusPill("Desligado".tr(), AdminTone.Neutral)
                                o.version == null -> AdminStatusPill("clamd inacessível em %s".tr().format(o.socket.orEmpty()), AdminTone.Danger)
                                else -> AdminStatusPill(o.version, AdminTone.Success)
                            }
                        }
                    }
                    adminBlock("kpis", bottom = 16) {
                        AdminStatGrid(
                            listOf(
                                AdminStat("Limpos".tr(), o.count("clean").toString(), Icons.Outlined.VerifiedUser),
                                AdminStat("Em quarentena".tr(), o.count("infected").toString(), Icons.Outlined.GppBad,
                                    if (o.count("infected") > 0) AdminTone.Danger.solid else AdminTone.Neutral.solid),
                                AdminStat("Pendentes".tr(), o.count("pending").toString(), Icons.Outlined.HourglassEmpty, AdminTone.Warning.solid),
                                AdminStat("Erros".tr(), o.count("error").toString(), Icons.Outlined.ErrorOutline, AdminTone.Danger.solid,
                                    hint = "%s: %d".format("Demasiado grandes".tr(), o.count("skipped"))),
                            ),
                        )
                    }
                    adminBlock("server", bottom = 16) {
                        AdminSection(
                            "Desligar no servidor".tr(),
                            icon = Icons.Outlined.Terminal,
                            tint = AdminTone.Neutral.solid,
                            description = "Depois de desligares acima, para os workers e, se quiseres, o próprio ClamAV para libertar 1,5–3 GB de RAM.".tr(),
                        ) {
                            SERVER_STEPS.forEachIndexed { i, (label, cmd) ->
                                Text(label.tr(), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = if (i == 0) 0.dp else 10.dp, bottom = 4.dp))
                                SelectionContainer {
                                    Text(
                                        cmd,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.fillMaxWidth()
                                            .background(co.golink.tester.ui.screens.settings.settingsInsetColor(), RoundedCornerShape(10.dp))
                                            .horizontalScroll(rememberScrollState())
                                            .padding(10.dp),
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                    item(key = "q-title") { co.golink.tester.ui.screens.settings.SettingsSectionTitle("Ficheiros em quarentena".tr()) }
                    if (state.files.isEmpty()) {
                        item { AdminEmptyState("Nenhum ficheiro em quarentena.".tr(), icon = Icons.Outlined.VerifiedUser) }
                    } else {
                        adminListGroup(state.files, key = { it.id }) { f ->
                            AdminListItem(
                                title = f.name ?: "—",
                                subtitle = listOfNotNull(f.owner?.email, formatAdminDate(f.scanned_at)).joinToString(" · "),
                                leading = { co.golink.tester.ui.screens.settings.SettingsIconTile(Icons.Outlined.GppBad, AdminTone.Danger.solid, size = 36) },
                                footer = {
                                    f.signature?.let { Row(Modifier.padding(top = 5.dp)) { AdminCodeChip(it) } }
                                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        f.owner?.let { o -> TextButton(onClick = { onOpenUser(o.id) }) { Text("Ver dono".tr()) } }
                                        TextButton(onClick = { viewModel.rescan(f) }, enabled = state.busy == null) { Text("Analisar de novo".tr()) }
                                        TextButton(onClick = { confirmDelete = f }, enabled = state.busy == null) {
                                            Text("Apagar".tr(), color = AdminTone.Danger.solid)
                                        }
                                    }
                                },
                            )
                        }
                        if (state.page < state.lastPage) {
                            item { AdminLoadMore(state.isLoadingMore) { viewModel.load(state.page + 1) } }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
