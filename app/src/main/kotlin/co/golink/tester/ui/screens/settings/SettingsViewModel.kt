package co.golink.tester.ui.screens.settings

import co.golink.tester.ui.i18n.tr
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.AppLogger
import co.golink.tester.data.LogEntry
import co.golink.tester.data.auth.AuthRepository
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.data.billing.BillingRepository
import co.golink.tester.data.config.BackendUrlHolder
import co.golink.tester.data.settings.AppSecurityPreferences
import co.golink.tester.data.settings.SettingsRepository
import co.golink.tester.domain.billing.Plan
import co.golink.tester.domain.settings.AccessToken
import co.golink.tester.domain.settings.SecurityEventData
import co.golink.tester.domain.settings.SessionItem
import co.golink.tester.domain.settings.StorageUsage
import co.golink.tester.domain.settings.TransactionItem
import co.golink.tester.domain.user.User
import coil.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val storage: StorageUsage? = null,
    val sessions: List<SessionItem> = emptyList(),
    val securityEvents: List<SecurityEventData> = emptyList(),
    val isLoadingSecurityEvents: Boolean = false,
    val hasMoreSecurityEvents: Boolean = false,
    val securityEventsPage: Int = 1,
    val transactions: List<TransactionItem> = emptyList(),
    val tokens: List<AccessToken> = emptyList(),
    val isLoadingStorage: Boolean = false,
    val isLoadingSessions: Boolean = false,
    val isLoadingTransactions: Boolean = false,
    val isLoadingTokens: Boolean = false,
    val isUpdatingPassword: Boolean = false,
    val isChangingPassphrase: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    val twoFactorEnabled: Boolean = false,
    val newTokenPlaintext: String? = null,
    val toast: String? = null,
    val logEntries: List<LogEntry> = emptyList(),
    val plans: List<Plan> = emptyList(),
    val isLoadingPlans: Boolean = false,
    val isStartingCheckout: Boolean = false,
    val checkoutUrl: String? = null,
    // Plano escolhido à espera que o utilizador escolha cartão ou crypto. null = sem diálogo.
    val pendingCheckoutPlan: Plan? = null,
    // Overlay de saída (revoke da sessão atual / de todas as outras). null = escondido.
    val logoutOverlay: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val securityPrefs: AppSecurityPreferences,
    private val logger: AppLogger,
    private val imageLoader: ImageLoader,
    private val backendUrlHolder: BackendUrlHolder,
    private val billingRepository: BillingRepository,
    private val e2eKeyManager: co.golink.tester.data.encryption.E2EKeyManager,
) : ViewModel() {

    val user: StateFlow<User?> = sessionManager.state
        .map { (it as? AuthState.Authenticated)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val backendUrl: StateFlow<String> = backendUrlHolder.state

    // E2E: fingerprint da chave pública (deteta key-substitution). Só disponível
    // com a encriptação desbloqueada (precisa da chave em memória).
    val e2eUnlocked: StateFlow<Boolean> = e2eKeyManager.unlocked
    fun e2eFingerprint(): String? = e2eKeyManager.fingerprint()

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val hasPinSet: Boolean get() = securityPrefs.hasPinSet

    init {
        loadStorage()
        loadSessions()
        loadTokens()
        _state.update {
            it.copy(
                biometricEnabled = securityPrefs.biometricEnabled,
                pinEnabled = securityPrefs.pinEnabled,
            )
        }
        viewModelScope.launch {
            user.collect { u ->
                _state.update { it.copy(twoFactorEnabled = u?.twoFactorEnabled == true) }
            }
        }
    }

    fun refreshUser() = viewModelScope.launch { sessionManager.refreshUser() }

    private val _isUpdatingAvatar = MutableStateFlow(false)
    val isUpdatingAvatar: StateFlow<Boolean> = _isUpdatingAvatar

    fun updateAvatar(jpeg: ByteArray) {
        _isUpdatingAvatar.value = true
        viewModelScope.launch {
            repository.updateAvatar(jpeg)
                .onSuccess {
                    sessionManager.refreshUser()
                    _state.update { it.copy(toast = "Imagem de perfil actualizada".tr()) }
                }
                .onFailure { t -> _state.update { it.copy(toast = "Falha: ${t.message}") } }
            _isUpdatingAvatar.value = false
        }
    }

    fun resetCsrf() {
        viewModelScope.launch {
            logger.log("Security", "Token CSRF redefinido".tr())
            _state.update { it.copy(toast = "Token CSRF redefinido".tr()) }
        }
    }

    fun loadStorage() {
        _state.update { it.copy(isLoadingStorage = true) }
        viewModelScope.launch {
            repository.storage()
                .onSuccess { s -> _state.update { it.copy(storage = s, isLoadingStorage = false) } }
                .onFailure { t -> _state.update { it.copy(isLoadingStorage = false, toast = t.message) } }
        }
    }

    fun loadSessions() {
        _state.update { it.copy(isLoadingSessions = true) }
        viewModelScope.launch {
            repository.sessions()
                .onSuccess { l -> _state.update { it.copy(sessions = l, isLoadingSessions = false) } }
                .onFailure { t -> _state.update { it.copy(isLoadingSessions = false, toast = t.message) } }
        }
    }

    /** Recarrega o histórico de segurança desde o início. */
    fun loadSecurityEvents() = fetchSecurityEvents(page = 1, append = false)

    fun loadMoreSecurityEvents() {
        val s = _state.value
        if (s.isLoadingSecurityEvents || !s.hasMoreSecurityEvents) return

        fetchSecurityEvents(page = s.securityEventsPage + 1, append = true)
    }

    private fun fetchSecurityEvents(page: Int, append: Boolean) {
        _state.update { it.copy(isLoadingSecurityEvents = true) }
        viewModelScope.launch {
            repository.securityEvents(page)
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            securityEvents = if (append) it.securityEvents + result.items else result.items,
                            hasMoreSecurityEvents = result.hasMore,
                            securityEventsPage = page,
                            isLoadingSecurityEvents = false,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(isLoadingSecurityEvents = false, toast = t.message) }
                }
        }
    }

    fun revokeSession(id: String) = viewModelScope.launch {
        val wasCurrent = _state.value.sessions.firstOrNull { it.id == id }?.is_current == true
        // Revogar a sessão atual desloga → mostrar já o overlay "A sair da conta…".
        if (wasCurrent) _state.update { it.copy(logoutOverlay = "A sair da conta…".tr()) }
        repository.revokeSession(id)
            .onSuccess {
                if (wasCurrent) {
                    // Overlay fica até o logout limpar o token e o RootGate navegar para fora.
                    authRepository.logout()
                } else {
                    _state.update { it.copy(toast = "Sessão revogada".tr(), sessions = it.sessions.filterNot { s -> s.id == id }) }
                }
            }
            .onFailure { t -> _state.update { it.copy(logoutOverlay = null, toast = t.message) } }
    }

    fun revokeAllSessions() = viewModelScope.launch {
        _state.update { it.copy(logoutOverlay = "A sair de todas as sessões…".tr()) }
        repository.revokeAllSessions()
            .onSuccess {
                _state.update { it.copy(logoutOverlay = null, toast = "Todas as outras sessões revogadas".tr()) }
                loadSessions()
            }
            .onFailure { t -> _state.update { it.copy(logoutOverlay = null, toast = t.message) } }
    }

    fun updatePassword(current: String, newPassword: String) {
        _state.update { it.copy(isUpdatingPassword = true) }
        viewModelScope.launch {
            repository.updatePassword(current, newPassword)
                .onSuccess { _state.update { it.copy(isUpdatingPassword = false, toast = "Password actualizada".tr()) } }
                .onFailure { t -> _state.update { it.copy(isUpdatingPassword = false, toast = t.message) } }
        }
    }

    // Muda a passphrase E2E: re-embrulha a chave privada (em memória) com o novo
    // segredo. NÃO re-cifra ficheiros — continuam selados à mesma chave pública.
    fun changeEncryptionPassphrase(secret: String) {
        if (secret.length < 8) {
            _state.update { it.copy(toast = "Mínimo 8 caracteres".tr()) }
            return
        }
        _state.update { it.copy(isChangingPassphrase = true) }
        viewModelScope.launch {
            runCatching { e2eKeyManager.rotateSecret(secret) }
                .onSuccess { _state.update { it.copy(isChangingPassphrase = false, toast = "Passphrase alterada".tr()) } }
                .onFailure { t -> _state.update { it.copy(isChangingPassphrase = false, toast = t.message ?: "Falha ao alterar passphrase".tr()) } }
        }
    }

    fun updateProfileField(name: String, value: String) {
        _state.update { it.copy(isUpdatingProfile = true) }
        viewModelScope.launch {
            repository.updateProfileField(name, value)
                .onSuccess {
                    sessionManager.refreshUser()
                    _state.update { it.copy(isUpdatingProfile = false, toast = "Perfil actualizado".tr()) }
                }
                .onFailure { t -> _state.update { it.copy(isUpdatingProfile = false, toast = t.message) } }
        }
    }

    fun loadTokens() {
        _state.update { it.copy(isLoadingTokens = true) }
        viewModelScope.launch {
            repository.listTokens()
                .onSuccess { l -> _state.update { it.copy(tokens = l, isLoadingTokens = false) } }
                .onFailure { t -> _state.update { it.copy(isLoadingTokens = false, toast = t.message) } }
        }
    }

    fun createToken(name: String) = viewModelScope.launch {
        repository.createToken(name)
            .onSuccess { plaintext -> _state.update { it.copy(newTokenPlaintext = plaintext) }; loadTokens() }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun deleteToken(id: Long) = viewModelScope.launch {
        repository.deleteToken(id)
            .onSuccess { _state.update { it.copy(tokens = it.tokens.filterNot { t -> t.id == id }) } }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun consumeNewToken() = _state.update { it.copy(newTokenPlaintext = null) }

    fun loadTransactions() {
        _state.update { it.copy(isLoadingTransactions = true) }
        viewModelScope.launch {
            repository.transactions()
                .onSuccess { l -> _state.update { it.copy(transactions = l, isLoadingTransactions = false) } }
                .onFailure { t -> _state.update { it.copy(isLoadingTransactions = false, toast = t.message) } }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securityPrefs.biometricEnabled = enabled
        logger.log("Security", if (enabled) "Biometria activada" else "Biometria desactivada")
        _state.update { it.copy(biometricEnabled = enabled) }
    }

    fun enablePin(pin: String) {
        securityPrefs.setPinHash(pin)
        securityPrefs.pinEnabled = true
        logger.log("Security", "PIN de bloqueio activado")
        _state.update { it.copy(pinEnabled = true) }
    }

    fun disablePin(pin: String): Boolean {
        if (!securityPrefs.verifyPin(pin)) return false
        securityPrefs.clearPin()
        logger.log("Security", "PIN de bloqueio desactivado")
        _state.update { it.copy(pinEnabled = false) }
        return true
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache() {
        viewModelScope.launch {
            try {
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                context.cacheDir.deleteRecursively()
                logger.log("Cache", "Cache local limpa".tr())
                _state.update { it.copy(toast = "Cache limpa com sucesso".tr()) }
            } catch (e: Exception) {
                _state.update { it.copy(toast = "Erro ao limpar cache".tr()) }
            }
        }
    }

    fun refreshLog() {
        _state.update { it.copy(logEntries = logger.getEntries()) }
    }

    fun clearLog() {
        logger.clear()
        _state.update { it.copy(logEntries = emptyList()) }
    }

    fun logout() = viewModelScope.launch { authRepository.logout() }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    fun loadPlans() {
        if (_state.value.isLoadingPlans || _state.value.plans.isNotEmpty()) return
        _state.update { it.copy(isLoadingPlans = true) }
        viewModelScope.launch {
            billingRepository.listPlans()
                .onSuccess { l -> _state.update { it.copy(plans = l, isLoadingPlans = false) } }
                .onFailure { t -> _state.update { it.copy(isLoadingPlans = false, toast = "Erro a carregar planos: ${t.message}") } }
        }
    }

    /** Abre o seletor de método de pagamento para o plano escolhido. */
    fun selectPlan(plan: Plan) = _state.update { it.copy(pendingCheckoutPlan = plan) }

    fun dismissPaymentMethods() = _state.update { it.copy(pendingCheckoutPlan = null) }

    fun startStripeCheckout(plan: Plan) {
        val priceId = plan.stripePriceId
        if (priceId.isNullOrBlank()) {
            _state.update { it.copy(toast = "Este plano não tem Stripe configurado".tr()) }
            return
        }
        startCheckout { billingRepository.createStripeCheckout(priceId) }
    }

    /**
     * O checkout crypto usa o id do plano local, por isso está sempre
     * disponível: não depende de um plano espelhado no gateway.
     */
    fun startCryptoCheckout(plan: Plan) {
        startCheckout { billingRepository.createCryptoCheckout(plan.id) }
    }

    private fun startCheckout(request: suspend () -> Result<String>) {
        _state.update { it.copy(isStartingCheckout = true, pendingCheckoutPlan = null) }
        viewModelScope.launch {
            request()
                .onSuccess { url -> _state.update { it.copy(isStartingCheckout = false, checkoutUrl = url) } }
                .onFailure { t -> _state.update { it.copy(isStartingCheckout = false, toast = "Erro a iniciar checkout: ${t.message}") } }
        }
    }

    fun consumeCheckoutUrl() = _state.update { it.copy(checkoutUrl = null) }
}
