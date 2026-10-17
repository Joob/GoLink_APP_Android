package co.golink.tester.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.golink.tester.data.encryption.SecretCodeException
import co.golink.tester.domain.encryption.SecretCodeResponse
import co.golink.tester.ui.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Passo "código por email" da mudança da passphrase E2E (Definições e reset
 * pós-recuperação). Pede o código ao aparecer, mostra a validade (2 min) e
 * limpa o campo quando expira; chama [onConfirmed] com o change_token.
 */
@Composable
fun SecretCodeStep(
    requestCode: suspend () -> SecretCodeResponse,
    confirmCode: suspend (String) -> String,
    onConfirmed: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var email by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableLongStateOf(0L) }
    var resendAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val remaining = ((expiresAt - now) / 1000).toInt().coerceAtLeast(0)
    val resendIn = ((resendAt - now) / 1000).toInt().coerceAtLeast(0)

    fun send() {
        if (sending) return
        sending = true
        error = null
        scope.launch {
            runCatching { requestCode() }
                .onSuccess { r ->
                    email = r.email ?: email
                    expiresAt = System.currentTimeMillis() + r.expires_in * 1000L
                    resendAt = System.currentTimeMillis() + r.resend_in * 1000L
                    code = ""
                }
                .onFailure { t ->
                    (t as? SecretCodeException)?.resendIn?.let { wait ->
                        resendAt = System.currentTimeMillis() + wait * 1000L
                        if (expiresAt == 0L) expiresAt = System.currentTimeMillis() + 120_000L
                    }
                    error = t.message ?: "Falha ao enviar o código".tr()
                }
            sending = false
        }
    }

    LaunchedEffect(Unit) {
        send()
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    // Expirou: limpa o que estava escrito (igual ao OTP de login).
    LaunchedEffect(remaining == 0 && expiresAt > 0) {
        if (remaining == 0 && expiresAt > 0) code = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            email?.let { "Enviámos um código de 6 dígitos para %s.".tr().format(it) } ?: "A enviar o código de confirmação…".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
            singleLine = true,
            label = { Text("Código de confirmação".tr()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (remaining > 0) "O código expira em %ds".tr().format(remaining) else "O código expirou. Pede um novo.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = if (remaining > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = ::send, enabled = resendIn == 0 && !sending) {
                Text(if (resendIn > 0) "Reenviar em %ds".tr().format(resendIn) else "Reenviar código".tr())
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    runCatching { confirmCode(code) }
                        .onSuccess(onConfirmed)
                        .onFailure { t -> error = t.message ?: "Código inválido".tr(); code = "" }
                    busy = false
                }
            },
            enabled = !busy && code.length == 6 && remaining > 0,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Confirmar código".tr(), fontWeight = FontWeight.SemiBold)
        }
    }
}
