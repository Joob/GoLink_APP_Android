package co.golink.tester.ui.screens.legacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.legacy.LegacyEventRow
import co.golink.tester.domain.legacy.LegacyItem
import co.golink.tester.domain.legacy.LegacyRecipient
import co.golink.tester.data.legacy.LegacyException
import co.golink.tester.data.legacy.LegacyRepository
import co.golink.tester.data.legacy.LegacyState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import co.golink.tester.ui.i18n.lt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Mensagem curta para o snackbar: chave legacy2.* e se é erro. */
data class LegacyToast(val key: String, val error: Boolean = false, val params: Map<String, Any?> = emptyMap())

data class LegacyUiState(
    val failed: Boolean = false,
    val creating: Boolean = false,
    /** Pedidos em curso (o Disarm confirmado com outro pedido a correr espera a vez). */
    val pending: Int = 0,
    val toast: LegacyToast? = null,
    /** Sobe a cada check-in manual: o Activity junta a linha nova. */
    val checkins: Int = 0,
    /** Adicionar do Drive em curso: (feitos, total) das chaves a cifrar. */
    val adding: Pair<Int, Int>? = null,
) {
    val busy: Boolean get() = pending > 0
}

/** Os erros que a UI sabe explicar (KNOWN_ERRORS de legacyUi.js). */
private val KNOWN_ERRORS = setOf(
    "legacy_guardian_self",
    "legacy_guardian_duplicate",
    "legacy_recipient_duplicate",
    "legacy_too_many_recipients",
    "legacy_invite_too_soon",
    "legacy_reconfirm_too_soon",
    "legacy_epoch_conflict",
    "legacy_not_editable",
    "legacy_not_armed",
    "legacy_last_recipient",
    "legacy_not_enough_guardians",
    "legacy_guardian_unreadable",
    "legacy_not_invited",
    "legacy_already_accepted",
    "legacy_wrong_account",
    "legacy_e2e_required",
    "legacy_missing_relays",
    "legacy_combine_failed",
    "legacy_step_up_password",
    "legacy_step_up_expired",
)

fun legacyErrorKey(e: Throwable): String {
    val code = (e as? LegacyException)?.code ?: e.message
    return if (code in KNOWN_ERRORS) "err_$code" else "error"
}

@HiltViewModel
class LegacyViewModel @Inject constructor(
    private val repo: LegacyRepository,
    private val backend: co.golink.tester.data.config.BackendUrlHolder,
    private val browse: co.golink.tester.data.browse.BrowseRepository,
    private val keys: co.golink.tester.data.encryption.E2EKeyManager,
    session: co.golink.tester.data.auth.SessionManager,
) : ViewModel() {
    /** O que a conta tem para o step-up: password (não social) e/ou passkeys. */
    data class StepUpAccount(val socialite: Boolean = false, val hasPasskeys: Boolean = false)

    val stepUpAccount: StateFlow<StepUpAccount> = session.state
        .map { state ->
            val user = (state as? co.golink.tester.data.auth.AuthState.Authenticated)?.user
            StepUpAccount(socialite = user?.socialiteAccount == true, hasPasskeys = user?.hasPasskeys == true)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StepUpAccount())

    /** Página da web do Legacy (o que a app ainda não faz). */
    fun webUrl(path: String): String = backend.current.trimEnd('/') + path

    val legacy: StateFlow<LegacyState> = repo.state

    private val _ui = MutableStateFlow(LegacyUiState())
    val ui: StateFlow<LegacyUiState> = _ui.asStateFlow()

    private var refreshedAt = 0L

    fun load() = viewModelScope.launch {
        _ui.update { it.copy(failed = false) }
        val ok = runCatching { repo.load() }.isSuccess
        if (!ok) {
            _ui.update { it.copy(failed = true) }
            return@launch
        }
        refreshedAt = System.currentTimeMillis()
        settle()
    }

    /** Trabalho que só o cliente do dono consegue fazer (legacySettle da web): rekey, partes por selar, pastas. */
    private suspend fun settle() {
        runCatching { repo.reconcile() }.onFailure { android.util.Log.w("Legacy", "reconciliar falhou", it) }
        runCatching { repo.syncFolders() }
    }

    /** Ao voltar à app: o estado muda sozinho no servidor. Uma falha deixa o que está no ecrã. */
    fun refreshIfStale() {
        // refreshedAt = 0: o load() deste ecrã ainda não acabou (o estado carregado é de uma visita anterior).
        if (refreshedAt == 0L || !legacy.value.loaded || System.currentTimeMillis() - refreshedAt < REFRESH_MS) return
        refreshedAt = System.currentTimeMillis()
        viewModelScope.launch { runCatching { repo.load() } }
    }

    fun create() = viewModelScope.launch {
        _ui.update { it.copy(creating = true) }
        runCatching { repo.create() }.onFailure { toast(LegacyToast("error", true)) }
        _ui.update { it.copy(creating = false) }
    }

    private fun run(genericError: Boolean = false, block: suspend () -> Unit) = viewModelScope.launch {
        _ui.update { it.copy(pending = it.pending + 1) }
        runCatching { block() }.onFailure { e -> toast(LegacyToast(if (genericError) "error" else legacyErrorKey(e), true)) }
        _ui.update { it.copy(pending = it.pending - 1) }
        // Como o writeVault da web: a resposta pode pedir um rekey (ex.: revogar e voltar a armar).
        if (legacy.value.vault?.rekey_required == true) settle()
    }

    fun checkin() = run(genericError = true) {
        val counted = repo.checkinNow()
        _ui.update { it.copy(checkins = it.checkins + 1) }
        toast(if (counted) LegacyToast("checkin_done") else LegacyToast("checkin_not_counted", true))
    }

    fun arm() = run(genericError = true) { repo.vaultAction("arm") }
    fun resume() = run { repo.vaultAction("resume") }

    /**
     * Desarmar/revogar (o servidor decide pelo estado) ou pausar, com step-up.
     * `step_up: true` vai sempre, para o servidor validar mesmo com LEGACY_STEP_UP
     * desligado. Erro do step-up → [onStepUpError] (o diálogo fica aberto); o resto
     * fecha-o e vai para o snackbar.
     */
    fun stepUpAction(
        request: LegacyStepUpRequest,
        password: String?,
        passkeyToken: String?,
        onStepUpError: (String) -> Unit,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        val body = buildJsonObject {
            if (request is LegacyStepUpRequest.Pause) put("until", request.until)
            put("step_up", true)
            if (!passkeyToken.isNullOrBlank()) put("passkey_token", passkeyToken)
            else if (!password.isNullOrEmpty()) put("password", password)
        }
        val action = if (request is LegacyStepUpRequest.Pause) "pause" else "disarm"
        _ui.update { it.copy(pending = it.pending + 1) }
        runCatching { repo.vaultAction(action, body) }
            .onSuccess { onDone() }
            .onFailure { e ->
                val code = (e as? LegacyException)?.code
                if (code == "legacy_step_up_password" || code == "legacy_step_up_expired") {
                    onStepUpError("err_$code")
                } else {
                    onDone()
                    toast(LegacyToast(legacyErrorKey(e), true))
                }
            }
        _ui.update { it.copy(pending = it.pending - 1) }
        if (legacy.value.vault?.rekey_required == true) settle()
    }

    /** Não grava o que já lá está (nem gera linha na Activity). */
    fun save(patch: JsonObject) {
        val vault = legacy.value.vault ?: return
        val current = mapOf(
            "release_after_days" to vault.release_after_days.toString(),
            "wait_days" to vault.wait_days.toString(),
            "checkin_web" to vault.checkin_web.toString(),
            "checkin_android" to vault.checkin_android.toString(),
            "checkin_email" to vault.checkin_email.toString(),
        )
        if (patch.all { (k, v) -> current[k] == (v as? kotlinx.serialization.json.JsonPrimitive)?.content }) return
        run { repo.saveSettings(patch) }
    }

    // ---- itens e mensagens (Fase 2) ----

    /** Escrita de um editor: fecha-o só se correu bem. */
    private fun write(onDone: () -> Unit, block: suspend () -> Unit) = viewModelScope.launch {
        _ui.update { it.copy(pending = it.pending + 1) }
        runCatching { block() }
            .onSuccess { onDone() }
            .onFailure { e -> toast(LegacyToast(legacyErrorKey(e), true)) }
        _ui.update { it.copy(pending = it.pending - 1) }
    }

    /** Corpo cifrado de um item. null = falhou (o editor fecha: gravar substituía-o por vazio). */
    fun readItem(id: String, onResult: (JsonObject?) -> Unit) = viewModelScope.launch {
        val payload = runCatching { repo.readItem(id) }.getOrNull()
        if (payload == null) toast(LegacyToast("error", true))
        onResult(payload)
    }

    fun saveNote(item: LegacyItem?, name: String, body: String, recipientIds: List<String>, onDone: () -> Unit) = write(onDone) {
        val payload = buildJsonObject { put("name", name.trim()); put("body", body) }
        val meta = buildJsonObject { put("name", name.trim()); put("lines", body.split('\n').size) }
        if (item != null) {
            repo.updateItem(item.dto.id, meta = meta, payload = payload)
        } else {
            repo.createItem("note", "sealed", meta, payload, recipientIds)
            repo.logEvent("vault", "item_added", mapOf("name" to name.trim()))
        }
    }

    fun saveItemSettings(item: LegacyItem, recipientIds: List<String>, timing: LegacyRepository.ItemTiming, onDone: () -> Unit) = write(onDone) {
        repo.updateItem(item.dto.id, recipientIds = recipientIds, timing = timing)
    }

    /** Anexar é dar o item a quem recebe a mensagem. Um rascunho ainda não dá nada a ninguém. */
    fun saveMessage(
        editing: LegacyItem?,
        subject: String,
        body: String,
        attached: List<LegacyItem>,
        recipientIds: List<String>,
        timing: LegacyRepository.ItemTiming,
        itemState: String,
        onDone: () -> Unit,
    ) = write(onDone) {
        val payload = buildJsonObject {
            put("subject", subject.trim())
            put("body", body)
            put("attached", kotlinx.serialization.json.buildJsonArray {
                attached.forEach { a -> add(buildJsonObject { put("id", a.dto.id); put("name", itemName(a)) }) }
            })
        }
        val meta = buildJsonObject { put("subject", subject.trim()); put("preview", body.take(PREVIEW_CHARS)) }
        if (itemState == "sealed") repo.grantItems(attached.map { it.dto.id }, recipientIds)
        if (editing != null) {
            repo.updateItem(editing.dto.id, meta = meta, payload = payload, recipientIds = recipientIds, itemState = itemState, timing = timing)
        } else {
            repo.createItem("message", itemState, meta, payload, recipientIds, timing)
            repo.logEvent("vault", "item_added", mapOf("name" to subject.trim()))
        }
    }

    fun removeItem(id: String) = write({}) { repo.removeItem(id) }

    // ---- guardiões e recipients (Fase 3) ----

    /** Adicionar guardião/recipient ou editar recipient. O erro fica no próprio diálogo. */
    fun savePerson(kind: String, person: LegacyRecipient?, email: String, name: String, relation: String, onError: (String) -> Unit, onDone: () -> Unit) =
        viewModelScope.launch {
            _ui.update { it.copy(pending = it.pending + 1) }
            runCatching {
                when {
                    person != null -> repo.updateRecipient(person.dto.id, email, name, relation)
                    kind == "recipient" -> repo.addRecipient(email, name, relation)
                    !repo.addGuardian(email, name, relation) -> toast(LegacyToast("guardian_not_recipient", true))
                }
            }
                .onSuccess { onDone() }
                .onFailure { e -> onError(legacyErrorKey(e)) }
            _ui.update { it.copy(pending = it.pending - 1) }
        }

    fun removeRecipient(id: String) = run { repo.removeRecipient(id) }
    fun removeGuardian(id: String) = run { repo.removeGuardian(id) }
    fun setThreshold(n: Int) {
        if (n == legacy.value.vault?.threshold) return
        run { repo.setThreshold(n) }
    }

    fun reinvite(id: String) = run {
        repo.reinviteGuardian(id)
        toast(LegacyToast("invite_sent"))
    }

    fun reconfirm(id: String) = run {
        repo.reconfirmGuardian(id)
        toast(LegacyToast("reconfirm_sent"))
    }

    /** Fecha só se correu bem; [onMoved] = a chave mudou entretanto (o diálogo mostra-o). */
    fun pinKey(id: String, publicKey: String, onMoved: () -> Unit, onDone: () -> Unit) = viewModelScope.launch {
        _ui.update { it.copy(pending = it.pending + 1) }
        runCatching { repo.pinGuardianKey(id, publicKey) }
            .onSuccess { onDone() }
            .onFailure { e -> if (e.message == "legacy_key_moved") onMoved() else toast(LegacyToast(legacyErrorKey(e), true)) }
        _ui.update { it.copy(pending = it.pending - 1) }
    }

    fun fingerprint(publicKey: String?): String = publicKey?.let { runCatching { keys.fingerprintOf(it) }.getOrNull() } ?: ""

    /** Pastas e ficheiros do Drive para o seletor (todas as páginas, até 20). */
    suspend fun listDriveFolder(id: String?): Result<List<co.golink.tester.domain.browse.BrowseItem>> = runCatching {
        val out = ArrayList<co.golink.tester.domain.browse.BrowseItem>()
        var page = 1
        do {
            val res = browse.listFolder(id, page, perPage = 200).getOrThrow()
            out += res.items
            page++
        } while (page <= res.lastPage && page <= 20)
        out
    }

    fun addDriveItems(entries: List<LegacyRepository.DrivePick>, recipientIds: List<String>, delayDays: Int) = viewModelScope.launch {
        _ui.update { it.copy(adding = 0 to 0) }
        runCatching {
            repo.addDriveItems(entries, recipientIds, delayDays) { done, total -> _ui.update { it.copy(adding = done to total) } }
        }
            .onSuccess { skipped -> toast(if (skipped > 0) LegacyToast("added_skipped", true, mapOf("count" to skipped)) else LegacyToast("added")) }
            .onFailure { toast(LegacyToast("error", true)) }
        _ui.update { it.copy(adding = null) }
    }

    fun toast(t: LegacyToast) = _ui.update { it.copy(toast = t) }
    fun consumeToast() = _ui.update { it.copy(toast = null) }

    private companion object {
        const val REFRESH_MS = 60_000L
        const val PREVIEW_CHARS = 140
    }
}

data class LegacyActivityState(
    val category: String = "all",
    val rows: List<LegacyEventRow> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val exporting: Boolean = false,
)

@HiltViewModel
class LegacyActivityViewModel @Inject constructor(
    private val repo: LegacyRepository,
    keys: co.golink.tester.data.encryption.E2EKeyManager,
) : ViewModel() {
    private val _state = MutableStateFlow(LegacyActivityState())
    val state: StateFlow<LegacyActivityState> = _state.asStateFlow()

    /** Sobe a cada carga: a resposta de uma anterior já não conta. */
    private var request = 0
    private var started = false

    init {
        // Bloqueio E2E: as linhas decifradas saem da memória (como o LegacyRepository);
        // ao desbloquear, recarrega se o ecrã já estava aberto.
        viewModelScope.launch {
            var wiped = false
            keys.unlocked.collect { unlocked ->
                if (!unlocked) {
                    request++
                    wiped = true
                    _state.update { LegacyActivityState(category = it.category) }
                } else if (wiped) {
                    wiped = false
                    if (started) load()
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun setCategory(key: String) {
        _state.update { it.copy(category = key, rows = emptyList(), cursor = null) }
        load()
    }

    fun load(cursor: String? = null) = viewModelScope.launch {
        val req = ++request
        val category = _state.value.category
        _state.update { it.copy(loading = true, failed = false) }
        runCatching { repo.events(category.takeIf { it != "all" }, cursor) }
            .onSuccess { page ->
                if (req != request) return@onSuccess
                // Chaves únicas na LazyColumn: uma linha repetida entre páginas não entra duas vezes.
                _state.update { s -> s.copy(rows = if (cursor != null) (s.rows + page.rows).distinctBy { it.id } else page.rows, cursor = page.cursor, loading = false) }
            }
            .onFailure {
                if (req != request) return@onFailure
                _state.update { s -> s.copy(loading = false, failed = cursor == null) }
            }
    }

    /** Junta ao topo o que entrou entretanto (ex.: depois de um check-in). */
    fun refresh() = viewModelScope.launch {
        val req = request
        val s = _state.value
        val page = runCatching { repo.events(s.category.takeIf { it != "all" }, null) }.getOrNull() ?: return@launch
        if (req != request) return@launch
        val known = _state.value.rows.map { it.id }.toSet()
        val fresh = page.rows.filter { it.id !in known }
        if (fresh.size < page.rows.size) {
            _state.update { it.copy(rows = fresh + it.rows) }
        } else {
            // Nada em comum com a lista: colar deixava um buraco no meio, por isso recomeça.
            request++
            _state.update { it.copy(rows = page.rows, cursor = page.cursor, loading = false, failed = false) }
        }
    }

    /** Todas as páginas por carregar, para o CSV (gerado no aparelho: o servidor não lê o registo). */
    suspend fun allRows(): List<LegacyEventRow> {
        val s = _state.value
        val rows = s.rows.toMutableList()
        var cursor = s.cursor
        while (cursor != null) {
            val page = repo.events(s.category.takeIf { it != "all" }, cursor)
            rows += page.rows
            cursor = page.cursor
        }
        return rows
    }

    fun setExporting(on: Boolean) = _state.update { it.copy(exporting = on) }

    /**
     * CSV de todas as páginas para o documento escolhido. Corre no viewModelScope (mudar de
     * pane não o corta) e, se falhar, apaga o documento em vez de o deixar truncado.
     */
    fun exportCsv(resolver: android.content.ContentResolver, uri: android.net.Uri, onError: () -> Unit) = viewModelScope.launch {
        if (_state.value.exporting) return@launch
        setExporting(true)
        runCatching {
            val rows = allRows()
            val csv = withContext(Dispatchers.Default) {
                val header = listOf(lt("csv_date"), lt("csv_category"), lt("csv_event"))
                val lines = listOf(header) + rows.map { listOf(dateTime(it.createdAt), lt("log_${it.category}"), eventText(it)) }
                lines.joinToString("\r\n") { line -> line.joinToString(",") { co.golink.tester.data.legacy.LegacyCrypto.csvCell(it) } }
            }
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    out.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("sem saída")
            }
        }.onFailure {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { android.provider.DocumentsContract.deleteDocument(resolver, uri) }
            }
            if (it !is kotlinx.coroutines.CancellationException) onError()
        }
        setExporting(false)
    }
}
