package co.golink.tester.ui.screens.support

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.data.download.FileDownloader
import co.golink.tester.domain.support.ConfirmDeletionRequest
import co.golink.tester.domain.support.MessageEnvelope
import co.golink.tester.domain.support.TicketData
import co.golink.tester.domain.support.UpdateTicketRequest
import co.golink.tester.network.SupportApi
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.source
import retrofit2.Response
import javax.inject.Inject

/** Limites do servidor (SupportTicketAttachment::MAX_FILES / MAX_FILESIZE_KB). */
const val SUPPORT_MAX_FILES = 5
const val SUPPORT_MAX_FILE_BYTES = 10L * 1024 * 1024

data class PickedFile(val uri: Uri, val name: String, val size: Long)

/** Lê nome/tamanho e converte os ficheiros escolhidos em partes multipart "attachments[]". */
internal class AttachmentReader(private val context: Context) {
    /** Consulta o ContentResolver: chamar fora da main thread ([describeAll]). */
    fun describe(uri: Uri): PickedFile {
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        // Sem SIZE no provider: conta os bytes até passar o limite (não lê o resto).
        if (size <= 0L) size = countUpTo(uri, SUPPORT_MAX_FILE_BYTES + 1)
        return PickedFile(uri, name, size)
    }

    private fun countUpTo(uri: Uri, limit: Long): Long = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (total < limit) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
            }
            total
        } ?: 0L
    }.getOrDefault(0L)

    suspend fun describeAll(uris: List<Uri>): List<PickedFile> = withContext(Dispatchers.IO) { uris.map { describe(it) } }

    /**
     * Partes "attachments[]" em streaming: o ficheiro é lido pelo OkHttp ao
     * enviar (fora da main thread) em vez de ir inteiro para a memória.
     */
    suspend fun parts(files: List<PickedFile>): List<MultipartBody.Part> = withContext(Dispatchers.IO) {
        files.map { f ->
            val mime = (context.contentResolver.getType(f.uri) ?: "application/octet-stream").toMediaTypeOrNull()
            MultipartBody.Part.createFormData("attachments[]", f.name, UriRequestBody(context, f.uri, f.size, mime))
        }
    }
}

/** Corpo que lê o URI só no writeTo (reabre o stream a cada escrita). */
private class UriRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val size: Long,
    private val mediaType: okhttp3.MediaType?,
) : RequestBody() {
    override fun contentType(): okhttp3.MediaType? = mediaType
    override fun contentLength(): Long = if (size > 0) size else -1L
    override fun writeTo(sink: okio.BufferedSink) {
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Não foi possível ler o ficheiro".tr())
        input.source().use { sink.writeAll(it) }
    }
}

internal fun text(value: String): RequestBody = value.toRequestBody("text/plain".toMediaTypeOrNull())

internal fun serverMessage(json: Json, response: Response<*>): String {
    val raw = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
    val obj = runCatching { json.decodeFromString(JsonObject.serializer(), raw) }.getOrNull()
    val firstError = runCatching {
        (obj?.get("errors") as? JsonObject)?.values?.firstOrNull()?.let { v ->
            (v as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.let { (it as JsonPrimitive).content }
        }
    }.getOrNull()
    val message = runCatching { (obj?.get("message") as? JsonPrimitive)?.content }.getOrNull()
    return firstError ?: message ?: "HTTP ${response.code()}"
}

// ---------------------------------------------------------------- Lista

data class SupportListUiState(
    val isLoading: Boolean = false,
    val tickets: List<TicketData> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    /** Utilizador: "active" | "closed". Admin: open | pending | closed (como na web). */
    val filter: String? = null,
    val query: String = "",
    /** Só staff: me | unassigned. */
    val assigned: String? = null,
    val priority: String? = null,
    val category: String? = null,
    /** Só staff: contagens dos separadores e filtros. */
    val counts: co.golink.tester.domain.support.SupportCounts? = null,
    /** Só staff: agentes para o filtro "Responsável". */
    val agents: List<co.golink.tester.domain.support.SupportAgentOption> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SupportListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: SupportApi,
) : ViewModel() {
    val isAdmin: Boolean = savedStateHandle.get<Boolean>("admin") ?: false

    // Atalhos do Dashboard (ex. "Sem responsável" → assigned=unassigned).
    private val _state = MutableStateFlow(
        SupportListUiState(
            filter = if (isAdmin) savedStateHandle.get<String>("status")?.takeIf { it.isNotBlank() } ?: "open" else "active",
            assigned = savedStateHandle.get<String>("assigned")?.takeIf { it.isNotBlank() },
            priority = savedStateHandle.get<String>("priority")?.takeIf { it.isNotBlank() },
        ),
    )
    val state: StateFlow<SupportListUiState> = _state.asStateFlow()

    init {
        if (isAdmin) viewModelScope.launch {
            runCatching { api.adminAgents().body()?.data }.getOrNull()
                ?.let { list -> _state.update { it.copy(agents = list) } }
        }
    }

    /** Filtro por agente (id) — partilha o campo assigned com me/unassigned. */
    fun setAgent(id: String?) {
        _state.update { it.copy(assigned = id) }
        refresh()
    }

    fun setFilter(filter: String?) {
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    /** Escolher o mesmo valor outra vez limpa o filtro. */
    fun setAssigned(value: String?) {
        _state.update { it.copy(assigned = if (it.assigned == value) null else value) }
        refresh()
    }

    fun setPriority(value: String?) {
        _state.update { it.copy(priority = value) }
        refresh()
    }

    fun setCategory(value: String?) {
        _state.update { it.copy(category = value) }
        refresh()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun refresh() = load(1)

    fun loadMore() {
        val s = _state.value
        if (!s.isLoading && s.hasMore && loadJob?.isActive != true) load(s.page + 1)
    }

    /** Atualização periódica (só na 1.ª página e sem pedido em curso), sem spinner. */
    fun poll() {
        val s = _state.value
        if (s.page == 1 && !s.isLoading && loadJob?.isActive != true) load(1, silent = true)
    }

    /** Pedido em curso: a página 1 (troca de filtro) cancela o anterior. */
    private var loadJob: Job? = null

    // A pesquisa só se aplica com "Procurar" (refresh cancela o pedido anterior), por isso não entra aqui.
    private fun filterKey(s: SupportListUiState) = listOf(s.filter, s.assigned, s.priority, s.category)

    private fun load(page: Int, silent: Boolean = false) {
        if (page == 1) loadJob?.cancel()
        if (!silent) _state.update { it.copy(isLoading = true, error = null) }
        // Filtro lido antes de lançar: uma resposta de outro filtro é descartada.
        val s = _state.value
        loadJob = viewModelScope.launch {
            val result = runCatching {
                val response = if (isAdmin) {
                    api.adminTickets(page, s.filter, s.query.ifBlank { null }, s.assigned, s.priority, s.category)
                } else {
                    api.tickets(page, s.filter)
                }
                check(response.isSuccessful) { "HTTP ${response.code()}" }
                response.body() ?: error("Resposta vazia".tr())
            }
            if (!currentCoroutineContext().isActive || filterKey(_state.value) != filterKey(s)) return@launch
            result.onSuccess { body ->
                val items = body.data.map { it.data }
                _state.update {
                    it.copy(
                        isLoading = false,
                        tickets = if (page == 1) items else (it.tickets + items).distinctBy { x -> x.id },
                        page = page,
                        hasMore = (body.meta?.current_page ?: 1) < (body.meta?.last_page ?: 1),
                    )
                }
            }.onFailure { t -> _state.update { it.copy(isLoading = false, error = if (silent) it.error else t.message) } }

            // Contagens dos separadores (falha não bloqueia a lista).
            if (isAdmin && page == 1) {
                runCatching { api.adminCounts().body() }
                    .getOrNull()
                    ?.let { counts -> _state.update { it.copy(counts = counts) } }
            }
        }
    }
}

// ---------------------------------------------------------------- Dashboard

data class SupportDashboardUiState(
    val isLoading: Boolean = false,
    val range: Int = 30,
    val data: co.golink.tester.domain.support.SupportDashboard? = null,
    val error: String? = null,
)

@HiltViewModel
class SupportDashboardViewModel @Inject constructor(
    private val api: SupportApi,
) : ViewModel() {
    private val _state = MutableStateFlow(SupportDashboardUiState())
    val state: StateFlow<SupportDashboardUiState> = _state.asStateFlow()

    fun setRange(range: Int) {
        if (_state.value.range == range) return
        _state.update { it.copy(range = range) }
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val range = _state.value.range
            runCatching {
                val response = api.adminDashboard(range)
                check(response.isSuccessful) { "HTTP ${response.code()}" }
                response.body() ?: error("Resposta vazia".tr())
            }.onSuccess { data -> if (_state.value.range == range) _state.update { it.copy(isLoading = false, data = data) } }
                .onFailure { t -> if (!silent) _state.update { it.copy(isLoading = false, error = t.message) } }
        }
    }

    /** Atualização periódica sem spinner. */
    fun poll() { if (!_state.value.isLoading) refresh(silent = true) }
}

// ---------------------------------------------------------------- Detalhe

data class SupportTicketUiState(
    val isLoading: Boolean = true,
    /** Membros a quem se pode atribuir (só carregado com can_assign). */
    val agents: List<co.golink.tester.domain.support.SupportAgentOption> = emptyList(),
    val ticket: TicketData? = null,
    val messages: List<MessageEnvelope> = emptyList(),
    val attachments: List<PickedFile> = emptyList(),
    val isSending: Boolean = false,
    val isBusy: Boolean = false,
    val deletionError: String? = null,
    val toast: String? = null,
    val deleted: Boolean = false,
    // Copiloto (Claude)
    val isDrafting: Boolean = false,
    val isSummarizing: Boolean = false,
    /** Rascunho pronto a ir para a caixa de resposta (evento consumido pelo ecrã). */
    val draft: String? = null,
    val summary: String? = null,
)

@HiltViewModel
class SupportTicketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val api: SupportApi,
    private val json: Json,
    private val downloader: FileDownloader,
    private val sessionManager: SessionManager,
    userRepository: co.golink.tester.data.user.UserRepository,
) : ViewModel() {
    val ticketId: String = checkNotNull(savedStateHandle["id"])
    val isAdmin: Boolean = savedStateHandle.get<Boolean>("admin") ?: false
    private val reader = AttachmentReader(context)

    /**
     * Utilizador atual (permissões de staff e id). Observado: num arranque a
     * frio (link de notificação) ainda pode ser null quando o ecrã abre.
     */
    val me: StateFlow<co.golink.tester.domain.user.User?> = userRepository.me

    private val _state = MutableStateFlow(SupportTicketUiState())
    val state: StateFlow<SupportTicketUiState> = _state.asStateFlow()

    init {
        refresh(markRead = true)
        if (isAdmin) {
            viewModelScope.launch {
                // Espera pelas permissões (podem chegar depois de abrir o ecrã).
                me.first { it?.supportStaff?.can_assign == true }
                runCatching { api.adminAgents().body()?.data.orEmpty() }
                    .onSuccess { agents -> _state.update { it.copy(agents = agents) } }
            }
        }
    }

    /** Atribui (ou larga, com null) o ticket. */
    fun assign(userId: String?) = action {
        api.adminAssign(
            ticketId,
            kotlinx.serialization.json.buildJsonObject {
                put("assigned_to", userId?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            },
        )
    }

    /** Atualização periódica do fio (como o setInterval da web); erros de rede calados. */
    fun poll() { if (!_state.value.isSending && !_state.value.isBusy) refresh(markRead = true, silent = true) }

    fun refresh(markRead: Boolean = false, silent: Boolean = false) {
        viewModelScope.launch {
            runCatching {
                val response = if (isAdmin) api.adminTicket(ticketId) else api.ticket(ticketId)
                // Agente "só atribuídos" que largou o ticket (ou ticket apagado): deixa de o ver.
                if (response.code() == 404 || response.code() == 403) {
                    _state.update { it.copy(deleted = true) }
                    return@launch
                }
                check(response.isSuccessful) { "HTTP ${response.code()}" }
                response.body()!!.data
            }.onSuccess { ticket ->
                _state.update {
                    // Ordem cronológica garantida do lado da app (servidores antigos
                    // devolviam as mensagens por ordem arbitrária).
                    val messages = ticket.relationships?.messages.orEmpty()
                        .sortedBy { m -> m.data.attributes.created_at.orEmpty() }
                    it.copy(isLoading = false, ticket = ticket, messages = messages)
                }
                // Abrir a thread é o que limpa os não lidos.
                if (markRead) runCatching { if (isAdmin) api.adminMarkRead(ticketId) else api.markRead(ticketId) }
            }.onFailure { t -> _state.update { it.copy(isLoading = false, toast = if (silent) it.toast else t.message) } }
        }
    }

    fun addAttachments(uris: List<Uri>) = viewModelScope.launch {
        val picked = reader.describeAll(uris)
        val tooBig = picked.filter { it.size > SUPPORT_MAX_FILE_BYTES }
        _state.update {
            it.copy(
                attachments = (it.attachments + (picked - tooBig.toSet())).take(SUPPORT_MAX_FILES),
                toast = if (tooBig.isNotEmpty()) "Ficheiro acima de 10 MB ignorado".tr() else it.toast,
            )
        }
    }

    fun removeAttachment(file: PickedFile) = _state.update { it.copy(attachments = it.attachments - file) }

    fun send(body: String, onSent: () -> Unit) {
        val files = _state.value.attachments
        if (body.isBlank() && files.isEmpty()) return
        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            runCatching {
                val parts = reader.parts(files)
                val textPart = body.takeIf { it.isNotBlank() }?.let(::text)
                val response = if (isAdmin) api.adminReply(ticketId, textPart, parts) else api.reply(ticketId, textPart, parts)
                check(response.isSuccessful) { serverMessage(json, response) }
            }.onSuccess {
                _state.update { it.copy(isSending = false, attachments = emptyList()) }
                onSent()
                refresh()
            }.onFailure { t -> _state.update { it.copy(isSending = false, toast = t.message) } }
        }
    }

    fun download(path: String, name: String) {
        downloader.downloadSupportAttachment(path, name)
            .onSuccess { _state.update { it.copy(toast = "A descarregar %s".tr().format(name)) } }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun closeTicket() = action { api.close(ticketId) }

    // ---- Admin
    fun update(status: String? = null, priority: String? = null, category: String? = null) =
        action { api.adminUpdate(ticketId, UpdateTicketRequest(status, priority, category)) }

    fun requestDeletion() = action { api.adminRequestDeletion(ticketId) }

    // ---- Copiloto (Claude): o agente revê sempre antes de enviar
    fun suggestReply() {
        _state.update { it.copy(isDrafting = true) }
        viewModelScope.launch {
            val response = runCatching { api.adminAiDraft(ticketId) }.getOrNull()
            val draft = response?.takeIf { it.isSuccessful }?.body()?.draft?.takeIf { it.isNotBlank() }
            _state.update {
                it.copy(
                    isDrafting = false,
                    draft = draft,
                    toast = if (draft != null) null else response?.let { r -> serverMessage(json, r) } ?: "O assistente IA não conseguiu responder. Tenta novamente.".tr(),
                )
            }
        }
    }

    fun consumeDraft() = _state.update { it.copy(draft = null) }

    fun summarize() {
        _state.update { it.copy(isSummarizing = true) }
        viewModelScope.launch {
            val response = runCatching { api.adminAiSummary(ticketId) }.getOrNull()
            val summary = response?.takeIf { it.isSuccessful }?.body()?.summary?.takeIf { it.isNotBlank() }
            _state.update {
                it.copy(
                    isSummarizing = false,
                    summary = summary ?: it.summary,
                    toast = if (summary != null) null else response?.let { r -> serverMessage(json, r) } ?: "O assistente IA não conseguiu responder. Tenta novamente.".tr(),
                )
            }
        }
    }

    /** Aplica a categoria/prioridade sugeridas (só o que difere do atual). */
    fun applyTriage() {
        val ticket = _state.value.ticket ?: return
        val ai = ticket.ai ?: return
        val category = ai.category?.takeIf { it != ticket.attributes.category }
        val priority = ai.priority?.takeIf { it != ticket.attributes.priority }
        if (category == null && priority == null) return
        update(priority = priority, category = category)
    }

    fun deleteTicket() {
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val response = runCatching { api.adminDelete(ticketId) }.getOrNull()
            if (response?.isSuccessful == true) {
                _state.update { it.copy(isBusy = false, deleted = true) }
            } else {
                _state.update { it.copy(isBusy = false, toast = response?.let { r -> serverMessage(json, r) } ?: "Erro".tr()) }
            }
        }
    }

    // ---- Pedido de eliminação de conta
    fun confirmDeletion(requestId: String, password: String, passkeyToken: String? = null) {
        _state.update { it.copy(isBusy = true, deletionError = null) }
        viewModelScope.launch {
            val response = runCatching { api.confirmDeletion(ticketId, requestId, ConfirmDeletionRequest(password, passkeyToken)) }.getOrNull()
            if (response?.isSuccessful == true) {
                _state.update { it.copy(isBusy = false) }
                sessionManager.refreshUser() // mostra o banner com contagem
                refresh()
            } else {
                _state.update {
                    it.copy(isBusy = false, deletionError = response?.let { r -> serverMessage(json, r) } ?: "Erro".tr())
                }
            }
        }
    }

    // O banner de eliminação só se atualiza depois de o DELETE ter sido aceite.
    fun cancelDeletion(requestId: String) = action(onSuccess = { if (!isAdmin) sessionManager.refreshUser() }) {
        if (isAdmin) api.adminCancelDeletion(ticketId, requestId) else api.cancelDeletion(ticketId, requestId)
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    private fun action(onSuccess: suspend () -> Unit = {}, call: suspend () -> Response<*>) {
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val response = runCatching { call() }.getOrNull()
            if (response?.isSuccessful == true) onSuccess()
            _state.update {
                it.copy(
                    isBusy = false,
                    toast = if (response?.isSuccessful == true) null else response?.let { r -> serverMessage(json, r) } ?: "Erro".tr(),
                )
            }
            refresh()
        }
    }
}

// ---------------------------------------------------------------- Novo ticket

data class NewTicketUiState(
    val attachments: List<PickedFile> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val createdId: String? = null,
    /** Resposta rápida (Claude + centro de ajuda) — só se o admin a ligou. */
    val assistEnabled: Boolean = false,
    val isAsking: Boolean = false,
    val assist: co.golink.tester.domain.support.SupportAssistResponse? = null,
)

@HiltViewModel
class NewTicketViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val api: SupportApi,
    private val json: Json,
    configRepository: co.golink.tester.data.config.ConfigRepository,
) : ViewModel() {
    private val reader = AttachmentReader(context)

    private val _state = MutableStateFlow(NewTicketUiState())
    val state: StateFlow<NewTicketUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val enabled = configRepository.load().getOrNull()?.ai?.support_assist == 1
            _state.update { it.copy(assistEnabled = enabled) }
        }
    }

    /** Pede uma resposta ao centro de ajuda; o ticket só é criado se o utilizador quiser. */
    fun askAssistant(subject: String, body: String) {
        _state.update { it.copy(isAsking = true, assist = null) }
        viewModelScope.launch {
            val response = runCatching {
                api.assist(co.golink.tester.domain.support.SupportAssistRequest(subject.trim(), body.trim()))
            }.getOrNull()
            val assist = response?.takeIf { it.isSuccessful }?.body()
                ?: co.golink.tester.domain.support.SupportAssistResponse()
            _state.update { it.copy(isAsking = false, assist = assist) }
        }
    }

    /** O texto mudou: a resposta anterior deixa de se aplicar. */
    fun clearAssist() {
        if (_state.value.assist != null) _state.update { it.copy(assist = null) }
    }

    fun addAttachments(uris: List<Uri>) = viewModelScope.launch {
        val all = reader.describeAll(uris)
        val picked = all.filter { it.size <= SUPPORT_MAX_FILE_BYTES }
        _state.update {
            it.copy(
                attachments = (it.attachments + picked).take(SUPPORT_MAX_FILES),
                error = if (picked.size < all.size) "Ficheiro acima de 10 MB ignorado".tr() else it.error,
            )
        }
    }

    fun removeAttachment(file: PickedFile) = _state.update { it.copy(attachments = it.attachments - file) }

    fun create(subject: String, body: String, priority: String, category: String) {
        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val response = api.createTicket(
                    text(subject.trim()),
                    text(body.trim()),
                    text(priority),
                    text(category),
                    reader.parts(_state.value.attachments),
                )
                check(response.isSuccessful) { serverMessage(json, response) }
                response.body()!!.data.id
            }.onSuccess { id -> _state.update { it.copy(isSending = false, createdId = id) } }
                .onFailure { t -> _state.update { it.copy(isSending = false, error = t.message) } }
        }
    }
}
