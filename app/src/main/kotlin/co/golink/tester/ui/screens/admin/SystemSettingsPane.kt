package co.golink.tester.ui.screens.admin

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.vector.ImageVector
import co.golink.tester.ui.screens.settings.SettingsColors
import co.golink.tester.ui.screens.settings.SettingsGroup
import co.golink.tester.ui.screens.settings.SettingsNavRow
import co.golink.tester.ui.screens.settings.SettingsSectionTitle
import co.golink.tester.ui.screens.settings.SettingsSwitch
import co.golink.tester.ui.screens.settings.settingsCardColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/** [min]: mínimo validado antes de gravar (igual ao do servidor); [default]: valor do servidor quando vazio. */
private class SField(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.Text,
    val help: String? = null,
    val min: Int? = null,
    val default: String? = null,
)

private fun SField.invalid(value: String): Boolean = min != null && (value.toIntOrNull() ?: Int.MIN_VALUE) < min

private class SSection(
    val title: String,
    val fields: List<SField>,
    val description: String? = null,
    val icon: ImageVector? = null,
    val tint: Color = SettingsColors.Green,
)

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
            SField("upload_limit", "Limite por ficheiro — plano Free (MB)".tr(), FieldKind.Number, "Só contas sem plano pago. Os planos pagos têm o limite próprio em Planos. Vazio = sem limite.".tr()),
            SField("chunk_size", "Tamanho das partes (MB)".tr(), FieldKind.Number, "Recomendado: 32 MB.".tr()),
            SField("mimetypes_blacklist", "Tipos de ficheiro bloqueados".tr(), help = "Extensões separadas por vírgulas, ex.: exe,bat".tr()),
        ), icon = Icons.Outlined.CloudUpload, tint = SettingsColors.Blue),
        SSection("Funcionalidades dos utilizadores".tr(), listOf(
            SField("storage_limitation", "Limitar armazenamento".tr(), FieldKind.Switch),
            SField("default_max_storage_amount", "Armazenamento por omissão (GB)".tr(), FieldKind.Number),
            SField("default_max_team_member", "Máximo de membros por equipa".tr(), FieldKind.Number, "0 = ilimitado".tr()),
            SField("account_deletion_grace_days", "Dias de graça antes de eliminar a conta".tr(), FieldKind.Number),
        ), icon = Icons.Outlined.People, tint = SettingsColors.Indigo),
        SSection("Aplicação".tr(), listOf(SField("contact_email", "Email de contacto".tr())), icon = Icons.Outlined.Email, tint = SettingsColors.Teal),
    )
    "signin" -> listOf(
        SSection("Registo".tr(), listOf(
            SField("registration", "Permitir registo".tr(), FieldKind.Switch),
            SField("user_verification", "Exigir verificação do email".tr(), FieldKind.Switch),
        ), icon = Icons.Outlined.PersonAdd, tint = SettingsColors.Green),
        SSection("Login social".tr(), SOCIAL.map { (k, n) -> SField("allowed_${k}_login", "Login com %s".tr().format(n), FieldKind.Switch) }, icon = Icons.AutoMirrored.Outlined.Login, tint = SettingsColors.Blue),
        // Como SignInUp.vue: mínimos = prazos prometidos na Política de Privacidade (secção 9).
        SSection(
            "Contas inativas".tr(),
            listOf(
                SField("inactive_deletion_enabled", "Eliminar contas inativas".tr(), FieldKind.Switch),
                SField("inactive_warn_days", "Dias de inatividade até ao aviso".tr(), FieldKind.Number, "Mínimo e valor por defeito 365 (12 meses), o prazo prometido na Política de Privacidade.".tr(), min = 365, default = "365"),
                SField("inactive_pass_warn_days", "Dias de inatividade até ao aviso (contas com Pass)".tr(), FieldKind.Number, "Mínimo e valor por defeito 730 (24 meses). Perder passwords guardadas pode deixar a pessoa sem acesso às outras contas, por isso estas contas têm mais tempo.".tr(), min = 730, default = "730"),
                SField("inactive_grace_days", "Dias para entrar após o aviso".tr(), FieldKind.Number, "Mínimo e valor por defeito 60. Há lembretes 30, 7 e 1 dia antes; a conta é eliminada depois deste prazo.".tr(), min = 60, default = "60"),
            ),
            "Os utilizadores sem atividade recebem um email, lembretes 30, 7 e 1 dia antes da data limite, e a conta é eliminada definitivamente se não entrarem até lá. Administradores, Support Team, subscritores pagos e contas com cofre Legacy nunca são afetados.".tr(),
            icon = Icons.Outlined.PersonOff,
            tint = SettingsColors.Amber,
        ),
    )
    "appearance" -> listOf(
        SSection("Geral".tr(), listOf(
            SField("app_color", "Cor do tema".tr(), FieldKind.Color, "Hexadecimal, ex.: #0F8A5F".tr()),
            SField("app_title", "Título da aplicação".tr()),
            SField("app_description", "Descrição".tr(), FieldKind.Multiline),
        ), icon = Icons.Outlined.Palette, tint = SettingsColors.Purple),
    )
    "mobile" -> listOf(
        SSection("App móvel".tr(), listOf(
            SField("app_version", "Versão da app".tr()),
            SField("android_download_link", "Link de download Android".tr()),
            SField("apple_download_link", "Link de download iOS".tr()),
        ), icon = Icons.Outlined.PhoneAndroid, tint = SettingsColors.Teal),
        // Rodapé das faturas (como em /admin/invoices na web): vazio → escondido.
        SSection("Links da fatura".tr(), listOf(
            SField("invoice_github_url", "GitHub"),
            SField("invoice_googleplay_url", "Google Play"),
            SField("invoice_appstore_url", "App Store"),
        ), icon = Icons.Outlined.Receipt, tint = SettingsColors.Slate),
    )
    // A homepage nova usa textos fixos (i18n): só estes dois ainda contam.
    "homepage" -> listOf(
        SSection("Página inicial".tr(), listOf(
            SField("allow_homepage", "Mostrar página inicial".tr(), FieldKind.Switch),
            SField("section_pricing_content", "Mostrar secção de preços".tr(), FieldKind.Switch),
        ), icon = Icons.Outlined.Public, tint = SettingsColors.Blue),
    )
    else -> emptyList()
}

private val ALL_KEYS: List<String> by lazy {
    DRAFT_TABS.flatMap { t -> schema(t).flatMap { s -> s.fields.map { it.key } } } + IMAGE_KEYS.map { it.first }
}

/** Função e não `lazy`: as etiquetas (.tr()) seguem o idioma atual da app. */
private fun fieldsWithMin(): Map<String, SField> =
    DRAFT_TABS.flatMap { t -> schema(t).flatMap { it.fields } }.filter { it.min != null }.associateBy { it.key }

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

    /** Página aberta (null = lista); no VM para a AdminScreen fechar a página no "voltar". */
    private val _page = MutableStateFlow<String?>(null)
    val page = _page.asStateFlow()
    fun openPage(p: String) { _page.value = p }
    fun closePage() { _page.value = null }

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
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = "Não foi possível carregar (%s)".tr().format(t.message.orEmpty())) } }
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
        // Mínimos (ex.: contas inativas): o servidor recusaria com 422 sem dizer porquê.
        val withMin = fieldsWithMin()
        draft.entries.firstNotNullOfOrNull { (k, v) -> withMin[k]?.takeIf { it.invalid(v) } }?.let { f ->
            _state.update { it.copy(toast = "%s: mínimo %d".tr().format(f.label, f.min)) }
            return
        }
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
            // Leitura fora da main thread e com limite (logótipo/favicon).
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        if (out.size() > MAX_SETTING_IMAGE_BYTES) error("Imagem demasiado grande (máximo %d MB)".tr().format(MAX_SETTING_IMAGE_BYTES / (1024 * 1024)))
                    }
                    out.toByteArray()
                }
            } ?: error("Não foi possível ler a imagem".tr())
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

/** Página das definições na lista de navegação (como o menu agrupado da web em mobile). */
private class SettingsPageDef(val key: String, val title: String, val subtitle: String, val icon: ImageVector, val color: Color)

private fun settingsGroups(): List<Pair<String, List<SettingsPageDef>>> = listOf(
    "Aplicação".tr() to listOf(
        SettingsPageDef("app", "Aplicação".tr(), "Envios, armazenamento, subscrição e cache".tr(), Icons.Outlined.Tune, SettingsColors.Green),
        SettingsPageDef("appearance", "Aparência".tr(), "Cor do tema, título e imagens da marca".tr(), Icons.Outlined.Palette, SettingsColors.Purple),
        SettingsPageDef("homepage", "Página inicial".tr(), "O que aparece na página pública".tr(), Icons.Outlined.Public, SettingsColors.Blue),
        SettingsPageDef("mobile", "App móvel".tr(), "Versão, links de download e da fatura".tr(), Icons.Outlined.PhoneAndroid, SettingsColors.Teal),
    ),
    "Utilizadores".tr() to listOf(
        SettingsPageDef("signin", "Login e registo".tr(), "Registo, login social e contas inativas".tr(), Icons.Outlined.HowToReg, SettingsColors.Indigo),
    ),
    "Serviços".tr() to listOf(
        SettingsPageDef("ai", "IA".tr(), "Funcionalidades, modelos e custos".tr(), Icons.Outlined.AutoAwesome, SettingsColors.Orange),
        SettingsPageDef("environment", "Ambiente".tr(), "Websockets, armazenamento e email".tr(), Icons.Outlined.Code, SettingsColors.Slate),
    ),
    "Sistema".tr() to listOf(
        SettingsPageDef("server", "Servidor".tr(), "Estado, PHP, registos e sessões".tr(), Icons.Outlined.Dns, SettingsColors.Red),
    ),
)

/** Título da página aberta, para a barra de topo da AdminScreen. */
internal fun settingsPageTitle(key: String): String? = settingsGroups().flatMap { it.second }.firstOrNull { it.key == key }?.title

@Composable
fun SystemSettingsPane(viewModel: SystemSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingPage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() } }
    LaunchedEffect(page) {
        when (page) {
            "ai" -> viewModel.loadAi()
            "environment" -> viewModel.loadEnv()
            "server" -> viewModel.loadServer()
        }
    }
    // Abrir uma página sem rascunho com alterações pendentes larga-as (como no web).
    fun open(p: String) {
        if (state.draft.isNotEmpty() && p !in DRAFT_TABS) pendingPage = p else viewModel.openPage(p)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                state.isLoading -> AdminLoading()
                state.error != null -> AdminError(state.error!!, viewModel::load)
                else -> {
                    // Cada página começa no topo.
                    val scroll = key(page) { rememberScrollState() }
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (val p = page) {
                            null -> SettingsHome(state, ::open)
                            "app" -> {
                                SubscriptionTypeCard(state, viewModel)
                                DraftSections(schema(p), state, viewModel)
                                SettingsCard(
                                    title = "Cache".tr(),
                                    description = "Limpa a cache de configuração, rotas e vistas do servidor.".tr(),
                                    icon = Icons.Outlined.Cached,
                                    iconTint = SettingsColors.Slate,
                                ) {
                                    SettingsOutlinedButton("Limpar cache".tr(), onClick = viewModel::flushCache, enabled = state.busy == null)
                                }
                            }
                            "signin" -> {
                                DraftSections(schema(p), state, viewModel)
                                SettingsSectionTitle("Credenciais do login social".tr())
                                SOCIAL.forEach { (k, n) -> SocialCredentialsCard(k, n, state, viewModel) }
                            }
                            "appearance" -> {
                                DraftSections(schema(p), state, viewModel)
                                ImagesCard(state, viewModel)
                            }
                            "ai" -> AiTab(state.ai, viewModel)
                            "environment" -> EnvTab(state.env)
                            "server" -> ServerTab(state, viewModel)
                            else -> DraftSections(schema(p), state, viewModel)
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        val showBar = page in DRAFT_TABS || (page == null && state.draft.isNotEmpty())
        if (showBar && !state.isLoading) SaveBar(state, viewModel)
    }

    pendingPage?.let { t ->
        SettingsDialog(
            title = "Descartar alterações?".tr(),
            message = "Tens %d alterações por guardar.".tr().format(state.draft.size),
            confirmText = "Descartar".tr(),
            destructive = true,
            onConfirm = { viewModel.discard(); viewModel.openPage(t); pendingPage = null },
            onDismiss = { pendingPage = null },
        )
    }
}

/** Lista agrupada das páginas, com a contagem de alterações por guardar em cada uma. */
@Composable
private fun SettingsHome(state: SystemSettingsViewModel.UiState, onOpen: (String) -> Unit) {
    AdminPageHeader("Configuração geral da aplicação. As alterações ficam em rascunho até carregares em Guardar.".tr())
    settingsGroups().forEach { (title, pages) ->
        SettingsGroup(title) {
            pages.forEachIndexed { i, p ->
                val pending = schema(p.key).sumOf { s -> s.fields.count { it.key in state.draft } }
                SettingsNavRow(
                    icon = p.icon,
                    color = p.color,
                    title = p.title,
                    subtitle = p.subtitle,
                    showDivider = i < pages.lastIndex,
                    badge = if (pending > 0) { { AdminCount(pending.toString(), AdminTone.Warning) } } else null,
                    onClick = { onOpen(p.key) },
                )
            }
        }
    }
}

@Composable
private fun SaveBar(state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    Surface(color = settingsCardColor(), shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.draft.isEmpty()) {
                Text("Sem alterações".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AdminStatusPill("%d alterações por guardar".tr().format(state.draft.size), AdminTone.Warning)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::discard, enabled = state.draft.isNotEmpty() && !state.saving) { Text("Descartar".tr()) }
            Spacer(Modifier.width(4.dp))
            SettingsPrimaryButton("Guardar".tr(), onClick = vm::save, enabled = state.draft.isNotEmpty(), loading = state.saving)
        }
    }
}

@Composable
private fun DraftSections(sections: List<SSection>, state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    sections.forEach { section ->
        SettingsCard(title = section.title, description = section.description, icon = section.icon, iconTint = section.tint) {
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
        FieldKind.Switch -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            SettingsSwitch(value.isOn(), { vm.set(f.key, if (it) "1" else "0") })
        }
        FieldKind.Number -> SettingsField(
            label, value, { vm.set(f.key, it.filter(Char::isDigit)) },
            keyboardType = KeyboardType.Number,
            supportingText = f.help,
            placeholder = f.default,
            isError = changed && f.invalid(value),
        )
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
    SettingsCard(
        title = "Tipo de subscrição".tr(),
        icon = Icons.Outlined.CreditCard,
        iconTint = SettingsColors.Blue,
        trailing = {
            TextButton(onClick = { locked = !locked }) {
                Icon(if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (locked) "Desbloquear".tr() else "Bloquear".tr())
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsNotice("Mudar o tipo de subscrição com utilizadores ativos pode deixar subscrições e planos inconsistentes.".tr(), AdminColors.Warning)
            AdminSegmented(
                options = listOf("none" to "Nenhuma".tr(), "fixed" to "Fixa".tr(), "metered" to "Por consumo".tr()),
                selected = state.subscriptionType,
                onSelect = { k -> if (k != state.subscriptionType) confirm = k },
                enabled = !locked && state.busy == null,
            )
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
        icon = Icons.Outlined.Key,
        iconTint = if (configured) SettingsColors.Green else SettingsColors.Amber,
        trailing = {
            if (configured) AdminStatusPill("Configurado".tr(), AdminTone.Success) else AdminStatusPill("Por configurar".tr(), AdminTone.Warning)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Callback".tr(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AdminCodeChip("${vm.imageUrl("socialite/$service/callback")}")
            if (!open) {
                SettingsOutlinedButton(if (configured) "Atualizar credenciais".tr() else "Configurar".tr(), onClick = { open = true })
            } else {
                SettingsField("Client ID", id, { id = it.trim() })
                SettingsField("Client secret", secret, { secret = it.trim() }, isPassword = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { id = ""; secret = ""; open = false }) { Text("Cancelar".tr()) }
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
}

@Composable
private fun ImagesCard(state: SystemSettingsViewModel.UiState, vm: SystemSettingsViewModel) {
    var target by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = target
        if (uri != null && key != null) vm.uploadImage(key, uri)
        target = null
    }
    SettingsCard(
        title = "Marca".tr(),
        description = "As imagens são gravadas logo, fora do rascunho.".tr(),
        icon = Icons.Outlined.Image,
        iconTint = SettingsColors.Pink,
    ) {
        Column {
            IMAGE_KEYS.forEachIndexed { i, (key, label) ->
                val url = vm.imageUrl(state.values[key])
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (url != null) {
                            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
                        } else {
                            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label.tr(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            if (url != null) "Definida".tr() else "Sem imagem".tr(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
    if (ai.usage.isNotEmpty()) {
        AdminStatGrid(
            listOf(
                AdminStat("Custo".tr(), "$%.2f".format(ai.usage.sumOf { it.cost_usd }), Icons.Outlined.Payments, SettingsColors.Green),
                AdminStat("Pedidos".tr(), ai.usage.sumOf { it.requests }.toString(), Icons.Outlined.Insights, SettingsColors.Blue),
                AdminStat("Tokens".tr(), compactNumber(ai.usage.sumOf { it.input_tokens + it.output_tokens }), Icons.Outlined.Tag, SettingsColors.Purple),
                AdminStat("Funcionalidades".tr(), "%d/%d".format(ai.features.count { it.enabled }, ai.features.size), Icons.Outlined.AutoAwesome, SettingsColors.Orange),
            ),
        )
    }
    SettingsCard(
        title = "Funcionalidades".tr(),
        description = "Os pedidos à Anthropic só incluem o texto necessário para cada funcionalidade.".tr(),
        icon = Icons.Outlined.AutoAwesome,
        iconTint = SettingsColors.Orange,
    ) {
        Column {
            ai.features.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(aiFeatureLabel(f.feature), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (f.toggle != null) {
                            SettingsSwitch(f.enabled, { vm.saveAi(f.toggle, if (it) "1" else "0") })
                        } else {
                            AdminStatusPill("Sempre ativa".tr(), AdminTone.Neutral)
                        }
                    }
                    if (f.model_setting != null) {
                        AdminSegmented(
                            options = ai.models.map { it to modelLabel(it) },
                            selected = f.model,
                            onSelect = { m -> if (m != null && m != f.model) vm.saveAi(f.model_setting, m) },
                        )
                    }
                }
            }
        }
    }
    SettingsCard(title = "Utilização".tr(), icon = Icons.AutoMirrored.Outlined.ShowChart, iconTint = SettingsColors.Blue) {
        if (ai.usage.isEmpty()) {
            Text("Ainda não há pedidos registados.".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column {
                ai.usage.sortedByDescending { it.cost_usd }.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(aiFeatureLabel(r.feature), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "%s · %s".format(modelLabel(r.model), "%d pedidos".tr().format(r.requests)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (r.cost_usd in 0.0001..0.01) "$%.4f".format(r.cost_usd) else "$%.2f".format(r.cost_usd),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun compactNumber(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "%.0fk".format(n / 1_000.0)
    else -> n.toString()
}

private fun modelLabel(m: String?) = when (m) {
    "claude-opus-5-5" -> "Opus 5.5"
    "claude-sonnet-5-5" -> "Sonnet 5.5"
    "claude-haiku-5-5" -> "Haiku 5.5"
    "claude-haiku-4-5" -> "Haiku 4.5"
    else -> m.orEmpty()
}

private fun aiFeatureLabel(f: String) = f.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun EnvTab(env: JsonObject?) {
    if (env == null) return AdminLoading(Modifier.fillMaxWidth().padding(32.dp))
    SettingsNotice("Só leitura: os segredos aparecem mascarados. Para alterar credenciais de ambiente usa o site.".tr(), AdminColors.Info)
    val titles = mapOf("broadcast" to "Websockets".tr(), "storage" to "Armazenamento".tr(), "mail" to "Email".tr())
    val icons = mapOf("broadcast" to Icons.Outlined.Wifi, "storage" to Icons.Outlined.Storage, "mail" to Icons.Outlined.Email)
    env.forEach { (section, node) ->
        val obj = node as? JsonObject ?: return@forEach
        val driver = (obj["driver"] as? JsonPrimitive)?.contentOrNull
        SettingsCard(
            title = titles[section] ?: section,
            icon = icons[section] ?: Icons.Outlined.Settings,
            iconTint = SettingsColors.Slate,
            trailing = { driver?.let { AdminCodeChip(it) } },
        ) {
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
    fun okCount(o: JsonObject?) = o?.values?.count { v ->
        ((v as? JsonPrimitive)?.booleanOrNull ?: ((v as? JsonObject)?.get("status") as? JsonPrimitive)?.booleanOrNull) == true
    } ?: 0
    val ini = s["ini"] as? JsonObject
    val modules = s["modules"] as? JsonObject
    val writable = s["writable"] as? JsonObject
    AdminStatGrid(
        listOf(
            AdminStat("PHP", (s["php_version"] as? JsonPrimitive)?.contentOrNull, Icons.Outlined.Code, SettingsColors.Indigo),
            AdminStat(
                "Cron", if (cronOk) "A correr".tr() else "Parado".tr(), Icons.Outlined.Schedule,
                if (cronOk) SettingsColors.Green else SettingsColors.Red,
                hint = (cron?.get("lastUpdate") as? JsonPrimitive)?.contentOrNull,
            ),
            AdminStat(
                "Módulos PHP".tr(), "%d/%d".format(okCount(modules), modules?.size ?: 0), Icons.Outlined.Extension,
                if (okCount(modules) == (modules?.size ?: 0)) SettingsColors.Green else SettingsColors.Red,
            ),
            AdminStat(
                "PHP ini", "%d/%d".format(okCount(ini), ini?.size ?: 0), Icons.Outlined.Tune,
                if (okCount(ini) == (ini?.size ?: 0)) SettingsColors.Green else SettingsColors.Amber,
            ),
        ),
    )
    StatusMap("Permissões de escrita".tr(), writable, Icons.Outlined.Folder)
    ini?.let {
        SettingsCard(title = "PHP ini", icon = Icons.Outlined.Tune, iconTint = SettingsColors.Indigo) {
            Column {
                it.entries.forEachIndexed { i, (k, v) ->
                    val o = v as? JsonObject
                    val ok = (o?.get("status") as? JsonPrimitive)?.booleanOrNull == true
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "%s / min. %s".format((o?.get("current") as? JsonPrimitive)?.contentOrNull, (o?.get("minimal") as? JsonPrimitive)?.contentOrNull),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (ok) AdminStatusPill("OK", AdminTone.Success) else AdminStatusPill("Baixo".tr(), AdminTone.Danger)
                    }
                }
            }
        }
    }
    StatusMap("Módulos PHP".tr(), modules, Icons.Outlined.Extension)
    SettingsCard(title = "Registos e backups".tr(), icon = Icons.Outlined.Description, iconTint = SettingsColors.Slate) {
        val logs = (s["logs"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val backups = (s["backups"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        AdminInfoRow("Registos".tr(), logs.joinToString("\n").ifBlank { "—" })
        AdminInfoRow("Backups".tr(), backups.joinToString("\n").ifBlank { "—" })
    }
    SettingsCard(
        title = "Sessões".tr(),
        description = "Termina a sessão de todos os utilizadores, em todos os aparelhos (incluindo a tua).".tr(),
        icon = Icons.AutoMirrored.Outlined.Logout,
        iconTint = SettingsColors.Red,
        danger = true,
    ) {
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
private fun StatusMap(title: String, map: JsonObject?, icon: ImageVector) {
    map ?: return
    val missing = map.values.count { (it as? JsonPrimitive)?.booleanOrNull != true }
    SettingsCard(
        title = title,
        icon = icon,
        iconTint = if (missing == 0) SettingsColors.Green else SettingsColors.Red,
        trailing = { if (missing == 0) AdminStatusPill("Tudo OK".tr(), AdminTone.Success) else AdminStatusPill("%d em falta".tr().format(missing), AdminTone.Danger) },
    ) {
        Column {
            map.entries.forEachIndexed { i, (k, v) ->
                val ok = (v as? JsonPrimitive)?.booleanOrNull == true
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (ok) AdminStatusPill("OK", AdminTone.Success) else AdminStatusPill("Falta".tr(), AdminTone.Danger)
                }
            }
        }
    }
}

/** Limite das imagens das definições (logótipo, favicon…). */
private const val MAX_SETTING_IMAGE_BYTES = 2 * 1024 * 1024
