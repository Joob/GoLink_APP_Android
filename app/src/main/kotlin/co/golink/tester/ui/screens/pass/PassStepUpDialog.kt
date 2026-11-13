package co.golink.tester.ui.screens.pass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.ui.i18n.pt
import co.golink.tester.ui.screens.settings.PasskeyConfirmRow

/** Ações do Pass que pedem password ou passkey (PassStepUp no servidor). */
enum class PassDeleteOp { TRASH, DELETE, EMPTY }

/**
 * Mover para o lixo, apagar de vez ou esvaziar o lixo: confirma com a password
 * da conta ou uma passkey (purpose pass_delete), como o PassStepUpPopup da web.
 * Um erro do step-up fica no diálogo; qualquer outro fecha-o e vai para o toast.
 */
@Composable
fun PassStepUpDialog(
    op: PassDeleteOp,
    vm: PassViewModel,
    itemId: String? = null,
    itemName: String = "",
    trashDays: Int = 30,
    onDismiss: () -> Unit,
    onDone: () -> Unit = {},
) {
    val account by vm.stepUpAccount.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Conta só social: sem password; com passkeys confirma com elas, sem nenhuma não há o que pedir.
    val needsPassword = token == null && !account.socialite
    val ready = token != null || (if (account.socialite) !account.hasPasskeys else password.isNotEmpty())
    val (title, message, confirm) = when (op) {
        PassDeleteOp.TRASH -> Triple(pt("trash_title"), pt("trash_text", "name" to itemName, "days" to trashDays), pt("move_to_trash"))
        PassDeleteOp.DELETE -> Triple(pt("delete_forever_title"), pt("delete_forever_text", "name" to itemName), pt("delete_forever"))
        PassDeleteOp.EMPTY -> Triple(pt("empty_trash_title"), pt("empty_trash_text"), pt("empty_trash"))
    }
    val danger = MaterialTheme.colorScheme.error

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(danger.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = danger, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(pt("step_up_text"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                PasskeyConfirmRow(purpose = "pass_delete", confirmed = token != null, onToken = { token = it; error = null }, hasPasskeys = account.hasPasskeys)
                if (needsPassword) {
                    OutlinedTextField(
                        password,
                        { password = it; error = null },
                        label = { Text(pt("step_up_password")) },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, fontSize = 12.sp, color = danger, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !saving) {
                        Text(pt("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = ready && !saving,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = danger),
                        onClick = {
                            saving = true
                            error = null
                            vm.deleteWithStepUp(
                                op,
                                itemId,
                                password = password.takeIf { token == null && it.isNotEmpty() },
                                passkeyToken = token,
                                onStepUpError = { msg ->
                                    // Uso único: falhe o que falhar, confirma-se de novo.
                                    token = null
                                    password = ""
                                    error = msg
                                    saving = false
                                },
                                onFinished = { ok ->
                                    onDismiss()
                                    if (ok) onDone()
                                },
                            )
                        },
                    ) { Text(confirm, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
