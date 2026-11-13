package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.screens.settings.PasskeyConfirmRow

/** Ação do interruptor que pede step-up (password ou passkey, finalidade legacy_disarm). */
sealed interface LegacyStepUpRequest {
    /** Desarmar ou, depois de aberto, revogar (o mesmo POST /vault/disarm). */
    data class Disarm(val revoke: Boolean) : LegacyStepUpRequest
    /** Pausar até [until] (yyyy-MM-dd), já escolhido no PauseDialog. */
    data class Pause(val until: String) : LegacyStepUpRequest
}

/**
 * Confirmação com password ou passkey antes de desarmar/revogar/pausar
 * (LegacyDisarmPopup/LegacyPausePopup da web). Um erro do step-up fica no
 * diálogo; qualquer outro fecha-o e vai para o snackbar.
 */
@Composable
fun LegacyStepUpDialog(request: LegacyStepUpRequest, vm: LegacyViewModel, onDismiss: () -> Unit) {
    val account by vm.stepUpAccount.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Conta só social: sem password; com passkeys confirma com elas, sem nenhuma não há o que pedir.
    val needsPassword = token == null && !account.socialite
    val ready = token != null || (if (account.socialite) !account.hasPasskeys else password.isNotEmpty())
    val (title, message, confirm) = when (request) {
        is LegacyStepUpRequest.Disarm ->
            if (request.revoke) Triple("revoke_title", "revoke_text", "revoke") else Triple("disarm_title", "disarm_text", "disarm")
        is LegacyStepUpRequest.Pause -> Triple("pause_title", "pause_text", "pause")
    }
    val destructive = request is LegacyStepUpRequest.Disarm

    LegacyFormDialog(
        title = lt(title),
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(lt("cancel")) }
            Button(
                enabled = ready && !saving,
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                onClick = {
                    saving = true
                    error = null
                    vm.stepUpAction(
                        request,
                        password = password.takeIf { token == null && it.isNotEmpty() },
                        passkeyToken = token,
                        onStepUpError = { key ->
                            // Uso único: falhe o que falhar, confirma-se de novo.
                            token = null
                            password = ""
                            error = lt(key)
                            saving = false
                        },
                        onDone = onDismiss,
                    )
                },
            ) { Text(lt(confirm)) }
        },
    ) {
        Text(lt(message), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(lt("step_up_text"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        PasskeyConfirmRow(purpose = "legacy_disarm", confirmed = token != null, onToken = { token = it }, hasPasskeys = account.hasPasskeys)
        if (needsPassword) {
            OutlinedTextField(
                password,
                { password = it },
                label = { Text(lt("step_up_password")) },
                singleLine = true,
                isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
    }
}
