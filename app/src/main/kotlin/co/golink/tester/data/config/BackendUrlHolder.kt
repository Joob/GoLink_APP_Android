package co.golink.tester.data.config

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Singleton
class BackendUrlHolder @Inject constructor(
    private val repository: BackendConfigRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state: MutableStateFlow<String> =
        MutableStateFlow(runBlocking { repository.currentUrl() })

    val state: StateFlow<String> = _state.asStateFlow()

    val current: String get() = _state.value

    init {
        scope.launch {
            repository.backendUrl.collect { _state.value = it }
        }
    }
}
