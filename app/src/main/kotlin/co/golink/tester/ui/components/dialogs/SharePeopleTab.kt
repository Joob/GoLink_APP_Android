package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import co.golink.tester.data.encryption.E2EShareService
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.TeamMember
import co.golink.tester.domain.teams.TeamInvitation
import co.golink.tester.domain.teams.TeamMemberUpdate
import co.golink.tester.ui.components.MemberAvatar
import co.golink.tester.ui.i18n.tr

/**
 * Separador "Pessoas" da partilha (só pastas). Pasta normal: convidar converte-a
 * em pasta de equipa (`convert = true`). Pasta de equipa (raiz, dono): lista
 * completa como no PATCH api/teams/folders/{id} — o que sai da lista é removido.
 */
data class SharePeopleConfig(
    val folder: BrowseItem.Folder,
    val ownerName: String,
    val ownerEmail: String,
    val convert: Boolean,
    // onDone(erro): null = sucesso (fecha a partilha); com erro fica aberto.
    val onSave: (members: List<TeamMemberUpdate>, invitations: List<TeamInvitation>, onDone: (String?) -> Unit) -> Unit,
)

private data class PeopleEntry(
    val key: String,
    val isMember: Boolean,
    val member: TeamMember,
    val permission: String,
    val isNew: Boolean = false,
)

@Composable
internal fun SharePeopleTab(config: SharePeopleConfig, onDone: () -> Unit) {
    val folder = config.folder
    val context = LocalContext.current
    val e2eUnlocked = remember { E2EShareService.get(context).isUnlocked }

    // O dono nunca muda; vai no pedido tal como veio.
    val owners = remember(folder.id) {
        if (config.convert) emptyList()
        else folder.members.filter { it.permission == "owner" || it.id == folder.ownerId }
    }
    val initial = remember(folder.id) {
        if (config.convert) emptyList()
        else folder.members.filterNot { m -> owners.any { it.id == m.id } }
            .map { PeopleEntry("m:${it.id}", true, it, it.permission ?: "can-edit") } +
            folder.pendingInvites.map { PeopleEntry("i:${it.email.lowercase()}", false, it, it.permission ?: "can-edit") }
    }
    val entries = remember(folder.id) { mutableStateListOf<PeopleEntry>().apply { addAll(initial) } }

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var grantMember by remember { mutableStateOf<TeamMember?>(null) }

    fun addMember(): Boolean {
        val e = email.trim().lowercase()
        if (e.isEmpty()) return true
        emailError = when {
            !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() -> "Escreve um e-mail válido".tr()
            e.equals(config.ownerEmail.trim(), ignoreCase = true) || owners.any { it.email.equals(e, true) } -> "Não te podes convidar a ti próprio".tr()
            entries.any { it.member.email.equals(e, ignoreCase = true) } -> "Esta pessoa já está na lista".tr()
            else -> null
        }
        if (emailError != null) return false
        entries.add(0, PeopleEntry("n:$e", false, TeamMember(id = "", email = e, name = null, avatarUrl = null, color = null, permission = "can-edit"), "can-edit", isNew = true))
        email = ""
        return true
    }

    grantMember?.let { m ->
        GrantE2EAccessDialog(member = m, folderId = folder.id, onDismiss = { grantMember = null })
    }

    Column {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; emailError = null },
                    placeholder = { Text("nome@exemplo.com") },
                    singleLine = true,
                    isError = emailError != null,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addMember() }),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { addMember() },
                    enabled = email.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) { Text("Convidar".tr(), fontWeight = FontWeight.SemiBold) }
            }
            emailError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
            if (config.convert) {
                Text(
                    "Ao convidar alguém, esta pasta passa a pasta de equipa.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("Com acesso".tr())
            entries.forEach { entry ->
                PersonRow(
                    member = entry.member,
                    title = if (entry.isMember) entry.member.name ?: entry.member.email else entry.member.email,
                    subtitle = when {
                        entry.isMember -> entry.member.email.takeIf { entry.member.name != null }
                        entry.isNew -> "Novo convite".tr()
                        else -> "À espera que aceite o convite".tr()
                    },
                    dim = !entry.isMember,
                ) {
                    RoleMenu(
                        permission = entry.permission,
                        onPermission = { p ->
                            val i = entries.indexOf(entry)
                            if (i >= 0) entries[i] = entry.copy(permission = p)
                        },
                        onRemove = { entries.remove(entry) },
                        onGrant = if (entry.isMember && e2eUnlocked) { { grantMember = entry.member } } else null,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            // Dono (tu) no fim, fixo.
            PersonRow(
                member = owners.firstOrNull()
                    ?: TeamMember(id = "", email = config.ownerEmail, name = config.ownerName.ifBlank { null }, avatarUrl = null, color = null, permission = "owner"),
                title = "Tu".tr(),
                subtitle = "Dono".tr(),
                dim = false,
            ) {
                RoleChip("Dono".tr(), enabled = false) {}
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = {
                if (!addMember()) return@Button
                val changed = entries.toList() != initial
                if (!changed) { onDone(); return@Button }
                if (entries.isEmpty()) {
                    error = "Adiciona pelo menos um membro à pasta de equipa.".tr()
                    return@Button
                }
                saving = true
                error = null
                val members = owners.map { TeamMemberUpdate(it.id, "owner") } +
                    entries.filter { it.isMember }.map { TeamMemberUpdate(it.member.id, it.permission) }
                val invitations = entries.filter { !it.isMember }.map { TeamInvitation(it.member.email, it.permission) }
                config.onSave(members, invitations) { failure ->
                    saving = false
                    if (failure == null) onDone() else error = failure
                }
            },
            enabled = !saving,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 15.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("Concluído".tr(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PersonRow(
    member: TeamMember,
    title: String,
    subtitle: String?,
    dim: Boolean,
    trailing: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(Modifier.alpha(if (dim) 0.75f else 1f)) { MemberAvatar(member = member, size = 38.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun RoleChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RoleMenu(
    permission: String,
    onPermission: (String) -> Unit,
    onRemove: () -> Unit,
    onGrant: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoleChip((if (permission == "can-edit") "Pode editar" else "Pode ver").tr(), enabled = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Pode ver".tr()) }, onClick = { onPermission("can-view"); open = false })
            DropdownMenuItem(text = { Text("Pode editar".tr()) }, onClick = { onPermission("can-edit"); open = false })
            onGrant?.let { grant ->
                DropdownMenuItem(text = { Text("Conceder acesso de encriptação".tr()) }, onClick = { grant(); open = false })
            }
            DropdownMenuItem(
                text = { Text("Remover".tr(), color = MaterialTheme.colorScheme.error) },
                onClick = { onRemove(); open = false },
            )
        }
    }
}
