package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.teams.TeamInvitation
import co.golink.tester.ui.i18n.tr

private data class InviteEntry(val email: String, val permission: String)

/**
 * Criar / transformar em pasta de equipa (folha, como na web): convidar por
 * e-mail com "+", lista de membros ("Tu · Dono" e convidados com permissão).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTeamFolderDialog(
    isConvert: Boolean = false,
    onDismiss: () -> Unit,
    onCreate: (name: String, invitations: List<TeamInvitation>) -> Unit,
    onConvert: (invitations: List<TeamInvitation>) -> Unit,
    folderName: String = "",
    ownerName: String = "",
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val invitations = remember { mutableStateListOf<InviteEntry>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val emailValid = email.trim().let { it.contains('@') && it.substringAfter('@').contains('.') }

    fun addInvite() {
        val e = email.trim().lowercase()
        if (!emailValid || invitations.any { it.email == e }) return
        invitations += InviteEntry(e, "can-edit")
        email = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Column {
                Text(
                    (if (isConvert) "Transformar em pasta de equipa" else "Nova pasta de equipa").tr(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (isConvert) "“%s” passa para Pastas de equipa. Continuas a ser o dono.".tr().format(folderName)
                    else "Uma pasta partilhada onde todos podem trabalhar".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!isConvert) {
                Text("Nome da pasta".tr(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Convidar".tr()) }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" (" + "opcional".tr() + ")") }
                },
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("nome@exemplo.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addInvite() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (emailValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = emailValid) { addInvite() },
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Adicionar".tr(),
                        tint = if (emailValid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Membros: tu (dono) + convidados
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                MemberRow(initials(ownerName.ifBlank { "Tu" }), "Tu".tr(), trailing = { Text("Dono".tr(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                invitations.forEachIndexed { index, invite ->
                    MemberRow(
                        initials(invite.email),
                        invite.email,
                        dark = false,
                        trailing = {
                            val edit = invite.permission == "can-edit"
                            Text(
                                (if (edit) "Pode editar" else "Pode ver").tr(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { invitations[index] = invite.copy(permission = if (edit) "can-view" else "can-edit") }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(28.dp).clip(CircleShape).clickable { invitations.removeAt(index) },
                            ) { Icon(Icons.Filled.Close, contentDescription = "Remover".tr(), modifier = Modifier.size(15.dp)) }
                        },
                    )
                }
            }
            Text(
                "Podes convidar pessoas agora ou mais tarde.".tr(),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val mapped = invitations.map { TeamInvitation(it.email, it.permission) }
                    if (isConvert) onConvert(mapped) else onCreate(name.trim(), mapped)
                    onDismiss()
                },
                enabled = isConvert || name.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text((if (isConvert) "Transformar em pasta de equipa" else "Criar pasta de equipa").tr(), fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Cancelar".tr(),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss).padding(vertical = 10.dp),
            )
        }
    }
}

private fun initials(text: String): String =
    text.substringBefore('@').split(' ', '.', '_', '-').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
private fun MemberRow(initials: String, label: String, dark: Boolean = true, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        ) {
            Text(
                initials,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (dark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}
