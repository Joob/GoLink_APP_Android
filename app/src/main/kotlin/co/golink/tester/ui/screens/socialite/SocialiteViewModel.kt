package co.golink.tester.ui.screens.socialite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthRepository
import co.golink.tester.data.config.BackendUrlHolder
import co.golink.tester.network.AuthApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SocialiteUiState(
    val provider: String? = null,
    val initialUrl: String? = null,
    val backendHost: String? = null,
    val signInPath: String = "/sign-in",
    val loadingUrl: Boolean = true,
    val finishingAuth: Boolean = false,
    val error: String? = null,
    val tokenAdopted: Boolean = false,
)

@HiltViewModel
class SocialiteViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
    private val backendUrlHolder: BackendUrlHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(SocialiteUiState())
    val state: StateFlow<SocialiteUiState> = _state.asStateFlow()

    fun start(provider: String) {
        if (_state.value.provider == provider && _state.value.initialUrl != null) return
        val backend = backendUrlHolder.current
        val host = backend.toHttpUrlOrNull()?.host
        _state.update { it.copy(provider = provider, loadingUrl = true, error = null, backendHost = host) }
        viewModelScope.launch {
            authRepository.socialiteRedirectUrl(provider)
                .onSuccess { url -> _state.update { it.copy(initialUrl = url, loadingUrl = false) } }
                .onFailure { t -> _state.update { it.copy(loadingUrl = false, error = t.message ?: "Erro") } }
        }
    }

    fun onCallbackLanded(cookies: String) {
        if (_state.value.finishingAuth || _state.value.tokenAdopted) return
        if (cookies.isBlank()) {
            _state.update { it.copy(error = "Sessão OAuth não encontrada") }
            return
        }
        _state.update { it.copy(finishingAuth = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val response = authApi.socialitePendingToken(cookies)
                check(response.isSuccessful) { "HTTP ${response.code()}" }
                response.body()?.token
            }.onSuccess { token ->
                if (token.isNullOrBlank()) {
                    _state.update { it.copy(finishingAuth = false, error = "Sem token. Tenta novamente.") }
                } else {
                    authRepository.adoptSocialiteToken(token)
                    _state.update { it.copy(finishingAuth = false, tokenAdopted = true) }
                }
            }.onFailure { t ->
                _state.update { it.copy(finishingAuth = false, error = t.message ?: "Erro a obter token") }
            }
        }
    }
}
