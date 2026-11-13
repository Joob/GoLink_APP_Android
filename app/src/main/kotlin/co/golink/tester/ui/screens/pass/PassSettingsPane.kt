package co.golink.tester.ui.screens.pass

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.pass.PassDraft
import co.golink.tester.data.pass.PassFolder
import co.golink.tester.data.pass.PassState
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.pt
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

private data class Source(val id: String, val name: String, val letter: String, val color: Color)

private val SOURCES = listOf(
    Source("chrome", "Chrome", "C", Color(0xFF2563EB)),
    Source("firefox", "Firefox", "F", Color(0xFFEA580C)),
    Source("bitwarden", "Bitwarden", "B", Color(0xFF1E40AF)),
    Source("1password", "1Password", "1", Color(0xFF0F766E)),
    Source("lastpass", "LastPass", "L", Color(0xFFDC2626)),
    Source("csv", "CSV", "⇪", Color(0xFF64748B)),
)

@Composable
fun PassSettingsPane(vm: PassViewModel, state: PassState, bioAvailable: Boolean, onBack: () -> Unit) {
    val p = LocalPassPalette.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val bioEnabled by vm.bioEnabled.collectAsStateWithLifecycle()
    val blockScreens by vm.blockScreens.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val s = state.settings

    var source by remember { mutableStateOf("csv") }
    var preview by remember { mutableStateOf<List<PassDraft>?>(null) }
    var filePassword by remember { mutableStateOf<JsonObject?>(null) }
    var exportAsk by remember { mutableStateOf(false) }
    var folderEdit by remember { mutableStateOf<PassFolder?>(null) }
    var folderDelete by remember { mutableStateOf<PassFolder?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            when (val r = vm.readImport(uri, source)) {
                is PassImportFile.Ready -> preview = r.drafts
                is PassImportFile.NeedsPassword -> filePassword = r.json
                null -> Unit
            }
        }
    }
    // A password do ficheiro fica no ViewModel (prepareExport) para sobreviver à recriação da atividade.
    val createExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        vm.exportEncrypted(uri)
    }

    Column(Modifier.fillMaxSize()) {
        PassBackBar(pt("settings_title"), onBack)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            PassAutofillCard(onLeave = vm::expectExternal)
            PassSectionLabel(pt("unlock_section"))
            PassCard {
                PassToggleRow(
                    pt("biometric"),
                    if (bioAvailable) pt("biometric_sub") else pt("biometric_none"),
                    checked = bioEnabled && bioAvailable,
                    enabled = bioAvailable,
                ) { on ->
                    if (!on) vm.disableBiometric()
                    else {
                        val cipher = vm.bio.encryptCipher()
                        if (cipher == null || activity == null) vm.toast(pt("error"))
                        else activity.passBiometricPrompt(cipher, pt("biometric_prompt"), onOk = { c -> vm.enableBiometric(c) })
                    }
                }
                PassDivider()
                PassToggleRow(pt("block_screens"), pt("block_screens_sub"), checked = blockScreens) { vm.setBlockScreens(it) }
                PassDivider()
                PassToggleRow(pt("reprompt_short"), pt("reprompt_sub"), checked = s.reprompt) { vm.saveSettings(s.copy(reprompt = it)) }
            }

            PassSectionLabel(pt("lock_after_short"))
            // Mesmas opções da web (0 = nunca).
            PassSegmented(
                options = listOf(1, 5, 15, 0).map { it.toString() to if (it == 0) pt("never") else pt("n_min", "n" to it) },
                selected = s.lockAfter.toString(),
                onSelect = { vm.saveSettings(s.copy(lockAfter = it.toInt())) },
            )

            PassSectionLabel(pt("clear_after_short"))
            PassSegmented(
                options = listOf(30, 60, 0).map { it.toString() to when (it) { 30 -> "30 s"; 60 -> pt("n_min", "n" to 1); else -> pt("never") } },
                selected = s.clipboardClear.toString(),
                onSelect = { vm.saveSettings(s.copy(clipboardClear = it.toInt())) },
            )

            PassSectionLabel(pt("import_from"))
            PassCard {
                SOURCES.forEachIndexed { i, src ->
                    if (i > 0) PassDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            source = src.id
                            vm.expectExternal()
                            pickFile.launch(arrayOf("text/*", "application/json", "application/octet-stream", "text/comma-separated-values"))
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(src.color), contentAlignment = Alignment.Center) {
                            if (src.id == "csv") Icon(Icons.Outlined.FileUpload, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            else Text(src.letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(if (src.id == "csv") pt("csv_file") else src.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.weight(1f))
                        Text(pt("import"), color = PassBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
            Text(pt("import_hint.$source"), fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))

            PassSectionLabel(pt("export"))
            PassCard {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { exportAsk = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(pt("export_encrypted"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                        Text(pt("export_sub"), fontSize = 12.5.sp, color = p.muted)
                    }
                    Text(pt("export"), color = PassBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            PassSectionLabel(pt("folders"))
            PassCard {
                state.folders.forEachIndexed { i, f ->
                    if (i > 0) PassDivider()
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, fontSize = 15.sp, color = p.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { folderEdit = f }) { Icon(Icons.Outlined.Edit, pt("rename"), tint = p.muted) }
                        IconButton(onClick = { folderDelete = f }) { Icon(Icons.Outlined.DeleteOutline, pt("delete_folder"), tint = p.muted) }
                    }
                }
                if (state.folders.isEmpty()) Text(pt("nothing_here"), color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            Text(pt("folders_sub"), fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
    }

    preview?.let { drafts ->
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text(pt("import_preview", "n" to drafts.size)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    drafts.take(6).forEach { d ->
                        Text(d.payload["name"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (drafts.size > 6) Text(pt("and_more", "n" to drafts.size - 6), fontSize = 13.sp, color = p.muted)
                    Spacer(Modifier.height(6.dp))
                    Text(pt("delete_export_after"), fontSize = 12.5.sp, color = PassAmber)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { scope.launch { vm.import(drafts); preview = null } }) {
                    Text(if (busy) pt("importing") else pt("import_n", "n" to drafts.size))
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text(pt("cancel")) } },
        )
    }

    filePassword?.let { json ->
        var pw by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        var working by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { filePassword = null },
            title = { Text(pt("file_password_title")) },
            text = {
                Column {
                    Text(pt("file_password_text"), fontSize = 14.sp)
                    OutlinedTextField(pw, { pw = it; wrong = false }, singleLine = true, isError = wrong, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    if (wrong) Text(pt("file_password_wrong"), color = PassRose, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = pw.isNotEmpty() && !working, onClick = {
                    working = true
                    scope.launch {
                        val drafts = vm.decryptImport(json, pw)
                        working = false
                        if (drafts == null) wrong = true
                        else { filePassword = null; if (drafts.isEmpty()) vm.toast(pt("import_empty")) else preview = drafts }
                    }
                }) { Text(if (working) pt("unlocking") else pt("confirm")) }
            },
            dismissButton = { TextButton(onClick = { filePassword = null }) { Text(pt("cancel")) } },
        )
    }

    if (exportAsk) {
        var a by remember { mutableStateOf("") }
        var b by remember { mutableStateOf("") }
        var secret by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        var checking by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!checking) exportAsk = false },
            title = { Text(pt("export_title")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pt("export_encrypted_text"), fontSize = 14.sp)
                    OutlinedTextField(a, { a = it }, singleLine = true, placeholder = { Text(pt("export_password")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(b, { b = it }, singleLine = true, placeholder = { Text(pt("export_password_again")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    // Como na web: exportar o cofre inteiro pede sempre a passphrase E2E atual.
                    Text(pt("export_confirm"), fontSize = 12.5.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                    OutlinedTextField(
                        secret, { secret = it; wrong = false }, singleLine = true, isError = wrong,
                        placeholder = { Text(pt("unlock_placeholder")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    if (wrong) Text(pt("unlock_wrong"), color = PassRose, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = a.length >= 8 && a == b && secret.isNotEmpty() && !checking, onClick = {
                    checking = true
                    scope.launch {
                        val ok = vm.verifySecret(secret)
                        checking = false
                        if (!ok) { wrong = true; return@launch }
                        secret = ""
                        exportAsk = false
                        vm.prepareExport(a)
                        vm.expectExternal()
                        createExport.launch("golink-pass-${java.time.LocalDate.now()}.json")
                    }
                }) { Text(if (checking) pt("unlocking") else pt("export")) }
            },
            dismissButton = { TextButton(onClick = { exportAsk = false }) { Text(pt("cancel")) } },
        )
    }

    folderEdit?.let { f ->
        var name by remember(f.id) { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { folderEdit = null },
            title = { Text(pt("rename")) },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { folderEdit = null; vm.renameFolder(f.id, name) }) { Text(pt("confirm")) } },
            dismissButton = { TextButton(onClick = { folderEdit = null }) { Text(pt("cancel")) } },
        )
    }

    folderDelete?.let { f ->
        ConfirmDialog(
            title = pt("delete_folder"),
            message = pt("folders_sub"),
            confirmText = pt("delete_folder"),
            destructive = true,
            onDismiss = { folderDelete = null },
            onConfirm = { folderDelete = null; vm.deleteFolder(f.id) },
        )
    }
}

@Composable
fun PassTrashPane(vm: PassViewModel, state: PassState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val p = LocalPassPalette.current
    var confirmEmpty by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PassBackBar(pt("trash"), onBack) {
            if (state.trash.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text(pt("empty_trash"), color = PassRose) }
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(pt("trash_hint", "days" to state.trashDays), fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            PassCard {
                if (state.trash.isEmpty()) Text(pt("trash_empty"), color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(18.dp))
                state.trash.sortedByDescending { it.deletedAt }.forEachIndexed { i, item ->
                    if (i > 0) PassDivider()
                    PassItemRow(item, displayName(item), itemSubtitle(item), null, onClick = { onOpen(item.id) }, onCopy = null)
                }
            }
        }
    }
    if (confirmEmpty) {
        PassStepUpDialog(op = PassDeleteOp.EMPTY, vm = vm, onDismiss = { confirmEmpty = false })
    }
}

/** Cartão "Serviço de preenchimento": estado + atalho para as definições do sistema. */
@Composable
private fun PassAutofillCard(onLeave: () -> Unit) {
    val p = LocalPassPalette.current
    val context = LocalContext.current
    val afm = remember { context.getSystemService(android.view.autofill.AutofillManager::class.java) }
    if (afm == null || !afm.isAutofillSupported) return
    var enabled by remember { mutableStateOf(afm.hasEnabledAutofillServices()) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) enabled = afm.hasEnabledAutofillServices()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val open: () -> Unit = {
        onLeave()
        val req = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:" + context.packageName))
        runCatching { context.startActivity(req) }.onFailure {
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }
    }
    PassCard(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(p.accentSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Key, null, tint = PassBlue)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(pt("af.service"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Text(if (enabled) pt("af.on") else pt("af.off"), fontSize = 12.5.sp, color = if (enabled) PassGreen else p.muted)
            }
            if (enabled) TextButton(onClick = open) { Text(pt("af.manage"), color = PassBlue, fontWeight = FontWeight.SemiBold) }
            else Button(
                onClick = open,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PassBlue),
            ) { Text(pt("af.turn_on"), fontWeight = FontWeight.SemiBold) }
        }
    }
}
