package co.golink.tester.ui.screens.settings

import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.i18n.bt
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resultado de um PIN pedido nas Definições (mesmas esperas do ecrã de bloqueio). */
sealed interface PinCheck {
    data object Ok : PinCheck
    data object Wrong : PinCheck
    data class LockedOut(val seconds: Int) : PinCheck
}

data class SettingsUiState(
    val storage: StorageUsage? = null,
    val storageInsights: co.golink.tester.domain.settings.StorageInsights? = null,
    /** Tamanho da cache local (miniaturas, temporários) — painel Bloqueio da app. */
    val cacheBytes: Long? = null,
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
    // Muda a cada password alterada com sucesso: o formulário só limpa os campos aí.
    val passwordChangedAt: Long = 0L,
    // Alteração de email: idle → code (código enviado ao novo email).
    val emailChangeStep: String = "idle",
    val emailChangeTarget: String = "",
    val emailChangeExpiresAt: Long = 0L,
    val isChangingEmail: Boolean = false,
    val emailChangeError: String? = null,
    val isChangingPassphrase: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    val lockDelayMs: Long = 0L,
    val hideInRecents: Boolean = true,
    val twoFactorEnabled: Boolean = false,
    val newTokenPlaintext: String? = null,
    val isCreatingToken: Boolean = false,
    val createTokenError: String? = null,
    val toast: String? = null,
    val logEntries: List<LogEntry> = emptyList(),
    // Billing (só leitura: pagamentos só na web).
    val billing: co.golink.tester.domain.billing.BillingSummary? = null,
    val plans: List<Plan> = emptyList(),
    val isLoadingBilling: Boolean = false,
    val invoices: List<co.golink.tester.domain.billing.Invoice> = emptyList(),
    val invoicesPage: Int = 0,
    val hasMoreInvoices: Boolean = false,
    val isLoadingInvoices: Boolean = false,
    // id da fatura a descarregar ("all" = ZIP). null = nenhuma.
    val downloadingInvoice: String? = null,
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
    autoBackupPreferences: co.golink.tester.data.backup.AutoBackupPreferences,
    private val autoBackupManager: co.golink.tester.data.backup.AutoBackupManager,
    private val fileViewerSession: co.golink.tester.ui.screens.viewer.FileViewerSession,
    private val uploadManager: co.golink.tester.data.upload.UploadManager,
    private val appLockBioKey: co.golink.tester.data.settings.AppLockBiometricKey,
) : ViewModel() {

    /** Estado do backup automático (cartão no menu das Definições). */
    val autoBackupState = autoBackupPreferences.state

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
        // O cartão do backup reflecte pausas feitas na web sem abrir o ecrã do backup.
        viewModelScope.launch { autoBackupManager.syncServerState() }
        // Armazenamento serve o menu e a faturação; sessões e tokens carregam por painel (LaunchedEffect(route)).
        loadStorage()
        _state.update {
            it.copy(
                biometricEnabled = securityPrefs.biometricEnabled,
                pinEnabled = securityPrefs.pinEnabled,
                lockDelayMs = securityPrefs.lockDelayMs,
                hideInRecents = securityPrefs.hideInRecents,
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
        if (_isUpdatingAvatar.value) return
        _isUpdatingAvatar.value = true
        viewModelScope.launch {
            repository.updateAvatar(jpeg)
                .onSuccess {
                    sessionManager.refreshUser()
                    _state.update { it.copy(toast = "Imagem de perfil actualizada".tr()) }
                }
                .onFailure { t -> _state.update { it.copy(toast = t.message ?: "Falha ao atualizar a imagem".tr()) } }
            _isUpdatingAvatar.value = false
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

    /** Detalhe do painel Armazenamento (falha em silêncio: o painel funciona sem ele). */
    fun loadStorageInsights() {
        viewModelScope.launch {
            repository.storageInsights().onSuccess { i -> _state.update { it.copy(storageInsights = i) } }
        }
    }

    /** Abre um ficheiro (ex.: maiores ficheiros) no visualizador. */
    fun prepareViewer(file: co.golink.tester.domain.browse.BrowseItem.File) {
        fileViewerSession.files = listOf(file)
        fileViewerSession.startId = file.id
        fileViewerSession.fromTrash = false
        fileViewerSession.readOnlyIds = emptySet()
        fileViewerSession.notOwnedIds = emptySet()
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

    // Só as outras sessões: a atual não tem "Terminar" (termina-se em Sair ou nos Tokens, com overlay + logout).
    fun revokeSession(id: String) = viewModelScope.launch {
        repository.revokeSession(id)
            .onSuccess {
                _state.update { it.copy(toast = "Sessão revogada".tr(), sessions = it.sessions.filterNot { s -> s.id == id }) }
            }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun revokeAllSessions() = viewModelScope.launch {
        _state.update { it.copy(logoutOverlay = "A terminar as outras sessões…".tr()) }
        repository.revokeAllSessions()
            .onSuccess {
                _state.update { it.copy(logoutOverlay = null, toast = "Todas as outras sessões revogadas".tr()) }
                loadSessions()
            }
            .onFailure { t -> _state.update { it.copy(logoutOverlay = null, toast = t.message) } }
    }

    fun updatePassword(current: String, newPassword: String, passkeyToken: String? = null) {
        _state.update { it.copy(isUpdatingPassword = true) }
        viewModelScope.launch {
            repository.updatePassword(current, newPassword, passkeyToken)
                .onSuccess {
                    _state.update {
                        it.copy(isUpdatingPassword = false, passwordChangedAt = System.currentTimeMillis(), toast = "Password actualizada".tr())
                    }
                }
                .onFailure { t -> _state.update { it.copy(isUpdatingPassword = false, toast = t.message) } }
        }
    }

    fun requestEmailChange(email: String, password: String, passkeyToken: String? = null) {
        _state.update { it.copy(isChangingEmail = true, emailChangeError = null) }
        viewModelScope.launch {
            repository.requestEmailChange(email, password, passkeyToken)
                .onSuccess { ttl ->
                    _state.update {
                        it.copy(
                            isChangingEmail = false,
                            emailChangeStep = "code",
                            emailChangeTarget = email.trim(),
                            emailChangeExpiresAt = System.currentTimeMillis() + ttl * 1000L,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(isChangingEmail = false, emailChangeError = t.message) } }
        }
    }

    fun confirmEmailChange(code: String) {
        _state.update { it.copy(isChangingEmail = true, emailChangeError = null) }
        viewModelScope.launch {
            repository.confirmEmailChange(code)
                .onSuccess {
                    sessionManager.refreshUser()
                    _state.update {
                        it.copy(
                            isChangingEmail = false,
                            emailChangeStep = "idle",
                            emailChangeTarget = "",
                            toast = "Email alterado. As outras sessões foram terminadas.".tr(),
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(isChangingEmail = false, emailChangeError = t.message) } }
        }
    }

    /**
     * Reenviar o código depois de confirmar com passkey: o token já foi gasto, por isso
     * volta ao 1.º passo (email mantido) para confirmar de novo com password ou passkey.
     */
    fun restartEmailChangeForResend() {
        _state.update {
            it.copy(
                emailChangeStep = "idle",
                emailChangeError = "Confirma de novo com a password ou a passkey para reenviar o código.".tr(),
            )
        }
    }

    fun cancelEmailChange() {
        _state.update { it.copy(emailChangeStep = "idle", emailChangeTarget = "", emailChangeError = null) }
    }

    // Mudança da passphrase E2E em 3 passos (atual → código por email → nova).
    suspend fun verifyCurrentPassphrase(secret: String): Boolean = e2eKeyManager.verifyCurrentSecret(secret)
    suspend fun requestPassphraseCode() = e2eKeyManager.requestSecretCode()
    suspend fun confirmPassphraseCode(code: String): String = e2eKeyManager.confirmSecretCode(code)

    // Re-embrulha a chave privada (em memória) com o novo segredo. NÃO re-cifra
    // ficheiros — continuam selados à mesma chave pública. `onDone(ok, tokenExpired)`.
    fun changeEncryptionPassphrase(secret: String, changeToken: String, onDone: (Boolean, Boolean) -> Unit) {
        _state.update { it.copy(isChangingPassphrase = true) }
        viewModelScope.launch {
            runCatching { e2eKeyManager.rotateSecret(secret, changeToken) }
                .onSuccess {
                    _state.update { it.copy(isChangingPassphrase = false, toast = "Passphrase alterada".tr()) }
                    onDone(true, false)
                }
                .onFailure { t ->
                    val expired = t is co.golink.tester.data.encryption.ChangeTokenExpiredException
                    _state.update {
                        it.copy(
                            isChangingPassphrase = false,
                            toast = if (expired) "A confirmação expirou. Confirma de novo com um código.".tr()
                            else t.message ?: "Falha ao alterar passphrase".tr(),
                        )
                    }
                    onDone(false, expired)
                }
        }
    }

    fun updateProfileField(name: String, value: String) = updateProfileFields(mapOf(name to value))

    // Um campo de cada vez; pára no primeiro erro e só refresca o utilizador uma vez.
    fun updateProfileFields(changes: Map<String, String>) {
        if (changes.isEmpty() || _state.value.isUpdatingProfile) return
        _state.update { it.copy(isUpdatingProfile = true) }
        viewModelScope.launch {
            var failure: Throwable? = null
            for ((name, value) in changes) {
                failure = repository.updateProfileField(name, value).exceptionOrNull()
                if (failure != null) break
            }
            sessionManager.refreshUser()
            _state.update {
                it.copy(
                    isUpdatingProfile = false,
                    toast = failure?.let { t -> t.message ?: "Falha ao atualizar o perfil".tr() } ?: "Perfil actualizado".tr(),
                )
            }
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

    fun createToken(name: String, password: String?, passkeyToken: String?) {
        if (_state.value.isCreatingToken) return
        _state.update { it.copy(isCreatingToken = true, createTokenError = null) }
        viewModelScope.launch {
            repository.createToken(name, password, passkeyToken)
                .onSuccess { plaintext ->
                    _state.update { it.copy(isCreatingToken = false, newTokenPlaintext = plaintext) }
                    loadTokens()
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(isCreatingToken = false, createTokenError = t.message ?: "Não foi possível criar o token".tr())
                    }
                }
        }
    }

    fun clearCreateTokenError() = _state.update { it.copy(createTokenError = null) }

    /** Revogar o token desta sessão termina-a: overlay + logout, como no painel Sessões. */
    fun deleteToken(id: Long) = viewModelScope.launch {
        val wasCurrent = _state.value.tokens.firstOrNull { it.id == id }?.is_current == true
        if (wasCurrent) _state.update { it.copy(logoutOverlay = "A sair da conta…".tr()) }
        repository.deleteToken(id)
            .onSuccess {
                if (wasCurrent) authRepository.logout()
                else _state.update { it.copy(tokens = it.tokens.filterNot { t -> t.id == id }) }
            }
            .onFailure { t -> _state.update { it.copy(logoutOverlay = null, toast = t.message) } }
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
        // Religar a biometria (após confirmação) começa com uma chave nova do Keystore.
        if (enabled) appLockBioKey.reset()
        securityPrefs.biometricEnabled = enabled
        logger.log("Security", if (enabled) "Biometria activada" else "Biometria desactivada")
        _state.update { it.copy(biometricEnabled = enabled) }
    }

    // PBKDF2 + EncryptedSharedPreferences: sempre fora da main thread.
    fun enablePin(pin: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { securityPrefs.setPinHash(pin) }
            securityPrefs.pinEnabled = true
            logger.log("Security", "PIN de bloqueio activado")
            _state.update { it.copy(pinEnabled = true) }
        }
    }

    /**
     * Verifica o PIN com o mesmo contador/esperas do ecrã de bloqueio: ao fim de
     * [AppSecurityPreferences.MAX_FAILURES] falhas a sessão é terminada.
     */
    private suspend fun checkPin(pin: String): PinCheck {
        securityPrefs.lockoutRemainingMs().let { if (it > 0) return PinCheck.LockedOut(((it + 999) / 1000).toInt()) }
        if (withContext(Dispatchers.Default) { securityPrefs.verifyPin(pin) }) {
            securityPrefs.resetFailures()
            return PinCheck.Ok
        }
        val n = securityPrefs.registerFailure()
        logger.log("Security", "PIN errado nas Definições ($n)")
        if (n >= AppSecurityPreferences.MAX_FAILURES) {
            logger.log("Security", "Demasiados PINs errados: sessão terminada")
            securityPrefs.resetFailures()
            authRepository.logout()
            return PinCheck.Wrong
        }
        val left = securityPrefs.lockoutRemainingMs()
        return if (left > 0) PinCheck.LockedOut(((left + 999) / 1000).toInt()) else PinCheck.Wrong
    }

    /** Mudar o PIN: só com o PIN atual certo. */
    fun changePin(current: String, new: String, onResult: (PinCheck) -> Unit) {
        viewModelScope.launch {
            val r = checkPin(current)
            if (r == PinCheck.Ok) {
                withContext(Dispatchers.Default) { securityPrefs.setPinHash(new) }
                logger.log("Security", "PIN de bloqueio alterado")
            }
            onResult(r)
        }
    }

    /**
     * Desligar o bloqueio (interruptor geral): pede o PIN quando há um; desliga
     * o PIN e a biometria.
     */
    fun disableAppLock(pin: String?, onResult: (PinCheck) -> Unit) {
        viewModelScope.launch {
            if (securityPrefs.hasPinSet) {
                val r = if (pin == null) PinCheck.Wrong else checkPin(pin)
                if (r != PinCheck.Ok) { onResult(r); return@launch }
                withContext(Dispatchers.IO) { securityPrefs.clearPin() }
            }
            securityPrefs.pinEnabled = false
            securityPrefs.biometricEnabled = false
            logger.log("Security", "Bloqueio da app desactivado")
            _state.update { it.copy(pinEnabled = false, biometricEnabled = false) }
            onResult(PinCheck.Ok)
        }
    }

    fun setLockDelay(ms: Long) {
        securityPrefs.lockDelayMs = ms
        _state.update { it.copy(lockDelayMs = ms) }
    }

    fun setHideInRecents(on: Boolean) {
        securityPrefs.hideInRecents = on
        _state.update { it.copy(hideInRecents = on) }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache() {
        viewModelScope.launch {
            try {
                imageLoader.memoryCache?.clear()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    imageLoader.diskCache?.clear()
                    clearCacheDirKeepingUploads()
                }
                logger.log("Cache", "Cache local limpa".tr())
                _state.update { it.copy(toast = "Cache limpa com sucesso".tr()) }
                loadCacheSize()
            } catch (e: Exception) {
                _state.update { it.copy(toast = "Erro ao limpar cache".tr()) }
            }
        }
    }

    /**
     * Apaga a cache, mas não os temporários de uploads: "camera/" é da fila retomável
     * e, com uploads por acabar, os e2e_/newtext_/newdoc_ ainda vão ser enviados.
     */
    private fun clearCacheDirKeepingUploads() {
        val busy = uploadManager.tasks.value.any { t ->
            t.state == co.golink.tester.data.upload.UploadTask.State.Queued ||
                t.state == co.golink.tester.data.upload.UploadTask.State.Uploading ||
                (t.state == co.golink.tester.data.upload.UploadTask.State.Failed &&
                    (t.networkError || t.e2eLocked || t.waitingForNetwork))
        }
        val uploadPrefixes = listOf("e2e_", "newtext_", "newdoc_")
        context.cacheDir.listFiles()?.forEach { f ->
            val keep = f.name == "camera" || (busy && uploadPrefixes.any { f.name.startsWith(it) })
            if (!keep) f.deleteRecursively()
        }
    }

    fun loadCacheSize() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = listOfNotNull(context.cacheDir, context.externalCacheDir)
                .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            _state.update { it.copy(cacheBytes = bytes) }
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

    // ── Billing (só leitura) ────────────────────────────────────────────
    // Sem checkout, troca de plano, cartões nem cancelamento: tudo isso só na
    // web (política de pagamentos da Play). Aqui só se lê e se editam os dados
    // de faturação, e se descarregam faturas já pagas.

    /** Resumo + planos + 1.ª página de faturas. */
    fun loadBilling() {
        if (_state.value.isLoadingBilling) return
        _state.update { it.copy(isLoadingBilling = true) }
        viewModelScope.launch {
            val summary = billingRepository.summary()
            val plans = billingRepository.listPlans()
            _state.update {
                it.copy(
                    isLoadingBilling = false,
                    billing = summary.getOrNull() ?: it.billing,
                    plans = plans.getOrNull() ?: it.plans,
                    toast = summary.exceptionOrNull()?.let { bt("error_generic") },
                )
            }
        }
        loadInvoices(reset = true)
    }

    fun loadInvoices(reset: Boolean = false) {
        if (_state.value.isLoadingInvoices) return
        val page = if (reset) 1 else _state.value.invoicesPage + 1
        _state.update { it.copy(isLoadingInvoices = true) }
        viewModelScope.launch {
            billingRepository.invoices(page)
                .onSuccess { (list, more) ->
                    _state.update {
                        it.copy(
                            invoices = if (reset) list else it.invoices + list,
                            invoicesPage = page,
                            hasMoreInvoices = more,
                            isLoadingInvoices = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(isLoadingInvoices = false) } }
        }
    }

    fun downloadInvoice(invoice: co.golink.tester.domain.billing.Invoice?) {
        if (_state.value.downloadingInvoice != null) return
        _state.update { it.copy(downloadingInvoice = invoice?.id ?: "all") }
        viewModelScope.launch {
            val result = if (invoice == null) billingRepository.downloadAllInvoices() else billingRepository.downloadInvoice(invoice)
            _state.update {
                it.copy(
                    downloadingInvoice = null,
                    toast = if (result.isSuccess) bt("saved_downloads") else bt("download_failed"),
                )
            }
        }
    }

    /** Dados de faturação (não é pagamento): grava e recarrega o resumo. */
    fun saveBillingDetails(changes: Map<String, String>) {
        if (changes.isEmpty() || _state.value.isUpdatingProfile) return
        _state.update { it.copy(isUpdatingProfile = true) }
        viewModelScope.launch {
            var failure: Throwable? = null
            for ((name, value) in changes) {
                failure = repository.updateProfileField(name, value).exceptionOrNull()
                if (failure != null) break
            }
            val summary = billingRepository.summary().getOrNull()
            sessionManager.refreshUser()
            _state.update {
                it.copy(
                    isUpdatingProfile = false,
                    billing = summary ?: it.billing,
                    toast = failure?.let { t -> t.message ?: bt("error_generic") } ?: bt("bd_saved"),
                )
            }
        }
    }
}
