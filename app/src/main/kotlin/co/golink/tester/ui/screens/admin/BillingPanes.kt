package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.AdminDiscount
import co.golink.tester.domain.admin.AdminPlan
import co.golink.tester.domain.admin.AdminSubscription
import co.golink.tester.domain.admin.CreateFixedPlanRequest
import co.golink.tester.domain.admin.DiscountRequest
import co.golink.tester.domain.admin.TransactionData
import co.golink.tester.domain.admin.UpdatePlanRequest
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsCard
import co.golink.tester.ui.screens.settings.SettingsDialog
import co.golink.tester.ui.screens.settings.SettingsField
import co.golink.tester.ui.screens.settings.SettingsNotice
import co.golink.tester.ui.screens.settings.SettingsOutlinedButton
import co.golink.tester.ui.screens.settings.SettingsPrimaryButton
import co.golink.tester.ui.screens.settings.TransactionLine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Valores das features de um plano fixo (o PHP manda `[]` quando não há). */
internal fun AdminPlan.fixedFeatures(): Map<String, String> =
    (attributes.features as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()

private fun featureLabel(key: String) = when (key) {
    "max_storage_amount" -> "Armazenamento (GB)".tr()
    "max_team_members" -> "Membros da equipa".tr()
    else -> key
}

private fun subscriptionBadge(a: co.golink.tester.domain.admin.AdminSubscriptionAttributes): Pair<String, Color> = when {
    a.is_suspended -> "Suspensa".tr() to AdminColors.Danger
    a.is_past_due -> "Em atraso".tr() to AdminColors.Warning
    a.is_cancelled -> "Cancelada".tr() to AdminColors.Neutral
    else -> "Ativa".tr() to SettingsAccent
}

// ---------------------------------------------------------------- Planos

@HiltViewModel
class PlansViewModel @Inject constructor(private val repository: AdminRepository) : ViewModel() {
    data class UiState(
        val plans: List<AdminPlan> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val isLoading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val selected: AdminPlan? = null,
        val creating: Boolean = false,
        val subscribers: List<AdminSubscription> = emptyList(),
        val saving: Boolean = false,
        val syncing: Boolean = false,
        val formError: String? = null,
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load(page: Int = 1) {
        _state.update { if (page == 1) it.copy(isLoading = it.plans.isEmpty(), error = null) else it.copy(loadingMore = true) }
        viewModelScope.launch {
            repository.plans(page)
                .onSuccess { (list, more) ->
                    _state.update { s -> s.copy(plans = if (page == 1) list else s.plans + list, page = page, hasMore = more, isLoading = false, loadingMore = false) }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, loadingMore = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun open(plan: AdminPlan?) {
        _state.update { it.copy(selected = plan, subscribers = emptyList(), formError = null) }
        plan ?: return
        viewModelScope.launch {
            repository.planSubscribers(plan.idString).onSuccess { (l, _) -> _state.update { it.copy(subscribers = l) } }
        }
    }

    fun showCreate(show: Boolean) = _state.update { it.copy(creating = show, formError = null) }

    private fun run(success: String, block: suspend () -> Result<*>, after: () -> Unit = {}) {
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(saving = false, toast = success) }; after(); load() }
                .onFailure { t -> _state.update { it.copy(saving = false, formError = t.message) } }
        }
    }

    fun create(r: CreateFixedPlanRequest) = run("Plano criado".tr(), { repository.createFixedPlan(r) }) { showCreate(false) }

    fun saveDetails(plan: AdminPlan, name: String, description: String, visible: Boolean, amount: Double?, features: Map<String, Int>) {
        val id = plan.idString
        run("Alterações guardadas".tr(), {
            runCatching {
                repository.updatePlan(id, UpdatePlanRequest(name, description.ifBlank { null }, visible)).getOrThrow()
                if (amount != null && amount != plan.attributes.amount) repository.updatePlanPrice(id, amount).getOrThrow()
                val changed = features.filter { (k, v) -> plan.fixedFeatures()[k]?.toDoubleOrNull()?.toInt() != v }
                if (changed.isNotEmpty()) repository.updatePlanFeatures(id, changed).getOrThrow()
            }
        }) { _state.update { it.copy(selected = null) } }
    }

    fun delete(plan: AdminPlan) = run("Plano eliminado".tr(), { repository.deletePlan(plan.idString) }) {
        _state.update { it.copy(selected = null) }
    }

    fun synchronize() {
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            repository.synchronizePlans()
                .onSuccess { _state.update { it.copy(syncing = false, toast = "Planos sincronizados".tr()) }; load() }
                .onFailure { t -> _state.update { it.copy(syncing = false, toast = t.message) } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

@Composable
fun PlansPane(isFixed: Boolean, viewModel: PlansViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    state.selected?.let { PlanSheet(it, state, viewModel) }
    if (state.creating) CreatePlanSheet(state, viewModel)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isFixed) "Planos de preço fixo que os utilizadores podem subscrever.".tr()
                        else "Planos por consumo. Para criar planos com medidores usa o site.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::synchronize, enabled = !state.syncing) {
                        Icon(Icons.Outlined.Sync, contentDescription = "Sincronizar planos".tr())
                    }
                }
            }
            when {
                state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                state.error != null -> item { AdminError(state.error!!, { viewModel.load() }, Modifier.fillMaxWidth()) }
                state.plans.isEmpty() -> item { AdminEmpty("Ainda não há planos.".tr()) }
                else -> {
                    items(state.plans, key = { it.idString }) { p ->
                        val a = p.attributes
                        AdminListRow(
                            title = a.name ?: "—",
                            subtitle = listOfNotNull(a.price, a.interval?.let { if (it == "year") "anual".tr() else "mensal".tr() }).joinToString(" · "),
                            onClick = { viewModel.open(p) },
                            trailing = { Text("%d subscritores".tr().format(a.subscribers), style = MaterialTheme.typography.labelSmall) },
                            footer = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                    if (a.status == "archived") AdminBadge("Arquivado".tr(), AdminColors.Neutral)
                                    else if (a.isVisible) AdminBadge("Visível".tr(), SettingsAccent)
                                    else AdminBadge("Oculto".tr(), AdminColors.Warning)
                                    a.discount?.label?.let { AdminBadge(it, AdminColors.Info) }
                                }
                            },
                        )
                    }
                    if (state.hasMore) item { AdminLoadMore(state.loadingMore) { viewModel.load(state.page + 1) } }
                }
            }
        }
        if (isFixed) {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreate(true) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Criar plano".tr()) },
                containerColor = SettingsAccent,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlanSheet(state: PlansViewModel.UiState, vm: PlansViewModel) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("EUR") }
    var interval by remember { mutableStateOf("month") }
    var storage by remember { mutableStateOf("") }
    var members by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { vm.showCreate(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Criar plano".tr(), style = MaterialTheme.typography.titleMedium)
            SettingsField("Nome".tr(), name, { name = it })
            SettingsField("Descrição".tr(), description, { description = it })
            SettingsField("Preço".tr(), amount, { amount = it.replace(',', '.') }, keyboardType = KeyboardType.Decimal)
            Text("Moeda".tr(), style = MaterialTheme.typography.labelMedium)
            AdminSegmented(listOf("EUR" to "EUR", "USD" to "USD", "GBP" to "GBP"), currency, { currency = it })
            Text("Intervalo".tr(), style = MaterialTheme.typography.labelMedium)
            AdminSegmented(listOf("month" to "Mensal".tr(), "year" to "Anual".tr()), interval, { interval = it })
            SettingsField(featureLabel("max_storage_amount"), storage, { storage = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
            SettingsField(featureLabel("max_team_members"), members, { members = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
            state.formError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
            val price = amount.toDoubleOrNull()
            SettingsPrimaryButton(
                "Criar plano".tr(),
                onClick = {
                    vm.create(
                        CreateFixedPlanRequest(
                            name = name.trim(),
                            description = description.trim().ifBlank { null },
                            amount = price ?: 0.0,
                            currency = currency,
                            interval = interval,
                            features = mapOf(
                                "max_storage_amount" to (storage.toIntOrNull() ?: 0),
                                "max_team_members" to (members.toIntOrNull() ?: 0),
                            ),
                        ),
                    )
                },
                enabled = name.isNotBlank() && price != null && price > 0 && storage.isNotBlank() && members.isNotBlank(),
                loading = state.saving,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanSheet(plan: AdminPlan, state: PlansViewModel.UiState, vm: PlansViewModel) {
    val a = plan.attributes
    val isFixed = a.type != "metered"
    val initialFeatures = remember(plan) { plan.fixedFeatures() }
    var name by remember(plan) { mutableStateOf(a.name.orEmpty()) }
    var description by remember(plan) { mutableStateOf(a.description.orEmpty()) }
    var visible by remember(plan) { mutableStateOf(a.isVisible) }
    var amount by remember(plan) { mutableStateOf(a.amount?.toString().orEmpty()) }
    var features by remember(plan) { mutableStateOf(initialFeatures.mapValues { it.value.toDoubleOrNull()?.toInt()?.toString() ?: it.value }) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { vm.open(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(a.name ?: "—", style = MaterialTheme.typography.titleMedium)
            AdminInfoRow("Tipo".tr(), if (isFixed) "Fixo".tr() else "Por consumo".tr())
            AdminInfoRow("Preço".tr(), a.price)
            AdminInfoRow("Subscritores".tr(), a.subscribers.toString())
            AdminInfoRow("Criado".tr(), formatAdminDate(a.created_at) ?: a.created_at)
            SettingsField("Nome".tr(), name, { name = it })
            SettingsField("Descrição".tr(), description, { description = it })
            SwitchRow("Visível para os utilizadores".tr(), visible) { visible = it }
            if (isFixed) {
                SettingsField(
                    "Preço".tr(), amount, { amount = it.replace(',', '.') },
                    keyboardType = KeyboardType.Decimal,
                    supportingText = "Mudar o preço cria um novo preço no gateway; as subscrições atuais mantêm o anterior.".tr(),
                )
                features.forEach { (k, v) ->
                    SettingsField(featureLabel(k), v, { nv -> features = features + (k to nv.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                }
            }
            state.formError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
            SettingsPrimaryButton(
                "Guardar".tr(),
                onClick = {
                    vm.saveDetails(
                        plan, name.trim(), description.trim(), visible,
                        if (isFixed) amount.toDoubleOrNull() else null,
                        if (isFixed) features.mapNotNull { (k, v) -> v.toIntOrNull()?.let { k to it } }.toMap() else emptyMap(),
                    )
                },
                enabled = name.isNotBlank() && (!isFixed || (amount.toDoubleOrNull() ?: 0.0) > 0),
                loading = state.saving,
            )
            if (state.subscribers.isNotEmpty()) {
                Text("Subscritores".tr(), style = MaterialTheme.typography.labelMedium)
                state.subscribers.forEach { s -> SubscriptionLine(s) }
            }
            SettingsOutlinedButton("Eliminar plano".tr(), onClick = { confirmDelete = true }, destructive = true)
        }
    }
    if (confirmDelete) {
        SettingsDialog(
            title = "Eliminar plano".tr(),
            message = if (a.subscribers > 0) "O plano tem subscritores, por isso é arquivado em vez de eliminado.".tr()
            else "O plano é eliminado também no gateway de pagamento.".tr(),
            confirmText = "Eliminar".tr(),
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(plan) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SubscriptionLine(s: AdminSubscription, onDelete: (() -> Unit)? = null) {
    val a = s.attributes
    val user = s.relationships?.user?.data?.attributes
    val (label, color) = subscriptionBadge(a)
    AdminListRow(
        title = user?.name ?: user?.email ?: "—",
        subtitle = listOfNotNull(
            s.relationships?.plan?.data?.attributes?.name ?: a.name,
            a.driver,
            (a.renews_at ?: a.ends_at)?.let { "%s: %s".format(if (a.is_cancelled) "Termina".tr() else "Renova".tr(), formatAdminDate(it) ?: it) },
        ).joinToString(" · "),
        trailing = { AdminBadge(label, color) },
        footer = onDelete?.let { { TextButton(onClick = it) { Text("Apagar registo".tr(), color = MaterialTheme.colorScheme.error) } } },
    )
}

// ---------------------------------------------------------- Subscrições

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(private val repository: AdminRepository) : ViewModel() {
    data class UiState(
        val items: List<AdminSubscription> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val isLoading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load(page: Int = 1) {
        _state.update { if (page == 1) it.copy(isLoading = it.items.isEmpty(), error = null) else it.copy(loadingMore = true) }
        viewModelScope.launch {
            repository.subscriptions(page)
                .onSuccess { (list, more) ->
                    _state.update { s -> s.copy(items = if (page == 1) list else s.items + list, page = page, hasMore = more, isLoading = false, loadingMore = false) }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, loadingMore = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun delete(s: AdminSubscription) = viewModelScope.launch {
        repository.deleteSubscription(s.idString)
            .onSuccess { _state.update { st -> st.copy(items = st.items - s, toast = "Registo apagado".tr()) } }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

@Composable
fun SubscriptionsPane(viewModel: SubscriptionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<AdminSubscription?>(null) }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                state.error != null -> item { AdminError(state.error!!, { viewModel.load() }, Modifier.fillMaxWidth()) }
                state.items.isEmpty() -> item { AdminEmpty("Ainda não há subscrições.".tr()) }
                else -> {
                    items(state.items, key = { it.idString }) { s -> SubscriptionLine(s) { confirm = s } }
                    if (state.hasMore) item { AdminLoadMore(state.loadingMore) { viewModel.load(state.page + 1) } }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    confirm?.let { s ->
        SettingsDialog(
            title = "Apagar registo".tr(),
            message = "Apaga só o registo local da subscrição; não cancela nada no gateway.".tr(),
            confirmText = "Apagar".tr(),
            destructive = true,
            onConfirm = { confirm = null; viewModel.delete(s) },
            onDismiss = { confirm = null },
        )
    }
}

// ------------------------------------------------------------ Descontos

@HiltViewModel
class DiscountsViewModel @Inject constructor(private val repository: AdminRepository) : ViewModel() {
    data class UiState(
        val items: List<AdminDiscount> = emptyList(),
        val plans: List<AdminPlan> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val editing: AdminDiscount? = null,
        val generatedCode: String? = null,
        val saving: Boolean = false,
        val formError: String? = null,
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { repository.plans().onSuccess { (l, _) -> _state.update { it.copy(plans = l.filter { p -> p.attributes.status != "archived" }) } } }
    }

    fun load() {
        _state.update { it.copy(isLoading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.discounts()
                .onSuccess { l -> _state.update { it.copy(items = l, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun edit(d: AdminDiscount?) = _state.update { it.copy(editing = d, formError = null, generatedCode = null) }

    fun generateCode() = viewModelScope.launch {
        repository.generateDiscountCode().onSuccess { c -> _state.update { it.copy(generatedCode = c) } }
    }

    fun save(r: DiscountRequest) {
        val id = _state.value.editing?.idString?.ifBlank { null }
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            repository.saveDiscount(id, r)
                .onSuccess { _state.update { it.copy(saving = false, editing = null, toast = "Alterações guardadas".tr()) }; load() }
                .onFailure { t -> _state.update { it.copy(saving = false, formError = t.message) } }
        }
    }

    fun delete(d: AdminDiscount) = viewModelScope.launch {
        repository.deleteDiscount(d.idString)
            .onSuccess { _state.update { it.copy(editing = null, toast = "Desconto eliminado".tr()) }; load() }
            .onFailure { t -> _state.update { it.copy(formError = t.message) } }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

@Composable
fun DiscountsPane(viewModel: DiscountsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    state.editing?.let { DiscountSheet(it, state, viewModel) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Descontos diretos num plano ou códigos promocionais.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            when {
                state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                state.error != null -> item { AdminError(state.error!!, viewModel::load, Modifier.fillMaxWidth()) }
                state.items.isEmpty() -> item { AdminEmpty("Ainda não há descontos.".tr()) }
                else -> items(state.items, key = { it.idString }) { d ->
                    AdminListRow(
                        title = d.name,
                        subtitle = listOfNotNull(
                            d.label ?: if (d.type == "percent") "-${d.value}%" else "-${d.value}",
                            if (d.kind == "code") d.code else d.plan_name,
                            d.max_redemptions?.let { "${d.redeemed}/$it" },
                        ).joinToString(" · "),
                        onClick = { viewModel.edit(d) },
                        footer = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                AdminBadge(if (d.kind == "code") "Código".tr() else "Direto".tr(), AdminColors.Info)
                                when {
                                    !d.active -> AdminBadge("Inativo".tr(), AdminColors.Neutral)
                                    d.running -> AdminBadge("A decorrer".tr(), SettingsAccent)
                                    else -> AdminBadge("Agendado/terminado".tr(), AdminColors.Warning)
                                }
                            }
                        },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { viewModel.edit(AdminDiscount()) },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Criar desconto".tr()) },
            containerColor = SettingsAccent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscountSheet(d: AdminDiscount, state: DiscountsViewModel.UiState, vm: DiscountsViewModel) {
    val isNew = d.idString.isBlank()
    var name by remember { mutableStateOf(d.name) }
    var kind by remember { mutableStateOf(d.kind) }
    var type by remember { mutableStateOf(d.type) }
    var value by remember { mutableStateOf(if (isNew) "" else d.value.toString()) }
    var planId by remember { mutableStateOf(d.plan_id) }
    var code by remember { mutableStateOf(d.code.orEmpty()) }
    var startsAt by remember { mutableStateOf(d.starts_at?.take(10).orEmpty()) }
    var endsAt by remember { mutableStateOf(d.ends_at?.take(10).orEmpty()) }
    var maxRedemptions by remember { mutableStateOf(d.max_redemptions?.toString().orEmpty()) }
    var active by remember { mutableStateOf(d.active) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.generatedCode) { state.generatedCode?.let { code = it } }
    val dateOk = { s: String -> s.isBlank() || Regex("""\d{4}-\d{2}-\d{2}""").matches(s) }

    ModalBottomSheet(onDismissRequest = { vm.edit(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isNew) "Criar desconto".tr() else d.name, style = MaterialTheme.typography.titleMedium)
            SettingsField("Nome".tr(), name, { name = it })
            Text("Tipo de desconto".tr(), style = MaterialTheme.typography.labelMedium)
            AdminSegmented(listOf("code" to "Código".tr(), "direct" to "Direto no plano".tr()), kind, { kind = it })
            AdminSegmented(listOf("percent" to "Percentagem".tr(), "fixed" to "Valor fixo".tr()), type, { type = it })
            SettingsField("Valor".tr(), value, { value = it.replace(',', '.') }, keyboardType = KeyboardType.Decimal)
            if (kind == "code") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsField("Código".tr(), code, { code = it.uppercase().trim() }, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::generateCode) { Text("Gerar".tr()) }
                }
            }
            Text(if (kind == "direct") "Plano".tr() else "Plano (opcional)".tr(), style = MaterialTheme.typography.labelMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (kind == "code") {
                    PlanChoice("Todos os planos".tr(), planId == null) { planId = null }
                }
                state.plans.forEach { p ->
                    PlanChoice(listOfNotNull(p.attributes.name, p.attributes.price).joinToString(" · "), planId == p.idString) { planId = p.idString }
                }
            }
            SettingsField("Início (AAAA-MM-DD)".tr(), startsAt, { startsAt = it.trim() }, isError = !dateOk(startsAt))
            SettingsField("Fim (AAAA-MM-DD)".tr(), endsAt, { endsAt = it.trim() }, isError = !dateOk(endsAt))
            SettingsField("Máximo de utilizações".tr(), maxRedemptions, { maxRedemptions = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
            SwitchRow("Ativo".tr(), active) { active = it }
            state.formError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
            val v = value.toDoubleOrNull()
            SettingsPrimaryButton(
                if (isNew) "Criar desconto".tr() else "Guardar".tr(),
                onClick = {
                    vm.save(
                        DiscountRequest(
                            name = name.trim(), kind = kind, type = type, value = v ?: 0.0,
                            plan_id = planId, code = code.takeIf { kind == "code" }?.ifBlank { null },
                            starts_at = startsAt.ifBlank { null }, ends_at = endsAt.ifBlank { null },
                            max_redemptions = maxRedemptions.toIntOrNull(), active = active,
                        ),
                    )
                },
                enabled = name.isNotBlank() && v != null && v > 0 && dateOk(startsAt) && dateOk(endsAt) &&
                    (kind != "direct" || planId != null) && (kind != "code" || code.isNotBlank()),
                loading = state.saving,
            )
            if (!isNew) SettingsOutlinedButton("Eliminar desconto".tr(), onClick = { confirmDelete = true }, destructive = true)
        }
    }
    if (confirmDelete) {
        SettingsDialog(
            title = "Eliminar desconto".tr(),
            confirmText = "Eliminar".tr(),
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(d) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PlanChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

// -------------------------------------------------------------- Faturas

@HiltViewModel
class InvoicesViewModel @Inject constructor(private val repository: AdminRepository) : ViewModel() {
    data class UiState(
        val items: List<TransactionData> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val isLoading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val filter: String = "all",
        val query: String = "",
        val selected: TransactionData? = null,
        val busy: Boolean = false,
        val toast: String? = null,
    ) {
        val visible: List<TransactionData>
            get() = items.filter { t ->
                val a = t.attributes
                val u = t.relationships?.user?.data?.attributes
                invoiceStatus(t) .let { filter == "all" || it == filter } &&
                    (query.isBlank() || listOfNotNull(u?.name, u?.email, a.note, a.reference, a.price).any { it.contains(query, true) })
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load(page: Int = 1) {
        _state.update { if (page == 1) it.copy(isLoading = it.items.isEmpty(), error = null) else it.copy(loadingMore = true) }
        viewModelScope.launch {
            repository.transactions(page)
                .onSuccess { (list, more) ->
                    _state.update { s -> s.copy(items = if (page == 1) list else s.items + list, page = page, hasMore = more, isLoading = false, loadingMore = false) }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, loadingMore = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun setFilter(f: String) = _state.update { it.copy(filter = f) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun open(t: TransactionData?) = _state.update { it.copy(selected = t) }

    fun refund(t: TransactionData) = act("Fatura reembolsada".tr()) { repository.refundTransaction(t.idString) }
    fun delete(t: TransactionData) = act("Fatura apagada".tr()) { repository.deleteTransaction(t.idString) }

    private fun act(success: String, block: suspend () -> Result<Unit>) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(busy = false, selected = null, toast = success) }; load() }
                .onFailure { t -> _state.update { it.copy(busy = false, toast = t.message) } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

private fun invoiceStatus(t: TransactionData): String = when {
    t.attributes.is_refunded -> "refunded"
    t.attributes.status == "completed" -> "paid"
    t.attributes.status == "cancelled" -> "cancelled"
    else -> "failed"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesPane(viewModel: InvoicesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<Pair<String, TransactionData>?>(null) }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }
    val counts = remember(state.items) { state.items.groupingBy(::invoiceStatus).eachCount() + ("all" to state.items.size) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AdminSegmented(
                    listOf("all" to "Todas".tr(), "paid" to "Pagas".tr(), "refunded" to "Reembolsadas".tr(), "failed" to "Falhadas".tr()),
                    state.filter, viewModel::setFilter, counts = counts,
                )
            }
            item { AdminSearchField(state.query, viewModel::setQuery, "Pesquisar por nome, email ou referência".tr()) }
            when {
                state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                state.error != null -> item { AdminError(state.error!!, { viewModel.load() }, Modifier.fillMaxWidth()) }
                state.visible.isEmpty() -> item { AdminEmpty("Sem faturas.".tr()) }
                else -> {
                    items(state.visible, key = { it.idString }) { t ->
                        SettingsCard(modifier = Modifier.fillMaxWidth().clickable { viewModel.open(t) }) { TransactionLine(t) }
                    }
                    if (state.hasMore) item { AdminLoadMore(state.loadingMore) { viewModel.load(state.page + 1) } }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.selected?.let { t ->
        val a = t.attributes
        val u = t.relationships?.user?.data?.attributes
        ModalBottomSheet(onDismissRequest = { viewModel.open(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(a.price ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                AdminInfoRow("Utilizador".tr(), listOfNotNull(u?.name, u?.email).joinToString(" · ").ifBlank { null })
                AdminInfoRow("Estado".tr(), a.status)
                AdminInfoRow("Gateway".tr(), a.driver)
                AdminInfoRow("Referência".tr(), a.reference)
                AdminInfoRow("Nota".tr(), a.note)
                AdminInfoRow("Data".tr(), formatAdminDate(a.created_at) ?: a.created_at)
                if (a.status == "completed" && !a.is_refunded && a.driver.equals("stripe", true)) {
                    SettingsPrimaryButton("Reembolsar".tr(), onClick = { confirm = "refund" to t }, loading = state.busy)
                }
                SettingsOutlinedButton("Apagar fatura".tr(), onClick = { confirm = "delete" to t }, destructive = true)
            }
        }
    }
    confirm?.let { (action, t) ->
        SettingsDialog(
            title = if (action == "refund") "Reembolsar".tr() else "Apagar fatura".tr(),
            message = if (action == "refund") "O valor total é devolvido ao cliente através do Stripe.".tr()
            else "Apaga o registo da fatura. Não devolve dinheiro.".tr(),
            confirmText = if (action == "refund") "Reembolsar".tr() else "Apagar".tr(),
            destructive = true,
            onConfirm = { confirm = null; if (action == "refund") viewModel.refund(t) else viewModel.delete(t) },
            onDismiss = { confirm = null },
        )
    }
}
