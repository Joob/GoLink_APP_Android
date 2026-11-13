package co.golink.tester.ui.screens.socialite

import co.golink.tester.ui.i18n.tr
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SocialiteUiState(
    val provider: String? = null,
    val loadingUrl: Boolean = true,
    /** Evento: abrir este URL num Custom Tab (o ecrã chama [SocialiteViewModel.onLaunched]). */
    val launchUrl: String? = null,
    val awaitingBrowser: Boolean = false,
    val finishingAuth: Boolean = false,
    val error: String? = null,
    val tokenAdopted: Boolean = false,
)

/**
 * Login social em Custom Tabs (RFC 8252): a app gera `state` e PKCE, o
 * servidor devolve o URL do fornecedor e, no fim, redireciona para
 * <applicationId>.oauth:/callback com um código de uso único (60 s) que só se
 * troca com o code_verifier. Substitui a WebView embebida (o Google recusa-a
 * e o SPA consumia o pending-token ao mesmo tempo que a app).
 */
@HiltViewModel
class SocialiteViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(SocialiteUiState())
    val state: StateFlow<SocialiteUiState> = _state.asStateFlow()

    // Guardados no SavedStateHandle: a atividade pode ser destruída enquanto o Custom Tab está aberto.
    private var pendingState: String?
        get() = savedState[KEY_STATE]
        set(value) { savedState[KEY_STATE] = value }
    private var verifier: String?
        get() = savedState[KEY_VERIFIER]
        set(value) { savedState[KEY_VERIFIER] = value }
    private var lastUrl: String?
        get() = savedState[KEY_URL]
        set(value) { savedState[KEY_URL] = value }

    init {
        viewModelScope.launch {
            OAuthRedirects.pending.filterNotNull().collect { onRedirect(it) }
        }
    }

    fun start(provider: String) {
        val s = _state.value
        if (s.provider == provider && s.error == null) return
        // Recriado depois de abrir o browser: continuar à espera do regresso.
        if (s.provider == null && pendingState != null && lastUrl != null) {
            _state.update { it.copy(provider = provider, loadingUrl = false, awaitingBrowser = true) }
            return
        }
        val st = randomToken()
        val cv = randomToken()
        pendingState = st
        verifier = cv
        lastUrl = null
        _state.value = SocialiteUiState(provider = provider, loadingUrl = true)
        viewModelScope.launch {
            authRepository.socialiteAppRedirectUrl(provider, st, challengeFor(cv), OAuthRedirects.scheme)
                .onSuccess { url ->
                    if (url == null) {
                        _state.update { it.copy(loadingUrl = false, error = "Login social indisponível. Tenta mais tarde.".tr()) }
                    } else {
                        lastUrl = url
                        _state.update { it.copy(loadingUrl = false, launchUrl = url) }
                    }
                }
                .onFailure { t -> _state.update { it.copy(loadingUrl = false, error = t.message ?: "Erro".tr()) } }
        }
    }

    fun onLaunched() {
        _state.update { it.copy(launchUrl = null, awaitingBrowser = true) }
    }

    fun onLaunchFailed() {
        _state.update { it.copy(launchUrl = null, awaitingBrowser = false, error = "Nenhum browser instalado para entrar.".tr()) }
    }

    /** "Abrir de novo": o mesmo pedido (o state continua válido no servidor 10 min). */
    fun reopen() {
        val url = lastUrl ?: return
        _state.update { it.copy(launchUrl = url, error = null) }
    }

    private fun onRedirect(uri: Uri) {
        val expected = pendingState
        val cv = verifier
        OAuthRedirects.consume(uri)
        if (expected == null || cv == null || _state.value.finishingAuth || _state.value.tokenAdopted) return
        // O state tem de ser o deste pedido: um regresso forjado ou antigo é recusado.
        if (uri.getQueryParameter("state") != expected) {
            fail("Pedido de login inválido ou expirado. Tenta novamente.".tr())
            return
        }
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        if (error != null || code.isNullOrBlank()) {
            fail(errorMessage(error, uri.getQueryParameter("message")))
            return
        }
        _state.update { it.copy(awaitingBrowser = false, finishingAuth = true, error = null) }
        viewModelScope.launch {
            authRepository.socialiteExchange(code, cv, expected)
                .onSuccess {
                    clearPending()
                    _state.update { it.copy(finishingAuth = false, tokenAdopted = true) }
                }
                .onFailure { t -> fail(t.message ?: "Não foi possível entrar. Tenta novamente.".tr()) }
        }
    }

    private fun fail(message: String) {
        clearPending()
        _state.update { it.copy(awaitingBrowser = false, finishingAuth = false, launchUrl = null, error = message) }
    }

    private fun clearPending() {
        pendingState = null
        verifier = null
        lastUrl = null
    }

    private fun errorMessage(code: String?, serverMessage: String?): String = when (code) {
        "cancelled" -> "Cancelado".tr()
        "account_suspended" -> serverMessage?.takeIf { it.isNotBlank() } ?: "Conta suspensa".tr()
        "registration_disabled" -> "Registo desactivado".tr()
        "provider_disabled" -> "Login social indisponível. Tenta mais tarde.".tr()
        "no_email" -> "O fornecedor não partilhou o teu email.".tr()
        else -> "Não foi possível entrar. Tenta novamente.".tr()
    }

    private companion object {
        const val KEY_STATE = "oauth_state"
        const val KEY_VERIFIER = "oauth_verifier"
        const val KEY_URL = "oauth_url"
        private val random = SecureRandom()
        val B64 = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

        /** 32 bytes aleatórios em base64url: 43 caracteres (state e code_verifier). */
        fun randomToken(): String = ByteArray(32).also { random.nextBytes(it) }.let { Base64.encodeToString(it, B64) }

        /** PKCE S256: base64url(sha256(verifier)). */
        fun challengeFor(verifier: String): String =
            Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)), B64)
    }
}
