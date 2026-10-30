package co.golink.tester.data.notifications

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.domain.notifications.Notification
import android.content.Context
import co.golink.tester.network.NotificationsApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
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

    // As ações são otimistas (a lista muda já) e os pedidos correm em fila
    // (marcar todas → limpar lidas chega ao servidor por essa ordem), no scope
    // do repositório: sair do ecrã não os cancela. Cada uma avança a geração:
    // um refresh que partiu antes dela não repõe o estado antigo. Se um pedido
    // falhar, desfaz-se (quando nada mudou entretanto) e recarrega quando a
    // fila esvaziar; o resync só se dá por feito quando o refresh escreve.
    private val mutations = Mutex()
    private val generation = AtomicInteger(0)
    private val pending = AtomicInteger(0)
    @Volatile private var needsResync = false

    private suspend fun mutate(
        local: (List<Notification>) -> List<Notification>,
        call: suspend () -> retrofit2.Response<*>,
    ) {
        val snapshot = _items.value
        val gen = generation.incrementAndGet()
        pending.incrementAndGet()
        _items.update(local)
        scope.async {
            try {
                mutations.withLock {
                    val response = call()
                    if (!response.isSuccessful) error("HTTP ${response.code()}")
                }
            } catch (e: Throwable) {
                // Nenhuma outra ação desde esta: repor a lista de antes é seguro.
                _items.update { if (generation.get() == gen) snapshot else it }
                needsResync = true
                throw e
            } finally {
                val left = pending.decrementAndGet()
                generation.incrementAndGet()
                if (left == 0 && needsResync) scope.launch { refresh() }
            }
        }.await()
    }

    /**
     * Limite das ações em massa: o created_at mais recente que o cliente tem e
     * os ids desse segundo (o servidor guarda ao segundo — outra criada no mesmo
     * segundo e ainda não vista fica de fora). null: não há nada visto.
     */
    private fun latestSeen(): Pair<String, List<String>>? {
        val dated = _items.value.filter { it.createdAtInstant != null }
        val newest = dated.maxByOrNull { it.createdAtInstant!! } ?: return null
        val second = newest.createdAtInstant!!.epochSecond
        return newest.createdAtIso!! to dated.filter { it.createdAtInstant!!.epochSecond == second }.map { it.id }
    }

    init {
        scope.launch {
            sessionManager.state.collect { s ->
                if (s is AuthState.Authenticated) {
                    if (pollJob == null || pollJob?.isActive != true) startPolling()
                } else {
                    pollJob?.cancel()
                    pollJob = null
                    _items.value = emptyList()
                    // Sessão terminada: o contador de outra conta não pode ficar
                    // no ícone.
                    NotificationBadge.clear(context)
                }
            }
        }
        // O badge segue o contador — cobre refresh, markRead, delete e flush sem
        // ter de tocar em cada um deles.
        scope.launch {
            unreadCount.collect { count -> NotificationBadge.update(context, count) }
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
        val gen = generation.get()
        val response = api.list()
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        val body = response.body() ?: error("Resposta vazia")
        val list = body.data.map { Notification.fromEnvelope(it) }
        // Houve uma ação entretanto (ou está a decorrer): esta resposta está velha.
        // Verificar e escrever no mesmo update: uma ação a meio não é pisada.
        var applied = false
        _items.update { current ->
            applied = gen == generation.get() && pending.get() == 0
            if (applied) list else current
        }
        if (applied) needsResync = false
    }

    suspend fun markAllRead(): Result<Unit> = runCatching {
        val (before, ids) = latestSeen() ?: return@runCatching
        mutate({ list -> list.map { it.copy(isRead = true) } }) { api.markAllRead(before, ids) }
    }

    suspend fun markRead(id: String): Result<Unit> = runCatching {
        mutate({ list -> list.map { if (it.id == id) it.copy(isRead = true) else it } }) { api.markRead(id) }
    }

    suspend fun markUnread(id: String): Result<Unit> = runCatching {
        mutate({ list -> list.map { if (it.id == id) it.copy(isRead = false) else it } }) { api.markUnread(id) }
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        mutate({ list -> list.filterNot { it.id == id } }) { api.delete(id) }
    }

    /** "Limpar lidas": apaga só as já lidas (e só as que o cliente já viu). */
    suspend fun deleteRead(): Result<Unit> = runCatching {
        val (before, ids) = latestSeen() ?: return@runCatching
        mutate({ list -> list.filterNot { it.isRead } }) { api.deleteRead(before, ids) }
    }
}
