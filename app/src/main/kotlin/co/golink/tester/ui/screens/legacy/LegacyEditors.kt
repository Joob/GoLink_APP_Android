package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.golink.tester.data.legacy.LegacyRepository
import co.golink.tester.data.legacy.LegacyState
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.legacy.LegacyItem
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.screens.admin.AdminSegmented
import co.golink.tester.ui.screens.calendar.DrivePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Diálogos do Legacy (os popups da web: LegacyAddPopup, LegacyNotePopup, edição de itens e mensagens). */
sealed interface LegacyDialog {
    data object Add : LegacyDialog
    data object DrivePicker : LegacyDialog
    data class DrivePeople(val entries: List<LegacyRepository.DrivePick>) : LegacyDialog
    data class Note(val item: LegacyItem?) : LegacyDialog
    data class ItemSettings(val item: LegacyItem) : LegacyDialog
    data class Message(val item: LegacyItem?) : LegacyDialog
    data class Remove(val item: LegacyItem) : LegacyDialog
    data class Person(val kind: String, val person: co.golink.tester.domain.legacy.LegacyRecipient? = null) : LegacyDialog
    data class Key(val guardian: co.golink.tester.domain.legacy.LegacyGuardian) : LegacyDialog
    data class RemoveGuardian(val guardian: co.golink.tester.domain.legacy.LegacyGuardian) : LegacyDialog
    data class RemoveRecipient(val person: co.golink.tester.domain.legacy.LegacyRecipient) : LegacyDialog
}

@Composable
fun LegacyDialogs(
    dialog: LegacyDialog?,
    legacy: LegacyState,
    ui: LegacyUiState,
    vm: LegacyViewModel,
    onDialog: (LegacyDialog?) -> Unit,
    onOpenPane: (String) -> Unit,
) {
    val close = { onDialog(null) }
    when (dialog) {
        null -> Unit
        LegacyDialog.Add -> AddToVaultDialog(onDismiss = close) { key ->
            when (key) {
                "drive" -> onDialog(LegacyDialog.DrivePicker)
                "note" -> onDialog(LegacyDialog.Note(null))
                else -> {
                    onOpenPane("messages")
                    onDialog(LegacyDialog.Message(null))
                }
            }
        }
        LegacyDialog.DrivePicker -> DrivePickerDialog(
            exclude = emptySet(),
            load = vm::listDriveFolder,
            onDismiss = close,
            title = lt("add_from_drive"),
            confirmLabel = { n -> if (n == 0) lt("add") else lt("add_selected", "count" to n) },
            onPickItems = { picked ->
                val entries = picked.map {
                    when (it) {
                        is BrowseItem.Folder -> LegacyRepository.DrivePick(it.id, it.name, folder = true, mime = null, size = 0)
                        is BrowseItem.File -> LegacyRepository.DrivePick(it.id, it.name, folder = false, mime = it.mimetype, size = it.filesizeBytes)
                    }
                }
                onDialog(LegacyDialog.DrivePeople(entries))
            },
        )
        is LegacyDialog.DrivePeople -> DrivePeopleDialog(dialog.entries, legacy, onDismiss = close) { ids, delay ->
            close()
            vm.addDriveItems(dialog.entries, ids, delay)
        }
        is LegacyDialog.Note -> NoteDialog(dialog.item, legacy, ui.busy, vm, onDismiss = close)
        is LegacyDialog.ItemSettings -> ItemSettingsDialog(dialog.item, legacy, ui.busy, vm, onDismiss = close, onEditNote = { onDialog(LegacyDialog.Note(dialog.item)) }, onRemove = { onDialog(LegacyDialog.Remove(dialog.item)) })
        is LegacyDialog.Message -> MessageDialog(dialog.item, legacy, ui.busy, vm, onDismiss = close, onRemove = { onDialog(LegacyDialog.Remove(it)) })
        is LegacyDialog.Remove -> {
            val message = dialog.item.dto.type == "message"
            ConfirmDialog(
                title = lt("remove_item_title"),
                message = lt(if (message) "remove_message_text" else "remove_item_text", "name" to itemName(dialog.item)),
                confirmText = lt("remove"),
                destructive = true,
                onDismiss = close,
                onConfirm = { close(); vm.removeItem(dialog.item.dto.id) },
            )
        }
        is LegacyDialog.Person -> PersonDialog(dialog.kind, dialog.person, vm, onDismiss = close)
        is LegacyDialog.Key -> KeyDialog(dialog.guardian, vm, onDismiss = close)
        is LegacyDialog.RemoveGuardian -> ConfirmDialog(
            title = lt("remove_guardian_title"),
            message = lt("remove_guardian_text", "name" to personName(dialog.guardian.meta, dialog.guardian.dto.email)),
            confirmText = lt("remove"),
            destructive = true,
            onDismiss = close,
            onConfirm = { close(); vm.removeGuardian(dialog.guardian.dto.id) },
        )
        is LegacyDialog.RemoveRecipient -> {
            val id = dialog.person.dto.id
            val orphans = legacy.items.count { it.dto.state == "sealed" && it.dto.recipient_ids == listOf(id) }
            val text = lt("remove_recipient_text", "name" to personName(dialog.person.meta, dialog.person.dto.email))
            ConfirmDialog(
                title = lt("remove_recipient_title"),
                message = if (orphans > 0) "$text ${lt("remove_recipient_orphans", "count" to orphans)}" else text,
                confirmText = lt("remove"),
                destructive = true,
                onDismiss = close,
                onConfirm = { close(); vm.removeRecipient(id) },
            )
        }
    }

    // A cifrar as chaves dos ficheiros: não se fecha a meio.
    ui.adding?.let { (done, total) ->
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp).widthIn(min = 260.dp)) {
                    Text(lt("add_from_drive"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(lt("adding_files", "done" to done, "total" to total), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

// ---- peças comuns ----

/** Diálogo com cabeçalho, corpo com scroll e rodapé de botões (CalModal da web). */
@Composable
fun LegacyFormDialog(
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 600.dp).heightIn(max = maxHeight).imePadding(),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = lt("cancel")) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer,
                )
            }
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EncryptedNote() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(lt("encrypted_typing"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Libertação: logo na abertura ou 30 dias depois, com "não antes de" opcional.
 * [dateOption]: as mensagens mostram "Numa data" como 3.ª opção (= 30 dias + data mínima).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseFields(
    delay: Int,
    deliverOn: String,
    onDelay: (Int) -> Unit,
    onDeliverOn: (String) -> Unit,
    showDate: Boolean = true,
    dateOption: Boolean = false,
    label: String = lt("when"),
) {
    var picking by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(if (delay == 0) "opens" else if (dateOption && deliverOn.isNotEmpty()) "date" else "later") }
    SectionLabel(label)
    val options = buildList {
        add("opens" to lt("release_opens"))
        add("later" to lt("release_later"))
        if (dateOption) add("date" to lt("release_date"))
    }
    LegacySegmented(
        options = options,
        selected = mode,
        onSelect = {
            mode = it
            when (it) {
                "opens" -> onDelay(0)
                "later" -> { onDelay(30); if (dateOption) onDeliverOn("") }
                else -> { onDelay(30); if (deliverOn.isEmpty()) picking = true }
            }
        },
    )
    if (dateOption && mode == "date") {
        // Cartão da data, como na maqueta (calendário + Change).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            DateBadge(deliverOn)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (deliverOn.isEmpty()) lt("pick_date") else pickedDate(deliverOn), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(lt("release_date_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { picking = true }) { Text(lt("change"), fontWeight = FontWeight.SemiBold) }
        }
    } else if (!dateOption && showDate && delay > 0) {
        FieldLabel(lt("deliver_on"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(12.dp)) {
                Text(if (deliverOn.isEmpty()) "—" else pickedDate(deliverOn))
            }
            if (deliverOn.isNotEmpty()) {
                IconButton(onClick = { onDeliverOn("") }) { Icon(Icons.Outlined.Close, contentDescription = lt("cancel"), modifier = Modifier.size(16.dp)) }
            }
        }
    }
    if (picking) {
        val millis = deliverOn.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDeliverOn(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text(lt("save")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(lt("cancel")) } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

@Composable
private fun DateBadge(day: String) {
    val date = day.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(44.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
    ) {
        Text(
            date?.format(java.time.format.DateTimeFormatter.ofPattern("MMM", legacyLocale()))?.uppercase(legacyLocale()) ?: "—",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(vertical = 2.dp),
        )
        Text(date?.dayOfMonth?.toString() ?: "?", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 3.dp))
    }
}

private fun timing(delay: Int, deliverOn: String) =
    LegacyRepository.ItemTiming(delay, if (delay > 0 && deliverOn.isNotEmpty()) deliverOn else null)

// ---- diálogos ----

/** "Adicionar ao cofre": escolher a origem (folha inferior). */
@Composable
private fun AddToVaultDialog(onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    LegacySheet(onDismiss, header = { Text(lt("add_to_vault"), fontSize = 18.sp, fontWeight = FontWeight.Bold) }) {
        listOf<Pair<String, ImageVector>>("drive" to Icons.Outlined.Storage, "note" to Icons.Outlined.Edit, "message" to Icons.Outlined.MailOutline).forEach { (key, icon) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable { onChoose(key) }
                    .padding(12.dp),
            ) {
                LegacyIconBox(icon, size = 38)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(lt("add_$key"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(lt("add_${key}_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Para quem vão os itens do Drive. */
@Composable
private fun DrivePeopleDialog(
    entries: List<LegacyRepository.DrivePick>,
    legacy: LegacyState,
    onDismiss: () -> Unit,
    onSubmit: (List<String>, Int) -> Unit,
) {
    var people by remember { mutableStateOf(listOf<String>()) }
    var delay by remember { mutableStateOf(0) }
    LegacyFormDialog(
        lt("add_from_drive"),
        onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(lt("cancel")) }
            Button(onClick = { onSubmit(people, delay) }, enabled = people.isNotEmpty(), shape = RoundedCornerShape(12.dp)) { Text(lt("add")) }
        },
    ) {
        Text(lt("add_selected", "count" to entries.size), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(lt("who_receives"))
        PeopleChecklist(legacy, people) { people = it }
        ReleaseFields(delay, "", onDelay = { delay = it }, onDeliverOn = {}, showDate = false)
    }
}

/** LegacyNotePopup.vue: na edição o corpo vem do servidor, cifrado. */
@Composable
private fun NoteDialog(item: LegacyItem?, legacy: LegacyState, busy: Boolean, vm: LegacyViewModel, onDismiss: () -> Unit) {
    var name by remember(item) { mutableStateOf(item?.meta?.str("name").orEmpty()) }
    var body by remember(item) { mutableStateOf("") }
    var loading by remember(item) { mutableStateOf(item != null) }
    var people by remember(item) { mutableStateOf(listOf<String>()) }
    LaunchedEffect(item) {
        if (item == null) return@LaunchedEffect
        // Sem o corpo, gravar substituía a nota por um campo vazio: fecha.
        vm.readItem(item.dto.id) { payload ->
            if (payload == null) onDismiss() else {
                body = payload.string("body")
                loading = false
            }
        }
    }
    LegacyFormDialog(
        lt(if (item != null) "edit_note" else "write_note"),
        onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(lt("cancel")) }
            Button(
                onClick = { vm.saveNote(item, name, body, people, onDismiss) },
                enabled = !busy && !loading && name.isNotBlank() && body.isNotEmpty() && (item != null || people.isNotEmpty()),
                shape = RoundedCornerShape(12.dp),
            ) { Text(lt("save")) }
        },
    ) {
        OutlinedTextField(name, { if (it.length <= 120) name = it }, label = { Text(lt("note_title")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box {
            OutlinedTextField(body, { body = it }, label = { Text(lt("note_body")) }, enabled = !loading, minLines = 6, modifier = Modifier.fillMaxWidth())
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp).align(Alignment.Center))
        }
        if (item == null) {
            SectionLabel(lt("who_receives"))
            PeopleChecklist(legacy, people) { people = it }
        }
        EncryptedNote()
    }
}

/** Lista de pessoas com caixa à direita (folha do item e "Para" da mensagem). */
@Composable
fun PeopleChecklist(legacy: LegacyState, selected: List<String>, onChange: (List<String>) -> Unit) {
    if (legacy.recipients.isEmpty()) {
        Text(lt("no_recipients"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
    ) {
        legacy.recipients.forEachIndexed { i, p ->
            val on = p.dto.id in selected
            val name = personName(p.meta, p.dto.email)
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChange(if (on) selected - p.dto.id else selected + p.dto.id) }
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                LegacyAvatar(name, p.dto.email, size = 32)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(p.meta.str("relation")?.takeIf { it.isNotBlank() }, if (legacy.guardians.any { it.dto.email.equals(p.dto.email, true) }) lt("role_guardian").lowercase(legacyLocale()) else null)
                            .joinToString(" · ").ifEmpty { p.dto.email },
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(checked = on, onCheckedChange = { onChange(if (on) selected - p.dto.id else selected + p.dto.id) })
            }
        }
    }
}

/** Folha do item: quem recebe e quando (substitui o modal de Items.vue). */
@Composable
private fun ItemSettingsDialog(
    item: LegacyItem,
    legacy: LegacyState,
    busy: Boolean,
    vm: LegacyViewModel,
    onDismiss: () -> Unit,
    onEditNote: () -> Unit,
    onRemove: () -> Unit,
) {
    var people by remember(item) { mutableStateOf(item.dto.recipient_ids) }
    var delay by remember(item) { mutableStateOf(item.dto.delay_days) }
    var deliverOn by remember(item) { mutableStateOf(item.dto.deliver_at?.take(10).orEmpty()) }
    LegacySheet(
        onDismiss,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(item, size = 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(itemName(item), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(itemDetail(item), item.dto.created_at?.let { lt("added_on", "date" to shortDate(it)) }).joinToString(" · "),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        footer = {
            OutlinedButton(
                onClick = onRemove,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
            ) { Text(lt("remove"), fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = { vm.saveItemSettings(item, people, timing(delay, deliverOn), onDismiss) },
                enabled = !busy && people.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(2f),
            ) { Text(lt("done"), fontWeight = FontWeight.SemiBold) }
        },
    ) {
        SectionLabel("${lt("who_receives")} · ${lt("people_n", "count" to people.size)}")
        PeopleChecklist(legacy, people) { people = it }
        Spacer(Modifier.height(4.dp))
        ReleaseFields(delay, deliverOn, onDelay = { delay = it }, onDeliverOn = { deliverOn = it })
        if (item.dto.type == "note") {
            TextButton(onClick = onEditNote) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(lt("edit_note"))
            }
        }
    }
}

/** Editor de mensagem em ecrã inteiro (Messages.vue). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageDialog(editing: LegacyItem?, legacy: LegacyState, busy: Boolean, vm: LegacyViewModel, onDismiss: () -> Unit, onRemove: (LegacyItem) -> Unit) {
    val attachable = legacy.vaultItems
    var subject by remember(editing) { mutableStateOf(editing?.meta?.str("subject").orEmpty()) }
    var body by remember(editing) { mutableStateOf("") }
    val attached = remember(editing) { mutableStateListOf<String>() }
    var people by remember(editing) { mutableStateOf(editing?.dto?.recipient_ids ?: emptyList()) }
    var delay by remember(editing) { mutableStateOf(editing?.dto?.delay_days ?: 0) }
    var deliverOn by remember(editing) { mutableStateOf(editing?.dto?.deliver_at?.take(10).orEmpty()) }
    var loading by remember(editing) { mutableStateOf(editing != null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pickingPeople by remember { mutableStateOf(false) }
    fun formState() = listOf(subject, body, people.sorted(), attached.sorted(), delay, deliverOn).toString()
    var baseline by remember(editing) { mutableStateOf(formState()) }

    LaunchedEffect(editing) {
        if (editing == null) return@LaunchedEffect
        vm.readItem(editing.dto.id) { payload ->
            // Sem o corpo e os anexos, gravar substituía a mensagem por campos vazios.
            if (payload == null) onDismiss() else {
                body = payload.string("body")
                // Um item entretanto removido do cofre deixa de contar como anexo.
                val ids = (payload["attached"] as? JsonArray).orEmpty().mapNotNull { (it.jsonObject["id"] as? JsonPrimitive)?.content }
                attached.clear()
                attached.addAll(ids.filter { id -> attachable.any { it.dto.id == id } })
                baseline = formState()
                loading = false
            }
        }
    }

    val close = { if (!loading && formState() != baseline) confirmDiscard = true else onDismiss() }
    // O anexo mantém o calendário dele: não muda só por ir nesta mensagem.
    val offSchedule = attachable.filter { i ->
        i.dto.id in attached && (i.dto.delay_days != delay || (i.dto.deliver_at?.take(10).orEmpty()) != (if (delay > 0) deliverOn else ""))
    }.joinToString(", ") { itemName(it) }
    val save: (String) -> Unit = { itemState ->
        vm.saveMessage(editing, subject, body, attachable.filter { it.dto.id in attached }, people, timing(delay, deliverOn), itemState, onDismiss)
    }
    val chosen = legacy.recipients.filter { it.dto.id in people }
    val canSave = !busy && !loading && subject.isNotBlank() && body.isNotEmpty()

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // Topo: ‹ Message 🗑
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = lt("cancel")) }
                    Text(lt(if (editing != null) "message" else "new_message"), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (editing != null) {
                        IconButton(onClick = { onRemove(editing) }, enabled = !busy) {
                            Icon(Icons.Outlined.Delete, contentDescription = lt("remove"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (editing != null) {
                        val sealed = editing.dto.state == "sealed"
                        Text(
                            (if (sealed) "🔒 " else "✎ ") + lt("message_${editing.dto.state}"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (sealed) LegacyGreen else ChipToneColor.warn(),
                        )
                    }
                    LegacyCard(contentPadding = 0) {
                        // Para
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(lt("to"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                            if (chosen.isEmpty()) {
                                Text(lt("nobody_yet"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            } else {
                                StackedAvatars(chosen.map { personName(it.meta, it.dto.email) to it.dto.email }, size = 24)
                                Spacer(Modifier.width(6.dp))
                                val first = chosen.first()
                                Text(
                                    personName(first.meta, first.dto.email) + (if (chosen.size > 1) " +${chosen.size - 1}" else first.meta.str("relation")?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            TextButton(onClick = { pickingPeople = true }, enabled = !loading) { Text(lt("change"), fontWeight = FontWeight.SemiBold) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        // Assunto
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
                            Text(lt("subject"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                            PlainField(subject, { if (it.length <= 160) subject = it }, singleLine = true, bold = true, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(14.dp)) {
                            PlainField(body, { body = it }, enabled = !loading, placeholder = lt("body"), modifier = Modifier.fillMaxWidth())
                            if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp).align(Alignment.Center))
                        }
                    }

                    SectionLabel(lt("attached"))
                    if (attachable.isEmpty()) {
                        Text(lt("attach_empty"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            attachable.forEach { item ->
                                val on = item.dto.id in attached
                                Text(
                                    itemName(item),
                                    fontSize = 12.sp,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 220.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                                        .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                                        .clickable(enabled = !loading) { if (on) attached.remove(item.dto.id) else attached.add(item.dto.id) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                        Text(lt("attach_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (offSchedule.isNotEmpty()) {
                        Text(lt("attach_timing", "names" to offSchedule), fontSize = 12.sp, color = ChipToneColor.warn())
                    }

                    ReleaseFields(delay, deliverOn, onDelay = { delay = it }, onDeliverOn = { deliverOn = it }, dateOption = true, label = lt("deliver"))
                    Spacer(Modifier.height(4.dp))
                }
                // Rodapé fixo: selar + rascunho.
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Button(
                        onClick = { save("sealed") },
                        enabled = canSave && people.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lt("seal"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            lt("seal_hint", "names" to chosen.joinToString(", ") { personName(it.meta, it.dto.email) }.ifEmpty { "—" }),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { save("draft") }, enabled = canSave) { Text(lt("save_draft")) }
                    }
                }
            }
        }

        if (pickingPeople) {
            var picked by remember { mutableStateOf(people) }
            LegacySheet(
                onDismiss = { pickingPeople = false },
                header = { Text(lt("who_receives"), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                footer = {
                    Button(onClick = { people = picked; pickingPeople = false }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text(lt("done")) }
                },
            ) { PeopleChecklist(legacy, picked) { picked = it } }
        }
        if (confirmDiscard) {
            ConfirmDialog(
                title = lt("discard_message"),
                message = "",
                confirmText = lt("discard"),
                destructive = true,
                onDismiss = { confirmDiscard = false },
                onConfirm = { confirmDiscard = false; onDismiss() },
            )
        }
    }
}

/** Campo sem moldura (assunto e corpo da carta). */
@Composable
private fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    bold: Boolean = false,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    val color = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = if (singleLine) 14.sp else 15.sp, lineHeight = 22.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = color),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                inner()
            }
        },
    )
}

private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()

private object ChipToneColor {
    @Composable
    fun warn() = if (co.golink.tester.ui.theme.LocalDarkTheme.current) androidx.compose.ui.graphics.Color(0xFFFBBF24) else androidx.compose.ui.graphics.Color(0xFFB45309)
}
