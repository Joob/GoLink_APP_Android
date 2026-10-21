package co.golink.tester.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.LanguageData
import co.golink.tester.domain.admin.stringMap
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsCard
import co.golink.tester.ui.screens.settings.SettingsDialog
import co.golink.tester.ui.screens.settings.SettingsField
import co.golink.tester.ui.screens.settings.SettingsNotice
import co.golink.tester.ui.screens.settings.SettingsOutlinedButton
import co.golink.tester.ui.screens.settings.SettingsPrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Placeholders que têm de sobreviver à tradução (mesma regex da web). */
private val VAR_RE = Regex(""":[A-Za-z_]\w*|\{[A-Za-z_]\w*\}""")
private val TRANSLATABLE_RE = Regex("""\p{L}{3,}""")
private fun varsOf(s: String?) = VAR_RE.findAll(s.orEmpty()).map { it.value }.toSet()

enum class StringFilter { All, Pending, MissingVars }

@HiltViewModel
class LanguagesViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val languages: List<LanguageData> = emptyList(),
        val defaultLocale: String = "en",
        val reference: Map<String, String> = emptyMap(),
        val progress: Map<String, Int> = emptyMap(),
        val ai: Boolean = false,
        val isLoading: Boolean = true,
        val error: String? = null,
        /** Língua aberta no editor de strings. */
        val selected: LanguageData? = null,
        val translations: Map<String, String> = emptyMap(),
        val query: String = "",
        val filter: StringFilter = StringFilter.All,
        val busy: String? = null,
        val formError: String? = null,
        val showCreate: Boolean = false,
        /** Tradução automática em curso: feitas / total. */
        val autoDone: Int = 0,
        val autoTotal: Int = 0,
        val toast: String? = null,
    ) {
        val isReference get() = selected?.attributes?.locale == "en"

        /** Mesmo critério do servidor: igual ao inglês e com texto traduzível. */
        val pendingKeys: Set<String>
            get() = if (isReference) emptySet() else translations.filter { (k, v) ->
                v == reference[k] && TRANSLATABLE_RE.containsMatchIn(v.replace(Regex("<[^>]*>"), ""))
            }.keys

        val missingVarKeys: Set<String>
            get() = translations.filter { (k, v) -> varsOf(reference[k]).any { it !in v } }.keys

        val visibleKeys: List<String>
            get() {
                val q = query.trim().lowercase()
                val base = when (filter) {
                    StringFilter.All -> translations.keys
                    StringFilter.Pending -> pendingKeys
                    StringFilter.MissingVars -> missingVarKeys
                }
                return base.filter { k ->
                    q.isEmpty() || k.lowercase().contains(q) || translations[k].orEmpty().lowercase().contains(q) ||
                        reference[k].orEmpty().lowercase().contains(q)
                }.sorted()
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var autoJob: Job? = null

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = it.languages.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.languages()
                .onSuccess { r ->
                    _state.update {
                        it.copy(
                            languages = r.data.map { e -> e.data },
                            defaultLocale = r.meta.current_language?.data?.attributes?.locale ?: "en",
                            reference = r.meta.reference_translations.stringMap(),
                            progress = r.meta.progress,
                            ai = r.meta.ai_available,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun open(lang: LanguageData) {
        _state.update { it.copy(selected = lang, translations = emptyMap(), query = "", filter = StringFilter.All, busy = "open") }
        viewModelScope.launch {
            repository.language(lang.idString)
                .onSuccess { l -> _state.update { it.copy(busy = null, selected = l, translations = l.attributes.translations.stringMap()) } }
                .onFailure { t -> _state.update { it.copy(busy = null, selected = null, toast = t.message) } }
        }
    }

    fun close() {
        autoJob?.cancel()
        _state.update { it.copy(selected = null, translations = emptyMap(), busy = null) }
        load()
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: StringFilter) = _state.update { it.copy(filter = f) }

    fun saveString(key: String, value: String) {
        val id = _state.value.selected?.idString ?: return
        viewModelScope.launch {
            repository.updateLanguageString(id, key, value)
                .onSuccess { _state.update { it.copy(translations = it.translations + (key to value), toast = "Texto guardado".tr()) } }
                .onFailure { t -> _state.update { it.copy(toast = t.message) } }
        }
    }

    fun rename(name: String) = action("rename", "Nome atualizado".tr()) {
        repository.renameLanguage(_state.value.selected!!.idString, name).onSuccess {
            _state.update { s -> s.copy(selected = s.selected?.let { it.copy(attributes = it.attributes.copy(name = name.trim())) }) }
        }
    }

    fun makeDefault() = action("default", "Língua por omissão alterada".tr()) {
        val locale = _state.value.selected!!.attributes.locale
        repository.updateSetting("language", locale).onSuccess { _state.update { it.copy(defaultLocale = locale) } }
    }

    fun delete() = action("delete", "Língua apagada".tr()) {
        repository.deleteLanguage(_state.value.selected!!.idString).onSuccess { close() }
    }

    fun showCreate(show: Boolean) = _state.update { it.copy(showCreate = show, formError = null) }

    fun create(name: String, locale: String) {
        _state.update { it.copy(busy = "create", formError = null) }
        viewModelScope.launch {
            repository.createLanguage(name, locale)
                .onSuccess { _state.update { it.copy(busy = null, showCreate = false, toast = "Língua criada".tr()) }; load() }
                .onFailure { t -> _state.update { it.copy(busy = null, formError = t.message) } }
        }
    }

    /** Lotes de 60 até não haver pendentes (como a web); pode ser cancelada. */
    fun autoTranslate() {
        val id = _state.value.selected?.idString ?: return
        autoJob = viewModelScope.launch {
            _state.update { it.copy(busy = "auto", autoDone = 0, autoTotal = it.pendingKeys.size) }
            var offset = 0
            while (isActive) {
                val r = repository.autoTranslate(id, offset).getOrElse { t ->
                    _state.update { it.copy(busy = null, toast = t.message) }
                    return@launch
                }
                val saved = r.translations.stringMap()
                _state.update { it.copy(translations = it.translations + saved, autoDone = (it.autoDone + saved.size).coerceAtMost(it.autoTotal)) }
                offset = r.next_offset
                if (r.remaining == 0) break
            }
            _state.update { it.copy(busy = null, toast = "%d textos traduzidos".tr().format(it.autoDone)) }
        }
    }

    fun cancelAuto() {
        autoJob?.cancel()
        _state.update { it.copy(busy = null) }
    }

    private fun action(key: String, success: String, block: suspend () -> Result<Unit>) {
        _state.update { it.copy(busy = key, formError = null) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(busy = null, toast = success) } }
                .onFailure { t -> _state.update { it.copy(busy = null, formError = t.message) } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

@Composable
fun LanguagesPane(viewModel: LanguagesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    if (state.showCreate) CreateLanguageSheet(state, viewModel)

    Box(Modifier.fillMaxSize()) {
        val selected = state.selected
        if (selected != null) {
            BackHandler(onBack = viewModel::close)
            LanguageEditor(selected, state, viewModel)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Línguas da interface e os textos de cada uma. O inglês é a referência.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                    state.error != null -> item { AdminError(state.error!!, viewModel::load, Modifier.fillMaxWidth()) }
                    else -> items(state.languages, key = { it.idString }) { l ->
                        val pct = state.progress[l.attributes.locale] ?: 0
                        AdminListRow(
                            title = l.attributes.name,
                            subtitle = l.attributes.locale,
                            onClick = { viewModel.open(l) },
                            trailing = { if (l.attributes.locale == state.defaultLocale) AdminBadge("Por omissão".tr(), SettingsAccent) },
                            footer = {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                    LinearProgressIndicator(
                                        progress = { pct / 100f },
                                        color = if (pct >= 100) SettingsAccent else AdminColors.Warning,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("  $pct%", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreate(true) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nova língua".tr()) },
                containerColor = SettingsAccent,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LanguageEditor(lang: LanguageData, state: LanguagesViewModel.UiState, vm: LanguagesViewModel) {
    var editingKey by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val keys = state.visibleKeys
    val pending = state.pendingKeys.size

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lang.attributes.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("%d textos · %d por traduzir".tr().format(state.translations.size, pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.TextButton(onClick = { showSettings = !showSettings }) { Text("Definições".tr()) }
            }
        }
        if (showSettings) item { LanguageSettings(lang, state, vm) }
        if (state.ai && !state.isReference && pending > 0) item {
            SettingsCard {
                if (state.busy == "auto") {
                    Text("A traduzir %d de %d…".tr().format(state.autoDone, state.autoTotal), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { if (state.autoTotal == 0) 0f else state.autoDone / state.autoTotal.toFloat() },
                        color = SettingsAccent, modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsOutlinedButton("Parar".tr(), onClick = vm::cancelAuto)
                } else {
                    SettingsPrimaryButton("Traduzir %d textos com IA".tr().format(pending), onClick = vm::autoTranslate, enabled = state.busy == null)
                }
            }
        }
        item {
            AdminSearchField(state.query, vm::setQuery, "Pesquisar chave ou texto".tr())
            AdminSegmented(
                options = listOf(
                    StringFilter.All to "Todos".tr(),
                    StringFilter.Pending to "Por traduzir".tr(),
                    StringFilter.MissingVars to "Variáveis em falta".tr(),
                ),
                selected = state.filter,
                onSelect = vm::setFilter,
                counts = mapOf(
                    StringFilter.All to state.translations.size,
                    StringFilter.Pending to pending,
                    StringFilter.MissingVars to state.missingVarKeys.size,
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.busy == "open") item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
        else if (keys.isEmpty()) item { AdminEmpty("Sem resultados.".tr()) }
        // Listas com milhares de chaves: o LazyColumn só compõe o que está visível.
        items(keys, key = { it }) { k ->
            val value = state.translations[k].orEmpty()
            Column(Modifier.fillMaxWidth().clickable { editingKey = k }.padding(vertical = 6.dp)) {
                Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!state.isReference) state.reference[k]?.takeIf { it != value }?.let {
                    Text("EN: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    editingKey?.let { k -> EditStringDialog(k, state.translations[k].orEmpty(), state.reference[k], { editingKey = null }) { v -> vm.saveString(k, v); editingKey = null } }
}

@Composable
private fun LanguageSettings(lang: LanguageData, state: LanguagesViewModel.UiState, vm: LanguagesViewModel) {
    var name by remember(lang) { mutableStateOf(lang.attributes.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isDefault = lang.attributes.locale == state.defaultLocale
    SettingsCard {
        SettingsField("Nome".tr(), name, { name = it })
        SettingsOutlinedButton("Guardar nome".tr(), onClick = { vm.rename(name) }, enabled = name.isNotBlank() && name != lang.attributes.name && state.busy == null)
        if (isDefault) {
            SettingsNotice("Esta é a língua por omissão do site.".tr(), SettingsAccent)
        } else {
            SettingsOutlinedButton("Tornar língua por omissão".tr(), onClick = vm::makeDefault, enabled = state.busy == null)
        }
        if (!state.isReference) {
            SettingsOutlinedButton("Apagar língua".tr(), onClick = { confirmDelete = true }, destructive = true, enabled = state.busy == null)
        }
        state.formError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) {
        SettingsDialog(
            title = "Apagar língua".tr(),
            message = "Os textos desta língua são apagados. Se for a língua por omissão, o site passa a usar o inglês.".tr(),
            confirmText = "Apagar".tr(),
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun EditStringDialog(key: String, value: String, reference: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    val missing = varsOf(reference).filter { it !in text }
    SettingsDialog(
        title = key,
        onDismiss = onDismiss,
        confirmText = "Guardar".tr(),
        confirmEnabled = text.isNotBlank() && text != value,
        onConfirm = { onSave(text) },
    ) {
        reference?.let { Text("EN: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
        if (missing.isNotEmpty()) {
            SettingsNotice("Variáveis em falta: %s".tr().format(missing.joinToString(" ")), AdminColors.Warning)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLanguageSheet(state: LanguagesViewModel.UiState, vm: LanguagesViewModel) {
    var name by remember { mutableStateOf("") }
    var locale by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { vm.showCreate(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nova língua".tr(), style = MaterialTheme.typography.titleMedium)
            SettingsField("Nome".tr(), name, { name = it.take(60) }, placeholder = "Deutsch")
            SettingsField("Código (locale)".tr(), locale, { v -> locale = v.lowercase().filter { it.isLetter() || it == '-' || it == '_' }.take(10) }, placeholder = "de")
            SettingsNotice("Os textos começam em inglês; depois podes traduzi-los à mão ou com IA.".tr(), AdminColors.Info)
            state.formError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
            SettingsPrimaryButton("Criar".tr(), onClick = { vm.create(name, locale) }, enabled = name.isNotBlank() && locale.length >= 2, loading = state.busy == "create")
        }
    }
}
