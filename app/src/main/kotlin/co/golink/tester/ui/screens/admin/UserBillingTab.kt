package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.AdminSubscription
import co.golink.tester.domain.admin.TransactionData
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsCard
import co.golink.tester.ui.screens.settings.SettingsField
import co.golink.tester.ui.screens.settings.SettingsNotice
import co.golink.tester.ui.screens.settings.SettingsPrimaryButton
import co.golink.tester.ui.screens.settings.TransactionLine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UserBillingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: AdminRepository,
) : ViewModel() {
    private val userId: String = checkNotNull(savedState["id"])

    data class UiState(
        val subscription: AdminSubscription? = null,
        val transactions: List<TransactionData> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val isLoading: Boolean = true,
        val error: String? = null,
        val crediting: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load(page: Int = 1) {
        if (page == 1) _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            if (page == 1) repository.userSubscription(userId).onSuccess { s -> _state.update { it.copy(subscription = s) } }
            repository.userTransactions(userId, page)
                .onSuccess { (l, more) ->
                    _state.update { s -> s.copy(transactions = if (page == 1) l else s.transactions + l, page = page, hasMore = more, isLoading = false) }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun credit(amount: Double) {
        _state.update { it.copy(crediting = true, message = null) }
        viewModelScope.launch {
            repository.creditUser(userId, amount)
                .onSuccess { _state.update { it.copy(crediting = false, message = "Crédito adicionado".tr()) }; load() }
                .onFailure { t -> _state.update { it.copy(crediting = false, message = t.message) } }
        }
    }
}

@Composable
internal fun UserBillingTab(subscriptionType: String, viewModel: UserBillingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.isLoading) return AdminLoading(Modifier.fillMaxWidth().height(160.dp))
    state.error?.let { return AdminError(it, { viewModel.load() }, Modifier.fillMaxWidth()) }

    SettingsCard(title = "Subscrição".tr()) {
        val s = state.subscription
        if (s == null) {
            AdminInfoRow("Plano".tr(), "Sem subscrição".tr())
        } else {
            val a = s.attributes
            AdminInfoRow("Plano".tr(), s.relationships?.plan?.data?.attributes?.name ?: a.name)
            AdminInfoRow("Estado".tr(), when {
                a.is_suspended -> "Suspensa".tr()
                a.is_past_due -> "Em atraso".tr()
                a.is_cancelled -> "Cancelada".tr()
                else -> "Ativa".tr()
            })
            AdminInfoRow("Gateway".tr(), a.driver)
            AdminInfoRow("Desde".tr(), formatAdminDate(a.created_at) ?: a.created_at)
            AdminInfoRow(if (a.is_cancelled) "Termina".tr() else "Renova".tr(), (a.renews_at ?: a.ends_at)?.let { formatAdminDate(it) ?: it })
            if (a.is_past_due) AdminInfoRow("Dias para pagar".tr(), a.days_left_to_pay.toString())
        }
    }

    if (subscriptionType == "metered") {
        var amount by remember { mutableStateOf("") }
        SettingsCard(title = "Adicionar crédito".tr()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsField("Valor".tr(), amount, { amount = it.replace(',', '.') }, keyboardType = KeyboardType.Decimal)
                state.message?.let { SettingsNotice(it, SettingsAccent) }
                val v = amount.toDoubleOrNull()
                SettingsPrimaryButton(
                    "Adicionar crédito".tr(),
                    onClick = { viewModel.credit(v ?: 0.0); amount = "" },
                    enabled = v != null && v > 0,
                    loading = state.crediting,
                )
            }
        }
    }

    SettingsCard(title = "Transações".tr()) {
        if (state.transactions.isEmpty()) {
            AdminInfoRow("—", "Sem transações.".tr())
        } else {
            state.transactions.forEach { TransactionLine(it) }
            if (state.hasMore) AdminLoadMore(false) { viewModel.load(state.page + 1) }
        }
    }
}
