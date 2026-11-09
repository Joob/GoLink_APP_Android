package co.golink.tester.ui.screens.legacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.legacy.LegacyCrypto
import co.golink.tester.data.legacy.LegacyState
import co.golink.tester.domain.legacy.LegacyItem
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.theme.LocalDarkTheme
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal fun LegacyItem.isPassword(): Boolean = (meta.json["password"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.isNotEmpty() } == true

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

private fun LegacyState.avatarsFor(ids: List<String>): List<Pair<String, String>> =
    recipients.filter { it.dto.id in ids }.map { personName(it.meta, it.dto.email) to it.dto.email }

/** Vault › Items. Tocar numa linha abre a folha de quem recebe. */
@Composable
fun LegacyItemsPane(legacy: LegacyState, onDialog: (LegacyDialog) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    fun matches(f: String, i: LegacyItem) = when (f) {
        "all" -> true
        "password" -> i.isPassword()
        else -> i.dto.type == f
    }
    val filters = listOf("all", "file", "folder", "note", "password")
    val counts = filters.associateWith { f -> legacy.vaultItems.count { matches(f, it) } }
    val rows = legacy.vaultItems.filter { matches(filter, it) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegacyFilterChips(
            options = filters.filter { it == "all" || it == filter || (counts[it] ?: 0) > 0 }.map { it to lt("filter_$it") },
            selected = filter,
            onSelect = { filter = it },
            counts = counts.filterKeys { it == "all" },
        )
        if (rows.isEmpty()) {
            LegacyCard { Text(lt("no_items"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LegacyCard(contentPadding = 6) {
                rows.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = legacy.editable) { onDialog(LegacyDialog.ItemSettings(item)) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        ItemIcon(item)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(itemName(item), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            Text(itemDetail(item), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        val people = legacy.avatarsFor(item.dto.recipient_ids)
                        if (people.isEmpty()) Text(lt("nobody_yet"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else StackedAvatars(people)
                    }
                }
            }
        }
    }
}

internal fun itemDetail(item: LegacyItem): String = when {
    item.dto.type == "note" -> lt("note_lines", "count" to (item.meta.str("lines") ?: (item.meta.json["lines"] as? JsonPrimitive)?.content ?: "1"))
    item.dto.type == "folder" -> "${lt("type_folder")} · ${lt("files_count", "count" to item.dto.file_count)} · ${formatSize(item.dto.bytes)}"
    item.dto.file_count == 0 -> lt("file_gone")
    else -> listOfNotNull(
        itemName(item).substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }?.uppercase(),
        formatSize(item.dto.bytes),
    ).joinToString(" · ")
}

/** Vault › Messages: cartas com barra lateral (verde selada, âmbar rascunho). */
@Composable
fun LegacyMessagesPane(legacy: LegacyState, onDialog: (LegacyDialog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (legacy.messages.isEmpty()) {
            LegacyCard { Text(lt("no_messages"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        legacy.messages.forEach { message ->
            val sealed = message.dto.state == "sealed"
            val bar = if (sealed) LegacyGreen else LegacyAmber
            LegacyCard(contentPadding = 0, onClick = if (legacy.editable) ({ onDialog(LegacyDialog.Message(message)) }) else null) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
                    Column(Modifier.weight(1f).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                message.meta.str("subject").orEmpty(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            StateChip(sealed)
                        }
                        message.meta.str("preview")?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                            val people = legacy.avatarsFor(message.dto.recipient_ids)
                            if (people.isNotEmpty()) {
                                StackedAvatars(people, size = 20)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                listOfNotNull(people.firstOrNull()?.first?.let { if (people.size > 1) "$it +${people.size - 1}" else it }, releaseLabel(message, legacy.vault?.opened_at).lowercase(legacyLocale()))
                                    .joinToString(" · "),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        InfoBox(Icons.Outlined.Lock, lt("sealed_note"))
    }
}

@Composable
private fun StateChip(sealed: Boolean) {
    val c = if (sealed) LegacyGreen else LegacyAmber
    val dark = LocalDarkTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.copy(alpha = if (dark) 0.2f else 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(if (sealed) Icons.Outlined.Lock else Icons.Outlined.Edit, contentDescription = null, tint = c, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(lt(if (sealed) "message_sealed" else "message_draft"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (dark) c else c.copy(red = c.red * 0.7f, green = c.green * 0.7f, blue = c.blue * 0.7f))
    }
}

/** Caixa roxa de aviso (cifra das mensagens). */
@Composable
fun InfoBox(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val p = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.copy(alpha = 0.08f)).padding(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = p, modifier = Modifier.size(15.dp).padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
    }
}

/** People › Guardians. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegacyGuardiansPane(legacy: LegacyState, busy: Boolean, vm: LegacyViewModel, onDialog: (LegacyDialog) -> Unit) {
    val vault = legacy.vault ?: return
    val editable = legacy.editable
    val total = legacy.guardians.size
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (total > 1) {
            LegacyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..total.coerceAtMost(5)).forEach { n ->
                            val on = n <= vault.threshold
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            ) {
                                Icon(Icons.Outlined.Shield, contentDescription = null, tint = if (on) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(lt("threshold_n_of", "count" to vault.threshold, "total" to total), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(lt("threshold_sub"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(lt("threshold_split", "count" to vault.threshold, "total" to total), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
                // Mínimo 2: com 1 bastava um guardião para abrir o cofre.
                LegacySegmented(
                    options = (2..total).map { it to lt("threshold_option", "count" to it, "total" to total) },
                    selected = vault.threshold,
                    onSelect = { if (editable && !busy && it != vault.threshold) vm.setThreshold(it) },
                )
            }
        }

        SectionLabel("${lt("your_guardians")} · $total")
        if (legacy.guardians.isEmpty()) {
            LegacyCard { Text(lt("no_guardians"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        legacy.guardians.forEachIndexed { index, g ->
            val name = personName(g.meta, g.dto.email)
            val keyState = LegacyCrypto.keyState(g)
            val pinned = LegacyCrypto.pinnedKey(g)
            val fingerprint = remember(pinned) { vm.fingerprint(pinned) }
            LegacyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegacyAvatar(name, g.dto.email, size = 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        val relation = g.meta.str("relation")?.takeIf { it.isNotBlank() }
                        Text(
                            listOfNotNull(relation, lt("key_part", "n" to (g.dto.slot ?: (index + 1)), "total" to total)).joinToString(" · "),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LegacyChip(
                        lt("guardian_${g.dto.status}"),
                        when (g.dto.status) {
                            "accepted" -> ChipTone.Ok
                            "invited" -> ChipTone.Warn
                            "declined" -> ChipTone.Danger
                            else -> ChipTone.Neutral
                        },
                    )
                }
                if (g.dto.sealed || keyState == "unverified" || keyState == "changed") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 52.dp, top = 6.dp)) {
                        if (g.dto.sealed) LegacyChip(lt("guardian_sealed"), ChipTone.Neutral)
                        if (keyState == "unverified") LegacyChip(lt("key_unverified"), ChipTone.Warn)
                        if (keyState == "changed") LegacyChip(lt("key_changed"), ChipTone.Danger)
                    }
                }
                if (fingerprint.isNotEmpty()) {
                    Text("${lt("fingerprint")}: $fingerprint", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 52.dp, top = 4.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val status = when {
                        g.dto.confirmed_at != null -> lt("guardian_confirmed_on", "date" to shortDate(g.dto.confirmed_at))
                        g.dto.invited_at != null -> lt("guardian_invited_on", "date" to shortDate(g.dto.invited_at))
                        else -> g.dto.email
                    }
                    Text(status, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (editable) {
                        TextButton(onClick = { onDialog(LegacyDialog.RemoveGuardian(g)) }, enabled = !busy) {
                            Text(lt("remove"), color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        when {
                            keyState == "unverified" || keyState == "changed" ->
                                FilledTonalButton(onClick = { onDialog(LegacyDialog.Key(g)) }, enabled = !busy, shape = RoundedCornerShape(10.dp)) { Text(lt("key_review"), fontSize = 13.sp) }
                            keyState == "pinned" ->
                                FilledTonalButton(onClick = { vm.reconfirm(g.dto.id) }, enabled = !busy, shape = RoundedCornerShape(10.dp)) { Text(lt("reconfirm"), fontSize = 13.sp) }
                            g.dto.status != "accepted" ->
                                FilledTonalButton(onClick = { vm.reinvite(g.dto.id) }, enabled = !busy, shape = RoundedCornerShape(10.dp)) { Text(lt("reinvite"), fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
        if (editable) {
            OutlinedButton(onClick = { onDialog(LegacyDialog.Person("guardian")) }, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(lt("add_guardian"))
            }
        }
        NoteLine(lt("guardians_note"))
    }
}

/** People › Recipients: lista expansível, um aberto de cada vez. Linhas para a LazyColumn do LegacyScreen. */
@Composable
fun legacyRecipientsContent(legacy: LegacyState, onToast: (LegacyToast) -> Unit, onDialog: (LegacyDialog) -> Unit): LazyListScope.() -> Unit {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var revealed by rememberSaveable { mutableStateOf<String?>(null) }
    val copySecret = rememberLegacyCopy()
    // Código à vista: fora de capturas, gravações e miniatura dos recentes.
    LegacySecureWhile(revealed != null)
    val guardianEmails = remember(legacy.guardians) { legacy.guardians.map { it.dto.email.lowercase() }.toSet() }
    return {
        item(key = "recipients_intro") {
            Text(lt("recipients_intro"), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (legacy.recipients.isEmpty()) {
            item(key = "recipients_empty") { LegacyCard { Text(lt("no_recipients"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        items(legacy.recipients, key = { "recipient_${it.dto.id}" }) { p ->
            RecipientCard(
                p,
                legacy,
                expanded = open == p.dto.id,
                revealed = revealed == p.dto.id,
                guardian = p.dto.email.lowercase() in guardianEmails,
                onToggle = { open = if (open == p.dto.id) null else p.dto.id },
                onReveal = { revealed = p.dto.id },
                onCopy = { code ->
                    copySecret(code)
                    onToast(LegacyToast("copied"))
                },
                onDialog = onDialog,
            )
        }
        if (legacy.editable) {
            item(key = "recipients_add") {
                OutlinedButton(onClick = { onDialog(LegacyDialog.Person("recipient")) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lt("add_recipient"))
                }
            }
        }
        item(key = "recipients_note") { NoteLine(lt("recipients_note")) }
    }
}

@Composable
private fun RecipientCard(
    p: co.golink.tester.domain.legacy.LegacyRecipient,
    legacy: LegacyState,
    expanded: Boolean,
    revealed: Boolean,
    guardian: Boolean,
    onToggle: () -> Unit,
    onReveal: () -> Unit,
    onCopy: (String) -> Unit,
    onDialog: (LegacyDialog) -> Unit,
) {
    val name = personName(p.meta, p.dto.email)
    val sealed = remember(legacy.items, p.dto.id) { legacy.items.filter { it.dto.state == "sealed" && p.dto.id in it.dto.recipient_ids } }
    LegacyCard(
        border = if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else null,
        contentPadding = 0,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp),
        ) {
            LegacyAvatar(name, p.dto.email, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    RoleTag(lt(if (guardian) "role_guardian" else "role_recipient"), guardian)
                }
                val relation = p.meta.str("relation")?.takeIf { it.isNotBlank() }
                Text(
                    listOfNotNull(relation, lt("gets_n", "count" to sealed.size)).joinToString(" · "),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(legacyMuted()).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    if (sealed.isEmpty()) Text(lt("receives_nothing"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    sealed.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            ItemIcon(item, size = 28)
                            Spacer(Modifier.width(10.dp))
                            Text(itemName(item), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Text(itemType(item), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Icon(Icons.Outlined.MailOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lt("delivered_by", "email" to p.dto.email), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(lt("col_code") + ": ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        p.code == null -> Text(lt("code_unreadable"), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        revealed -> {
                            val code = p.code
                            Text(code, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                            TextButton(onClick = { onCopy(code) }) { Text(lt("copy"), fontSize = 12.sp) }
                        }
                        else -> TextButton(onClick = onReveal) { Text(lt("show_code"), fontSize = 12.sp) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (legacy.editable) RowActions(onEdit = { onDialog(LegacyDialog.Person("recipient", p)) }, onRemove = { onDialog(LegacyDialog.RemoveRecipient(p)) })
                }
            }
        }
    }
}

internal fun itemType(item: LegacyItem): String = when {
    item.dto.type == "message" -> lt("type_message")
    item.isPassword() -> lt("filter_password")
    else -> lt("type_${item.dto.type}").takeIf { !it.startsWith("type_") } ?: lt("type_file")
}

@Composable
private fun RoleTag(text: String, guardian: Boolean) {
    val c = if (guardian) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text.uppercase(legacyLocale()),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        color = c,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun NoteLine(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
