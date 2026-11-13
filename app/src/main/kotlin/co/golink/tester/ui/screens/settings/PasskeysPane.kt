package co.golink.tester.ui.screens.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.PasskeyError
import co.golink.tester.data.auth.PasskeyInfo
import co.golink.tester.data.auth.PasskeyRepository
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PasskeysUiState(
    val passkeys: List<PasskeyInfo> = emptyList(),
    val loaded: Boolean = false,
    val busy: Boolean = false,
    /** Criar/remover pediu confirmação (423): mostrar o diálogo. */
    val confirmFor: PasskeyAction? = null,
    /** Erro dentro do diálogo de confirmação (ex.: password errada) — o diálogo fica aberto. */
    val confirmError: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

/** Ação que ficou à espera de confirmação (password ou outra passkey). */
sealed interface PasskeyAction {
    data object Add : PasskeyAction
    data class Remove(val key: PasskeyInfo) : PasskeyAction
}

@HiltViewModel
class PasskeysViewModel @Inject constructor(
    private val repository: PasskeyRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PasskeysUiState())
    val state: StateFlow<PasskeysUiState> = _state.asStateFlow()

    /** Conta com passkeys (vem do /api/user): decide se aparece "Usar passkey". */
    val hasPasskeys: StateFlow<Boolean> = sessionManager.state
        .map { (it as? co.golink.tester.data.auth.AuthState.Authenticated)?.user?.hasPasskeys == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun load() = viewModelScope.launch {
        repository.list()
            .onSuccess { list -> _state.update { it.copy(passkeys = list, loaded = true) } }
            .onFailure { _state.update { it.copy(loaded = true) } }
    }

    /**
     * passkey_token (add_passkey) ainda por gastar: o servidor só o gasta
     * quando a passkey fica guardada, por isso cancelar o diálogo do sistema
     * não obriga a confirmar outra vez.
     */
    private var addToken: String? = null

    /** Sem password/token primeiro; o servidor diz (423) se precisa de confirmação. */
    fun add(activity: Context, password: String? = null, passkeyToken: String? = null) {
        if (_state.value.busy) return
        if (passkeyToken != null) addToken = passkeyToken
        _state.update { it.copy(busy = true, message = null, confirmFor = null) }
        viewModelScope.launch {
            repository.register(activity, password, if (password.isNullOrBlank()) addToken else null, deviceName())
                .onSuccess {
                    addToken = null
                    _state.update { it.copy(busy = false, message = "Passkey adicionada.".tr(), isError = false) }
                    load()
                    sessionManager.refreshUser()
                }
                .onFailure { t ->
                    if (t is PasskeyError.ConfirmationRequired || t is PasskeyError.ReauthRequired) addToken = null
                    _state.update {
                        when (t) {
                            is PasskeyError.ConfirmationRequired -> it.copy(busy = false, confirmFor = PasskeyAction.Add)
                            is PasskeyError.Cancelled -> it.copy(busy = false)
                            is PasskeyError.ReauthRequired ->
                                // Conta sem password mas com passkeys: confirma com uma delas.
                                if (hasPasskeys.value) it.copy(busy = false, confirmFor = PasskeyAction.Add)
                                else it.copy(busy = false, message = t.message.orEmpty().tr(), isError = true)
                            is PasskeyError.Duplicate -> it.copy(busy = false, message = "Este aparelho já tem uma passkey para a tua conta.".tr(), isError = true)
                            is co.golink.tester.domain.auth.AuthError.Validation ->
                                it.copy(busy = false, message = "Password incorreta ou passkey inválida.".tr(), isError = true)
                            else -> it.copy(busy = false, message = "Não foi possível adicionar a passkey.".tr(), isError = true)
                        }
                    }
                }
        }
    }

    fun dismissConfirmation() = _state.update { it.copy(confirmFor = null, confirmError = null) }

    fun rename(id: String, name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        repository.rename(id, name).onSuccess { load() }
            .onFailure { _state.update { it.copy(message = "Erro".tr(), isError = true) } }
    }

    /** Remover também exige confirmação: o 1.º pedido vai sem nada e o 423 abre o diálogo. */
    fun delete(key: PasskeyInfo, password: String? = null, passkeyToken: String? = null) = viewModelScope.launch {
        // O diálogo (se aberto) fica até correr bem: password errada volta a pedir só a password.
        _state.update { it.copy(message = null, confirmError = null) }
        repository.delete(key.id, password, passkeyToken)
            .onSuccess {
                _state.update { it.copy(confirmFor = null, message = "Passkey removida.".tr(), isError = false) }
                load()
                sessionManager.refreshUser()
            }
            .onFailure { t ->
                _state.update {
                    when (t) {
                        is PasskeyError.ConfirmationRequired -> it.copy(confirmFor = it.confirmFor ?: PasskeyAction.Remove(key))
                        // Conta sem password: confirma com uma passkey (a própria serve). Se até
                        // essa confirmação foi recusada, só resta voltar a entrar.
                        is PasskeyError.ReauthRequired ->
                            if (passkeyToken == null) it.copy(confirmFor = it.confirmFor ?: PasskeyAction.Remove(key))
                            else it.copy(confirmFor = null, message = "Por segurança, termina sessão e volta a entrar; depois remove a passkey nos 15 minutos seguintes.".tr(), isError = true)
                        is co.golink.tester.domain.auth.AuthError.Validation ->
                            if (it.confirmFor != null) it.copy(confirmError = "Password incorreta ou passkey inválida.".tr())
                            else it.copy(message = "Password incorreta ou passkey inválida.".tr(), isError = true)
                        else -> it.copy(confirmFor = null, message = "Erro".tr(), isError = true)
                    }
                }
            }
    }

    /**
     * Confirmar com passkey em vez da password. [onToken] recebe o
     * passkey_token; cancelar não mostra erro.
     */
    fun confirm(activity: Context, purpose: String, onToken: (String) -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        repository.confirm(activity, purpose)
            .onSuccess(onToken)
            .onFailure { t -> if (t !is PasskeyError.Cancelled) onError("A confirmação com a passkey falhou.".tr()) }
    }

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
            .distinct().joinToString(" ").take(80)
}

private fun shortDate(iso: String?): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
}.getOrDefault("")

/** Secção "Passkeys" do painel Password & segurança. */
@Composable
internal fun PasskeysSection(
    socialiteAccount: Boolean,
    hasPasskeys: Boolean,
    viewModel: PasskeysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var renaming by remember { mutableStateOf<PasskeyInfo?>(null) }
    var removing by remember { mutableStateOf<PasskeyInfo?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    PaneSection("Passkeys".tr())
    PaneCard {
        Text(
            "Entra com a impressão digital, o rosto ou o PIN do aparelho em vez da password e do código de segurança. As passkeys não podem ser roubadas por phishing.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.loaded && state.passkeys.isEmpty()) {
            Text("Ainda não tens passkeys.".tr(), style = MaterialTheme.typography.bodyMedium)
        }

        state.passkeys.forEach { key ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Key, contentDescription = null, tint = SettingsAccent, modifier = Modifier.size(20.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(key.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    val used = key.lastUsedAt?.let { "último uso %s".tr().format(shortDate(it)) } ?: "nunca usada".tr()
                    Text(
                        listOfNotNull(
                            "Adicionada %s".tr().format(shortDate(key.createdAt)),
                            used,
                            if (key.synced) "sincronizada".tr() else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { renaming = key }) { Icon(Icons.Outlined.Edit, contentDescription = "Renomear".tr()) }
                IconButton(onClick = { removing = key }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remover".tr(), tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        state.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isError) MaterialTheme.colorScheme.error else SettingsAccent,
            )
        }

        SettingsPrimaryButton(
            text = "Adicionar passkey".tr(),
            onClick = { viewModel.add(context) },
            loading = state.busy,
            icon = Icons.Outlined.Key,
        )
    }

    state.confirmFor?.let { action ->
        var password by remember(action) { mutableStateOf("") }
        var error by remember(action) { mutableStateOf<String?>(null) }
        val removing = action as? PasskeyAction.Remove
        SettingsDialog(
            title = "Confirma a tua identidade".tr(),
            message = when {
                socialiteAccount -> null
                removing != null -> "Para remover uma passkey, confirma com a password da conta.".tr()
                else -> "Para adicionar uma passkey, confirma com a password da conta.".tr()
            },
            icon = Icons.Outlined.Key,
            confirmText = "Continuar".tr(),
            confirmEnabled = !socialiteAccount && password.isNotBlank(),
            onConfirm = {
                if (removing != null) viewModel.delete(removing.key, password = password)
                else viewModel.add(context, password = password)
            },
            onDismiss = viewModel::dismissConfirmation,
        ) {
            if (!socialiteAccount) SettingsField("Password atual".tr(), password, { password = it }, isPassword = true, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
            if (hasPasskeys || state.passkeys.isNotEmpty()) {
                TextButton(
                    onClick = {
                        viewModel.confirm(
                            context,
                            if (removing != null) "remove_passkey" else "add_passkey",
                            onToken = { token ->
                                if (removing != null) viewModel.delete(removing.key, passkeyToken = token)
                                else viewModel.add(context, passkeyToken = token)
                            },
                            onError = { error = it },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Usar passkey em vez da password".tr(), color = SettingsAccent, fontWeight = FontWeight.SemiBold) }
            }
            (error ?: state.confirmError)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    renaming?.let { key ->
        var name by remember(key.id) { mutableStateOf(key.name) }
        SettingsDialog(
            title = "Renomear".tr(),
            confirmText = "Guardar".tr(),
            confirmEnabled = name.isNotBlank(),
            onConfirm = { viewModel.rename(key.id, name); renaming = null },
            onDismiss = { renaming = null },
        ) {
            SettingsField("Nome".tr(), name, { name = it.take(80) })
        }
    }

    removing?.let { key ->
        SettingsDialog(
            title = "Remover passkey?".tr(),
            message = "Deixas de poder entrar com \"%s\".".tr().format(key.name),
            icon = Icons.Outlined.Delete,
            confirmText = "Remover".tr(),
            destructive = true,
            onConfirm = { viewModel.delete(key); removing = null },
            onDismiss = { removing = null },
        )
    }
}

/**
 * "Usar passkey em vez da password" nos formulários sensíveis. Só aparece se a
 * conta tiver passkeys; depois de confirmar mostra o estado.
 */
@Composable
internal fun PasskeyConfirmRow(
    /** password | email | delete_account — o token só serve para essa ação. */
    purpose: String,
    confirmed: Boolean,
    onToken: (String) -> Unit,
    hasPasskeys: Boolean? = null,
    viewModel: PasskeysViewModel = hiltViewModel(),
) {
    val fromSession by viewModel.hasPasskeys.collectAsStateWithLifecycle()
    if (!(hasPasskeys ?: fromSession)) return
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (confirmed) {
        Text("Confirmado com a tua passkey.".tr(), color = SettingsAccent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        return
    }

    Row(horizontalArrangement = Arrangement.Start, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(
            onClick = {
                loading = true
                error = null
                viewModel.confirm(
                    context,
                    purpose,
                    onToken = { loading = false; onToken(it) },
                    onError = { loading = false; error = it },
                ).invokeOnCompletion { loading = false }
            },
            enabled = !loading,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            } else {
                Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = SettingsAccent)
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            }
            Text("Usar passkey em vez da password".tr(), color = SettingsAccent, fontWeight = FontWeight.SemiBold)
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}
