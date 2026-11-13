package co.golink.tester.data.config

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * URL do backend em memória. Lido de forma síncrona (SharedPreferences, sem
 * runBlocking) ao criar o holder: os primeiros pedidos já vão para o URL certo.
 */
@Singleton
class BackendUrlHolder @Inject constructor(
    private val repository: BackendConfigRepository,
) {
    private val _state = MutableStateFlow(repository.currentUrl())

    val state: StateFlow<String> = _state.asStateFlow()

    val current: String get() = _state.value

    fun set(url: String) {
        repository.setBackendUrl(url)
        _state.value = repository.currentUrl()
    }
}
