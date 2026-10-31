package co.golink.tester.data.auth

import co.golink.tester.data.network.ConnectivityMonitor
import co.golink.tester.data.user.UserRepository
import co.golink.tester.domain.user.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    private val connectivity: ConnectivityMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var fetchJob: Job? = null

    // Sessão aberta com o utilizador guardado, ainda por confirmar no servidor.
    @Volatile private var stale = false

    init {
        scope.launch {
            tokenStore.state.collect { stored ->
                fetchJob?.cancel()
                stale = false
                when {
                    stored.token == null -> {
                        userRepository.clear()
                        _state.value = AuthState.Unauthenticated
                    }
                    !stored.otpValidated -> _state.value = AuthState.OtpRequired
                    else -> {
                        val cached = userRepository.me.value
                        val restored = if (cached == null) userRepository.restore() else null
                        when {
                            cached != null -> _state.value = AuthState.Authenticated(cached)
                            // Entra já; 401/403 na confirmação terminam a sessão na mesma.
                            restored != null -> {
                                stale = true
                                _state.value = AuthState.Authenticated(restored)
                                fetchJob = scope.launch { syncUser() }
                            }
                            else -> {
                                _state.value = AuthState.Loading
                                fetchJob = scope.launch { loadUserOrFail() }
                            }
                        }
                    }
                }
            }
        }
        scope.launch {
            connectivity.online.collect { if (it) resyncIfNeeded() }
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

    /** App em primeiro plano ou rede de volta: repete o que ficou por resolver. */
    fun resyncIfNeeded() {
        if (tokenStore.token == null || fetchJob?.isActive == true) return
        when {
            _state.value is AuthState.BootstrapFailed -> retryBootstrap()
            stale -> fetchJob = scope.launch { syncUser() }
        }
    }

    fun forceLogout() {
        scope.launch { tokenStore.clear() }
    }

    private suspend fun loadUserOrFail() {
        if (!syncUser()) _state.value = AuthState.BootstrapFailed("Erro ao carregar utilizador")
    }

    /**
     * Confirma a sessão no servidor. false se continuar sem resposta útil:
     * falhas de rede e 5xx passam sozinhas e o arranque não pode depender de um
     * único pedido — nem sequer corre sempre com a app à frente (ver App.onCreate).
     */
    private suspend fun syncUser(): Boolean {
        RETRY_DELAYS_MS.forEach { wait ->
            delay(wait)
            if (!connectivity.awaitOnline(ONLINE_WAIT_MS)) return false
            val result = userRepository.fetchMe()
            // fetchMe apanha o cancelamento; sem isto virava uma falha de arranque.
            currentCoroutineContext().ensureActive()
            result.getOrNull()?.let {
                stale = false
                _state.value = AuthState.Authenticated(it)
                return true
            }
            val error = result.exceptionOrNull()
            when ((error as? UserRepository.UserFetchException)?.statusCode) {
                // Invalid/expired token → full logout.
                401 -> { tokenStore.clear(); return true }
                // OTP not (or no longer) verified server-side → back to OTP,
                // keeping the pending token so the user can enter the code.
                403 -> { tokenStore.markOtpPending(); return true }
            }
        }
        return false
    }

    private companion object {
        val RETRY_DELAYS_MS = listOf(0L, 1_000L, 3_000L)
        const val ONLINE_WAIT_MS = 5_000L
    }
}
