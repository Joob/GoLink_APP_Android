package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import co.golink.tester.data.config.ConfigRepository
import co.golink.tester.domain.admin.PaymentServiceRequest
import co.golink.tester.domain.config.GatewayConfig
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsCard
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

private val BILLING_KEYS = listOf(
    "billing_name", "billing_vat_number", "billing_country", "billing_address",
    "billing_city", "billing_postal_code", "billing_state", "billing_phone_number",
)
private val PAYMENT_KEYS = listOf(
    "allowed_payments", "allowed_stripe", "allowed_nowpayments",
    "stripe_payment_description", "nowpayments_payment_description",
)
private val METERED_KEYS = listOf(
    "allowed_registration_bonus", "registration_bonus_amount",
    "limit_usage_in_new_accounts", "limit_usage_in_new_accounts_amount", "usage_bigger_than_balance",
)

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val repository: AdminRepository,
    private val configRepository: ConfigRepository,
) : ViewModel() {
    data class UiState(
        val values: Map<String, String?> = emptyMap(),
        val subscriptionType: String = "none",
        val stripe: GatewayConfig? = null,
        val nowpayments: GatewayConfig? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val saving: String? = null,
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = it.values.isEmpty(), error = null) }
        viewModelScope.launch {
            val config = configRepository.load(force = true).getOrNull()
            repository.settings(*(BILLING_KEYS + PAYMENT_KEYS + METERED_KEYS).toTypedArray())
                .onSuccess { map ->
                    _state.update {
                        it.copy(
                            values = map,
                            subscriptionType = config?.payments?.type ?: it.subscriptionType,
                            stripe = config?.gateways?.stripe,
                            nowpayments = config?.gateways?.nowpayments,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    /** Grava só as chaves que mudaram, uma a uma (é o que o PATCH aceita). */
    fun save(section: String, changes: Map<String, String>) {
        val diff = changes.filter { (k, v) -> _state.value.values[k].orEmpty() != v }
        if (diff.isEmpty()) return _state.update { it.copy(toast = "Sem alterações".tr()) }
        _state.update { it.copy(saving = section) }
        viewModelScope.launch {
            val failure = diff.entries.firstNotNullOfOrNull { (k, v) -> repository.updateSetting(k, v).exceptionOrNull() }
            _state.update { s ->
                s.copy(
                    saving = null,
                    values = if (failure == null) s.values + diff else s.values,
                    toast = failure?.message ?: "Alterações guardadas".tr(),
                )
            }
        }
    }

    fun toggle(key: String, on: Boolean) = save(key, mapOf(key to if (on) "1" else "0"))

    fun storeCredentials(r: PaymentServiceRequest, test: Boolean) {
        _state.update { it.copy(saving = r.service) }
        viewModelScope.launch {
            val result = if (test) repository.testPaymentService(r) else repository.storePaymentService(r)
            result
                .onSuccess { _state.update { it.copy(saving = null, toast = if (test) "Ligação ao Stripe OK".tr() else "Credenciais guardadas".tr()) }; if (!test) load() }
                .onFailure { t -> _state.update { it.copy(saving = null, toast = t.message) } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

private fun String?.isOn() = this == "1" || this == "true"

@Composable
fun PaymentsPane(viewModel: PaymentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf("billing") }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> AdminLoading()
            state.error != null -> AdminError(state.error!!, viewModel::load)
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val tabs = buildList {
                    add("billing" to "Dados de faturação".tr())
                    add("gateways" to "Gateways".tr())
                    if (state.subscriptionType == "metered") add("metered" to "Consumo".tr())
                }
                AdminSegmented(tabs, tab, { tab = it })
                when (tab) {
                    "gateways" -> GatewaysTab(state, viewModel)
                    "metered" -> MeteredTab(state, viewModel)
                    else -> BillingInfoTab(state, viewModel)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BillingInfoTab(state: PaymentsViewModel.UiState, vm: PaymentsViewModel) {
    val labels = mapOf(
        "billing_name" to "Nome da empresa".tr(),
        "billing_vat_number" to "NIF / IVA".tr(),
        "billing_country" to "País".tr(),
        "billing_address" to "Morada".tr(),
        "billing_city" to "Cidade".tr(),
        "billing_postal_code" to "Código postal".tr(),
        "billing_state" to "Distrito / Estado".tr(),
        "billing_phone_number" to "Telefone".tr(),
    )
    var draft by remember(state.values) { mutableStateOf(BILLING_KEYS.associateWith { state.values[it].orEmpty() }) }
    SettingsCard(title = "Dados de faturação".tr(), description = "Aparecem nas faturas enviadas aos clientes.".tr()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BILLING_KEYS.forEach { k ->
                SettingsField(
                    labels.getValue(k), draft[k].orEmpty(), { draft = draft + (k to it) },
                    keyboardType = if (k == "billing_phone_number") KeyboardType.Phone else KeyboardType.Text,
                )
            }
            SettingsPrimaryButton("Guardar".tr(), onClick = { vm.save("billing", draft.mapValues { it.value.trim() }) }, loading = state.saving == "billing")
        }
    }
}

@Composable
private fun GatewaysTab(state: PaymentsViewModel.UiState, vm: PaymentsViewModel) {
    SettingsCard(title = "Pagamentos".tr()) {
        SwitchRow("Permitir pagamentos".tr(), state.values["allowed_payments"].isOn()) { vm.toggle("allowed_payments", it) }
    }
    GatewayCard("stripe", "Stripe", state.stripe, state, vm)
    GatewayCard("nowpayments", "NOWPayments (cripto)", state.nowpayments, state, vm)
}

@Composable
private fun GatewayCard(service: String, title: String, config: GatewayConfig?, state: PaymentsViewModel.UiState, vm: PaymentsViewModel) {
    val configured = if (service == "stripe") config?.public_key != null else (config?.is_configured ?: 0) == 1
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var webhook by remember { mutableStateOf("") }
    var description by remember(state.values) { mutableStateOf(state.values["${service}_payment_description"].orEmpty()) }
    SettingsCard(
        title = title,
        trailing = { if (configured) AdminBadge("Configurado".tr(), SettingsAccent) else AdminBadge("Por configurar".tr(), AdminColors.Warning) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Ativo".tr(), state.values["allowed_$service"].isOn()) { vm.toggle("allowed_$service", it) }
            SettingsField("Descrição do pagamento".tr(), description, { description = it })
            SettingsOutlinedButton("Guardar descrição".tr(), onClick = { vm.save(service, mapOf("${service}_payment_description" to description.trim())) })
            Text(
                "As credenciais nunca são mostradas. Preenche para substituir as atuais.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (service == "stripe" && config?.public_key != null) {
                AdminInfoRow("Chave pública".tr(), config.public_key.take(12) + "…")
            }
            SettingsField(if (service == "stripe") "Chave pública".tr() else "Chave de API".tr(), key, { key = it.trim() })
            SettingsField(if (service == "stripe") "Chave secreta".tr() else "Segredo IPN".tr(), secret, { secret = it.trim() }, isPassword = true)
            if (service == "stripe") SettingsField("Segredo do webhook".tr(), webhook, { webhook = it.trim() }, isPassword = true)
            val request = PaymentServiceRequest(service, key, secret, webhook.ifBlank { null })
            val ready = key.isNotBlank() && secret.isNotBlank()
            SettingsPrimaryButton(
                "Guardar credenciais".tr(),
                onClick = { vm.storeCredentials(request, test = false); key = ""; secret = ""; webhook = "" },
                enabled = ready,
                loading = state.saving == service,
            )
            if (service == "stripe") {
                SettingsOutlinedButton("Testar ligação".tr(), onClick = { vm.storeCredentials(request, test = true) }, enabled = ready && state.saving == null)
            }
        }
    }
}

@Composable
private fun MeteredTab(state: PaymentsViewModel.UiState, vm: PaymentsViewModel) {
    var bonus by remember(state.values) { mutableStateOf(state.values["registration_bonus_amount"].orEmpty()) }
    var limit by remember(state.values) { mutableStateOf(state.values["limit_usage_in_new_accounts_amount"].orEmpty()) }
    SettingsCard(title = "Bónus de registo".tr()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Dar crédito a contas novas".tr(), state.values["allowed_registration_bonus"].isOn()) { vm.toggle("allowed_registration_bonus", it) }
            SettingsField("Valor do bónus".tr(), bonus, { bonus = it.replace(',', '.') }, keyboardType = KeyboardType.Decimal)
        }
    }
    SettingsCard(title = "Limites de consumo".tr()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Limitar consumo em contas novas".tr(), state.values["limit_usage_in_new_accounts"].isOn()) { vm.toggle("limit_usage_in_new_accounts", it) }
            SettingsField("Limite".tr(), limit, { limit = it.replace(',', '.') }, keyboardType = KeyboardType.Decimal)
            SwitchRow("Bloquear quando o consumo excede o saldo".tr(), state.values["usage_bigger_than_balance"].isOn()) { vm.toggle("usage_bigger_than_balance", it) }
        }
    }
    if (bonus.isNotBlank() && bonus.toDoubleOrNull() == null || limit.isNotBlank() && limit.toDoubleOrNull() == null) {
        SettingsNotice("Valor inválido".tr(), MaterialTheme.colorScheme.error)
    }
    SettingsPrimaryButton(
        "Guardar".tr(),
        onClick = { vm.save("metered", mapOf("registration_bonus_amount" to bonus, "limit_usage_in_new_accounts_amount" to limit)) },
        enabled = (bonus.isBlank() || bonus.toDoubleOrNull() != null) && (limit.isBlank() || limit.toDoubleOrNull() != null),
        loading = state.saving == "metered",
        modifier = Modifier.fillMaxWidth(),
    )
}
