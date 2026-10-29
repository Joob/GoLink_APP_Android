package co.golink.tester.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.calendar.CalInfo
import co.golink.tester.domain.calendar.CalendarConst
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.tr

/**
 * Definições de um calendário (CalendarSettingsPopup.vue). [calendar] null →
 * novo calendário. Só o dono renomeia, muda a cor, partilha e apaga; os
 * membros só podem sair.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsSheet(
    calendar: CalInfo?,
    myUserId: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, color: String) -> Unit,
    onUpdate: (name: String?, color: String?) -> Unit,
    onShare: (email: String, permission: String) -> Unit,
    onRemoveMember: (userId: String) -> Unit,
    onDelete: () -> Unit,
) {
    val owner = calendar == null || calendar.isOwner
    var name by remember(calendar?.id) { mutableStateOf(calendar?.name?.takeIf { !calendar.locked }.orEmpty()) }
    var color by remember(calendar?.id) { mutableStateOf(calendar?.color ?: "green") }
    var email by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("can-view") }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
        ) {
            Text(
                if (calendar == null) "Novo calendário".tr() else "Definições do calendário".tr(),
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))

            if (owner && calendar?.locked != true) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    singleLine = true,
                    label = { Text("Nome".tr()) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Cor".tr(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalendarConst.COLORS.forEach { key ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(calendarColor(key)).clickable { color = key },
                        ) {
                            if (key == color) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                val changed = calendar == null || name.trim() != calendar.name || color != calendar.color
                Button(
                    onClick = {
                        if (calendar == null) onCreate(name.trim(), color)
                        else onUpdate(name.trim().takeIf { it != calendar.name }, color.takeIf { it != calendar.color })
                    },
                    enabled = !busy && name.isNotBlank() && changed,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) { Text(if (calendar == null) "Criar".tr() else "Guardar".tr(), fontWeight = FontWeight.SemiBold) }
            } else if (calendar != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(calendarColor(calendar.color)))
                    Spacer(Modifier.width(10.dp))
                    Text(calendar.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (calendar != null) {
                // Partilhar (só o dono; a K_cal é selada à chave pública do outro).
                if (owner && !calendar.locked) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 16.dp))
                    Text("Partilhar calendário".tr(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Só utilizadores registados com encriptação ativa.".tr(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        singleLine = true,
                        placeholder = { Text("nome@exemplo.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        options = listOf("can-view" to "Pode ver".tr(), "can-edit" to "Pode editar".tr()),
                        selected = permission,
                        onSelect = { permission = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onShare(email.trim(), permission); email = "" },
                        enabled = !busy && email.contains('@'),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Partilhar".tr()) }
                }

                if (calendar.members.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 16.dp))
                    calendar.members.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            ) {
                                Text((m.name ?: m.email ?: "?").take(1).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name ?: m.email.orEmpty(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when (m.permission) {
                                        "owner" -> "Dono"
                                        "can-edit" -> "Pode editar"
                                        else -> "Pode ver"
                                    }.tr(),
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Dono remove os outros; o dono não sai.
                            if (owner && m.permission != "owner" && m.user_id != myUserId) {
                                IconButton(onClick = { onRemoveMember(m.user_id) }, enabled = !busy) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remover".tr(), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(46.dp).border(0.dp, Color.Transparent),
                ) { Text(if (owner) "Apagar calendário".tr() else "Sair do calendário".tr(), fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (confirmDelete && calendar != null) {
        ConfirmDialog(
            title = if (owner) "Apagar calendário".tr() else "Sair do calendário".tr(),
            message = if (owner) "Apagar o calendário e todos os seus eventos?".tr() else calendar.name,
            confirmText = if (owner) "Apagar".tr() else "Sair".tr(),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; onDelete() },
        )
    }
}

fun countryName(code: String): String =
    java.util.Locale("", code).getDisplayCountry(co.golink.tester.ui.common.appLocale()).ifBlank { code }

/** País dos feriados (HolidaysPopup.vue): lista do servidor, a mesma da web. null = automático (país da conta). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysSheet(selected: String?, countries: List<String>, busy: Boolean, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text("País dos feriados".tr(), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Procurar país…".tr()) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            val named = remember(countries, co.golink.tester.ui.i18n.I18n.lang) {
                countries.map { it to countryName(it) }.sortedBy { it.second }
            }
            val q = query.trim()
            val options = (if (q.isEmpty()) listOf<Pair<String?, String>>(null to "Automático (país da conta)".tr()) else emptyList()) +
                named.filter { q.isEmpty() || it.second.contains(q, ignoreCase = true) || it.first.equals(q, ignoreCase = true) }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                items(options.size) { i ->
                    val (code, label) = options[i]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onPick(code) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (code == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
