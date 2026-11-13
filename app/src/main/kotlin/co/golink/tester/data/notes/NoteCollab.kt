package co.golink.tester.data.notes

import android.util.Log
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Quem está na nota agora (presença do relay). */
data class NotePeer(val client: String, val userId: String?, val name: String?, val avatar: String?, val canEdit: Boolean)

sealed interface CollabEvent {
    /** Chegou conteúdo de outro cliente: o documento mudou. */
    data object RemoteChange : CollabEvent
    /** Sessão reiniciada (versão restaurada, acesso perdido): reabrir a nota. */
    data object Reset : CollabEvent
    /** O servidor recusou o envio (4xx definitivo: 403/413/422…): o lote fica por gravar ("Não gravada"). */
    data class Rejected(val status: Int) : CollabEvent
}

/**
 * Edição em tempo real de uma nota — porta de services/office/collab.js (web).
 * Relay por polling (/api/notes/notes/{id}/collab): base da sessão + updates por
 * ordem, tudo cifrado com a NoteCipher. O Y.Doc vive no NotesCore (QuickJS) com o id da nota.
 */
class NoteCollab(
    val noteId: String,
    private val api: NotesHttp,
    private val core: NotesCore,
    private val cipher: NoteCipher,
    private val canEdit: Boolean,
    private val scope: CoroutineScope,
) {
    val clientId: String = ByteArray(12).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val base = "/notes/$noteId/collab"
    private var sessionId: String? = null
    private var lastSeq = 0L
    var appliedSeq = 0L
        private set
    private var sinceBase = 0
    /** Sessão cuja base já está no Y.Doc (o relay manda a base sempre que after=0). */
    private var baseSession: String? = null
    private val pending = ArrayList<String>()
    private val flushMutex = Mutex()
    private val pollMutex = Mutex()
    private var flushJob: Job? = null
    private var pollJob: Job? = null
    @Volatile private var destroyed = false
    @Volatile private var closing = false
    /** Falhas seguidas (rede/5xx) para o recuo exponencial do envio e do poll. */
    private var flushFailures = 0
    private var pollFailures = 0
    /** Envio recusado de vez: não volta a tentar até nova edição (como `rejected` no collab.js). */
    @Volatile var rejected: Boolean = false
        private set

    /** Em segundo plano o polling abranda (como document.hidden na web). */
    @Volatile var foreground: Boolean = true

    private val _events = MutableSharedFlow<CollabEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CollabEvent> = _events.asSharedFlow()

    private val _peers = MutableStateFlow<List<NotePeer>>(emptyList())
    /** Outros clientes (sem este). */
    val peers: StateFlow<List<NotePeer>> = _peers.asStateFlow()

    val hasPending: Boolean get() = pending.isNotEmpty() || flushMutex.isLocked

    private fun emit(event: CollabEvent) {
        if (!closing) _events.tryEmit(event)
    }

    // ---- entrada -------------------------------------------------------------

    /**
     * Abre o Y.Doc no núcleo e entra na sessão (cria-a se ainda não existe e pode editar).
     * false = só ver e ninguém criou sessão (documento vazio).
     */
    suspend fun join(): Boolean {
        core.open(noteId)
        var state = fetchAll(0)
        if (state.session == null) {
            if (!canEdit) return false
            val created = try {
                api.post("$base/session", buildJsonObject {
                    put("client", clientId)
                    put("revision", 0)
                    put("base", cipher.encryptB64(core.emptyState()))
                })
            } catch (e: NotesHttpException) {
                if (e.message == "session_exists") null else throw e
            }
            state = if (created != null) {
                RelayState(parseSession(created["session"] as? JsonObject), emptyList(), state.presence, false)
            } else {
                fetchAll(0)
            }
        }
        sessionId = state.session?.id ?: return false
        applyState(state)
        schedulePoll()
        return true
    }

    // ---- envio ---------------------------------------------------------------

    /** Update local (base64 em claro, vindo do NotesCore.edit/take). Envia em lote ao fim de 120 ms. */
    fun localUpdate(updateB64: String) {
        if (updateB64.isEmpty() || destroyed || !canEdit) return
        synchronized(pending) { pending += updateB64 }
        rejected = false
        scheduleFlush(FLUSH_DELAY)
    }

    /**
     * Debounce: cancelar só apanha a espera. O envio corre em NonCancellable porque o
     * flush() já tirou o lote da fila — cancelá-lo a meio perdia essas teclas.
     */
    private fun scheduleFlush(delayMs: Long) {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(delayMs)
            withContext(NonCancellable) { flush() }
        }
    }

    suspend fun flush() {
        flushMutex.withLock {
            val session = sessionId ?: return
            val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
            if (batch.isEmpty()) return
            val merged = try {
                core.merge(batch)
            } catch (e: Exception) {
                // Não perder o lote: volta à fila tal como estava.
                synchronized(pending) { pending.addAll(0, batch) }
                if (!destroyed) scheduleFlush(backoff(++flushFailures))
                return
            }
            try {
                val res = api.post("$base/updates", buildJsonObject {
                    put("client", clientId)
                    put("session", session)
                    put("payload", cipher.encryptB64(merged))
                })
                res["seq"]?.jsonPrimitive?.longOrNull?.let { appliedSeq = maxOf(appliedSeq, it) }
                sinceBase++
                flushFailures = 0
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is NotesHttpException && e.message == "collab_reset") {
                    // Fica na fila (pending.unshift na web): quem trata o reset reenvia-o na sessão nova.
                    synchronized(pending) { pending.add(0, merged) }
                    emit(CollabEvent.Reset)
                    return
                }
                if (e is NotesHttpException && isFatal(e.status)) {
                    // Repetir não muda nada (sem acesso, grande demais, inválido): pára e avisa.
                    synchronized(pending) { pending.add(0, merged) }
                    rejected = true
                    emit(CollabEvent.Rejected(e.status))
                    return
                }
                // Volta para a fila: segue no próximo envio, cada vez mais espaçado.
                synchronized(pending) { pending.add(0, merged) }
                if (!destroyed) scheduleFlush(backoff(++flushFailures))
            }
        }
    }

    /** Depois de gravar (como a web): troca a base pelo estado completo, de vez em quando. */
    suspend fun compactIfNeeded() {
        val session = sessionId ?: return
        if (sinceBase < COMPACT_AFTER || appliedSeq <= 0) return
        runCatching {
            api.post("$base/compact", buildJsonObject {
                put("client", clientId)
                put("session", session)
                put("base", cipher.encryptB64(core.state(noteId)))
                put("upto", appliedSeq)
            })
            sinceBase = 0
        }
    }

    // ---- receção -------------------------------------------------------------

    private class RelaySession(val id: String, val base: String?)
    private class RelayUpdate(val seq: Long, val client: String?, val type: String, val payload: String?)
    private class RelayState(val session: RelaySession?, val updates: List<RelayUpdate>, val presence: JsonArray?, val more: Boolean)

    private fun parseSession(o: JsonObject?): RelaySession? {
        val id = o?.get("id")?.jsonPrimitive?.contentOrNull ?: return null
        return RelaySession(id, o["base"]?.jsonPrimitive?.contentOrNull)
    }

    private fun parseState(o: JsonObject) = RelayState(
        session = parseSession(o["session"] as? JsonObject),
        updates = (o["updates"] as? JsonArray).orEmpty().mapNotNull { el ->
            val u = el as? JsonObject ?: return@mapNotNull null
            RelayUpdate(
                seq = u["seq"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                client = u["client"]?.jsonPrimitive?.contentOrNull,
                type = u["type"]?.jsonPrimitive?.contentOrNull ?: "update",
                payload = u["payload"]?.jsonPrimitive?.contentOrNull,
            )
        },
        presence = o["presence"] as? JsonArray,
        more = o["more"]?.jsonPrimitive?.booleanOrNull == true,
    )

    private suspend fun fetchAll(after: Long): RelayState {
        var state = parseState(api.get(base, mapOf("client" to clientId, "after" to after)))
        val updates = ArrayList(state.updates)
        var presence = state.presence
        val first = state.session
        while (state.more && state.session != null && !destroyed) {
            val next = maxOf(after, updates.maxOfOrNull { it.seq } ?: after)
            state = parseState(api.get(base, mapOf("client" to clientId, "after" to next)))
            if (state.session?.id != first?.id) return state
            updates += state.updates
            presence = state.presence
        }
        return RelayState(first, updates, presence, false)
    }

    private suspend fun applyState(state: RelayState) {
        var changed = false
        // A base só se aplica uma vez por sessão: enquanto a nota não tiver updates, cada
        // poll (after=0) traz a base outra vez e reaplicá-la era decifrar tudo e redesenhar.
        val freshBase = state.session?.base?.takeIf { state.session.id != baseSession }
        freshBase?.let { b ->
            val err = runCatching { core.apply(noteId, cipher.decryptToB64(b)) }.getOrElse { it.message }
            if (err != null) Log.w(TAG, "base ilegível: $err") else {
                changed = true
                baseSession = state.session.id
            }
        }
        for (u in state.updates) {
            if (u.type == "update" && u.client != clientId && u.payload != null) {
                // Um update ilegível (chave errada, corrompido) não pode parar a sessão.
                val err = runCatching { core.apply(noteId, cipher.decryptToB64(u.payload)) }.getOrElse { it.message }
                if (err != null) Log.w(TAG, "update ${u.seq} ignorado: $err") else changed = true
            }
            if (u.type == "update") sinceBase++
            lastSeq = maxOf(lastSeq, u.seq)
            appliedSeq = maxOf(appliedSeq, u.seq)
        }
        if (freshBase != null) sinceBase = state.updates.size
        state.presence?.let(::applyPresence)
        if (changed) emit(CollabEvent.RemoteChange)
    }

    private fun applyPresence(list: JsonArray) {
        _peers.value = list.mapNotNull { el ->
            val p = el as? JsonObject ?: return@mapNotNull null
            val client = p["client"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (client == clientId) return@mapNotNull null
            NotePeer(
                client = client,
                userId = p["user_id"]?.jsonPrimitive?.contentOrNull,
                name = p["name"]?.jsonPrimitive?.contentOrNull,
                avatar = p["avatar"]?.jsonPrimitive?.contentOrNull,
                canEdit = p["can_edit"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.intOrNull == 1) } == true,
            )
        }
    }

    private fun schedulePoll() {
        pollJob?.cancel()
        if (destroyed) return
        val delayMs = when {
            !foreground -> POLL_HIDDEN
            _peers.value.isNotEmpty() -> POLL_SHARED
            else -> POLL_SOLO
        }
        pollJob = scope.launch {
            delay(if (pollFailures > 0) maxOf(delayMs, backoff(pollFailures)) else delayMs)
            poll()
        }
    }

    private suspend fun poll() {
        if (destroyed || !pollMutex.tryLock()) return
        var again = true
        try {
            val state = fetchAll(lastSeq)
            if (destroyed) return
            if (state.session == null || state.session.id != sessionId) {
                emit(CollabEvent.Reset)
                again = false
                return
            }
            applyState(state)
            pollFailures = 0
        } catch (e: NotesHttpException) {
            // Sem rede: tenta no próximo ciclo (com recuo). Sem acesso / nota apagada: reabrir.
            if (e.status == 403 || e.status == 404) {
                emit(CollabEvent.Reset)
                again = false
            } else pollFailures++
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Resposta estranha (JSON, núcleo): regista e continua — nunca parar o sync.
            Log.w(TAG, "poll falhou", e)
            pollFailures++
        } finally {
            pollMutex.unlock()
            if (again && !destroyed) schedulePoll()
        }
    }

    /** Pedir já (ex.: a app voltou ao primeiro plano). */
    fun pokePoll() {
        if (destroyed || pollMutex.isLocked) return
        pollJob?.cancel()
        pollJob = scope.launch { poll() }
    }

    /** Grava o pendente, sai da presença e fecha o Y.Doc. */
    suspend fun destroy() {
        if (destroyed || closing) return
        closing = true
        flushJob?.cancel()
        withContext(NonCancellable) {
            runCatching { flush() }
            destroyed = true
            pollJob?.cancel()
            runCatching { api.post("$base/leave", buildJsonObject { put("client", clientId) }) }
            runCatching { core.close(noteId) }
        }
    }

    /** 4xx definitivos (o collab.js chama-lhe isFatal): 408/429 voltam a tentar. */
    private fun isFatal(status: Int) = status in 400..499 && status != 408 && status != 429

    /** 2 s, 4 s, 8 s… até 30 s. */
    private fun backoff(failures: Int): Long = minOf(30_000L, 2_000L shl (failures - 1).coerceIn(0, 4))

    private companion object {
        const val TAG = "NoteCollab"
        const val POLL_SOLO = 2_500L
        const val POLL_SHARED = 800L
        const val POLL_HIDDEN = 6_000L
        const val FLUSH_DELAY = 120L
        const val COMPACT_AFTER = 150
    }
}
