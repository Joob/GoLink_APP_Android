package co.golink.tester.ui.screens.pass

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import co.golink.tester.data.pass.PassItem
import co.golink.tester.data.pass.PassLogin
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import co.golink.tester.data.pass.PassState
import co.golink.tester.data.pass.Totp
import co.golink.tester.ui.i18n.pt
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lê um QR de uma imagem (foto da câmara ou captura), tudo no aparelho. */
private fun decodeQr(context: android.content.Context, uri: Uri): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
    val bmp: Bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
    val source = RGBLuminanceSource(bmp.width, bmp.height, px)
    return runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text }.getOrNull()
        ?: runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source.invert())), hints).text }.getOrNull()
}

/** Chave lida à espera do login, com o Emissor/Conta (do QR ou escritos no "Inserir chave"). */
private data class PendingTotp(val raw: String, val issuer: String, val account: String)

@Composable
fun PassTotpPane(vm: PassViewModel, state: PassState, onNewWithKey: (PassPrefill) -> Unit, onOpenItem: (String) -> Unit) {
    val p = LocalPassPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember(state.items) { state.active.filter { it.type == "login" && it.totp != null }.sortedBy { it.name.lowercase() } }
    var chooser by remember { mutableStateOf(false) }
    var enterKey by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingTotp?>(null) }

    val accept: (String?) -> Unit = { raw ->
        val cfg = raw?.let { Totp.parse(it) }
        when {
            raw != null && cfg != null -> pending = PendingTotp(raw.trim(), cfg.issuer, cfg.account)
            raw != null && raw.startsWith("otpauth-migration:", ignoreCase = true) -> vm.toast(pt("totp_form.qr_migration"))
            else -> vm.toast(pt("qr_invalid"))
        }
    }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        photoUri = null
        if (ok && uri != null) scope.launch {
            val text = withContext(Dispatchers.Default) { runCatching { decodeQr(context, uri) }.getOrNull() }
            runCatching { File(context.cacheDir, "camera/pass-qr.jpg").delete() }
            accept(text)
        } else {
            // Cancelado: a câmara pode ter deixado um ficheiro (vazio ou parcial).
            runCatching { File(context.cacheDir, "camera/pass-qr.jpg").delete() }
        }
    }
    val launchCamera: () -> Unit = {
        val file = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "pass-qr.jpg") }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        photoUri = uri
        vm.expectExternal()
        runCatching { takePhoto.launch(uri) }.onFailure { vm.toast(pt("camera_denied")) }
    }
    // CAMERA está no manifest: sem a permissão dada o TakePicture dá SecurityException (como no ReceiptScan).
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchCamera() else vm.toast(pt("camera_denied"))
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { accept(withContext(Dispatchers.Default) { runCatching { decodeQr(context, uri) }.getOrNull() }) }
    }

    // Lista preguiçosa e o relógio lido dentro de cada cartão: o tique de 1 s só recompõe os cartões.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item(key = "intro") {
            Column {
                Text(pt("totp_local"), fontSize = 14.sp, color = p.muted, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                Spacer(Modifier.height(10.dp))
                if (items.isEmpty()) {
                    PassCard(padding = 20.dp) {
                        Text(pt("totp_empty"), fontWeight = FontWeight.SemiBold, color = p.text, fontSize = 15.sp)
                        Text(pt("totp_empty_text"), color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        items(items, key = { it.id }) { item ->
            Column {
                TotpCard(item, onCopy = { code -> vm.copy(pt("f.totp"), code) }, onOpen = { onOpenItem(item.id) })
                Spacer(Modifier.height(10.dp))
            }
        }
        item(key = "actions") {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashedButton(Icons.Outlined.QrCodeScanner, pt("scan_qr"), Modifier.weight(1f)) { chooser = true }
                DashedButton(null, pt("enter_key"), Modifier.weight(1f)) { enterKey = true }
            }
        }
    }

    if (chooser) {
        AlertDialog(
            onDismissRequest = { chooser = false },
            title = { Text(pt("scan_qr")) },
            text = {
                Column {
                    Text(pt("scan_hint"), fontSize = 14.sp, color = p.muted)
                    Spacer(Modifier.height(8.dp))
                    ChooserRow(Icons.Outlined.CameraAlt, pt("scan_camera")) {
                        chooser = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            vm.expectExternal()
                            askCamera.launch(Manifest.permission.CAMERA)
                        }
                    }
                    ChooserRow(Icons.Outlined.Image, pt("scan_image")) {
                        chooser = false
                        vm.expectExternal()
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooser = false }) { Text(pt("cancel")) } },
        )
    }

    if (enterKey) {
        TotpAddSheet(
            onDismiss = { enterKey = false },
            onScan = { enterKey = false; chooser = true },
            onDone = { raw, issuer, account -> enterKey = false; pending = PendingTotp(raw, issuer, account) },
        )
    }

    pending?.let { pk ->
        val raw = pk.raw
        val candidates = remember(state.items) { state.active.filter { it.type == "login" && it.totp == null && !it.locked }.sortedBy { it.name.lowercase() } }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(pt("pick_login")) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    // Só o 2FA, sem site/password (como o "Add account" dos autenticadores).
                    ChooserRow(Icons.Outlined.Key, pt("totp_form.save_only")) {
                        pending = null
                        val account = pk.account
                        val name = pk.issuer.ifEmpty { null } ?: account.ifEmpty { "2FA" }
                        scope.launch {
                            vm.save(
                                null,
                                JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("login"),
                                        "name" to JsonPrimitive(name),
                                        "folder" to JsonNull,
                                        "favourite" to JsonPrimitive(false),
                                        "notes" to JsonPrimitive(""),
                                        "login" to PassLogin("", account, "", raw).toJson(),
                                    ),
                                ),
                                null,
                            )
                        }
                    }
                    ChooserRow(Icons.Outlined.Add, pt("new_login")) {
                        pending = null
                        onNewWithKey(PassPrefill(name = pk.issuer, username = pk.account, totp = raw))
                    }
                    candidates.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                pending = null
                                scope.launch {
                                    val payload = item.payload.toMutableMap()
                                    val login = item.login ?: return@launch
                                    payload["login"] = login.copy(totp = raw).toJson(item.payload["login"] as? kotlinx.serialization.json.JsonObject)
                                    if (vm.save(item.id, kotlinx.serialization.json.JsonObject(payload), item.revision) != null) vm.toast(pt("add_to_login", "name" to item.name))
                                }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PassAvatar(item, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(itemSubtitle(item), fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pending = null }) { Text(pt("cancel")) } },
        )
    }
}

@Composable
private fun TotpCard(item: PassItem, onCopy: (String) -> Unit, onOpen: () -> Unit) {
    val p = LocalPassPalette.current
    val cfg = item.totp ?: return
    val now = rememberTotp(cfg, rememberNowMs()) ?: return
    val ending = now.remaining <= 7
    PassCard {
        Row(
            Modifier.fillMaxWidth().clickable { onCopy(now.code) }.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.clickable(onClick = onOpen)) { PassAvatar(item, 44.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val account = item.login?.username?.ifEmpty { null } ?: cfg.account.ifEmpty { null }
                Text(listOfNotNull(item.name, account).joinToString(" · "), fontSize = 14.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Totp.format(now.code),
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ending) PassRose else p.text,
                    letterSpacing = 1.sp,
                )
                Text("${pt("next_label")}: ${Totp.format(now.next)}", fontSize = 12.5.sp, color = p.soft, fontFamily = FontFamily.Monospace)
            }
            TotpRing(now.remaining, now.period, 46.dp)
        }
    }
}

@Composable
private fun DashedButton(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.5.dp, PassBlue.copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = PassBlue, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, color = PassBlue, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChooserRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = PassBlue)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
