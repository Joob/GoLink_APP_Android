package co.golink.tester.ui.screens.pass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.pass.PassCard as CardData
import co.golink.tester.data.pass.PassGenerator
import co.golink.tester.data.pass.PassHealth
import co.golink.tester.data.pass.PassItem
import co.golink.tester.data.pass.PassLogin
import co.golink.tester.data.pass.PassState
import co.golink.tester.data.pass.PassStrength
import co.golink.tester.data.pass.Totp
import co.golink.tester.ui.i18n.pt
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun PassItemForm(
    vm: PassViewModel,
    state: PassState,
    id: String?,
    prefill: PassPrefill?,
    onClose: () -> Unit,
    onSaved: (PassItem) -> Unit,
) {
    val p = LocalPassPalette.current
    val scope = rememberCoroutineScope()
    val original = remember(id) { id?.let { state.item(it) } }
    if (id != null && (original == null || original.locked)) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onClose() }
        return
    }

    var type by rememberSaveable { mutableStateOf(original?.type ?: prefill?.type ?: "login") }
    var name by rememberSaveable { mutableStateOf(original?.name ?: prefill?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(original?.login?.url ?: prefill?.url.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(original?.login?.username ?: prefill?.username.orEmpty()) }
    // Segredos fora do SavedState (Bundle); a rotação não recria a Activity (configChanges).
    var password by remember { mutableStateOf(original?.login?.password ?: prefill?.password.orEmpty()) }
    var totp by remember { mutableStateOf(original?.login?.totp ?: prefill?.totp.orEmpty()) }
    var holder by rememberSaveable { mutableStateOf(original?.card?.holder.orEmpty()) }
    var number by remember { mutableStateOf(original?.card?.number.orEmpty()) }
    var expiry by rememberSaveable { mutableStateOf(original?.card?.expiry.orEmpty()) }
    var cvv by remember { mutableStateOf(original?.card?.cvv.orEmpty()) }
    var pin by remember { mutableStateOf(original?.card?.pin.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(original?.notes.orEmpty()) }
    var folder by rememberSaveable { mutableStateOf(original?.folder?.takeIf { f -> state.folders.any { it.id == f } }) }
    var favourite by rememberSaveable { mutableStateOf(original?.favourite ?: false) }
    var showPw by rememberSaveable { mutableStateOf(prefill?.password?.isNotEmpty() == true) }
    var baseRevision by rememberSaveable { mutableStateOf(original?.revision) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var saving by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }

    // Impressões das passwords dos outros logins (aviso "já usas esta password").
    val otherPrints = remember(state.items) {
        state.active.filter { it.type == "login" && it.id != id && !it.login?.password.isNullOrEmpty() }.map { PassHealth.passwordPrint(it.login!!.password) }.toSet()
    }
    val reused = password.isNotEmpty() && PassHealth.passwordPrint(password) in otherPrints
    val score = if (password.isEmpty()) 0 else PassStrength.of(password).score

    fun fillNameFromUrl() {
        if (name.isBlank() && url.isNotBlank()) {
            val host = PassHealth.hostOf(url)
            if (host.isNotEmpty()) name = host.split('.').let { parts -> parts.getOrNull(parts.size - 2) }?.replaceFirstChar { it.uppercase() } ?: host
        }
    }

    fun onTotpDone() {
        val cfg = Totp.parse(totp) ?: return
        if (name.isBlank() && cfg.issuer.isNotEmpty()) name = cfg.issuer
        if (username.isBlank() && cfg.account.isNotEmpty()) username = cfg.account
    }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (type == "login") { fillNameFromUrl(); onTotpDone() }
        if (name.isBlank() && !(type == "card" && number.isNotBlank())) e["name"] = pt("err.name")
        if (type == "login" && totp.isNotBlank() && Totp.parse(totp) == null) e["totp"] = pt("totp_invalid")
        if (type == "card" && number.isNotBlank() && !Regex("^[\\d\\s-]{8,23}$").matches(number)) e["number"] = pt("err.number")
        errors = e
        return e.isEmpty()
    }

    fun save() {
        if (saving || !validate()) return
        saving = true
        // Parte do payload anterior para preservar campos que o Android não conhece.
        val base = original?.payload?.toMutableMap() ?: mutableMapOf()
        base["type"] = JsonPrimitive(type)
        base["name"] = JsonPrimitive(name.trim())
        base["folder"] = folder?.let(::JsonPrimitive) ?: JsonNull
        base["favourite"] = JsonPrimitive(favourite)
        base["notes"] = JsonPrimitive(notes)
        if (type == "login") base["login"] = PassLogin(url.trim(), username, password, totp.trim()).toJson(original?.payload?.get("login") as? JsonObject)
        else base.remove("login")
        if (type == "card") base["card"] = CardData(holder, number.trim(), expiry, cvv, pin).toJson(original?.payload?.get("card") as? JsonObject)
        else base.remove("card")
        scope.launch {
            val saved = vm.save(id, JsonObject(base as Map<String, JsonElement>), baseRevision)
            saving = false
            // Conflito (409): o item fresco já está no estado; a próxima tentativa baseia-se nele (como a web).
            if (saved != null) onSaved(saved) else if (id != null) vm.state.value.item(id)?.revision?.let { baseRevision = it }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PassBackBar(if (id != null) pt("edit_item") else pt("new_item"), onClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            PassSegmented(
                options = listOf("login" to pt("type.login"), "card" to pt("type.card"), "note" to pt("type.note")).filter { id == null || it.first == type },
                selected = type,
                onSelect = { type = it },
            )
            Spacer(Modifier.height(14.dp))
            FormField(if (type == "note") pt("f.title") else pt("f.name"), name, { name = it; errors = errors - "name" }, placeholder = when (type) { "card" -> pt("ph.card_name"); "note" -> pt("ph.note_title"); else -> pt("ph.name") }, error = errors["name"])
            when (type) {
                "login" -> {
                    FormField(pt("f.website"), url, { url = it }, placeholder = "https://", keyboard = KeyboardType.Uri, capitalize = false)
                    FormField(pt("f.username_email"), username, { username = it }, keyboard = KeyboardType.Email, capitalize = false)
                    Label(pt("f.password"))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showPw = !showPw }) { Icon(if (showPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, tint = p.muted) }
                                Row(
                                    Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp)).background(p.accentSoft)
                                        .clickable {
                                            password = PassGenerator.random(vm.prefs.genRandom)
                                            showPw = true
                                            // Copia logo e avisa (respeita o "limpar clipboard" do cofre).
                                            vm.copy(pt("f.password"), password)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Autorenew, null, tint = PassBlue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(pt("generate"), color = PassBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (password.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        StrengthBars(score)
                        Text(pt("strength." + PassStrength.SCORE_LABELS[score]), color = strengthColor(score), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (reused) {
                        Text(
                            pt("reused_warning"),
                            color = Color(0xFFB45309),
                            fontSize = 13.5.sp,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (p.dark) Color(0x2EF59E0B) else Color(0xFFFEF7E6)).padding(12.dp),
                        )
                    }
                    FormField(
                        "${pt("f.totp_key")} · ${pt("optional")}", totp, { totp = it; errors = errors - "totp" },
                        placeholder = pt("ph.totp"), keyboard = KeyboardType.Password, mono = true, capitalize = false, error = errors["totp"],
                    )
                    Totp.parse(totp)?.let { cfg ->
                        val now = rememberTotp(cfg, rememberNowMs())
                        if (now != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            TotpRing(now.remaining, now.period, 18.dp, showNumber = false)
                            Spacer(Modifier.width(6.dp))
                            Text("${pt("totp_preview")} ", fontSize = 12.5.sp, color = p.muted)
                            Text(Totp.format(now.code), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = p.text)
                        }
                    }
                }
                "card" -> {
                    FormField(pt("f.holder"), holder, { holder = it })
                    FormField(pt("f.number"), number, { number = it.take(23); errors = errors - "number" }, placeholder = "1234 5678 9012 3456", keyboard = KeyboardType.Number, mono = true, error = errors["number"])
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            FormField(pt("f.expiry"), expiry, { v ->
                                val d = v.filter { it.isDigit() }.take(4)
                                expiry = if (d.length > 2) "${d.take(2)}/${d.drop(2)}" else d
                            }, placeholder = "MM/YY", keyboard = KeyboardType.Number, mono = true)
                        }
                        Column(Modifier.weight(1f)) { FormField(pt("f.cvv"), cvv, { cvv = it.filter(Char::isDigit).take(4) }, keyboard = KeyboardType.NumberPassword, mono = true, secret = true) }
                        Column(Modifier.weight(1f)) { FormField(pt("f.pin"), pin, { pin = it.filter(Char::isDigit).take(12) }, keyboard = KeyboardType.NumberPassword, mono = true, secret = true) }
                    }
                }
            }
            FormField(if (type == "note") pt("f.note") else pt("f.notes"), notes, { notes = it.take(20000) }, minLines = if (type == "note") 8 else 3)

            PassSectionLabel(pt("folder"))
            PassChipRow {
                PassChip(pt("no_folder"), null, folder == null) { folder = null }
                state.folders.forEach { f -> PassChip(f.name, null, folder == f.id) { folder = f.id } }
                PassChip("+ ${pt("new_folder")}", null, false) { newFolder = true }
            }
            Spacer(Modifier.height(14.dp))
            PassCard {
                PassToggleRow(pt("favourite"), pt("favourite_sub"), checked = favourite) { favourite = it }
                // "Share with Family vault" fica escondido (como na web).
            }
            Text(pt("encrypted_before_upload"), fontSize = 12.sp, color = p.soft, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = ::save,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PassBlue),
        ) { Text(if (saving) pt("saving") else pt("save"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
    }

    if (newFolder) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text(pt("new_folder")) },
            text = { OutlinedTextField(folderName, { folderName = it.take(40) }, singleLine = true, placeholder = { Text(pt("folder_name")) }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = folderName.isNotBlank(), onClick = {
                    newFolder = false
                    scope.launch { vm.addFolder(folderName)?.let { folder = it.id } }
                }) { Text(pt("confirm")) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(pt("cancel")) } },
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PassBlue,
    unfocusedBorderColor = LocalPassPalette.current.line,
    focusedContainerColor = LocalPassPalette.current.card,
    unfocusedContainerColor = LocalPassPalette.current.card,
)

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalPassPalette.current.muted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 2.dp))
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    secret: Boolean = false,
    capitalize: Boolean = true,
    minLines: Int = 1,
    error: String? = null,
) {
    Label(label)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = minLines == 1,
        minLines = minLines,
        isError = error != null,
        placeholder = placeholder?.let { { Text(it, color = LocalPassPalette.current.soft) } },
        textStyle = TextStyle(fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, fontSize = 16.sp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (capitalize && keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            autoCorrectEnabled = !mono && capitalize,
        ),
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) Text(error, color = PassRose, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
}
