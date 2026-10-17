package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.i18n.tr

/**
 * Pedir ficheiros (folha, como na web): subpasta opcional, mensagem, prazo para
 * aceitar ficheiros e aviso opcional por e-mail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFileRequestDialog(
    onDismiss: () -> Unit,
    folderName: String = "",
    onCreate: (name: String?, email: String?, notes: String?, expiresInDays: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var notify by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(7) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val emailOk = !notify || email.contains('@')

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Column {
                Text("Pedir ficheiros".tr(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Qualquer pessoa com o link pode enviar-te ficheiros, sem conta.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Label("Nome da subpasta".tr())
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Ex.: Faturas de outubro".tr()) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                buildAnnotatedString {
                    append("Vai para".tr() + " ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(listOf(folderName, name.trim().ifEmpty { "Pedido de [data]".tr() }).filter { it.isNotBlank() }.joinToString(" / "))
                    }
                },
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Label("Mensagem".tr())
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Ex.: Envia-me aqui as faturas do mês.".tr()) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Aceitar ficheiros durante".tr(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "1 dia".tr(), 7 to "7 dias".tr(), 30 to "30 dias".tr(), 0 to "Sem prazo".tr()).forEach { (value, label) ->
                    val active = days == value
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { days = value },
                    ) {
                        Text(
                            label,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { notify = !notify }.padding(top = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Avisar por e-mail".tr(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Envia o pedido a alguém".tr(), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(checked = notify, onCheckedChange = { notify = it })
            }
            if (notify) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("nome@exemplo.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onCreate(name.takeIf { it.isNotBlank() }, email.takeIf { notify && it.isNotBlank() }, notes.takeIf { it.isNotBlank() }, days)
                    onDismiss()
                },
                enabled = emailOk,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Criar link do pedido".tr(), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text) }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" (" + "opcional".tr() + ")") }
        },
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}
