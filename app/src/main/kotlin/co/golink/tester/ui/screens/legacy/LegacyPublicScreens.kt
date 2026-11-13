package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.legacy.LegacyOpenRepository
import co.golink.tester.data.legacy.LegacyOpenedItem
import co.golink.tester.domain.legacy.LegacyInviteDto
import co.golink.tester.domain.legacy.LegacyOpenDto
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class LegacyOpenState(
    val loading: Boolean = true,
    /** Rede ou servidor: o link pode estar bom, deixa tentar de novo. */
    val loadFailed: Boolean = false,
    val vault: LegacyOpenDto? = null,
    val items: List<LegacyOpenedItem>? = null,
    val unlocking: Boolean = false,
    val wrongCode: Boolean = false,
    val downloading: String? = null,
    val received: Long = 0,
    val total: Long = 0,
    val failed: String? = null,
    val saved: String? = null,
)

/** Páginas sem sessão: convite, check-in do email e acesso do recipient. */
@HiltViewModel
class LegacyPublicViewModel @Inject constructor(private val repo: LegacyOpenRepository) : ViewModel() {
    private val _open = MutableStateFlow(LegacyOpenState())
    val open: StateFlow<LegacyOpenState> = _open.asStateFlow()

    private val _invite = MutableStateFlow<Pair<Boolean, LegacyInviteDto?>>(true to null)
    val invite: StateFlow<Pair<Boolean, LegacyInviteDto?>> = _invite.asStateFlow()

    /** "idle" | "busy" | "counted" | "not_needed" | "expired" | "failed". */
    private val _checkin = MutableStateFlow("idle")
    val checkin: StateFlow<String> = _checkin.asStateFlow()

    private var token = ""

    fun loadOpen(token: String) {
        if (this.token == token) return
        this.token = token
        retryOpen()
    }

    fun retryOpen() {
        _open.value = LegacyOpenState(loading = true)
        viewModelScope.launch {
            runCatching { repo.vault(token) }
                .onSuccess { vault -> _open.value = LegacyOpenState(loading = false, vault = vault) }
                .onFailure { _open.value = LegacyOpenState(loading = false, loadFailed = true) }
        }
    }

    fun unlock(code: String, onDone: () -> Unit) {
        val vault = _open.value.vault ?: return
        _open.update { it.copy(unlocking = true, wrongCode = false) }
        viewModelScope.launch {
            val items = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { repo.unlock(vault, code) } }.getOrNull()
            _open.update { it.copy(unlocking = false, wrongCode = items == null, items = items ?: it.items) }
            if (items != null) onDone()
        }
    }

    fun download(item: LegacyOpenedItem) {
        if (_open.value.downloading != null) return
        _open.update { it.copy(downloading = item.dto.id, failed = null, saved = null, received = 0, total = 0) }
        viewModelScope.launch {
            runCatching { repo.download(token, item) { received, total -> _open.update { it.copy(received = received, total = total) } } }
                .onSuccess { name -> _open.update { it.copy(downloading = null, saved = name) } }
                .onFailure {
                    android.util.Log.w("Legacy", "download falhou", it)
                    _open.update { it.copy(downloading = null, failed = item.dto.id) }
                }
        }
    }

    fun loadInvite(id: String) {
        if (token == id) return
        token = id
        viewModelScope.launch { _invite.value = false to repo.invite(id) }
    }

    /** Só com o botão: os scanners de email abrem links. */
    fun confirmCheckin(token: String) {
        if (_checkin.value != "idle") return
        _checkin.value = "busy"
        viewModelScope.launch { _checkin.value = repo.checkin(token) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublicShell(onBack: () -> Unit, snackbar: SnackbarHostState? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = { Text("Legacy", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr()) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
}

/** LegacyInvite.vue: só mostra quem convidou; aceitar é no separador Guarding, com sessão. */
@Composable
fun LegacyInviteScreen(
    id: String,
    authenticated: Boolean,
    onOpenGuarding: () -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onBack: () -> Unit,
    vm: LegacyPublicViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { vm.loadInvite(id) }
    val state by vm.invite.collectAsStateWithLifecycle()
    val (loading, invite) = state
    PublicShell(onBack) {
        when {
            loading -> Loading()
            invite == null || invite.status != "invited" -> LegacyCard {
                Text(
                    lt(if (invite?.status == "accepted") "invite_accepted_title" else "invite_gone_title"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invite?.status == "accepted") {
                    Button(onClick = onOpenGuarding, shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)) {
                        Text(lt("invite_open"))
                    }
                }
            }
            else -> LegacyCard {
                Text(lt("invite_title", "name" to invite.owner_name), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                CardText(lt("invite_text", "count" to invite.threshold), Modifier.padding(top = 4.dp))
                Text(lt("invite_account", "email" to invite.email), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                    Button(onClick = if (authenticated) onOpenGuarding else onSignIn, shape = RoundedCornerShape(12.dp)) {
                        Text(lt(if (authenticated) "invite_open" else "invite_sign_in"))
                    }
                    if (!authenticated) OutlinedButton(onClick = onSignUp, shape = RoundedCornerShape(12.dp)) { Text(lt("invite_sign_up")) }
                }
            }
        }
    }
}

/** LegacyCheckin.vue. O check-in só conta com este botão (POST), nunca ao abrir o link. */
@Composable
fun LegacyCheckinScreen(token: String, onBack: () -> Unit, vm: LegacyPublicViewModel = hiltViewModel()) {
    val state by vm.checkin.collectAsStateWithLifecycle()
    PublicShell(onBack) {
        LegacyCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state == "idle" || state == "busy") {
                    Text(lt("checkin_title"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(lt("checkin_text"), fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.padding(top = 12.dp))
                    StillHereButton(enabled = state == "idle") { vm.confirmCheckin(token) }
                } else {
                    Text(lt("checkin_$state"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** LegacyOpen.vue: o código decifra no aparelho; os ficheiros vão para Transferências. */
@Composable
fun LegacyOpenScreen(token: String, onBack: () -> Unit, vm: LegacyPublicViewModel = hiltViewModel()) {
    LaunchedEffect(token) { vm.loadOpen(token) }
    val state by vm.open.collectAsStateWithLifecycle()
    // Fora do saved state: o código não fica no Bundle da atividade.
    var code by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.saved) { state.saved?.let { snackbar.showSnackbar(it) } }
    // Código a ser escrito ou conteúdo decifrado: fora de capturas e da miniatura dos recentes.
    LegacySecureWhile(code.isNotEmpty() || state.items != null)
    PublicShell(onBack, snackbar) {
        val vault = state.vault
        val items = state.items
        when {
            state.loading -> Loading()
            state.loadFailed -> LegacyCard {
                CardText(lt("load_failed"), Modifier.padding(top = 0.dp))
                OutlinedButton(onClick = vm::retryOpen, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 10.dp)) { Text(lt("retry")) }
            }
            vault == null -> LegacyCard {
                CardTitle(lt("open_gone_title"))
                CardText(lt("open_gone_text"))
            }
            items == null -> LegacyCard {
                Text(lt("open_title", "name" to vault.owner_name), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                CardText(lt("open_text"), Modifier.padding(top = 4.dp))
                OutlinedTextField(
                    code,
                    { code = it.uppercase().take(40) },
                    label = { Text(lt("open_code")) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    // Password: o teclado não aprende nem sugere o código.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (state.wrongCode) Text(lt("open_wrong_code"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                Button(
                    onClick = { vm.unlock(code) { code = "" } },
                    enabled = !state.unlocking && code.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text(lt("open_unlock")) }
            }
            else -> {
                Text(lt("open_left_for_you", "name" to vault.owner_name), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                items.forEach { item -> OpenedItemCard(item, items, state, onDownload = { vm.download(item) }) }
            }
        }
    }
}

private fun JsonObject?.str(key: String): String = (this?.get(key) as? JsonPrimitive)?.content.orEmpty()

@Composable
private fun OpenedItemCard(item: LegacyOpenedItem, all: List<LegacyOpenedItem>, state: LegacyOpenState, onDownload: () -> Unit) {
    val payload = item.payload
    LegacyCard {
        when {
            item.unreadable -> Row(verticalAlignment = Alignment.CenterVertically) {
                LegacyIconBox(Icons.Outlined.Lock)
                Spacer(Modifier.width(12.dp))
                Text(lt("error"), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
            payload == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                LegacyIconBox(Icons.Outlined.Lock)
                Spacer(Modifier.width(12.dp))
                Text(lt("open_locked", "date" to shortDate(item.dto.available_at)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.dto.type == "message" -> {
                Text(payload.str("subject"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(payload.str("body"), fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
                // A mensagem selada ainda nomeia o que entretanto saiu do cofre: só conta o que chegou.
                val names = (payload["attached"] as? JsonArray).orEmpty()
                    .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                    .filter { a -> all.any { it.dto.id == a.str("id") } }
                    .joinToString(", ") { it.str("name") }
                if (names.isNotEmpty()) Text(lt("open_attached", "names" to names), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
            item.dto.type == "note" -> {
                Text(payload.str("name"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(payload.str("body"), fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegacyIconBox(if (item.dto.type == "folder") Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(payload.str("name"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        val size = formatSize(item.dto.bytes)
                        Text(
                            if (item.dto.type == "folder") "${lt("files_count", "count" to item.dto.file_count)} · $size" else size,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onDownload,
                        enabled = state.downloading == null && item.dto.file_count > 0,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            if (state.downloading == item.dto.id) {
                                val pct = if (state.total > 0) minOf(100, (state.received * 100 / state.total).toInt()) else 0
                                "$pct%"
                            } else lt("open_download"),
                        )
                    }
                }
                if (state.failed == item.dto.id) {
                    Text(lt("open_download_failed"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
