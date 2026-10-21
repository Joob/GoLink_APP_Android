package co.golink.tester.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.domain.admin.CreatePageRequest
import co.golink.tester.domain.admin.PageAttributes
import co.golink.tester.domain.admin.PageTranslation
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsCard
import co.golink.tester.ui.screens.settings.SettingsField
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
class PagesViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {
    data class UiState(
        val pages: List<PageAttributes> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val showCreate: Boolean = false,
        /** Página aberta no editor (carregada com conteúdo e irmãs). */
        val editing: PageAttributes? = null,
        val busy: String? = null,
        val formError: String? = null,
        val translation: PageTranslation? = null,
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = it.pages.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.pages()
                .onSuccess { l -> _state.update { it.copy(pages = l, isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun showCreate(show: Boolean) = _state.update { it.copy(showCreate = show, formError = null) }

    fun create(r: CreatePageRequest) {
        _state.update { it.copy(busy = "create", formError = null) }
        viewModelScope.launch {
            repository.createPage(r)
                .onSuccess { p -> _state.update { it.copy(busy = null, showCreate = false, toast = "Página criada (inativa)".tr()) }; load(); open(p.slug) }
                .onFailure { t -> _state.update { it.copy(busy = null, formError = t.message) } }
        }
    }

    fun open(slug: String) {
        _state.update { it.copy(busy = "open", translation = null, formError = null) }
        viewModelScope.launch {
            repository.page(slug)
                .onSuccess { p -> _state.update { it.copy(busy = null, editing = p) } }
                .onFailure { t -> _state.update { it.copy(busy = null, toast = t.message) } }
        }
    }

    fun close() { _state.update { it.copy(editing = null, translation = null) }; load() }

    /** Grava só os campos alterados (um PATCH por campo, como o $updateText da web). */
    fun save(title: String, slug: String, content: String, visible: Boolean) {
        val p = _state.value.editing ?: return
        _state.update { it.copy(busy = "save", formError = null) }
        viewModelScope.launch {
            val result = runCatching {
                if (title != p.title) repository.updatePage(p.slug, "title", title).getOrThrow()
                if (content != p.content.orEmpty()) repository.updatePage(p.slug, "content", content).getOrThrow()
                if (visible != p.isVisible) repository.updatePage(p.slug, "visibility", if (visible) "1" else "0").getOrThrow()
                // O slug é a chave primária: muda por último.
                if (slug != p.slug) repository.updatePage(p.slug, "slug", slug).getOrThrow()
            }
            result
                .onSuccess { _state.update { it.copy(busy = null, toast = "Página guardada".tr()) }; open(slug) }
                .onFailure { t -> _state.update { it.copy(busy = null, formError = t.message) } }
        }
    }

    fun translate(sourceSlug: String) {
        val p = _state.value.editing ?: return
        _state.update { it.copy(busy = "translate", formError = null, translation = null) }
        viewModelScope.launch {
            repository.translatePage(p.slug, sourceSlug)
                .onSuccess { t -> _state.update { it.copy(busy = null, translation = t) } }
                .onFailure { t -> _state.update { it.copy(busy = null, formError = t.message) } }
        }
    }

    fun discardTranslation() = _state.update { it.copy(translation = null) }
    fun consumeToast() = _state.update { it.copy(toast = null) }
}

@Composable
fun PagesPane(viewModel: PagesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }

    if (state.showCreate) CreatePageSheet(state, viewModel)

    Box(Modifier.fillMaxSize()) {
        val editing = state.editing
        if (editing != null) {
            BackHandler(onBack = viewModel::close)
            PageEditor(editing, state, viewModel)
        } else {
            // Agrupadas por slug_group (a mesma página nas várias línguas), como na web.
            val groups = state.pages.groupBy { it.slug_group ?: it.slug }.toSortedMap()
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Páginas do site (termos, privacidade, …), uma por língua.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    state.isLoading -> item { AdminLoading(Modifier.fillMaxWidth().height(160.dp)) }
                    state.error != null -> item { AdminError(state.error!!, viewModel::load, Modifier.fillMaxWidth()) }
                    groups.isEmpty() -> item { AdminEmpty("Sem páginas.".tr()) }
                    else -> groups.forEach { (group, pages) ->
                        item(key = "g-$group") {
                            Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(pages.sortedBy { it.lang }, key = { it.slug }) { p ->
                            AdminListRow(
                                title = p.title ?: p.slug,
                                subtitle = "/page/${p.slug}",
                                onClick = { viewModel.open(p.slug) },
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        p.lang?.let { AdminBadge(it.uppercase(), AdminColors.Info) }
                                        if (p.isVisible) AdminBadge("Ativa".tr(), SettingsAccent) else AdminBadge("Inativa".tr(), AdminColors.Neutral)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreate(true) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nova página".tr()) },
                containerColor = SettingsAccent,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        if (state.busy == "open") AdminLoading()
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PageEditor(page: PageAttributes, state: PagesViewModel.UiState, vm: PagesViewModel) {
    var title by remember(page) { mutableStateOf(page.title.orEmpty()) }
    var slug by remember(page) { mutableStateOf(page.slug) }
    var content by remember(page) { mutableStateOf(page.content.orEmpty()) }
    var visible by remember(page) { mutableStateOf(page.isVisible) }
    val siblings = page.translations.filter { it.lang != null && it.lang != page.lang }
    var source by remember(page) { mutableStateOf(siblings.firstOrNull { it.lang == "en" }?.slug ?: siblings.firstOrNull()?.slug) }
    val dirty = title != page.title.orEmpty() || slug != page.slug || content != page.content.orEmpty() || visible != page.isVisible

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(page.title ?: page.slug, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            page.lang?.let { AdminBadge(it.uppercase(), AdminColors.Info) }
        }
        SettingsCard {
            SettingsField("Título".tr(), title, { title = it.take(255) })
            SettingsField("Slug", slug, { v -> slug = v.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(255) })
            SwitchRow("Visível no site".tr(), visible) { visible = it }
        }
        if (siblings.isNotEmpty()) {
            SettingsCard(title = "Traduzir com IA".tr(), description = "Traduz a partir da mesma página noutra língua; revês antes de aplicar.".tr()) {
                AdminSegmented(siblings.map { it.slug to (it.lang ?: "?").uppercase() }, source, { source = it })
                SettingsOutlinedButton(
                    if (state.busy == "translate") "A traduzir… (pode demorar 1–2 min)".tr() else "Traduzir".tr(),
                    onClick = { source?.let(vm::translate) },
                    enabled = state.busy == null && source != null,
                )
                state.translation?.let { t ->
                    Text(t.title, style = MaterialTheme.typography.titleSmall)
                    Text(t.content.take(600) + if (t.content.length > 600) "…" else "", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsPrimaryButton("Usar tradução".tr(), onClick = { title = t.title; content = t.content; vm.discardTranslation() }, modifier = Modifier.weight(1f))
                        SettingsOutlinedButton("Descartar".tr(), onClick = vm::discardTranslation, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Text("Conteúdo (HTML)".tr(), style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )
        state.formError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
        SettingsPrimaryButton(
            "Guardar".tr(),
            onClick = { vm.save(title, slug, content, visible) },
            enabled = dirty && title.isNotBlank() && slug.isNotBlank(),
            loading = state.busy == "save",
        )
        SettingsOutlinedButton("Fechar".tr(), onClick = vm::close)
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePageSheet(state: PagesViewModel.UiState, vm: PagesViewModel) {
    var title by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("en") }
    var group by remember { mutableStateOf("") }
    val groups = state.pages.mapNotNull { it.slug_group }.distinct().sorted()
    ModalBottomSheet(onDismissRequest = { vm.showCreate(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nova página".tr(), style = MaterialTheme.typography.titleMedium)
            SettingsField("Título".tr(), title, { title = it.take(255) })
            SettingsField("Slug", slug, { v -> slug = v.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(255) })
            SettingsField("Língua (código)".tr(), lang, { v -> lang = v.lowercase().filter { it.isLetter() || it == '-' || it == '_' }.take(10) })
            SettingsField("Grupo (mesma página noutras línguas)".tr(), group, { v -> group = v.filter { it.isLetterOrDigit() || it == '-' || it == '_' } })
            if (groups.isNotEmpty()) AdminSegmented(groups.map { it to it }, group, { group = it })
            SettingsNotice("A página nasce inativa: só aparece no site depois de a ativares.".tr(), AdminColors.Info)
            state.formError?.let { SettingsNotice(it, MaterialTheme.colorScheme.error) }
            SettingsPrimaryButton(
                "Criar".tr(),
                onClick = { vm.create(CreatePageRequest(title.trim(), slug, lang, group.ifBlank { null })) },
                enabled = title.isNotBlank() && slug.isNotBlank() && lang.isNotBlank(),
                loading = state.busy == "create",
            )
        }
    }
}
