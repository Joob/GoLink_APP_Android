package co.golink.tester.ui.screens.browse

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.encryption.E2EShareService
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.TeamMember
import co.golink.tester.domain.teams.TeamInvitation
import co.golink.tester.domain.teams.TeamMemberUpdate
import co.golink.tester.ui.components.MemberAvatar
import co.golink.tester.ui.components.dialogs.GrantE2EAccessDialog
import co.golink.tester.ui.i18n.tr

/** Linha editável: membro registado ou convite (pendente ou acabado de adicionar). */
private data class EditEntry(
    val key: String,
    val isMember: Boolean,
    val member: TeamMember,
    val permission: String,
    val isNew: Boolean = false,
)

/**
 * Editar pasta de equipa (como o popup "Edit Team Folder" da web): convidar por
 * e-mail, remover, mudar "Pode ver/Pode editar" e conceder acesso E2E. Envia a
 * lista completa (PATCH api/teams/folders/{id}); o que sai da lista é removido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTeamFolderSheet(
    folder: BrowseItem.Folder,
    ownerEmail: String,
    onDismiss: () -> Unit,
    // onDone(erro): null = sucesso (fecha); com erro fica aberto.
    onSave: (members: List<TeamMemberUpdate>, invitations: List<TeamInvitation>, onDone: (String?) -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val e2eUnlocked = remember { E2EShareService.get(context).isUnlocked }

    // O dono nunca aparece nem muda; vai no pedido tal como veio.
    val owners = remember(folder.id) { folder.members.filter { it.permission == "owner" || it.id == folder.ownerId } }
    val initial = remember(folder.id) {
        folder.members.filterNot { m -> owners.any { it.id == m.id } }
            .map { EditEntry("m:${it.id}", true, it, it.permission ?: "can-edit") } +
            folder.pendingInvites.map { EditEntry("i:${it.email.lowercase()}", false, it, it.permission ?: "can-edit") }
    }
    val entries = remember(folder.id) { mutableStateListOf<EditEntry>().apply { addAll(initial) } }

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var grantMember by remember { mutableStateOf<TeamMember?>(null) }

    val changed = entries.toList() != initial
    val canSave = !saving && entries.isNotEmpty() && (changed || email.isNotBlank())

    fun addMember(): Boolean {
        val e = email.trim().lowercase()
        if (e.isEmpty()) return true
        emailError = when {
            !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() -> "Escreve um e-mail válido".tr()
            e.equals(ownerEmail.trim(), ignoreCase = true) || owners.any { it.email.equals(e, true) } -> "Não te podes convidar a ti próprio".tr()
            entries.any { it.member.email.equals(e, ignoreCase = true) } -> "Esta pessoa já está na lista".tr()
            else -> null
        }
        if (emailError != null) return false
        entries.add(0, EditEntry("n:$e", false, TeamMember(id = "", email = e, name = null, avatarUrl = null, color = null, permission = "can-edit"), "can-edit", isNew = true))
        email = ""
        return true
    }

    grantMember?.let { m ->
        GrantE2EAccessDialog(member = m, folderId = folder.id, onDismiss = { grantMember = null })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            // Cabeçalho
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                ) {
                    Icon(Icons.Outlined.PersonAddAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "Editar pasta de equipa".tr(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss),
                ) { Icon(Icons.Filled.Close, contentDescription = "Fechar".tr(), modifier = Modifier.size(20.dp)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                // Pasta
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(
                            folder.itemCount?.let { "%d itens".tr().format(it) },
                            folder.filesize?.takeIf { it.isNotBlank() },
                            folder.createdAt?.takeIf { it.isNotBlank() },
                        ).joinToString(", ")
                        if (meta.isNotEmpty()) {
                            Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Adicionar membro
                Text("Adicionar membro".tr() + ":", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; emailError = null },
                    placeholder = { Text("Escreve o e-mail do membro…".tr()) },
                    singleLine = true,
                    isError = emailError != null,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addMember() }),
                    trailingIcon = if (email.isNotBlank()) {
                        {
                            Text(
                                "Adicionar".tr(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { addMember() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                emailError?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }

                // Membros
                Text("Os teus membros".tr() + ":", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                if (entries.isEmpty()) {
                    Text(
                        "Adiciona pelo menos um membro à pasta de equipa.".tr(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    entries.forEach { entry ->
                        EditMemberRow(
                            entry = entry,
                            onRemove = { entries.remove(entry) },
                            onTogglePermission = {
                                val i = entries.indexOf(entry)
                                if (i >= 0) entries[i] = entry.copy(permission = if (entry.permission == "can-edit") "can-view" else "can-edit")
                            },
                            onGrant = if (entry.isMember && e2eUnlocked) { { grantMember = entry.member } } else null,
                        )
                    }
                }
                error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar".tr(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) }
                Button(
                    onClick = {
                        if (!addMember()) return@Button
                        if (entries.isEmpty()) return@Button
                        saving = true
                        error = null
                        val members = owners.map { TeamMemberUpdate(it.id, "owner") } +
                            entries.filter { it.isMember }.map { TeamMemberUpdate(it.member.id, it.permission) }
                        val invitations = entries.filter { !it.isMember }.map { TeamInvitation(it.member.email, it.permission) }
                        onSave(members, invitations) { failure ->
                            saving = false
                            error = failure
                        }
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) {
                        androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Atualizar".tr(), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditMemberRow(
    entry: EditEntry,
    onRemove: () -> Unit,
    onTogglePermission: () -> Unit,
    onGrant: (() -> Unit)?,
) {
    val m = entry.member
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onRemove),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Remover".tr(), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.alpha(if (entry.isMember) 1f else 0.75f)) { MemberAvatar(member = m, size = 40.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (entry.isMember) m.name ?: m.email else m.email,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                entry.isMember -> m.email.takeIf { m.name != null }
                entry.isNew -> "Novo convite".tr()
                else -> "À espera que aceite o convite".tr()
            }
            sub?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        onGrant?.let {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .clickable(onClick = it),
            ) {
                Icon(Icons.Outlined.GppGood, contentDescription = "Conceder acesso de encriptação".tr(), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            }
        }
        val edit = entry.permission == "can-edit"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .clickable(onClick = onTogglePermission)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                (if (edit) "Pode editar" else "Pode ver").tr(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}
