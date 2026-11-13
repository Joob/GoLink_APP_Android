package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
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
import co.golink.tester.ui.screens.settings.SettingsColors
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
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (%s)".tr().format(t.message.orEmpty())) } }
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

    /** [onStored]: só depois de o servidor aceitar (o ecrã limpa aí os campos). */
    fun storeCredentials(r: PaymentServiceRequest, test: Boolean, onStored: () -> Unit = {}) {
        _state.update { it.copy(saving = r.service) }
        viewModelScope.launch {
            val result = if (test) repository.testPaymentService(r) else repository.storePaymentService(r)
            result
                .onSuccess {
                    _state.update { it.copy(saving = null, toast = if (test) "Ligação ao Stripe OK".tr() else "Credenciais guardadas".tr()) }
                    if (!test) { onStored(); load() }
                }
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
                AdminPageHeader("Dados das faturas, gateways de pagamento e regras de consumo.".tr())
                val paymentsOn = state.values["allowed_payments"].isOn()
                val gateways = listOf(state.stripe?.public_key != null, (state.nowpayments?.is_configured ?: 0) == 1).count { it }
                AdminStatGrid(
                    listOf(
                        AdminStat(
                            "Pagamentos".tr(), if (paymentsOn) "Ativos".tr() else "Desligados".tr(), Icons.Outlined.Payments,
                            if (paymentsOn) SettingsColors.Green else SettingsColors.Slate,
                            onClick = { tab = "gateways" },
                        ),
                        AdminStat(
                            "Gateways configurados".tr(), "%d/2".format(gateways), Icons.Outlined.CreditCard,
                            if (gateways > 0) SettingsColors.Indigo else SettingsColors.Amber,
                            onClick = { tab = "gateways" },
                        ),
                    ),
                )
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
    val changed = draft.any { (k, v) -> state.values[k].orEmpty() != v }
    SettingsCard(
        title = "Dados de faturação".tr(),
        description = "Aparecem nas faturas enviadas aos clientes.".tr(),
        icon = Icons.Outlined.Business,
        iconTint = SettingsColors.Blue,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BILLING_KEYS.forEach { k ->
                SettingsField(
                    labels.getValue(k), draft[k].orEmpty(), { draft = draft + (k to it) },
                    keyboardType = if (k == "billing_phone_number") KeyboardType.Phone else KeyboardType.Text,
                )
            }
            FormActions(
                onDiscard = if (changed) { { draft = BILLING_KEYS.associateWith { state.values[it].orEmpty() } } } else null,
                onSave = { vm.save("billing", draft.mapValues { it.value.trim() }) },
                enabled = changed,
                loading = state.saving == "billing",
            )
        }
    }
}

/** Rodapé Descartar/Guardar das secções (como o ui-section-footer da web). */
@Composable
private fun FormActions(onDiscard: (() -> Unit)?, onSave: () -> Unit, enabled: Boolean = true, loading: Boolean = false, saveText: String = "Guardar".tr()) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        if (onDiscard != null) TextButton(onClick = onDiscard, enabled = !loading) { Text("Descartar".tr()) }
        Spacer(Modifier.width(4.dp))
        SettingsPrimaryButton(saveText, onClick = onSave, enabled = enabled, loading = loading)
    }
}

@Composable
private fun GatewaysTab(state: PaymentsViewModel.UiState, vm: PaymentsViewModel) {
    SettingsCard(title = "Pagamentos".tr(), icon = Icons.Outlined.Payments, iconTint = SettingsColors.Green) {
        SwitchRow(
            "Permitir pagamentos".tr(),
            state.values["allowed_payments"].isOn(),
            description = "Desligado, ninguém consegue subscrever nem carregar saldo.".tr(),
        ) { vm.toggle("allowed_payments", it) }
    }
    GatewayCard("stripe", "Stripe", state.stripe, state, vm)
    GatewayCard("nowpayments", "NOWPayments (cripto)", state.nowpayments, state, vm)
}

@Composable
private fun GatewayCard(service: String, title: String, config: GatewayConfig?, state: PaymentsViewModel.UiState, vm: PaymentsViewModel) {
    val configured = if (service == "stripe") config?.public_key != null else (config?.is_configured ?: 0) == 1
    val active = state.values["allowed_$service"].isOn()
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var webhook by remember { mutableStateOf("") }
    val savedDescription = state.values["${service}_payment_description"].orEmpty()
    var description by remember(state.values) { mutableStateOf(savedDescription) }
    SettingsCard(
        title = title,
        icon = if (service == "stripe") Icons.Outlined.CreditCard else Icons.Outlined.CurrencyBitcoin,
        iconTint = if (service == "stripe") SettingsColors.Indigo else SettingsColors.Orange,
        trailing = {
            if (!configured) AdminStatusPill("Por configurar".tr(), AdminTone.Warning)
            else if (active) AdminStatusPill("Ativo".tr(), AdminTone.Success)
            else AdminStatusPill("Desligado".tr(), AdminTone.Neutral)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Ativo".tr(), active) { vm.toggle("allowed_$service", it) }
            SettingsField("Descrição do pagamento".tr(), description, { description = it })
            if (description.trim() != savedDescription) {
                FormActions(
                    onDiscard = { description = savedDescription },
                    onSave = { vm.save(service, mapOf("${service}_payment_description" to description.trim())) },
                    loading = state.saving == service,
                    saveText = "Guardar descrição".tr(),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text("Credenciais".tr(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "As credenciais nunca são mostradas. Preenche para substituir as atuais.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (service == "stripe" && config?.public_key != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chave pública".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    AdminCodeChip(config.public_key.take(12) + "…")
                }
            }
            SettingsField(if (service == "stripe") "Chave pública".tr() else "Chave de API".tr(), key, { key = it.trim() })
            SettingsField(if (service == "stripe") "Chave secreta".tr() else "Segredo IPN".tr(), secret, { secret = it.trim() }, isPassword = true)
            if (service == "stripe") SettingsField("Segredo do webhook".tr(), webhook, { webhook = it.trim() }, isPassword = true)
            val request = PaymentServiceRequest(service, key, secret, webhook.ifBlank { null })
            val ready = key.isNotBlank() && secret.isNotBlank()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (service == "stripe") {
                    SettingsOutlinedButton("Testar ligação".tr(), onClick = { vm.storeCredentials(request, test = true) }, enabled = ready && state.saving == null)
                }
                SettingsPrimaryButton(
                    "Guardar credenciais".tr(),
                    // Num erro os campos ficam preenchidos para corrigir e repetir.
                    onClick = { vm.storeCredentials(request, test = false) { key = ""; secret = ""; webhook = "" } },
                    enabled = ready,
                    loading = state.saving == service,
                )
            }
        }
    }
}

@Composable
private fun MeteredTab(state: PaymentsViewModel.UiState, vm: PaymentsViewModel) {
    val savedBonus = state.values["registration_bonus_amount"].orEmpty()
    val savedLimit = state.values["limit_usage_in_new_accounts_amount"].orEmpty()
    var bonus by remember(state.values) { mutableStateOf(savedBonus) }
    var limit by remember(state.values) { mutableStateOf(savedLimit) }
    val valid = (bonus.isBlank() || bonus.toDoubleOrNull() != null) && (limit.isBlank() || limit.toDoubleOrNull() != null)
    val changed = bonus != savedBonus || limit != savedLimit
    SettingsCard(title = "Bónus de registo".tr(), icon = Icons.Outlined.CardGiftcard, iconTint = SettingsColors.Pink) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Dar crédito a contas novas".tr(), state.values["allowed_registration_bonus"].isOn()) { vm.toggle("allowed_registration_bonus", it) }
            SettingsField("Valor do bónus".tr(), bonus, { bonus = it.replace(',', '.') }, keyboardType = KeyboardType.Decimal, isError = bonus.isNotBlank() && bonus.toDoubleOrNull() == null)
        }
    }
    SettingsCard(title = "Limites de consumo".tr(), icon = Icons.Outlined.Speed, iconTint = SettingsColors.Amber) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Limitar consumo em contas novas".tr(), state.values["limit_usage_in_new_accounts"].isOn()) { vm.toggle("limit_usage_in_new_accounts", it) }
            SettingsField("Limite".tr(), limit, { limit = it.replace(',', '.') }, keyboardType = KeyboardType.Decimal, isError = limit.isNotBlank() && limit.toDoubleOrNull() == null)
            SwitchRow("Bloquear quando o consumo excede o saldo".tr(), state.values["usage_bigger_than_balance"].isOn()) { vm.toggle("usage_bigger_than_balance", it) }
        }
    }
    if (!valid) SettingsNotice("Valor inválido".tr(), MaterialTheme.colorScheme.error)
    FormActions(
        onDiscard = if (changed) { { bonus = savedBonus; limit = savedLimit } } else null,
        onSave = { vm.save("metered", mapOf("registration_bonus_amount" to bonus, "limit_usage_in_new_accounts_amount" to limit)) },
        enabled = valid && changed,
        loading = state.saving == "metered",
    )
}
