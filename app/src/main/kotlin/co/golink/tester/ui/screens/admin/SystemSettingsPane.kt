package co.golink.tester.ui.screens.admin

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.admin.AdminRepository
import co.golink.tester.data.config.BackendUrlHolder
import co.golink.tester.data.config.ConfigRepository
import co.golink.tester.domain.admin.AiOverview
import co.golink.tester.domain.admin.SocialServiceRequest
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsCard
import co.golink.tester.ui.screens.settings.SettingsDialog
import co.golink.tester.ui.screens.settings.SettingsField
import co.golink.tester.ui.screens.settings.SettingsNotice
import co.golink.tester.ui.screens.settings.SettingsOutlinedButton
import co.golink.tester.ui.screens.settings.SettingsPrimaryButton
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

// ------------------------------------------------------------ Esquema

private enum class FieldKind { Text, Multiline, Number, Switch, Color }

private class SField(val key: String, val label: String, val kind: FieldKind = FieldKind.Text, val help: String? = null)

private class SSection(val title: String, val fields: List<SField>, val description: String? = null)

/** Separadores com rascunho (barra Guardar/Descartar), como no web. */
private val DRAFT_TABS = setOf("app", "signin", "appearance", "mobile", "homepage")

private val IMAGE_KEYS = listOf(
    "app_logo" to "Logótipo",
    "app_logo_dark" to "Logótipo (modo escuro)",
    "app_logo_horizontal" to "Logótipo horizontal",
    "app_logo_horizontal_dark" to "Logótipo horizontal (modo escuro)",
    "app_favicon" to "Favicon",
    "app_og_image" to "Imagem de partilha (OG)",
    "app_touch_icon" to "Ícone de ecrã inicial",
)

private val SOCIAL = listOf("google" to "Google", "github" to "GitHub", "microsoft" to "Microsoft")

private fun schema(tab: String): List<SSection> = when (tab) {
    "app" -> listOf(
        SSection("Envio de ficheiros".tr(), listOf(
            SField("upload_limit", "Limite de upload (MB)".tr(), FieldKind.Number, "Vazio = sem limite.".tr()),
            SField("chunk_size", "Tamanho das partes (MB)".tr(), FieldKind.Number),
            SField("mimetypes_blacklist", "Tipos de ficheiro bloqueados".tr(), help = "Extensões separadas por vírgulas, ex.: exe,bat".tr()),
        )),
        SSection("Funcionalidades dos utilizadores".tr(), listOf(
            SField("storage_limitation", "Limitar armazenamento".tr(), FieldKind.Switch),
            SField("default_max_storage_amount", "Armazenamento por omissão (GB)".tr(), FieldKind.Number),
            SField("default_max_team_member", "Máximo de membros por equipa".tr(), FieldKind.Number, "0 = ilimitado".tr()),
            SField("account_deletion_grace_days", "Dias de graça antes de eliminar a conta".tr(), FieldKind.Number),
        )),
        SSection("Aplicação".tr(), listOf(SField("contact_email", "Email de contacto".tr()))),
    )
    "signin" -> listOf(
        SSection("Registo".tr(), listOf(
            SField("registration", "Permitir registo".tr(), FieldKind.Switch),
            SField("user_verification", "Exigir verificação do email".tr(), FieldKind.Switch),
        )),
        SSection("Login social".tr(), SOCIAL.map { (k, n) -> SField("allowed_${k}_login", "Login com %s".tr().format(n), FieldKind.Switch) }),
    )
    "appearance" -> listOf(
        SSection("Geral".tr(), listOf(
            SField("app_color", "Cor do tema".tr(), FieldKind.Color, "Hexadecimal, ex.: #00BC7E".tr()),
            SField("app_title", "Título da aplicação".tr()),
            SField("app_description", "Descrição".tr(), FieldKind.Multiline),
        )),
    )
    "mobile" -> listOf(
        SSection("App móvel".tr(), listOf(
            SField("app_version", "Versão da app".tr()),
            SField("android_download_link", "Link de download Android".tr()),
            SField("apple_download_link", "Link de download iOS".tr()),
        )),
    )
    "homepage" -> listOf(
        SSection("Página inicial".tr(), listOf(
            SField("allow_homepage", "Mostrar página inicial".tr(), FieldKind.Switch),
            SField("header_title", "Título do cabeçalho".tr()),
            SField("header_description", "Descrição do cabeçalho".tr(), FieldKind.Multiline),
        )),
        SSection("Funcionalidades".tr(), listOf(
            SField("section_features", "Mostrar secção".tr(), FieldKind.Switch),
            SField("features_title", "Título".tr()),
            SField("features_description", "Descrição".tr(), FieldKind.Multiline),
        )),
        SSection("Caixas de funcionalidades".tr(), listOf(SField("section_feature_boxes", "Mostrar secção".tr(), FieldKind.Switch)) +
            (1..3).flatMap { i ->
                listOf(
                    SField("feature_title_$i", "Título %d".tr().format(i)),
                    SField("feature_description_$i", "Descrição %d".tr().format(i), FieldKind.Multiline),
                )
            }),
        SSection("Preços".tr(), listOf(
            SField("section_pricing_content", "Mostrar secção".tr(), FieldKind.Switch),
            SField("pricing_title", "Título".tr()),
            SField("pricing_description", "Descrição".tr(), FieldKind.Multiline),
        )),
        SSection("Começar".tr(), listOf(
            SField("section_get_started", "Mostrar secção".tr(), FieldKind.Switch),
            SField("get_started_title", "Título".tr()),
            SField("get_started_description", "Descrição".tr(), FieldKind.Multiline),
        )),
        SSection("Rodapé".tr(), listOf(SField("footer_content", "Conteúdo do rodapé".tr(), FieldKind.Multiline))),
    )
    else -> emptyList()
}

private val ALL_KEYS: List<String> by lazy {
    DRAFT_TABS.flatMap { t -> schema(t).flatMap { s -> s.fields.map { it.key } } } + IMAGE_KEYS.map { it.first }
}

private fun String?.isOn() = this == "1" || this == "true"

// ---------------------------------------------------------- ViewModel

@HiltViewModel
class SystemSettingsViewModel @Inject constructor(
    private val repository: AdminRepository,
    private val configRepository: ConfigRepository,
    private val backendUrl: BackendUrlHolder,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    data class UiState(
        val values: Map<String, String?> = emptyMap(),
        val draft: Map<String, String> = emptyMap(),
        val subscriptionType: String = "none",
        val socialConfigured: Set<String> = emptySet(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val saving: Boolean = false,
        /** Ação em curso fora do rascunho (chave). */
        val busy: String? = null,
        val ai: AiOverview? = null,
        val env: JsonObject? = null,
        val server: JsonObject? = null,
        val toast: String? = null,
    ) {
        fun value(key: String): String = draft[key] ?: values[key].orEmpty()
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    val pending: Int get() = _state.value.draft.size

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = it.values.isEmpty(), error = null) }
        viewModelScope.launch {
            val config = configRepository.load(force = true).getOrNull()
            repository.settings(*ALL_KEYS.toTypedArray())
                .onSuccess { map ->
                    val social = config?.social_logins
                    _state.update {
                        it.copy(
                            values = map,
                            subscriptionType = config?.payments?.type ?: it.subscriptionType,
                            socialConfigured = buildSet {
                                if (social?.is_google_configured == 1) add("google")
                                if (social?.is_github_configured == 1) add("github")
                                if (social?.is_microsoft_configured == 1) add("microsoft")
                            },
                            isLoading = false,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (${t.message})") } }
        }
    }

    fun imageUrl(path: String?): String? =
        path?.takeIf { it.isNotBlank() && it != "null" }?.let { backendUrl.current.trimEnd('/') + "/" + it.trimStart('/') }

    fun set(key: String, value: String) = _state.update { s ->
        s.copy(draft = if (value == s.values[key].orEmpty()) s.draft - key else s.draft + (key to value))
    }

    fun discard() = _state.update { it.copy(draft = emptyMap()) }

    fun save() {
        val draft = _state.value.draft
        if (draft.isEmpty()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = mutableMapOf<String, String>()
            draft.forEach { (k, v) -> repository.updateSetting(k, v).onSuccess { saved[k] = v } }
            val failed = draft.size - saved.size
            _state.update { s ->
                s.copy(
                    saving = false,
                    values = s.values + saved,
                    draft = s.draft - saved.keys,
                    toast = if (failed == 0) "Alterações guardadas".tr() else "%d alterações não foram guardadas".tr().format(failed),
                )
            }
            configRepository.load(force = true)
        }
    }

    private fun act(key: String, success: String?, block: suspend () -> Result<*>, after: () -> Unit = {}) {
        _state.update { it.copy(busy = key) }
        viewModelScope.launch {
            block()
                .onSuccess { r -> _state.update { it.copy(busy = null, toast = success ?: (r as? String)) }; after() }
                .onFailure { t -> _state.update { it.copy(busy = null, toast = t.message) } }
        }
    }

    /** Muda logo (fora do rascunho), como o web depois da confirmação. */
    fun changeSubscriptionType(type: String) = act("subscription_type", "Tipo de subscrição alterado".tr(), {
        repository.updateSetting("subscription_type", type).onSuccess { configRepository.load(force = true) }
    }) { _state.update { it.copy(subscriptionType = type) } }

    fun flushCache() = act("cache", "Cache limpa".tr(), { repository.flushCache() })

    fun storeSocial(service: String, id: String, secret: String) =
        act("social_$service", "Credenciais guardadas".tr(), { repository.storeSocialService(SocialServiceRequest(service, id, secret)) }) { load() }

    fun uploadImage(key: String, uri: Uri) = act(key, "Imagem atualizada".tr(), {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "image/png"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Não foi possível ler a imagem".tr())
            val path = repository.uploadSettingImage(key, bytes, mime, "$key.${mime.substringAfter('/').substringBefore('+')}").getOrThrow()
            _state.update { it.copy(values = it.values + (key to path)) }
        }
    })

    fun removeImage(key: String) = act(key, "Imagem removida".tr(), {
        repository.clearSetting(key).onSuccess { _state.update { it.copy(values = it.values + (key to null)) } }
    })

    fun loadAi() = viewModelScope.launch {
        repository.aiOverview()
            .onSuccess { o -> _state.update { it.copy(ai = o) } }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    /** IA: grava logo cada alteração (como o web, sem rascunho). */
    fun saveAi(name: String, value: String) = viewModelScope.launch {
        repository.updateSetting(name, value)
            .onSuccess { loadAi() }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun loadEnv() = viewModelScope.launch {
        repository.envCredentials()
            .onSuccess { e -> _state.update { it.copy(env = e) } }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun loadServer() = viewModelScope.launch {
        repository.serverStatus()
            .onSuccess { s -> _state.update { it.copy(server = s) } }
            .onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun forceLogoutAll() = act("logout_all", null, { repository.forceLogoutAll().map { it ?: "Sessões terminadas".tr() } })

    fun consumeToast() = _state.update { it.copy(toast = null) }
}

// ---------------------------------------------------------------- UI

@Composable
fun SystemSettingsPane(viewModel: SystemSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf("app") }
    var pendingTab by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }
    LaunchedEffect(tab) {
        when (tab) {
            "ai" -> viewModel.loadAi()
            "environment" -> viewModel.loadEnv()
            "server" -> viewModel.loadServer()
        }
    }

    val tabs = listOf(
        "app" to "Aplicação".tr(),
        "signin" to "Login e registo".tr(),
        "appearance" to "Aparência".tr(),
        "mobile" to "App móvel".tr(),
        "ai" to "IA".tr(),
        "homepage" to "Página inicial".tr(),
        "environment" to "Ambiente".tr(),
        "server" to "Servidor".tr(),
    )
    // Sair de um separador com rascunho para um sem rascunho larga as alterações (como no web).
    fun select(t: String) {
        if (state.draft.isNotEmpty() && t !in DRAFT_TABS) pendingTab = t else tab = t
    }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs, key = { it.first }) { (k, l) -> FilterChip(selected = tab == k, onClick = { select(k) }, label = { Text(l) }) }
        }
        Box(Modifier.weight(1f)) {
            when {
                state.isLoading -> AdminLoading()
                state.error != null -> AdminError(state.error!!, viewModel::load)
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (tab) {
                        "app" -> {
                            SubscriptionTypeCard(state, viewModel)
                            DraftSections(schema(tab), state, viewModel)
                            SettingsCard(title = "Cache".tr(), description = "Limpa a cache de configuração, rotas e vistas do servidor.".tr()) {
                                SettingsOutlinedButton("Limpar cache".tr(), onClick = viewModel::flushCache, enabled = state.busy == null)
                            }
                        }
                        "signin" -> {
                            DraftSections(schema(tab), state, viewModel)
                            SOCIAL.forEach { (k, n) -> SocialCredentialsCard(k, n, state, viewModel) }
                        }
                        "appearance" -> {
                            DraftSections(schema(tab), state, viewModel)
                            ImagesCard(state, viewModel)
                        }
                        "ai" -> AiTab(state.ai, viewModel)
                        "environment" -> EnvTab(state.env)
                        "server" -> ServerTab(state, viewModel)
                        else -> DraftSections(schema(tab), state, viewModel)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        if (tab in DRAFT_TABS && !state.isLoading) SaveBar(state, viewModel)
    }

    pendingTab?.let { t ->
        SettingsDialog(
            title = "Descartar alterações?".tr(),
            message = "Tens %d alterações por guardar.".tr().format(state.draft.size),
            confirmText = "Descartar".tr(),
            destructive = true,
            onConfirm = { viewModel.discard(); tab = t; pendingTab = null },
            onDismiss = { pendingTab = null },
        )
    }
}

@Composable
private fun SaveBar(state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.draft.isEmpty()) "Sem alterações".tr() else "%d alterações por guardar".tr().format(state.draft.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::discard, enabled = state.draft.isNotEmpty() && !state.saving) { Text("Descartar".tr()) }
            Spacer(Modifier.width(4.dp))
            SettingsPrimaryButton("Guardar".tr(), onClick = vm::save, enabled = state.draft.isNotEmpty(), loading = state.saving)
        }
    }
}

@Composable
private fun DraftSections(sections: List<SSection>, state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    sections.forEach { section ->
        SettingsCard(title = section.title, description = section.description) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                section.fields.forEach { f -> FieldEditor(f, state, vm) }
            }
        }
    }
}

@Composable
private fun FieldEditor(f: SField, state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    val value = state.value(f.key)
    val changed = f.key in state.draft
    val label = if (changed) "${f.label} •" else f.label
    when (f.kind) {
        FieldKind.Switch -> SwitchRow(label, value.isOn()) { vm.set(f.key, if (it) "1" else "0") }
        FieldKind.Number -> SettingsField(label, value, { vm.set(f.key, it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number, supportingText = f.help)
        FieldKind.Color -> Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsField(label, value, { vm.set(f.key, it.trim()) }, modifier = Modifier.weight(1f), supportingText = f.help, isError = value.isNotBlank() && parseHex(value) == null)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(parseHex(value) ?: Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
            )
        }
        FieldKind.Multiline, FieldKind.Text -> SettingsField(label, value, { vm.set(f.key, it) }, supportingText = f.help)
    }
}

private fun parseHex(s: String): Color? = runCatching {
    val h = s.trim().removePrefix("#")
    require(h.length == 6 || h.length == 3)
    val full = if (h.length == 3) h.map { "$it$it" }.joinToString("") else h
    Color(0xFF000000 or full.toLong(16))
}.getOrNull()

@Composable
private fun SubscriptionTypeCard(state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    var locked by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf<String?>(null) }
    SettingsCard(title = "Tipo de subscrição".tr()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsNotice("Mudar o tipo de subscrição com utilizadores ativos pode deixar subscrições e planos inconsistentes.".tr(), AdminColors.Warning)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("none" to "Nenhuma".tr(), "fixed" to "Fixa".tr(), "metered" to "Por consumo".tr()).forEach { (k, l) ->
                    FilterChip(
                        selected = state.subscriptionType == k,
                        enabled = !locked && state.busy == null,
                        onClick = { if (k != state.subscriptionType) confirm = k },
                        label = { Text(l) },
                    )
                }
            }
            TextButton(onClick = { locked = !locked }) { Text(if (locked) "Desbloquear".tr() else "Bloquear".tr()) }
        }
    }
    confirm?.let { t ->
        SettingsDialog(
            title = "Mudar tipo de subscrição?".tr(),
            message = "A alteração é gravada de imediato e muda a faturação de toda a aplicação.".tr(),
            confirmText = "Mudar".tr(),
            destructive = true,
            onConfirm = { confirm = null; locked = true; vm.changeSubscriptionType(t) },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun SocialCredentialsCard(service: String, name: String, state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    val configured = service in state.socialConfigured
    var open by remember { mutableStateOf(false) }
    var id by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    SettingsCard(
        title = name,
        trailing = { if (configured) AdminBadge("Configurado".tr(), SettingsAccent) else AdminBadge("Por configurar".tr(), AdminColors.Warning) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminInfoRow("Callback".tr(), "${vm.imageUrl("socialite/$service/callback")}")
            if (!open) {
                SettingsOutlinedButton(if (configured) "Atualizar credenciais".tr() else "Configurar".tr(), onClick = { open = true })
            } else {
                SettingsField("Client ID", id, { id = it.trim() })
                SettingsField("Client secret", secret, { secret = it.trim() }, isPassword = true)
                SettingsPrimaryButton(
                    "Guardar credenciais".tr(),
                    onClick = { vm.storeSocial(service, id, secret); id = ""; secret = ""; open = false },
                    enabled = id.isNotBlank() && secret.isNotBlank(),
                    loading = state.busy == "social_$service",
                )
            }
        }
    }
}

@Composable
private fun ImagesCard(state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    var target by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = target
        if (uri != null && key != null) vm.uploadImage(key, uri)
        target = null
    }
    SettingsCard(title = "Marca".tr(), description = "As imagens são gravadas logo, fora do rascunho.".tr()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IMAGE_KEYS.forEach { (key, label) ->
                val url = vm.imageUrl(state.values[key])
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (url != null) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(label.tr(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { target = key; picker.launch("image/*") }, enabled = state.busy == null) {
                        Text(if (state.busy == key) "…" else "Carregar".tr())
                    }
                    if (url != null) {
                        TextButton(onClick = { vm.removeImage(key) }, enabled = state.busy == null) {
                            Text("Remover".tr(), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiTab(ai: AiOverview?, vm: SystemSettingsViewModel) {
    if (ai == null) return AdminLoading(Modifier.fillMaxWidth().padding(32.dp))
    if (!ai.has_key || !ai.has_sdk) {
        SettingsNotice("A IA não está configurada no servidor: falta a ANTHROPIC_API_KEY ou o SDK.".tr(), AdminColors.Warning)
    }
    SettingsCard(title = "Funcionalidades".tr(), description = "Os pedidos à Anthropic só incluem o texto necessário para cada funcionalidade.".tr()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ai.features.forEach { f ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (f.toggle != null) {
                        SwitchRow(aiFeatureLabel(f.feature), f.enabled) { vm.saveAi(f.toggle, if (it) "1" else "0") }
                    } else {
                        Text(aiFeatureLabel(f.feature), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (f.model_setting != null) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ai.models) { m ->
                                FilterChip(selected = f.model == m, onClick = { vm.saveAi(f.model_setting, m) }, label = { Text(modelLabel(m)) })
                            }
                        }
                    }
                }
            }
        }
    }
    SettingsCard(title = "Utilização".tr()) {
        if (ai.usage.isEmpty()) {
            Text("Ainda não há pedidos registados.".tr(), style = MaterialTheme.typography.bodySmall)
        } else {
            val cost = ai.usage.sumOf { it.cost_usd }
            AdminInfoRow("Custo".tr(), "$%.2f".format(cost))
            AdminInfoRow("Pedidos".tr(), ai.usage.sumOf { it.requests }.toString())
            AdminInfoRow("Tokens".tr(), ai.usage.sumOf { it.input_tokens + it.output_tokens }.toString())
            ai.usage.sortedByDescending { it.cost_usd }.forEach { r ->
                AdminInfoRow(
                    "${aiFeatureLabel(r.feature)} · ${modelLabel(r.model)}",
                    "%d · %s".format(r.requests, if (r.cost_usd in 0.0001..0.01) "$%.4f".format(r.cost_usd) else "$%.2f".format(r.cost_usd)),
                )
            }
        }
    }
}

private fun modelLabel(m: String) = when (m) {
    "claude-opus-5-5" -> "Opus 5.5"
    "claude-sonnet-5-5" -> "Sonnet 5.5"
    "claude-haiku-5-5" -> "Haiku 5.5"
    "claude-haiku-4-5" -> "Haiku 4.5"
    else -> m
}

private fun aiFeatureLabel(f: String) = f.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun EnvTab(env: JsonObject?) {
    if (env == null) return AdminLoading(Modifier.fillMaxWidth().padding(32.dp))
    SettingsNotice("Só leitura: os segredos aparecem mascarados. Para alterar credenciais de ambiente usa o site.".tr(), AdminColors.Info)
    val titles = mapOf("broadcast" to "Websockets".tr(), "storage" to "Armazenamento".tr(), "mail" to "Email".tr())
    env.forEach { (section, node) ->
        val obj = node as? JsonObject ?: return@forEach
        val driver = (obj["driver"] as? JsonPrimitive)?.contentOrNull
        SettingsCard(title = titles[section] ?: section) {
            AdminInfoRow("Driver", driver)
            // Só os campos do driver ativo (os outros vêm vazios).
            flatten(obj.filterKeys { it != "driver" && (obj[it] !is JsonObject || it == driver || (section == "storage" && it == "s3" && driver !in listOf("local", "ftp"))) })
                .forEach { (k, v) -> AdminInfoRow(k, v) }
        }
    }
}

private fun flatten(map: Map<String, JsonElement>, prefix: String = ""): List<Pair<String, String>> = map.flatMap { (k, v) ->
    when (v) {
        is JsonObject -> flatten(v, "$prefix$k.")
        is JsonNull -> emptyList()
        is JsonPrimitive -> listOf("$prefix$k" to v.content)
        is JsonArray -> listOf("$prefix$k" to v.joinToString())
    }
}

@Composable
private fun ServerTab(state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    val s = state.server ?: return AdminLoading(Modifier.fillMaxWidth().padding(32.dp))
    var confirm by remember { mutableStateOf(false) }
    val cron = s["cron"] as? JsonObject
    val cronOk = (cron?.get("running") as? JsonPrimitive)?.booleanOrNull == true
    SettingsCard(title = "Estado".tr()) {
        AdminInfoRow("PHP", (s["php_version"] as? JsonPrimitive)?.contentOrNull)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Cron", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (cronOk) AdminBadge("A correr".tr(), SettingsAccent) else AdminBadge("Parado".tr(), AdminColors.Danger)
        }
        (cron?.get("lastUpdate") as? JsonPrimitive)?.contentOrNull?.let { AdminInfoRow("Última execução".tr(), it) }
    }
    StatusMap("Permissões de escrita".tr(), s["writable"] as? JsonObject)
    (s["ini"] as? JsonObject)?.let { ini ->
        SettingsCard(title = "PHP ini") {
            ini.forEach { (k, v) ->
                val o = v as? JsonObject
                val ok = (o?.get("status") as? JsonPrimitive)?.booleanOrNull == true
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(k, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "%s / min. %s".format((o?.get("current") as? JsonPrimitive)?.contentOrNull, (o?.get("minimal") as? JsonPrimitive)?.contentOrNull),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (ok) AdminBadge("OK", SettingsAccent) else AdminBadge("Baixo".tr(), AdminColors.Danger)
                }
            }
        }
    }
    StatusMap("Módulos PHP".tr(), s["modules"] as? JsonObject)
    SettingsCard(title = "Registos e backups".tr()) {
        val logs = (s["logs"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val backups = (s["backups"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        AdminInfoRow("Registos".tr(), logs.joinToString("\n").ifBlank { "—" })
        AdminInfoRow("Backups".tr(), backups.joinToString("\n").ifBlank { "—" })
    }
    SettingsCard(title = "Sessões".tr(), description = "Termina a sessão de todos os utilizadores, em todos os aparelhos (incluindo a tua).".tr()) {
        SettingsOutlinedButton("Terminar todas as sessões".tr(), onClick = { confirm = true }, destructive = true, enabled = state.busy == null)
    }
    if (confirm) {
        SettingsDialog(
            title = "Terminar todas as sessões?".tr(),
            message = "Todos os utilizadores vão ter de voltar a iniciar sessão.".tr(),
            confirmText = "Terminar".tr(),
            destructive = true,
            onConfirm = { confirm = false; vm.forceLogoutAll() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun StatusMap(title: String, map: JsonObject?) {
    map ?: return
    SettingsCard(title = title) {
        map.forEach { (k, v) ->
            val ok = (v as? JsonPrimitive)?.booleanOrNull == true
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(k, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (ok) AdminBadge("OK", SettingsAccent) else AdminBadge("Falta".tr(), AdminColors.Danger)
            }
        }
    }
}
