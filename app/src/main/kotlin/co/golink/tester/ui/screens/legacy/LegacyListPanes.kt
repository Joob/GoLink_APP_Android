package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.legacy.LegacyCrypto
import co.golink.tester.data.legacy.LegacyState
import co.golink.tester.domain.legacy.LegacyItem
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.screens.admin.AdminSegmented
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

private fun LegacyItem.isPassword(): Boolean = (meta.json["password"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.isNotEmpty() } == true

/** Quem recebe um item. "Todos os guardiões" quando são todos os guardiões (LegacyPeopleChips.vue). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleChips(legacy: LegacyState, recipientIds: List<String>) {
    val people = legacy.recipients.filter { it.dto.id in recipientIds }
    val emails = people.map { it.dto.email }
    val guardians = legacy.guardians
    val allGuardians = guardians.size > 1 && emails.size == guardians.size && guardians.all { it.dto.email in emails }
    when {
        people.isEmpty() -> Text(lt("nobody_yet"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        allGuardians -> LegacyChip(lt("all_guardians"), ChipTone.Neutral)
        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            people.forEach { LegacyChip(personName(it.meta, it.dto.email), ChipTone.Neutral) }
        }
    }
}

/** Items.vue (leitura). */
@Composable
fun LegacyItemsPane(legacy: LegacyState, onDialog: (LegacyDialog) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    fun matches(f: String, i: LegacyItem) = when (f) {
        "all" -> true
        "password" -> i.isPassword()
        else -> i.dto.type == f
    }
    val filters = listOf("all", "file", "folder", "note", "password")
    val rows = legacy.vaultItems.filter { matches(filter, it) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegacyHeader(lt("items"), lt("items_text")) {
            if (legacy.editable) {
                OutlinedButton(onClick = { onDialog(LegacyDialog.Note(null)) }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lt("write_note"))
                }
                Button(onClick = { onDialog(LegacyDialog.DrivePicker) }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lt("add_from_drive"))
                }
            }
        }
        AdminSegmented(
            options = filters.map { it to lt("filter_$it") },
            selected = filter,
            onSelect = { filter = it },
            counts = filters.associateWith { f -> legacy.vaultItems.count { matches(f, it) } },
        )
        if (rows.isEmpty()) {
            LegacyCard { Text(lt("no_items"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LegacyCard {
                rows.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegacyIconBox(
                                when {
                                    item.isPassword() -> Icons.Outlined.Key
                                    item.dto.type == "folder" -> Icons.Outlined.Folder
                                    item.dto.type == "note" -> Icons.Outlined.Description
                                    else -> Icons.Outlined.InsertDriveFile
                                },
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(itemName(item), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                Text(itemDetail(item), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (legacy.editable) RowActions(onEdit = { onDialog(LegacyDialog.ItemSettings(item)) }, onRemove = { onDialog(LegacyDialog.Remove(item)) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 42.dp, top = 6.dp)) {
                            Column(Modifier.weight(1f)) { PeopleChips(legacy, item.dto.recipient_ids) }
                            Text(releaseLabel(item, legacy.vault?.opened_at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun itemDetail(item: LegacyItem): String = when {
    item.dto.type == "note" -> lt("note_lines", "count" to (item.meta.str("lines") ?: (item.meta.json["lines"] as? JsonPrimitive)?.content ?: "1"))
    item.dto.type == "folder" -> "${lt("files_count", "count" to item.dto.file_count)} · ${formatSize(item.dto.bytes)}"
    item.dto.file_count == 0 -> lt("file_gone")
    else -> formatSize(item.dto.bytes)
}

/** Messages.vue (leitura). */
@Composable
fun LegacyMessagesPane(legacy: LegacyState, onDialog: (LegacyDialog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegacyHeader(lt("messages"), lt("messages_text")) {
            if (legacy.editable) {
                Button(onClick = { onDialog(LegacyDialog.Message(null)) }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lt("new_message"))
                }
            }
        }
        if (legacy.messages.isEmpty()) {
            LegacyCard { Text(lt("no_messages"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        legacy.messages.forEach { message ->
            LegacyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegacyIconBox(Icons.Outlined.MailOutline)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        message.meta.str("subject").orEmpty(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    LegacyChip(lt("message_${message.dto.state}"), if (message.dto.state == "sealed") ChipTone.Ok else ChipTone.Neutral)
                    if (legacy.editable) RowActions(onEdit = { onDialog(LegacyDialog.Message(message)) }, onRemove = { onDialog(LegacyDialog.Remove(message)) })
                }
                message.meta.str("preview")?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Column(Modifier.weight(1f)) { PeopleChips(legacy, message.dto.recipient_ids) }
                    Text(releaseLabel(message, legacy.vault?.opened_at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Guardians.vue. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegacyGuardiansPane(legacy: LegacyState, busy: Boolean, vm: LegacyViewModel, onDialog: (LegacyDialog) -> Unit) {
    val vault = legacy.vault ?: return
    val editable = legacy.editable
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegacyHeader(lt("guardians"), lt("guardians_text")) {
            if (editable) {
                Button(onClick = { onDialog(LegacyDialog.Person("guardian")) }, enabled = !busy, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lt("add_guardian"))
                }
            }
        }
        val total = legacy.guardians.size
        if (total > 1) {
            LegacyCard {
                CardTitle(lt("threshold_title", "count" to vault.threshold, "total" to total))
                CardText(lt("threshold_text"))
                Spacer(Modifier.size(10.dp))
                AdminSegmented(
                    options = (2..total).map { it to it.toString() },
                    selected = vault.threshold,
                    onSelect = { if (editable && !busy) vm.setThreshold(it) },
                )
            }
        }
        if (legacy.guardians.isEmpty()) {
            LegacyCard { Text(lt("no_guardians"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        legacy.guardians.forEach { g ->
            val name = personName(g.meta, g.dto.email)
            val keyState = LegacyCrypto.keyState(g)
            val pinned = LegacyCrypto.pinnedKey(g)
            val fingerprint = remember(pinned) { vm.fingerprint(pinned) }
            LegacyCard {
                Row(verticalAlignment = Alignment.Top) {
                    LegacyAvatar(name, g.dto.email, size = 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            LegacyChip(
                                lt("guardian_${g.dto.status}"),
                                when (g.dto.status) {
                                    "accepted" -> ChipTone.Ok
                                    "invited" -> ChipTone.Warn
                                    "declined" -> ChipTone.Danger
                                    else -> ChipTone.Neutral
                                },
                            )
                            if (g.dto.sealed) LegacyChip(lt("guardian_sealed"), ChipTone.Neutral)
                            if (keyState == "unverified") LegacyChip(lt("key_unverified"), ChipTone.Warn)
                            if (keyState == "changed") LegacyChip(lt("key_changed"), ChipTone.Danger)
                        }
                        val relation = g.meta.str("relation")?.takeIf { it.isNotBlank() }
                        Text(
                            listOfNotNull(relation, g.dto.email).joinToString(" · "),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        g.dto.confirmed_at?.let {
                            Text(lt("guardian_confirmed_on", "date" to shortDate(it)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (fingerprint.isNotEmpty()) {
                            Text("${lt("fingerprint")}: $fingerprint", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (editable) {
                        IconButton(onClick = { onDialog(LegacyDialog.RemoveGuardian(g)) }, enabled = !busy, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = lt("remove"), modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (editable) {
                    Row(Modifier.padding(start = 52.dp, top = 8.dp)) {
                        when {
                            keyState == "unverified" || keyState == "changed" ->
                                Button(onClick = { onDialog(LegacyDialog.Key(g)) }, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(lt("key_review")) }
                            keyState == "pinned" ->
                                OutlinedButton(onClick = { vm.reconfirm(g.dto.id) }, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(lt("reconfirm")) }
                            g.dto.status != "accepted" ->
                                OutlinedButton(onClick = { vm.reinvite(g.dto.id) }, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(lt("reinvite")) }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Text(lt("guardians_note"), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Recipients.vue. O código só aparece a pedido, como na web. */
@Composable
fun LegacyRecipientsPane(legacy: LegacyState, onToast: (LegacyToast) -> Unit, onDialog: (LegacyDialog) -> Unit) {
    var revealed by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegacyHeader(lt("recipients"), lt("recipients_text")) {
            if (legacy.editable) {
                Button(onClick = { onDialog(LegacyDialog.Person("recipient")) }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lt("add_recipient"))
                }
            }
        }
        if (legacy.recipients.isEmpty()) {
            LegacyCard { Text(lt("no_recipients"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        legacy.recipients.forEach { p ->
            val name = personName(p.meta, p.dto.email)
            val sealed = legacy.items.filter { it.dto.state == "sealed" && p.dto.id in it.dto.recipient_ids }
            val messages = sealed.count { it.dto.type == "message" }
            LegacyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegacyAvatar(name, p.dto.email, size = 36)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        val relation = p.meta.str("relation")?.takeIf { it.isNotBlank() }
                        Text(listOfNotNull(relation, p.dto.email).joinToString(" · "), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            lt("receives", "items" to sealed.size - messages, "messages" to messages),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (legacy.editable) RowActions(onEdit = { onDialog(LegacyDialog.Person("recipient", p)) }, onRemove = { onDialog(LegacyDialog.RemoveRecipient(p)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 48.dp, top = 6.dp)) {
                    Text(lt("col_code") + ": ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        p.code == null -> Text(lt("code_unreadable"), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        revealed == p.dto.id -> {
                            Text(p.code, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(p.code))
                                onToast(LegacyToast("copied"))
                            }) { Text(lt("copy"), fontSize = 12.sp) }
                        }
                        else -> TextButton(onClick = { revealed = p.dto.id }) { Text(lt("show_code"), fontSize = 12.sp) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Text(lt("recipients_note"), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowActions(onEdit: () -> Unit, onRemove: () -> Unit) {
    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Outlined.Edit, contentDescription = lt("edit"), modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Outlined.Delete, contentDescription = lt("remove"), modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
