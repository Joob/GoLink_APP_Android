package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.golink.tester.domain.teams.TeamInvitation

private data class InviteEntry(val email: String, val permission: String)

@Composable
fun CreateTeamFolderDialog(
    isConvert: Boolean = false,
    onDismiss: () -> Unit,
    onCreate: (name: String, invitations: List<TeamInvitation>) -> Unit,
    onConvert: (invitations: List<TeamInvitation>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val invitations = remember { mutableStateListOf<InviteEntry>() }

    val title = if (isConvert) "Converter em pasta de equipa" else "Criar pasta de equipa"
    val confirmText = if (isConvert) "Converter" else "Criar"

    val canConfirm = (isConvert || name.isNotBlank()) &&
        invitations.all { it.email.isNotBlank() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!isConvert) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nome da pasta") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        "Membros (opcional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )

                    invitations.forEachIndexed { index, entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = entry.email,
                                onValueChange = { invitations[index] = entry.copy(email = it) },
                                label = { Text("Email") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            var permMenuOpen by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { permMenuOpen = true },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        if (entry.permission == "can-edit") "Editar" else "Ver",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                DropdownMenu(
                                    expanded = permMenuOpen,
                                    onDismissRequest = { permMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Visualizar") },
                                        onClick = {
                                            invitations[index] = entry.copy(permission = "can-view")
                                            permMenuOpen = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Editar") },
                                        onClick = {
                                            invitations[index] = entry.copy(permission = "can-edit")
                                            permMenuOpen = false
                                        },
                                    )
                                }
                            }
                            IconButton(onClick = { invitations.removeAt(index) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Remover",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { invitations.add(InviteEntry(email = "", permission = "can-view")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Adicionar membro")
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canConfirm,
                        onClick = {
                            val mapped = invitations.map { TeamInvitation(it.email.trim(), it.permission) }
                            if (isConvert) onConvert(mapped)
                            else onCreate(name.trim(), mapped)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(confirmText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
