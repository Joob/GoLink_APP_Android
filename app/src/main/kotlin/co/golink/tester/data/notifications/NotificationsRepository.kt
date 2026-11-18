package co.golink.tester.data.notifications

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.domain.notifications.Notification
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import co.golink.tester.network.NotificationsApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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

    private val authenticated = MutableStateFlow(false)

    // "Carregar mais": o GET só traz as READ_CAP lidas mais recentes. As que vêm
    // do /older ficam na mesma lista; o poll junta-as (por id) em vez de as largar.
    private val _hasMoreOlder = MutableStateFlow(false)
    val hasMoreOlder: StateFlow<Boolean> = _hasMoreOlder.asStateFlow()
    @Volatile private var olderLoaded = false
    // Segundo da lida mais antiga quando o /older disse que não há mais (null: não disse).
    @Volatile private var exhaustedAt: Long? = null
    private val olderLock = Mutex()

    // Até ao primeiro refresh da sessão a lista vazia não quer dizer "0 por ler":
    // o badge do arranque anterior fica (offline pode ficar assim muito tempo).
    private val loaded = MutableStateFlow(false)

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
        // UNDISPATCHED: entra na fila do Mutex (justa) ainda aqui, pela ordem dos
        // toques. Num dispatcher, duas ações seguidas podiam trocar de lugar.
        scope.async(start = CoroutineStart.UNDISPATCHED) {
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
                authenticated.value = s is AuthState.Authenticated
                // Só o fim de sessão limpa: Loading e BootstrapFailed (ex. arrancar
                // offline) não dizem nada sobre as notificações da conta.
                if (s is AuthState.Unauthenticated) {
                    // Um refresh a caminho não pode repor a lista desta conta.
                    generation.incrementAndGet()
                    loaded.value = false
                    _items.value = emptyList()
                    olderLoaded = false
                    exhaustedAt = null
                    _hasMoreOlder.value = false
                    // O contador de outra conta não pode ficar no ícone.
                    NotificationBadge.clear(context)
                }
            }
        }
        // O badge segue o contador — cobre refresh, markRead, delete e flush sem
        // ter de tocar em cada um deles.
        scope.launch {
            combine(_items, loaded) { list, ready -> if (ready) list.count { !it.isRead } else null }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { count -> NotificationBadge.update(context, count) }
        }
        scope.launch {
            val visible = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.STARTED) }
            combine(authenticated, visible) { signedIn, shown -> if (signedIn) shown else null }
                .distinctUntilChanged()
                .collectLatest { shown -> if (shown != null) poll(shown) }
        }
    }

    /**
     * Com a app à vista, de minuto a minuto (recua se falhar) e já ao voltar.
     * Em segundo plano não há ecrã a atualizar, mas sem push só isto mantém o
     * badge: um pedido leve (só o contador) de vez em quando.
     */
    private suspend fun poll(shown: Boolean) {
        if (!shown) delay(BACKGROUND_POLL_MS)
        var failures = 0
        while (true) {
            val ok = (if (shown) refresh() else backgroundCheck()).isSuccess
            failures = if (ok) 0 else failures + 1
            if (shown) {
                if (ok) restoreBadge()
                delay(FOREGROUND_POLL_MS shl failures.coerceAtMost(3))
            } else {
                delay(BACKGROUND_POLL_MS)
            }
        }
    }

    /**
     * Em segundo plano só interessa o badge: pede o contador (e a data da mais
     * recente) e só vai buscar a lista inteira quando difere do que já há. Um
     * servidor antigo (sem a rota) cai no refresh completo.
     */
    private suspend fun backgroundCheck(): Result<Unit> {
        val response = try {
            api.unreadCount()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            return Result.failure(e)
        }
        if (response.code() == 404) return refresh()
        if (!response.isSuccessful) return Result.failure(IllegalStateException("HTTP ${response.code()}"))
        val body = response.body() ?: return refresh()
        val list = _items.value
        val localLatest = list.mapNotNull { it.createdAtInstant?.epochSecond }.maxOrNull()
        val remoteLatest = body.latest_at?.let { runCatching { java.time.Instant.parse(it).epochSecond }.getOrNull() }
        // A lista traz no máximo UNREAD_CAP não lidas (GetUserNotificationsController).
        val same = loaded.value && pending.get() == 0 &&
            body.unread.coerceAtMost(UNREAD_CAP) == list.count { !it.isRead } &&
            remoteLatest == localLatest
        return if (same) Result.success(Unit) else refresh()
    }

    // O contador não muda quando o badge some sem passar por aqui: notificação
    // dispensada na barra, ou permissão concedida depois do primeiro refresh.
    private fun restoreBadge() {
        val count = _items.value.count { !it.isRead }
        if (count > 0 && !NotificationBadge.isShowing(context)) NotificationBadge.update(context, count)
    }

    /** Para quem só precisa de avisar que a lista mudou: não atrasa a operação. */
    fun refreshInBackground() {
        scope.launch { refresh() }
    }

    suspend fun refresh(): Result<Unit> = runCatching {
        val gen = generation.get()
        val response = api.list()
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        val body = response.body() ?: error("Resposta vazia")
        // O servidor junta duas queries (não lidas + lidas): uma lida entre as
        // duas vem repetida, e vale a segunda. O LazyColumn não aceita ids iguais.
        val list = body.data.map { Notification.fromEnvelope(it) }.associateBy { it.id }.values.toList()
        val read = list.filter { it.isRead }
        // Menos de READ_CAP lidas: o servidor não tem mais nenhuma (as antigas que
        // houvesse cá foram apagadas noutro lado).
        val full = read.size >= READ_CAP
        val cutoff = read.mapNotNull { it.createdAtInstant?.epochSecond }.minOrNull()
        // Houve uma ação entretanto (ou está a decorrer): esta resposta está velha.
        // Verificar e escrever no mesmo update: uma ação a meio não é pisada.
        var applied = false
        _items.update { current ->
            applied = gen == generation.get() && pending.get() == 0
            if (applied) mergeOlder(list, read, current, full) else current
        }
        if (applied) {
            needsResync = false
            loaded.value = true
            if (!full) {
                olderLoaded = false
                exhaustedAt = null
                _hasMoreOlder.value = false
            } else if (!olderLoaded) {
                // Já se pediu e não havia mais; só volta a haver se a janela das
                // lidas avançou (lidas novas empurraram outras para fora dela).
                val done = exhaustedAt
                _hasMoreOlder.value = done == null || cutoff == null || cutoff > done
            }
        }
    }

    /**
     * Junta ao poll as lidas mais antigas já carregadas (só depois de um
     * "Carregar mais"): ficam as que não vieram no poll e são do segundo da lida
     * mais antiga do poll ou anteriores. As mais recentes que faltam no poll
     * foram apagadas (ou passaram a não lidas e vêm no poll).
     */
    private fun mergeOlder(list: List<Notification>, read: List<Notification>, current: List<Notification>, full: Boolean): List<Notification> {
        if (!olderLoaded || !full) return list
        val cutoff = read.mapNotNull { it.createdAtInstant?.epochSecond }.minOrNull() ?: return list
        val ids = list.mapTo(HashSet()) { it.id }
        val kept = current.filter { n ->
            n.isRead && n.id !in ids && (n.createdAtInstant?.epochSecond ?: Long.MAX_VALUE) <= cutoff
        }
        return list + kept
    }

    /** "Carregar mais": lidas anteriores à lida mais antiga que já cá está. */
    suspend fun loadOlder(): Result<Unit> = runCatching {
        if (!olderLock.tryLock()) return@runCatching
        try {
            val read = _items.value.filter { it.isRead && it.createdAtInstant != null }
            val oldest = read.minByOrNull { it.createdAtInstant!! }
            if (oldest == null) {
                _hasMoreOlder.value = false
                return@runCatching
            }
            val second = oldest.createdAtInstant!!.epochSecond
            val seen = read.filter { it.createdAtInstant!!.epochSecond == second }.map { it.id }
            val response = api.older(oldest.createdAtIso!!, seen.take(500), OLDER_PAGE)
            if (!response.isSuccessful) error("HTTP ${response.code()}")
            val body = response.body() ?: error("Resposta vazia")
            val more = body.data.map { Notification.fromEnvelope(it) }
            // Antes de escrever: um refresh que escreva a seguir já as mantém.
            if (more.isNotEmpty()) olderLoaded = true
            _items.update { current ->
                val have = current.mapTo(HashSet()) { it.id }
                current + more.filter { it.id !in have }
            }
            exhaustedAt = if (body.has_more) null else (more.mapNotNull { it.createdAtInstant?.epochSecond }.minOrNull() ?: second)
            _hasMoreOlder.value = body.has_more
        } finally {
            olderLock.unlock()
        }
    }

    suspend fun stats(): Result<co.golink.tester.domain.notifications.NotificationStats> = runCatching {
        val response = api.stats()
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        response.body() ?: error("Resposta vazia")
    }

    /** Efeito local de uma ação em massa (otimista). */
    private fun applyLocal(action: String, list: List<Notification>, hit: (Notification) -> Boolean): List<Notification> = when (action) {
        "delete" -> list.filterNot(hit)
        "read" -> list.map { if (hit(it)) it.copy(isRead = true) else it }
        "unread" -> list.map { if (hit(it)) it.copy(isRead = false) else it }
        else -> list
    }

    /** Ação sobre os ids escolhidos (pedidos de 500 em 500, pela fila). */
    suspend fun bulk(action: String, ids: Collection<String>): Result<Unit> = runCatching {
        if (ids.isEmpty()) return@runCatching
        val set = ids.toHashSet()
        mutate({ list -> applyLocal(action, list) { it.id in set } }) {
            var last: retrofit2.Response<*>? = null
            for (chunk in set.chunked(500)) {
                last = api.bulk(co.golink.tester.domain.notifications.BulkNotificationsRequest(action = action, ids = chunk))
                if (!last.isSuccessful) break
            }
            last!!
        }
    }

    /**
     * Ação sobre todas as do filtro no servidor (também as não carregadas), até à
     * mais recente que o cliente viu. [matches]: as do filtro que já cá estão.
     */
    suspend fun bulkScope(action: String, filter: String, matches: (Notification) -> Boolean): Result<Unit> = runCatching {
        val (before, ids) = latestSeen() ?: return@runCatching
        val scope = co.golink.tester.domain.notifications.BulkScope(filter = filter, before = before, ids = ids.take(500))
        mutate({ list -> applyLocal(action, list, matches) }) {
            api.bulk(co.golink.tester.domain.notifications.BulkNotificationsRequest(action = action, scope = scope))
        }
        // Mexeu também nas que não estavam carregadas: recarregar.
        refreshInBackground()
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

    /** Eliminar fora de um ecrã (ViewModel a fechar com um "Desfazer" no ar). */
    fun deleteDetached(id: String) {
        scope.launch { delete(id) }
    }

    /** "Limpar lidas": apaga só as já lidas (e só as que o cliente já viu). */
    suspend fun deleteRead(): Result<Unit> = runCatching {
        val (before, ids) = latestSeen() ?: return@runCatching
        mutate({ list -> list.filterNot { it.isRead } }) { api.deleteRead(before, ids) }
    }

    private companion object {
        const val FOREGROUND_POLL_MS = 60_000L
        const val BACKGROUND_POLL_MS = 15 * 60_000L
        const val UNREAD_CAP = 500
        // GetUserNotificationsController: as 50 lidas mais recentes.
        const val READ_CAP = 50
        const val OLDER_PAGE = 50
    }
}
