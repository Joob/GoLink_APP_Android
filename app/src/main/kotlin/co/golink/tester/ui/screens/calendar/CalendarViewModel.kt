package co.golink.tester.ui.screens.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.calendar.CalendarRepository
import co.golink.tester.data.calendar.EventConflictException
import co.golink.tester.data.calendar.EventForm
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.calendar.CalEvent
import co.golink.tester.domain.calendar.CalInfo
import co.golink.tester.domain.calendar.CalInstance
import co.golink.tester.domain.calendar.CalendarDates
import co.golink.tester.domain.calendar.Recurrence
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CalView { Month, Agenda, Day }

data class CalendarUiState(
    val view: CalView = CalView.Month,
    /** Dia selecionado (o mês/semana à volta dele é o que se carrega). */
    val cursor: LocalDate = LocalDate.now(),
    val events: List<CalEvent> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    /** Agenda: quantos dias à frente do cursor (cresce com o scroll). */
    val agendaDays: Long = AGENDA_STEP,
    /** Ocorrência aberta na folha de detalhe. */
    val open: CalInstance? = null,
    val searchQuery: String = "",
    val searchResults: List<CalEvent>? = null,
    val searching: Boolean = false,
    /** Editor aberto (event = null → novo). */
    val editor: EditorRequest? = null,
    val saving: Boolean = false,
    /** Seletor "Anexar do Drive" aberto. */
    val picker: Boolean = false,
    /** Escolhidos no seletor, à espera de entrar no formulário. */
    val pendingAttachments: List<co.golink.tester.domain.calendar.EventAttachment> = emptyList(),
    /** Anexo pronto a abrir no viewer (consumido pelo ecrã). */
    val openFileId: String? = null,
    /** Anexo sem acesso ao Drive mas com link de partilha: abre no browser. */
    val openUrl: String? = null,
    /** Definições abertas: "new" ou o id do calendário. */
    val settings: String? = null,
    val calBusy: Boolean = false,
)

data class EditorRequest(val event: CalEvent?, val start: java.time.Instant? = null)

const val AGENDA_STEP = 60L
private const val AGENDA_MAX = 360L

@HiltViewModel
class CalendarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: CalendarRepository,
    private val keys: E2EKeyManager,
    private val attachments: co.golink.tester.data.calendar.CalendarAttachments,
    private val viewerSession: co.golink.tester.ui.screens.viewer.FileViewerSession,
    private val userRepository: co.golink.tester.data.user.UserRepository,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("calendar", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        CalendarUiState(view = runCatching { CalView.valueOf(prefs.getString("view", null) ?: "") }.getOrDefault(CalView.Month)),
    )
    val state: StateFlow<CalendarUiState> = _state

    val calendars: StateFlow<List<CalInfo>> = repo.calendars

    // Feriados: visibilidade por aparelho (como o localStorage da web), país no perfil.
    private val _holidaysVisible = MutableStateFlow(prefs.getBoolean("holidays_visible", true))
    val holidaysVisible: StateFlow<Boolean> = _holidaysVisible

    /** null → país escolhido na web que o Android não conhece (sem feriados). */
    val holidayCountry: StateFlow<String?> = userRepository.me
        .map { u -> co.golink.tester.domain.calendar.Holidays.resolveCountry(u?.holidayCountry, u?.country, java.util.Locale.getDefault().country) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "PT")

    /** Escolha guardada no perfil (null = automático). */
    val holidayCountryChoice: StateFlow<String?> = userRepository.me
        .map { it?.holidayCountry }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Ocorrências do intervalo carregado, só dos calendários visíveis (como visibleCalendarEvents). */
    // Só refaz quando mudam os eventos ou o intervalo (não a cada toast/folha aberta),
    // e fora da main thread: a expansão das repetições pode dar milhares de ocorrências.
    private data class RangeKey(val events: List<CalEvent>, val from: LocalDate, val to: LocalDate)

    val instances: StateFlow<List<CalInstance>> = combine(
        _state.map { s -> range(s).let { (f, t) -> RangeKey(s.events, f, t) } }
            .distinctUntilChanged { a, b -> a.events === b.events && a.from == b.from && a.to == b.to },
        repo.calendars,
        _holidaysVisible,
        holidayCountry,
    ) { key, cals, showHolidays, country ->
        val visible = cals.filter { it.visible }.map { it.id }.toSet()
        val events = key.events.filter { e ->
            if (e.isGuest) e.raw.me?.status != "declined" else e.calendarId in visible
        }
        val from = key.from
        val to = key.to
        val zone = ZoneId.systemDefault()
        val holidays = if (showHolidays && country != null && keys.isUnlocked) {
            co.golink.tester.domain.calendar.Holidays.between(country, from, to).map { holidayEvent(country, it, zone) }
        } else emptyList()
        Recurrence.expand(events + holidays, CalendarDates.dayStart(from, zone), CalendarDates.dayStart(to, zone))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var loadJob: Job? = null
    private var loadedRange: Triple<CalView, LocalDate, LocalDate>? = null

    init {
        viewModelScope.launch {
            keys.unlocked.collect { unlocked ->
                if (unlocked) {
                    loadCalendars()
                    reload(force = true)
                } else {
                    loadedRange = null
                    _state.update { it.copy(events = emptyList(), open = null, loaded = false) }
                }
            }
        }
        viewModelScope.launch {
            repo.changes.collect {
                searchPool = null
                reload(force = true)
            }
        }
    }

    private suspend fun loadCalendars() {
        repo.loadCalendars().onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    private fun range(s: CalendarUiState): Pair<LocalDate, LocalDate> = when (s.view) {
        CalView.Month -> CalendarDates.monthGrid(s.cursor).let { it.first() to it.last().plusDays(1) }
        CalView.Day -> CalendarDates.startOfWeek(s.cursor).let { it to it.plusDays(7) }
        CalView.Agenda -> s.cursor to s.cursor.plusDays(s.agendaDays)
    }

    fun reload(force: Boolean = false) {
        if (!keys.isUnlocked) return
        val s = _state.value
        val (from, to) = range(s)
        val r = Triple(s.view, from, to)
        val prev = loadedRange
        if (!force && prev == r) return
        // Agenda a crescer: só o bloco novo, juntado ao que já está carregado.
        val append = !force && s.loaded && prev != null && prev.first == CalView.Agenda &&
            s.view == CalView.Agenda && prev.second == from && to.isAfter(prev.third)
        val fetchFrom = if (append) prev!!.third else from
        loadedRange = r
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val zone = ZoneId.systemDefault()
            if (repo.calendars.value.isEmpty()) loadCalendars()
            repo.fetchEvents(CalendarDates.dayStart(fetchFrom, zone), CalendarDates.dayStart(to, zone))
                .onSuccess { list ->
                    _state.update {
                        val events = if (append) (it.events + list).distinctBy { e -> e.id } else list
                        it.copy(events = events, loading = false, loaded = true, error = null)
                    }
                }
                .onFailure { e ->
                    loadedRange = null
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun setView(view: CalView) {
        prefs.edit().putString("view", view.name).apply()
        _state.update { it.copy(view = view, agendaDays = AGENDA_STEP) }
        reload()
    }

    fun select(day: LocalDate) {
        _state.update { it.copy(cursor = day, agendaDays = AGENDA_STEP) }
        reload()
    }

    fun today() = select(LocalDate.now())

    fun step(delta: Long) {
        val s = _state.value
        select(if (s.view == CalView.Day) s.cursor.plusDays(delta) else s.cursor.plusMonths(delta))
    }

    fun loadMoreAgenda() {
        val s = _state.value
        if (s.view != CalView.Agenda || s.loading || s.agendaDays >= AGENDA_MAX) return
        _state.update { it.copy(agendaDays = it.agendaDays + AGENDA_STEP) }
        reload()
    }

    fun openInstance(instance: CalInstance?) = _state.update { it.copy(open = instance) }

    /** Do sino / pesquisa: abre o evento pelo id e salta para o dia dele. */
    fun openEventById(id: String, date: LocalDate? = null) {
        if (date != null) select(date)
        viewModelScope.launch {
            if (repo.calendars.value.isEmpty()) loadCalendars()
            repo.fetchEvent(id)
                .onSuccess { ev ->
                    val day = date ?: ev.start.atZone(ZoneId.systemDefault()).toLocalDate()
                    if (date == null) select(day)
                    val zone = ZoneId.systemDefault()
                    val inst = Recurrence.expand(listOf(ev), CalendarDates.dayStart(day, zone), CalendarDates.dayStart(day.plusDays(1), zone)).firstOrNull()
                        ?: CalInstance(ev, ev.start, ev.end, "${ev.id}@${ev.start.toEpochMilli()}")
                    openInstance(inst)
                }
                .onFailure { _state.update { it.copy(toast = "Não foi possível concluir. Tenta de novo.".tr()) } }
        }
    }

    fun toggleCalendar(cal: CalInfo) = viewModelScope.launch {
        repo.updateCalendar(cal.id, visible = !cal.visible)
            .onFailure { _state.update { it.copy(toast = "Não foi possível concluir. Tenta de novo.".tr()) } }
    }

    fun deleteEvent(event: CalEvent) = viewModelScope.launch {
        repo.deleteEvent(event.id)
            .onSuccess { _state.update { it.copy(open = null, toast = "Evento apagado".tr()) } }
            .onFailure { _state.update { it.copy(toast = "Não foi possível concluir. Tenta de novo.".tr()) } }
    }

    fun rsvp(instance: CalInstance, status: String) = viewModelScope.launch {
        repo.rsvp(instance.event, status)
            .onSuccess { saved -> _state.update { it.copy(open = instance.copy(event = saved)) } }
            .onFailure { e ->
                val msg = if (e is EventConflictException) "O evento foi alterado noutro lado — mostramos a versão mais recente." else "Não foi possível concluir. Tenta de novo."
                _state.update { it.copy(toast = msg.tr()) }
            }
    }

    fun newEvent(start: java.time.Instant? = null) = _state.update { it.copy(editor = EditorRequest(null, start)) }

    fun editEvent(event: CalEvent) = _state.update { it.copy(editor = EditorRequest(event)) }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    /** Grava; 409 → o editor passa a mostrar a versão atual e avisa (como a web). */
    fun save(form: EventForm) {
        val req = _state.value.editor ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            // Com convidados, quem gere o evento cria links "só ver" dos anexos (só o dono do ficheiro consegue).
            val manages = req.event == null || req.event.canManage
            val ready = if (manages && form.guests.isNotEmpty() && form.attachments.isNotEmpty()) {
                val (list, tokens) = attachments.ensureLinks(form.attachments)
                form.copy(attachments = list, shareTokens = form.shareTokens + tokens)
            } else form
            repo.saveEvent(req.event, ready)
                .onSuccess { _state.update { it.copy(saving = false, editor = null, open = null, toast = "Evento guardado".tr()) } }
                .onFailure { e ->
                    if (e is EventConflictException) {
                        _state.update {
                            it.copy(
                                saving = false,
                                editor = EditorRequest(e.fresh),
                                toast = "O evento foi alterado noutro lado — mostramos a versão mais recente.".tr(),
                            )
                        }
                    } else {
                        _state.update { it.copy(saving = false, toast = "Não foi possível concluir. Tenta de novo.".tr()) }
                    }
                }
        }
    }

    // ---- feriados -------------------------------------------------------------------

    fun toggleHolidays() {
        val next = !_holidaysVisible.value
        _holidaysVisible.value = next
        prefs.edit().putBoolean("holidays_visible", next).apply()
    }

    fun setHolidayCountry(code: String?) = calendarOp(true) {
        repo.setHolidayCountry(code).onSuccess { userRepository.fetchMe() }
    }

    // ---- calendários ------------------------------------------------------------

    fun openSettings(id: String?) = _state.update { it.copy(settings = id) }

    private fun calendarOp(closeOnSuccess: Boolean, op: suspend () -> Result<Unit>) {
        if (_state.value.calBusy) return
        _state.update { it.copy(calBusy = true) }
        viewModelScope.launch {
            op()
                .onSuccess { _state.update { s -> s.copy(calBusy = false, settings = if (closeOnSuccess) null else s.settings) } }
                .onFailure { e ->
                    val msg = if (e is co.golink.tester.data.calendar.CalendarShareNotFoundException) "Esse email não tem conta com encriptação ativa."
                    else "Não foi possível concluir. Tenta de novo."
                    _state.update { it.copy(calBusy = false, toast = msg.tr()) }
                }
        }
    }

    fun createCalendar(name: String, color: String) = calendarOp(true) { repo.createCalendar(name, color) }

    fun updateCalendar(id: String, name: String?, color: String?) = calendarOp(true) { repo.updateCalendar(id, name = name, color = color) }

    fun shareCalendar(id: String, email: String, permission: String) = calendarOp(false) { repo.shareCalendar(id, email, permission) }

    fun removeMember(id: String, userId: String) = calendarOp(false) { repo.removeMember(id, userId) }

    /** Dono: apaga (em fila no servidor). Membro: sai. */
    fun deleteCalendar(id: String) = calendarOp(true) { repo.deleteCalendar(id) }

    // ---- anexos ----------------------------------------------------------------

    fun openPicker(open: Boolean) = _state.update { it.copy(picker = open) }

    suspend fun listDriveFolder(id: String?) = attachments.listFolder(id)

    fun attach(files: List<co.golink.tester.domain.browse.BrowseItem.File>) {
        attachments.remember(files)
        _state.update { it.copy(picker = false, pendingAttachments = files.map(attachments::toAttachment)) }
    }

    fun consumePendingAttachments() = _state.update { it.copy(pendingAttachments = emptyList()) }

    /** Abre no viewer pela pasta-mãe; sem acesso usa o link de partilha (convidados). */
    fun openAttachment(a: co.golink.tester.domain.calendar.EventAttachment, myUserId: String?) = viewModelScope.launch {
        val file = attachments.findFile(a)
        when {
            file != null -> {
                viewerSession.files = listOf(file)
                viewerSession.startId = file.id
                viewerSession.fromTrash = false
                val foreign = file.ownerId != null && file.ownerId != myUserId
                viewerSession.readOnlyIds = if (foreign) setOf(file.id) else emptySet()
                viewerSession.notOwnedIds = if (foreign) setOf(file.id) else emptySet()
                _state.update { it.copy(openFileId = file.id) }
            }
            !a.share_url.isNullOrBlank() -> _state.update { it.copy(openUrl = a.share_url) }
            else -> _state.update { it.copy(toast = "Sem acesso a este ficheiro.".tr()) }
        }
    }

    fun consumeOpenFile() = _state.update { it.copy(openFileId = null, openUrl = null) }

    private var searchJob: Job? = null

    /** 24 meses de eventos, pedidos uma vez por sessão de pesquisa; cada tecla só filtra. */
    private var searchPool: List<CalEvent>? = null

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            val pool = searchPool ?: run {
                _state.update { it.copy(searching = true) }
                repo.searchPool(LocalDate.now()).getOrElse {
                    _state.update { it.copy(searching = false, toast = "Não foi possível concluir. Tenta de novo.".tr()) }
                    return@launch
                }.also { searchPool = it }
            }
            val q = query.trim().lowercase()
            val visible = repo.calendars.value.filter { it.visible }.map { it.id }.toSet()
            val shown = kotlinx.coroutines.withContext(Dispatchers.Default) {
                pool.filter { e ->
                    (if (e.isGuest) e.raw.me?.status != "declined" else e.calendarId in visible) &&
                        listOf(e.data.title, e.data.location, e.data.notes).any { it.lowercase().contains(q) }
                }.sortedBy { it.start }
            }
            _state.update { it.copy(searchResults = shown, searching = false) }
        }
    }

    /** Fechar a pesquisa larga os eventos decifrados que carregou. */
    fun closeSearch() {
        search("")
        searchPool = null
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    fun toast(msg: String) = _state.update { it.copy(toast = msg) }
}

/** Feriado como evento de dia inteiro, só de leitura (roxo; não vem do servidor). */
private fun holidayEvent(country: String, h: co.golink.tester.domain.calendar.Holidays.Holiday, zone: ZoneId): CalEvent {
    val raw = co.golink.tester.domain.calendar.EventDto(
        id = "holiday-$country-${h.date}",
        calendar_id = "holidays",
        starts_at = CalendarDates.allDayToIso(h.date),
        ends_at = CalendarDates.allDayToIso(h.date.plusDays(1)),
        all_day = true,
    )
    return CalEvent(
        raw = raw,
        start = CalendarDates.dayStart(h.date, zone),
        end = CalendarDates.dayStart(h.date.plusDays(1), zone),
        data = co.golink.tester.domain.calendar.EventPayload(title = h.name),
        locked = false,
        key = null,
        holiday = true,
    )
}
