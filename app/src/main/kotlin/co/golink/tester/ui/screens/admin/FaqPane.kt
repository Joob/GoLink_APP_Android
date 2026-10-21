package co.golink.tester.ui.screens.admin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.FaqContent
import co.golink.tester.domain.admin.FaqEntry
import co.golink.tester.domain.admin.FaqRequest
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsDialog
import co.golink.tester.ui.screens.settings.SettingsNotice
import co.golink.tester.ui.screens.settings.SettingsOutlinedButton
import co.golink.tester.ui.screens.settings.SettingsPrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FaqViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val entries: List<FaqEntry> = emptyList(),
        val categories: List<String> = emptyList(),
        val locales: List<String> = listOf("en", "pt", "fr", "es"),
        val ai: Boolean = false,
        val category: String? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        /** Folha de edição: null = fechada; entrada sem id = nova. */
        val editing: FaqEntry? = null,
        val busy: String? = null,
        val formError: String? = null,
        /** Tradução devolvida pela IA, aplicada no editor aberto. */
        val translated: Map<String, FaqContent>? = null,
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = it.entries.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.faqs()
                .onSuccess { r ->
                    _state.update { it.copy(entries = r.data, categories = r.categories, locales = r.locales.ifEmpty { it.locales }, ai = r.ai, isLoading = false) }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun setCategory(c: String?) = _state.update { it.copy(category = c) }

    fun edit(entry: FaqEntry?) = _state.update { it.copy(editing = entry, formError = null, translated = null) }

    fun save(request: FaqRequest) {
        val id = _state.value.editing?.idString?.ifBlank { null }
        _state.update { it.copy(busy = "save", formError = null) }
        viewModelScope.launch {
            repository.saveFaq(id, request)
                .onSuccess { _state.update { it.copy(busy = null, editing = null, toast = "Pergunta guardada".tr()) }; load() }
                .onFailure { t -> _state.update { it.copy(busy = null, formError = t.message) } }
        }
    }

    fun delete(entry: FaqEntry) = viewModelScope.launch {
        repository.deleteFaq(entry.idString)
            .onSuccess { _state.update { it.copy(editing = null, toast = "Pergunta apagada".tr()) }; load() }
            .onFailure { t -> _state.update { it.copy(formError = t.message) } }
    }

    /** Preenche as outras línguas a partir de [source] (rever antes de gravar). */
    fun translate(source: String) {
        val id = _state.value.editing?.idString?.ifBlank { null } ?: return
        _state.update { it.copy(busy = "translate", formError = null) }
        viewModelScope.launch {
            repository.translateFaq(id, source)
                .onSuccess { c -> _state.update { it.copy(busy = null, translated = c) } }
                .onFailure { t -> _state.update { it.copy(busy = null, formError = t.message) } }
        }
    }

    fun generate() {
        _state.update { it.copy(busy = "generate") }
        viewModelScope.launch {
            repository.generateFaqs(I18n.lang)
                .onSuccess { _state.update { it.copy(busy = null, toast = "A gerar sugestões em segundo plano; aparecem aqui dentro de alguns minutos.".tr()) } }
                .onFailure { t -> _state.update { it.copy(busy = null, toast = t.message) } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

internal fun faqCategoryLabel(c: String) = when (c) {
    "account" -> "Conta".tr()
    "billing" -> "Faturação".tr()
    "encryption" -> "Encriptação".tr()
    "files" -> "Ficheiros".tr()
    "sharing" -> "Partilha".tr()
    "apps" -> "Apps".tr()
    "other" -> "Outro".tr()
    else -> c
}

/** Pergunta na língua da app, ou a primeira preenchida. */
private fun FaqEntry.questionFor(locale: String): String {
    val c = contentMap
    return c[locale]?.question?.ifBlank { null }
        ?: c.values.firstNotNullOfOrNull { it.question?.ifBlank { null } }
        ?: "—"
}

@Composable
fun FaqPane(viewModel: FaqViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    state.editing?.let { FaqEditorSheet(it, state, viewModel) }

    val visible = state.entries.filter { state.category == null || it.category == state.category }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Perguntas do centro de ajuda, em todas as línguas.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.ai) {
                    TextButton(onClick = viewModel::generate, enabled = state.busy == null) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Text("  " + "Gerar sugestões com IA".tr())
                    }
                }
                if (state.categories.isNotEmpty()) {
                    AdminSegmented(
                        options = listOf<Pair<String?, String>>(null to "Todas".tr()) + state.categories.map { it to faqCategoryLabel(it) },
                        selected = state.category,
                        onSelect = viewModel::setCategory,
                        counts = (listOf<String?>(null) + state.categories).associateWith { c -> state.entries.count { c == null || it.category == c } },
                    )
                }
            }
            when {
                state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                state.error != null -> item { AdminError(state.error!!, viewModel::load, Modifier.fillMaxWidth()) }
                visible.isEmpty() -> item { AdminEmpty("Sem perguntas.".tr()) }
                else -> items(visible, key = { it.idString }) { e ->
                    AdminListRow(
                        title = e.questionFor(I18n.lang),
                        subtitle = faqCategoryLabel(e.category),
                        onClick = { viewModel.edit(e) },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (e.is_published) AdminBadge("Publicada".tr(), SettingsAccent) else AdminBadge("Rascunho".tr(), AdminColors.Neutral)
                                if (e.source == "ai") AdminBadge("IA", AdminColors.Info)
                            }
                        },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { viewModel.edit(FaqEntry(category = state.categories.firstOrNull() ?: "other")) },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Nova pergunta".tr()) },
            containerColor = SettingsAccent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaqEditorSheet(entry: FaqEntry, state: FaqViewModel.UiState, vm: FaqViewModel) {
    val isNew = entry.idString.isBlank()
    var category by remember { mutableStateOf(entry.category) }
    var published by remember { mutableStateOf(entry.is_published) }
    var locale by remember { mutableStateOf(if (I18n.lang in state.locales) I18n.lang else state.locales.first()) }
    val content = remember { mutableStateMapOf<String, FaqContent>().apply { putAll(entry.contentMap) } }
    var confirmDelete by remember { mutableStateOf(false) }

    // Tradução da IA: preenche as línguas sem tocar na de origem.
    LaunchedEffect(state.translated) {
        state.translated?.forEach { (l, c) -> if (l != locale) content[l] = c }
    }

    ModalBottomSheet(onDismissRequest = { vm.edit(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isNew) "Nova pergunta".tr() else "Editar pergunta".tr(), style = MaterialTheme.typography.titleMedium)
            Text("Categoria".tr(), style = MaterialTheme.typography.labelMedium)
            AdminSegmented(state.categories.map { it to faqCategoryLabel(it) }, category, { category = it })
            SwitchRow("Publicada".tr(), published) { published = it }
            AdminSegmented(
                state.locales.map { l -> l to (l.uppercase() + if (content[l]?.question.isNullOrBlank()) "" else " ✓") },
                locale, { locale = it },
            )
            val current = content[locale] ?: FaqContent()
            OutlinedTextField(
                value = current.question.orEmpty(),
                onValueChange = { content[locale] = current.copy(question = it.take(300)) },
                label = { Text("Pergunta".tr()) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = current.answer.orEmpty(),
                onValueChange = { content[locale] = current.copy(answer = it.take(5000)) },
                label = { Text("Resposta".tr()) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isNew && state.ai) {
                SettingsOutlinedButton(
                    if (state.busy == "translate") "A traduzir…".tr() else "Traduzir as outras línguas a partir de %s".tr().format(locale.uppercase()),
                    onClick = { vm.translate(locale) },
                    enabled = state.busy == null && !current.question.isNullOrBlank(),
                )
                if (state.translated != null) SettingsNotice("Revê as traduções antes de guardar.".tr(), AdminColors.Info)
            }
            state.formError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
            SettingsPrimaryButton(
                "Guardar".tr(),
                onClick = { vm.save(FaqRequest(category, content.toMap(), published)) },
                enabled = content.values.any { !it.question.isNullOrBlank() },
                loading = state.busy == "save",
            )
            if (!isNew) SettingsOutlinedButton("Apagar".tr(), onClick = { confirmDelete = true }, destructive = true)
        }
    }
    if (confirmDelete) {
        SettingsDialog(
            title = "Apagar pergunta".tr(),
            message = entry.questionFor(locale),
            confirmText = "Apagar".tr(),
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(entry) },
            onDismiss = { confirmDelete = false },
        )
    }
}
