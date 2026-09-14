package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.encryption.E2EShareService
import co.golink.tester.domain.browse.TeamMember
import co.golink.tester.ui.i18n.tr
import kotlinx.coroutines.launch

/**
 * Concede acesso E2E a um membro de team folder: mostra o FINGERPRINT do membro
 * para o dono confirmar (comparação out-of-band) e depois sela as data keys dos
 * ficheiros cifrados da pasta para ele.
 */
@Composable
fun GrantE2EAccessDialog(
    member: TeamMember,
    folderId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val service = remember { E2EShareService.get(context) }
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf("loading") } // loading, no_e2e, confirm, progress, done
    var fingerprint by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var okCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(member.id) {
        val rec = runCatching { service.recipientKey(member.id) }.getOrNull()
        if (rec == null) {
            phase = "no_e2e"
        } else {
            publicKey = rec.publicKey
            fingerprint = rec.fingerprint
            phase = "confirm"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conceder acesso de encriptação".tr()) },
        text = {
            when (phase) {
                "loading" -> Text("A carregar a chave do destinatário…".tr())
                "no_e2e" -> Text(
                    "%s ainda não configurou a encriptação, por isso não pode receber ficheiros cifrados.".tr()
                        .format(member.name ?: member.email),
                )
                "confirm" -> Column {
                    Text(
                        "Antes de conceder acesso, confirma que o fingerprint é igual ao mostrado no dispositivo do membro (compara fora da app).".tr(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("${member.name ?: ""} · ${member.email}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fingerprint, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Spacer(Modifier.width(4.dp))
                        Text("Confirmo que este fingerprint corresponde ao do membro.".tr(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                "progress" -> Column {
                    Text("A conceder acesso aos ficheiros cifrados…".tr(), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("$done / $total", style = MaterialTheme.typography.labelSmall)
                }
                "done" -> Text("Acesso concedido a %1\$d de %2\$d ficheiros.".tr().format(okCount, total))
            }
        },
        confirmButton = {
            if (phase == "confirm") {
                TextButton(
                    enabled = confirmed,
                    onClick = {
                        phase = "progress"
                        scope.launch {
                            val r = service.grantFolderAccess(folderId, member.id, publicKey) { d, t ->
                                done = d; total = t
                            }
                            okCount = r.ok
                            total = r.total
                            phase = "done"
                        }
                    },
                ) { Text("Conceder acesso".tr()) }
            } else {
                TextButton(onClick = onDismiss, enabled = phase != "progress") { Text("Fechar".tr()) }
            }
        },
    )
}
