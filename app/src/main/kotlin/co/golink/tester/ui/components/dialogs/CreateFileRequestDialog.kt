package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.i18n.tr

/**
 * Pedido de ficheiros (como na web): nome, email a notificar, mensagem e
 * prazo para aceitar ficheiros (0 = sem prazo, 1, 7 ou 30 dias).
 */
@Composable
fun CreateFileRequestDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String?, email: String?, notes: String?, expiresInDays: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(7) }

    DialogScaffold(
        title = "Pedir ficheiros".tr(),
        subtitle = "Quem tiver o link pode enviar-te ficheiros, sem conta.".tr(),
        onDismiss = onDismiss,
        confirmText = "Criar pedido".tr(),
        onConfirm = {
            onCreate(name.takeIf { it.isNotBlank() }, email.takeIf { it.isNotBlank() }, notes.takeIf { it.isNotBlank() }, days)
            onDismiss()
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome do pedido (opcional)".tr()) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Notificar por email (opcional)".tr()) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Mensagem (opcional)".tr()) },
            maxLines = 3,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        SectionLabel("Aceitar ficheiros durante".tr())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "1 dia".tr(), 7 to "7 dias".tr(), 30 to "30 dias".tr(), 0 to "Sem prazo".tr()).forEach { (value, label) ->
                val active = days == value
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                    border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { days = value },
                ) {
                    Text(
                        label,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
