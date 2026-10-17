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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject

/** Limites do servidor (SupportTicketAttachment::MAX_FILES / MAX_FILESIZE_KB). */
const val SUPPORT_MAX_FILES = 5
const val SUPPORT_MAX_FILE_BYTES = 10L * 1024 * 1024

data class PickedFile(val uri: Uri, val name: String, val size: Long)

/** Lê nome/tamanho e converte os ficheiros escolhidos em partes multipart "attachments[]". */
internal class AttachmentReader(private val context: Context) {
    fun describe(uri: Uri): PickedFile {
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        return PickedFile(uri, name, size)
    }

    fun parts(files: List<PickedFile>): List<MultipartBody.Part> = files.map { f ->
        val mime = context.contentResolver.getType(f.uri) ?: "application/octet-stream"
        val bytes = context.contentResolver.openInputStream(f.uri)?.use { it.readBytes() } ?: ByteArray(0)
        MultipartBody.Part.createFormData("attachments[]", f.name, bytes.toRequestBody(mime.toMediaTypeOrNull()))
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
        if (!s.isLoading && s.hasMore) load(s.page + 1)
    }

    private fun load(page: Int) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val s = _state.value
            runCatching {
                val response = if (isAdmin) {
                    api.adminTickets(page, s.filter, s.query.ifBlank { null }, s.assigned, s.priority, s.category)
                } else {
                    api.tickets(page, s.filter)
                }
                check(response.isSuccessful) { "HTTP ${response.code()}" }
                response.body() ?: error("Resposta vazia")
            }.onSuccess { body ->
                val items = body.data.map { it.data }
                _state.update {
                    it.copy(
                        isLoading = false,
                        tickets = if (page == 1) items else it.tickets + items,
                        page = page,
                        hasMore = (body.meta?.current_page ?: 1) < (body.meta?.last_page ?: 1),
                    )
                }
            }.onFailure { t -> _state.update { it.copy(isLoading = false, error = t.message) } }

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

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val response = api.adminDashboard(_state.value.range)
                check(response.isSuccessful) { "HTTP ${response.code()}" }
                response.body() ?: error("Resposta vazia")
            }.onSuccess { data -> _state.update { it.copy(isLoading = false, data = data) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = t.message) } }
        }
    }
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

    /** Permissões de staff (admins e membros da Support Team). */
    val permissions = userRepository.me.value?.supportStaff
    val myId: String? = userRepository.me.value?.id

    private val _state = MutableStateFlow(SupportTicketUiState())
    val state: StateFlow<SupportTicketUiState> = _state.asStateFlow()

    init {
        refresh(markRead = true)
        if (isAdmin && permissions?.can_assign == true) {
            viewModelScope.launch {
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

    fun refresh(markRead: Boolean = false) {
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
            }.onFailure { t -> _state.update { it.copy(isLoading = false, toast = t.message) } }
        }
    }

    fun addAttachments(uris: List<Uri>) {
        val current = _state.value.attachments
        val picked = uris.map(reader::describe)
        val tooBig = picked.filter { it.size > SUPPORT_MAX_FILE_BYTES }
        val accepted = (current + (picked - tooBig.toSet())).take(SUPPORT_MAX_FILES)
        _state.update {
            it.copy(
                attachments = accepted,
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
    fun confirmDeletion(requestId: String, password: String) {
        _state.update { it.copy(isBusy = true, deletionError = null) }
        viewModelScope.launch {
            val response = runCatching { api.confirmDeletion(ticketId, requestId, ConfirmDeletionRequest(password)) }.getOrNull()
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

    fun cancelDeletion(requestId: String) = action {
        if (isAdmin) api.adminCancelDeletion(ticketId, requestId) else api.cancelDeletion(ticketId, requestId)
    }.also { if (!isAdmin) viewModelScope.launch { sessionManager.refreshUser() } }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    private fun action(call: suspend () -> Response<*>) {
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val response = runCatching { call() }.getOrNull()
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

    fun addAttachments(uris: List<Uri>) {
        val picked = uris.map(reader::describe).filter { it.size <= SUPPORT_MAX_FILE_BYTES }
        _state.update { it.copy(attachments = (it.attachments + picked).take(SUPPORT_MAX_FILES)) }
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
