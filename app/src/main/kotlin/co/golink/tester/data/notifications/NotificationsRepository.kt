package co.golink.tester.data.notifications

import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.domain.notifications.Notification
import co.golink.tester.network.NotificationsApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
    private val sessionManager: SessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<Notification>>(emptyList())
    val items: StateFlow<List<Notification>> = _items.asStateFlow()

    val unreadCount: StateFlow<Int> = _items
        .let { src ->
            MutableStateFlow(0).also { sink ->
                scope.launch {
                    src.collect { list -> sink.value = list.count { !it.isRead } }
                }
            }
        }

    private var pollJob: Job? = null

    init {
        scope.launch {
            sessionManager.state.collect { s ->
                if (s is AuthState.Authenticated) {
                    if (pollJob == null || pollJob?.isActive != true) startPolling()
                } else {
                    pollJob?.cancel()
                    pollJob = null
                    _items.value = emptyList()
                }
            }
        }
    }

    private fun startPolling() {
        pollJob = scope.launch {
            while (isActive) {
                refresh()
                delay(60_000)
            }
        }
    }

    suspend fun refresh(): Result<Unit> = runCatching {
        val response = api.list()
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        val body = response.body() ?: error("Resposta vazia")
        _items.value = body.data.map { Notification.fromEnvelope(it) }
    }

    suspend fun markAllRead(): Result<Unit> = runCatching {
        val response = api.markAllRead()
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        _items.value = _items.value.map { it.copy(isRead = true) }
    }

    suspend fun markRead(id: String): Result<Unit> = runCatching {
        val response = api.markRead(id)
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        _items.value = _items.value.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val response = api.delete(id)
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        _items.value = _items.value.filterNot { it.id == id }
    }

    suspend fun flushAll(): Result<Unit> = runCatching {
        val response = api.flushAll()
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        _items.value = emptyList()
    }
}
