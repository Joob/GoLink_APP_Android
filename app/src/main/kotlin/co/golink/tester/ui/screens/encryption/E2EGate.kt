package co.golink.tester.ui.screens.encryption

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class GateMode { Checking, Prompt, ShowRecovery, Recover, Reset, Done }

@HiltViewModel
class E2EGateViewModel @Inject constructor(
    private val keys: E2EKeyManager,
) : ViewModel() {

    val unlocked: StateFlow<Boolean> = keys.unlocked

    var mode by mutableStateOf(GateMode.Checking); private set
    var configured by mutableStateOf(false); private set
    var recoveryKey by mutableStateOf<String?>(null); private set
    var working by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    /** Ao entrar no ecrã autenticado: se já desbloqueado, nada; senão pede. */
    fun start() {
        if (keys.isUnlocked) { mode = GateMode.Done; return }
        if (mode != GateMode.Checking) return
        viewModelScope.launch {
            configured = runCatching { keys.isConfigured() }.getOrDefault(false)
            mode = GateMode.Prompt
        }
    }

    fun submitSecret(secret: String) {
        if (secret.isBlank() || working) return
        submitMinMillis = 10_000L // manter a animação de decifra visível ~10s (pedido de design)
        // Mínimo 8 chars NO SETUP (wrap = alvo de brute-force offline); no unlock
        // não, para chaves antigas continuarem a abrir. Igual à Web.
        if (!configured && secret.length < 8) {
            error = "A passphrase tem de ter pelo menos 8 caracteres.".tr()
            return
        }
        run("segredo errado".tr()) {
            val rk = keys.setupOrUnlock(secret)
            // A mudança de modo (fechar o gate) só corre DEPOIS do delay — senão
            // o diálogo desaparecia no instante do unlock e a animação era cortada.
            val finish: () -> Unit = {
                if (rk != null) { recoveryKey = rk; mode = GateMode.ShowRecovery }
                else mode = GateMode.Done
            }
            finish
        }
    }

    fun confirmRecoverySaved() { mode = GateMode.Done }

    fun startRecover() { error = null; mode = GateMode.Recover }
    fun backToPrompt() { error = null; mode = GateMode.Prompt }

    fun submitRecovery(rk: String) {
        if (rk.isBlank() || working) return
        run("recovery key inválida".tr()) {
            keys.recoverWithKey(rk)
            val finish: () -> Unit = { mode = GateMode.Reset } // nova password (pós-delay)
            finish
        }
    }

    fun submitReset(secret: String) {
        if (secret.isBlank() || working) return
        if (secret.length < 8) {
            error = "A passphrase tem de ter pelo menos 8 caracteres.".tr()
            return
        }
        run("falhou".tr()) {
            keys.rotateSecret(secret)
            val finish: () -> Unit = { mode = GateMode.Done }
            finish
        }
    }

    private var submitMinMillis = 0L

    // O bloco devolve a AÇÃO DE CONCLUSÃO (mudar de modo/fechar) — executada só
    // depois do delay mínimo, para a animação de decifra correr até ao fim.
    private fun run(errMsg: String, block: suspend () -> (() -> Unit)?) {
        working = true; error = null
        val minMillis = submitMinMillis
        submitMinMillis = 0L
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val result = runCatching { block() }
            // Em sucesso, segura a animação até ao mínimo pedido; em erro mostra já.
            if (result.isSuccess && minMillis > 0) {
                kotlinx.coroutines.delay((minMillis - (System.currentTimeMillis() - t0)).coerceAtLeast(0L))
            }
            result.onSuccess { it?.invoke() }.onFailure { error = errMsg }
            working = false
        }
    }
}

@Composable
fun E2EGate(viewModel: E2EGateViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.start() }

    val mode = viewModel.mode
    if (mode == GateMode.Checking || mode == GateMode.Done) return

    Dialog(onDismissRequest = { /* modal obrigatório */ }) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                when (mode) {
                    GateMode.ShowRecovery -> RecoveryContent(viewModel)
                    GateMode.Recover -> RecoverContent(viewModel)
                    GateMode.Reset -> SecretContent(
                        title = "Define uma nova password".tr(),
                        desc = "Escolhe uma nova password de encriptação.".tr(),
                        button = "Guardar".tr(),
                        viewModel = viewModel,
                        onSubmit = viewModel::submitReset,
                    )
                    else -> SecretContent(
                        title = if (viewModel.configured) "Desbloquear os teus ficheiros".tr() else "Proteger os teus ficheiros".tr(),
                        desc = if (viewModel.configured) "Introduz a tua password de encriptação.".tr() else "Define uma passphrase de encriptação.".tr(),
                        button = if (viewModel.configured) "Desbloquear".tr() else "Criar".tr(),
                        viewModel = viewModel,
                        onSubmit = viewModel::submitSecret,
                        showForgot = viewModel.configured,
                    )
                }
            }
        }
    }
}

@Composable
private fun DecryptEffect(label: String) {
    val pool = "ABCDEF0123456789"
    fun rand(n: Int) = buildString {
        repeat(n) { i ->
            append(pool.random())
            if (i % 4 == 3 && i < n - 1) append(' ')
        }
    }
    var line1 by remember { mutableStateOf(rand(20)) }
    var line2 by remember { mutableStateOf(rand(20)) }
    var fixed by remember { mutableStateOf(rand(20)) }
    var step by remember { mutableStateOf(0) }
    var dots by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            step = (step + 1) % 28
            val solved = minOf(step, 20)
            line1 = fixed.take(solved + solved / 4) + rand(20 - solved)
            line2 = rand(20)
            if (step == 0) fixed = rand(20)
            if (step % 6 == 0) dots = if (dots.length >= 3) "" else "$dots."
            kotlinx.coroutines.delay(65)
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(line1, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        Text(line2, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
        Text(label + dots, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Botão primário do gate — mesmo estilo dos botões da Web (rounded-xl, verde, texto branco semibold). */
@Composable
private fun GateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = co.golink.tester.ui.theme.BrandGreen,
            disabledContainerColor = co.golink.tester.ui.theme.BrandGreen.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = androidx.compose.ui.graphics.Color.White,
        )
    }
}

@Composable
private fun SecretContent(
    title: String,
    desc: String,
    button: String,
    viewModel: E2EGateViewModel,
    onSubmit: (String) -> Unit,
    showForgot: Boolean = false,
) {
    var secret by remember { mutableStateOf("") }
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(4.dp))
    Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    if (viewModel.working) {
        // Efeito "a desencriptar" no lugar do formulário — igual à Web.
        Spacer(Modifier.height(8.dp))
        co.golink.tester.ui.components.DecryptEffect(label = if (viewModel.configured) "A desencriptar".tr() else "A proteger os teus ficheiros".tr())
        Spacer(Modifier.height(8.dp))
    } else {
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        viewModel.error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(14.dp))
        GateButton(button, enabled = secret.isNotBlank()) { onSubmit(secret) }
    }
    if (showForgot && !viewModel.working) {
        TextButton(onClick = viewModel::startRecover, modifier = Modifier.fillMaxWidth()) {
            Text("Esqueci a password?".tr())
        }
    }
}

@Composable
private fun RecoveryContent(viewModel: E2EGateViewModel) {
    Text("Guarda a tua chave de recuperação".tr(), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(4.dp))
    Text(
        "Se esqueceres a password, é a ÚNICA forma de recuperar os ficheiros. Guarda-a em segurança.".tr(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Text(
            viewModel.recoveryKey.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    GateButton("Guardei".tr(), onClick = viewModel::confirmRecoverySaved)
}

@Composable
private fun RecoverContent(viewModel: E2EGateViewModel) {
    var rk by remember { mutableStateOf("") }
    Text("Recuperar com a chave de recuperação".tr(), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(4.dp))
    Text("Introduz a chave de recuperação que guardaste.".tr(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = rk, onValueChange = { rk = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
    viewModel.error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(14.dp))
    GateButton("Recuperar".tr(), enabled = !viewModel.working && rk.isNotBlank()) { viewModel.submitRecovery(rk) }
    TextButton(onClick = viewModel::backToPrompt, modifier = Modifier.fillMaxWidth()) { Text("Voltar".tr()) }
}
