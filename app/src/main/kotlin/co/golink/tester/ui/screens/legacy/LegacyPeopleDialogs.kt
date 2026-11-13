package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.legacy.LegacyCrypto
import co.golink.tester.domain.legacy.LegacyGuardian
import co.golink.tester.domain.legacy.LegacyRecipient
import co.golink.tester.ui.i18n.lt

/** LegacyPersonPopup.vue: adicionar guardião ou recipient, ou editar um recipient. */
@Composable
fun PersonDialog(kind: String, person: LegacyRecipient?, vm: LegacyViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(person?.meta?.str("name").orEmpty()) }
    var relation by remember { mutableStateOf(person?.meta?.str("relation").orEmpty()) }
    var email by remember { mutableStateOf(person?.dto?.email.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val title = when {
        person != null -> lt("edit_recipient")
        kind == "guardian" -> lt("add_guardian")
        else -> lt("add_recipient")
    }
    LegacyFormDialog(
        title = title,
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(lt("cancel")) }
            Button(
                enabled = valid && !saving,
                onClick = {
                    saving = true
                    error = null
                    vm.savePerson(
                        kind, person, email.trim(), name.trim(), relation.trim(),
                        onError = { error = lt(it); saving = false },
                        onDone = onDismiss,
                    )
                },
            ) { Text(lt(if (person != null) "save" else "add")) }
        },
    ) {
        OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text(lt("name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            relation,
            { if (it.length <= 40) relation = it },
            label = { Text(lt("relation")) },
            placeholder = { Text(lt("relation_hint")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            email,
            { if (it.length <= 255) email = it },
            label = { Text(lt("email")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        if (kind == "guardian" && person == null) {
            Text(lt("guardian_account_note"), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * LegacyKeyPopup.vue. A comparação tem de acontecer fora do GoLink: é a única coisa que o
 * servidor não consegue falsificar. Até ser confirmada, a parte não é selada a esta chave.
 */
@Composable
fun KeyDialog(guardian: LegacyGuardian, vm: LegacyViewModel, onDismiss: () -> Unit) {
    // A chave mostrada é a que se fixa, mesmo que o estado mude com o diálogo aberto.
    val publicKey = remember { guardian.dto.public_key }
    val changed = LegacyCrypto.keyState(guardian) == "changed"
    val name = personName(guardian.meta, guardian.dto.email)
    val serverPrint = remember(publicKey) { vm.fingerprint(publicKey) }
    val pinnedPrint = remember { vm.fingerprint(LegacyCrypto.pinnedKey(guardian)) }
    var checked by remember { mutableStateOf(false) }
    var moved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LegacyFormDialog(
        title = lt("key_title", "name" to name),
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(lt("cancel")) }
            Button(
                enabled = checked && !saving && serverPrint.isNotEmpty() && publicKey != null,
                onClick = {
                    saving = true
                    vm.pinKey(guardian.dto.id, publicKey!!, onMoved = { moved = true; saving = false }, onDone = onDismiss)
                },
            ) { Text(lt("key_confirm")) }
        },
    ) {
        Text(
            lt(if (changed) "key_changed_text" else "key_new_text", "name" to name),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (changed) {
            Column {
                FieldLabel(lt("key_current"))
                Text(pinnedPrint, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column {
            FieldLabel(lt(if (changed) "key_new" else "fingerprint"))
            Text(serverPrint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().clickable { checked = !checked }) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Text(lt("key_confirm_check", "name" to name), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp))
        }
        if (moved) Text(lt("key_moved"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
    }
}
