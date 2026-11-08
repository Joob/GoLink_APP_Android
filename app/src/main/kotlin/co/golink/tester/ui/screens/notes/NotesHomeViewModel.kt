package co.golink.tester.ui.screens.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.notes.NoteItem
import co.golink.tester.data.notes.NoteTask
import co.golink.tester.data.notes.NoteVersion
import co.golink.tester.data.notes.NotesCore
import co.golink.tester.data.notes.NotesRepository
import co.golink.tester.data.notes.NotesState
import co.golink.tester.ui.i18n.nt
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray

/** Secção da área Notes (as rotas da web: all, pinned, shared, notebook/:nb, tag/:tag, checklists, search, history). */
sealed interface NotesSection {
    data object All : NotesSection
    data object Pinned : NotesSection
    data object Shared : NotesSection
    data class Notebook(val id: String) : NotesSection
    data class Tag(val name: String) : NotesSection
    data object Checklists : NotesSection
    data object Search : NotesSection
    data object History : NotesSection

    val isList: Boolean get() = this is All || this is Pinned || this is Shared || this is Notebook || this is Tag

    /** Caminho da web (o editor na WebView abre "<path>/<id>"). */
    val path: String get() = when (this) {
        All -> "all"
        Pinned -> "pinned"
        Shared -> "shared"
        is Notebook -> "notebook/$id"
        is Tag -> "tag/" + android.net.Uri.encode(name)
        Checklists -> "checklists"
        Search -> "search"
        History -> "history"
    }

    companion object {
        /** "all/<id>" → (All, id). */
        fun parse(path: String): Pair<NotesSection, String?> {
            val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
            // O id da nota vem depois dos argumentos da secção (os cadernos também são UUIDs).
            val argc = when (parts.firstOrNull()) {
                "notebook", "tag" -> 2
                "all", "pinned", "shared", "checklists", "search", "history" -> 1
                else -> 0
            }
            val id = parts.getOrNull(argc)?.takeIf { UUID_RE.matches(it) }
            val section = when (parts.firstOrNull()) {
                "pinned" -> Pinned
                "shared" -> Shared
                "notebook" -> parts.getOrNull(1)?.let(::Notebook) ?: All
                "tag" -> parts.getOrNull(1)?.let { Tag(android.net.Uri.decode(it)) } ?: All
                "checklists" -> Checklists
                "search" -> Search
                "history" -> History
                else -> All
            }
            return section to id
        }

        private val UUID_RE = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

data class SearchState(
    val query: String = "",
    /** all | shared | files */
    val filter: String = "all",
    val results: List<SearchHit> = emptyList(),
    val indexed: Int = 0,
    val total: Int = 0,
)

/** Resultado da pesquisa: excerto com o termo marcado (texto, é_acerto). */
data class SearchHit(val note: NoteItem, val parts: List<Pair<String, Boolean>>)

data class HistoryState(
    val tab: String = "versions",
    val noteId: String? = null,
    val versions: List<NoteVersion>? = null,
    val keepDays: Int = 90,
    val versionId: String? = null,
    val diff: JsonArray? = null,
    val loading: Boolean = false,
    val restoring: Boolean = false,
)

/**
 * Casca nativa das Notes: lista, Checklists, pesquisa no aparelho e histórico,
 * sobre o NotesRepository (em memória: voltar às Notes é instantâneo). O editor de
 * uma nota ainda é a WebView até a Fase 3.
 */
@HiltViewModel
class NotesHomeViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: NotesRepository,
    core: NotesCore,
    userRepository: co.golink.tester.data.user.UserRepository,
) : ViewModel() {

    /** Utilizador atual (avatar do "Agora" no histórico). */
    val me = userRepository.me

    val state: StateFlow<NotesState> = repo.state
    val trash: StateFlow<List<NoteItem>?> = repo.trash

    private val initial = NotesSection.parse(savedState.get<String>("path").orEmpty())

    private val _section = MutableStateFlow(initial.first)
    val section: StateFlow<NotesSection> = _section.asStateFlow()

    /** Nota pedida no arranque (deep link "all/<id>"): o ecrã abre-a uma vez. */
    var initialNoteId: String? = initial.second
        private set

    fun consumeInitialNote(): String? = initialNoteId.also { initialNoteId = null }

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        core.warmUp()
        if (!repo.state.value.loaded) viewModelScope.launch { repo.refresh() }
    }

    fun open(section: NotesSection) {
        _section.value = section
        when (section) {
            NotesSection.Checklists -> loadTasks()
            NotesSection.History -> if (history.value.tab == "trash") loadTrash()
            else -> Unit
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            repo.refresh().onFailure { toast(nt("load_failed")) }
            if (_section.value == NotesSection.Checklists) loadTasksNow()
            _refreshing.value = false
        }
    }

    /** /sync a cada 5 s enquanto o ecrã está visível (o ecrã chama isto num repeatOnLifecycle). */
    suspend fun syncLoop() {
        while (true) {
            if (repo.syncIfChanged() && _section.value == NotesSection.Checklists) loadTasksNow()
            delay(5_000)
        }
    }

    /** Já (ex.: o editor fechou): recarrega se o carimbo mudou, sem indicador. */
    fun syncNow() {
        viewModelScope.launch { repo.syncIfChanged() }
    }

    private fun toast(message: String) {
        _toasts.tryEmit(message)
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { toast(nt("error")) }
        }
    }

    // ---- notas ---------------------------------------------------------------

    fun setPinned(note: NoteItem, pinned: Boolean) = act { repo.setPinned(note.id, pinned) }

    fun move(note: NoteItem, notebookId: String?) = act { repo.moveToNotebook(note.id, notebookId) }

    fun delete(note: NoteItem) = act { repo.delete(note.id) }

    fun createNotebook(name: String, color: String) = act { repo.createNotebook(name, color) }

    fun updateNotebook(id: String, name: String?, color: String?) = act { repo.updateNotebook(id, name, color) }

    fun deleteNotebook(id: String) = act {
        repo.deleteNotebook(id)
        if ((_section.value as? NotesSection.Notebook)?.id == id) _section.value = NotesSection.All
    }

    // ---- Checklists ------------------------------------------------------------

    private val _tasks = MutableStateFlow<List<NoteTask>?>(null)
    val tasks: StateFlow<List<NoteTask>?> = _tasks.asStateFlow()

    fun loadTasks() {
        viewModelScope.launch { loadTasksNow() }
    }

    private suspend fun loadTasksNow() {
        runCatching { repo.loadTasks() }
            .onSuccess { _tasks.value = it }
            .onFailure { if (_tasks.value == null) _tasks.value = emptyList(); toast(nt("load_failed")) }
    }

    fun toggleTask(task: NoteTask) {
        if (!task.canEdit) return
        val done = !task.done
        _tasks.value = _tasks.value?.map { if (it.noteId == task.noteId && it.uid == task.uid) it.copy(done = done) else it }
        viewModelScope.launch {
            runCatching { repo.toggleTask(task.noteId, task.uid, done) }.onFailure {
                _tasks.value = _tasks.value?.map { if (it.noteId == task.noteId && it.uid == task.uid) it.copy(done = !done) else it }
                toast(nt("error"))
            }
        }
    }

    // ---- pesquisa (no aparelho) -------------------------------------------------

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    /** Texto decifrado por nota ("updated_at|version" → texto em claro). */
    private val texts = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()
    /** Texto já dobrado por nota (dobrar é caro: faz-se uma vez por versão da nota). */
    private class Folded(val stamp: String, val hay: String, val folded: String, val map: IntArray)
    private val folds = java.util.concurrent.ConcurrentHashMap<String, Folded>()
    private var indexJob: Job? = null
    private var searchJob: Job? = null

    fun setQuery(q: String) {
        _search.value = _search.value.copy(query = q)
        runSearch()
        if (q.isNotBlank()) ensureIndex()
    }

    fun setFilter(filter: String) {
        _search.value = _search.value.copy(filter = filter)
        runSearch()
    }

    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")
    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "")

    /** Fora da thread principal; a pesquisa mais recente cancela a anterior. */
    private fun runSearch() {
        searchJob?.cancel()
        val s = _search.value
        val notes = repo.state.value.notes
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val terms = norm(s.query).split(spaces).filter { it.isNotEmpty() }
            val pool = notes.filter {
                !it.locked && (s.filter != "shared" || it.shared) && (s.filter != "files" || it.meta.hasFiles || it.filesCount > 0)
            }
            val results = if (terms.isEmpty()) emptyList() else pool.sortedByDescending { it.updatedAt }.mapNotNull { n ->
                ensureActive()
                val f = folded(n)
                if (!terms.all { it in f.folded }) return@mapNotNull null
                val idx = f.folded.indexOf(terms[0])
                val start = f.map[idx]
                val end = f.map.getOrElse(idx + terms[0].length) { f.hay.length }
                SearchHit(n, excerpt(f.hay, start, maxOf(1, end - start)))
            }
            ensureActive()
            _search.update { cur -> if (cur.query == s.query && cur.filter == s.filter) cur.copy(results = results) else cur }
        }
    }

    private fun folded(n: NoteItem): Folded {
        val text = texts[n.id]
        val stamp = "${n.updatedAt}|${n.version}|${text?.first}|${n.meta.title}|${n.meta.tags}"
        folds[n.id]?.takeIf { it.stamp == stamp }?.let { return it }
        val hay = n.meta.title + "\n" + n.meta.tags.joinToString(" ") { "#$it" } + "\n" + (text?.second ?: n.meta.snippet)
        val (f, map) = foldMap(hay)
        return Folded(stamp, hay, f, map).also { folds[n.id] = it }
    }

    /** Texto dobrado (minúsculas, sem acentos) + posição de cada carácter no original. */
    private fun foldMap(text: String): Pair<String, IntArray> {
        val out = StringBuilder(text.length)
        val map = IntArray(text.length * 2)
        var k = 0
        fun push(c: Char, i: Int) {
            if (k == map.size) return
            out.append(c); map[k++] = i
        }
        text.forEachIndexed { i, c ->
            if (c.code < 0x80) push(c.lowercaseChar(), i)
            else Normalizer.normalize(c.lowercaseChar().toString(), Normalizer.Form.NFD).forEach { d ->
                if (Character.getType(d) != Character.NON_SPACING_MARK.toInt()) push(d, i)
            }
        }
        return out.toString() to map.copyOf(k)
    }

    /** Como excerpt() de Search.vue: 40 caracteres antes, 60 depois. */
    private fun excerpt(text: String, idx: Int, len: Int): List<Pair<String, Boolean>> {
        val flat = text.replace(spaces, " ")
        val offset = text.substring(0, idx).replace(spaces, " ").length
        val start = maxOf(0, offset - 40)
        val end = minOf(flat.length, offset + len + 60)
        val hitEnd = minOf(flat.length, offset + len)
        return listOf(
            (if (start > 0) "…" else "") + flat.substring(start, offset) to false,
            flat.substring(offset, hitEnd) to true,
            flat.substring(hitEnd, maxOf(hitEnd, end)) + (if (end < flat.length) "…" else "") to false,
        )
    }

    private fun ensureIndex() {
        if (indexJob?.isActive == true) return
        indexJob = viewModelScope.launch {
            val notes = repo.state.value.notes.filter { !it.locked }
            val todo = notes.filter { texts[it.id]?.first != "${it.updatedAt}|${it.version}" }
            _search.value = _search.value.copy(indexed = notes.size - todo.size, total = notes.size)
            // Resultados a meio da indexação: no máximo a cada 400 ms (não a cada nota).
            var lastRun = 0L
            for (n in todo) {
                runCatching { repo.plainText(n.id) }
                    .onSuccess { texts[n.id] = "${n.updatedAt}|${n.version}" to it }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                _search.update { it.copy(indexed = it.indexed + 1) }
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastRun > 400) { lastRun = now; runSearch() }
            }
            runSearch()
        }
    }

    // ---- histórico -------------------------------------------------------------

    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history.asStateFlow()
    private var versionJob: Job? = null
    private var diffJob: Job? = null

    fun setHistoryTab(tab: String) {
        _history.value = _history.value.copy(tab = tab)
        if (tab == "trash") loadTrash()
    }

    fun loadTrash() {
        viewModelScope.launch { repo.loadTrash().onFailure { toast(nt("load_failed")) } }
    }

    fun pickHistoryNote(id: String?) {
        versionJob?.cancel()
        diffJob?.cancel()
        _history.value = _history.value.copy(noteId = id, versions = null, versionId = null, diff = null, loading = false)
        if (id == null) return
        versionJob = viewModelScope.launch {
            runCatching { repo.loadVersions(id) }
                .onSuccess { (list, keep) ->
                    if (_history.value.noteId != id) return@onSuccess
                    _history.value = _history.value.copy(versions = list, keepDays = keep)
                    list.firstOrNull()?.let { pickVersion(it.id) }
                }
                .onFailure {
                    // Cancelado (outra nota escolhida entretanto): não mexe no estado nem avisa.
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    if (_history.value.noteId == id) _history.value = _history.value.copy(versions = emptyList())
                    toast(nt("load_failed"))
                }
        }
    }

    fun pickVersion(versionId: String?) {
        val noteId = _history.value.noteId ?: return
        diffJob?.cancel()
        if (versionId == null) {
            _history.value = _history.value.copy(versionId = null, diff = null, loading = false)
            return
        }
        _history.value = _history.value.copy(versionId = versionId, diff = null, loading = true)
        diffJob = viewModelScope.launch {
            runCatching {
                val old = repo.versionLines(noteId, versionId)
                val now = repo.currentLines(noteId)
                repo.lineDiff(old, now)
            }.onSuccess { diff ->
                if (_history.value.versionId == versionId) _history.value = _history.value.copy(diff = diff, loading = false)
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                if (_history.value.versionId == versionId) _history.value = _history.value.copy(loading = false)
                toast(nt("error"))
            }
        }
    }

    fun restoreVersion() {
        val h = _history.value
        val noteId = h.noteId ?: return
        val versionId = h.versionId ?: return
        _history.value = h.copy(restoring = true)
        viewModelScope.launch {
            runCatching { repo.restoreVersion(noteId, versionId) }
                .onSuccess { toast(nt("restored")); pickHistoryNote(noteId) }
                .onFailure { toast(nt("error")) }
            _history.value = _history.value.copy(restoring = false)
        }
    }

    fun restore(note: NoteItem) = act {
        repo.restore(note.id)
        toast(nt("note_restored"))
    }

    fun deleteForever(note: NoteItem) = act { repo.forceDelete(note.id) }

    fun emptyTrash() = act {
        repo.trash.value.orEmpty().forEach { repo.forceDelete(it.id) }
    }
}
