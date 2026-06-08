package co.golink.tester.data.auth

import co.golink.tester.data.user.UserRepository
import co.golink.tester.domain.user.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data object OtpRequired : AuthState
    data class Authenticated(val user: User) : AuthState
    data class BootstrapFailed(val message: String) : AuthState
}

@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val userRepository: UserRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var fetchJob: Job? = null

    init {
        scope.launch {
            tokenStore.state.collect { stored ->
                fetchJob?.cancel()
                _state.value = when {
                    stored.token == null -> {
                        userRepository.clear()
                        AuthState.Unauthenticated
                    }
                    !stored.otpValidated -> AuthState.OtpRequired
                    else -> {
                        val cached = userRepository.me.value
                        if (cached != null) {
                            AuthState.Authenticated(cached)
                        } else {
                            fetchJob = scope.launch { loadUserOrFail() }
                            AuthState.Loading
                        }
                    }
                }
            }
        }
    }

    suspend fun refreshUser(): Result<User> = userRepository.fetchMe().onSuccess { user ->
        _state.value = AuthState.Authenticated(user)
    }

    fun retryBootstrap() {
        if (tokenStore.token == null) return
        fetchJob?.cancel()
        _state.value = AuthState.Loading
        fetchJob = scope.launch { loadUserOrFail() }
    }

    fun forceLogout() {
        scope.launch { tokenStore.clear() }
    }

    private suspend fun loadUserOrFail() {
        userRepository.fetchMe()
            .onSuccess { _state.value = AuthState.Authenticated(it) }
            .onFailure { error ->
                val code = (error as? co.golink.tester.data.user.UserRepository.UserFetchException)?.statusCode
                if (code == 401) {
                    tokenStore.clear()
                } else {
                    _state.value = AuthState.BootstrapFailed(error.message ?: "Erro ao carregar utilizador")
                }
            }
    }
}
