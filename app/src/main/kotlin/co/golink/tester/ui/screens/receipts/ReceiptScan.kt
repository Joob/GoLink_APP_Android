package co.golink.tester.ui.screens.receipts

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.receipts.AtQr
import co.golink.tester.data.receipts.RcCategories
import co.golink.tester.data.receipts.RcFormat
import co.golink.tester.data.receipts.Receipt
import co.golink.tester.data.receipts.ReceiptsState
import co.golink.tester.data.receipts.VatLine
import co.golink.tester.ui.i18n.rt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val QR_FIELDS = listOf("merchant_nif", "buyer_nif", "date", "total", "vat", "atcud")

/** Formulário do Scan/Editar (= emptyForm() de ReceiptScan.vue). */
private data class RcForm(
    val merchant: String = "",
    val merchantNif: String = "",
    val buyerNif: String = "",
    val date: String = "",
    val time: String = "",
    val total: String = "",
    val vat: String = "",
    val atcud: String = "",
    val docNo: String = "",
    val spendCat: String = "groceries",
    val irsCat: String = "gerais",
    val multi: Boolean = false,
    val note: String = "",
)

private data class QrExtra(val vatLines: List<VatLine> = emptyList(), val vatExempt: Boolean = false, val docType: String = "")

private fun digits(s: String) = s.filter { it.isDigit() }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScanPage(
    vm: ReceiptsViewModel,
    state: ReceiptsState,
    editId: String?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val p = LocalRc.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val busy by vm.busy.collectAsStateWithLifecycle()

    val isEdit = editId != null
    // camera | reading | form
    var phase by remember { mutableStateOf(if (isEdit) "form" else "camera") }
    var mode by remember { mutableStateOf("qr") }
    var form by remember { mutableStateOf(RcForm()) }
    val from = remember { mutableStateMapOf<String, String>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    var extra by remember { mutableStateOf(QrExtra()) }
    var qrFound by remember { mutableStateOf(false) }
    var qrRead by remember { mutableStateOf(false) }
    var catTouched by remember { mutableStateOf(false) }
    var showCats by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var editing by remember { mutableStateOf<Receipt?>(null) }
    var torch by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    fun reset() {
        form = RcForm()
        from.clear(); errors.clear()
        extra = QrExtra(); qrFound = false; qrRead = false; catTouched = false; showCats = false
        photo = null; preview = null; hint = null; mode = "qr"
        val me = state.meta.household.firstOrNull { it.id == "me" }
        if (me != null && me.nif.isNotEmpty() && !isEdit) {
            form = form.copy(buyerNif = me.nif)
            from["buyer_nif"] = "history"
        }
    }

    // Abrir: edição → dados do recibo; novo → NIF do "eu" do agregado.
    LaunchedEffect(editId) {
        reset()
        val it = editId?.let { id -> state.item(id) }?.takeIf { !it.locked } ?: return@LaunchedEffect
        editing = it
        mode = if (it.source == "qr") "qr" else "manual"
        form = RcForm(
            merchant = it.merchant, merchantNif = it.merchantNif, buyerNif = it.buyerNif, date = it.date, time = it.time,
            total = RcFormat.moneyInput(it.total), vat = RcFormat.moneyInput(it.vat), atcud = it.atcud, docNo = it.docNo,
            spendCat = it.spendCat.ifEmpty { "groceries" }, irsCat = it.irsCat.ifEmpty { "gerais" }, multi = it.multiActivity, note = it.note,
        )
        listOf("merchant", "merchant_nif", "buyer_nif", "date", "time", "total", "vat", "atcud").forEach { k -> from[k] = if (it.source == "qr" && k in QR_FIELDS) "qr" else "manual" }
        extra = QrExtra(it.vatLines, it.vatExempt, it.docType)
        catTouched = true
        if (it.hasPhoto) preview = vm.photoBitmap(it.id, 900)
    }

    val history = remember(state.items, editing) { state.active.filter { it.id != editing?.id }.sortedByDescending { it.date } }
    val suggestion = remember(form.merchant, form.merchantNif, history) { RcCategories.suggest(form.merchant, digits(form.merchantNif), history) }
    LaunchedEffect(suggestion) {
        val s = suggestion ?: return@LaunchedEffect
        if (!catTouched && !isEdit) form = form.copy(spendCat = s.spend, irsCat = s.irs, multi = s.multi)
    }

    fun fillFromHistory() {
        val nif = digits(form.merchantNif)
        if (nif.isEmpty() || form.merchant.isNotEmpty()) return
        RcCategories.merchantFor(nif, history)?.let {
            form = form.copy(merchant = it)
            from["merchant"] = "history"
        }
    }

    fun applyQr(text: String?) {
        qrRead = true
        val q = AtQr.parse(text)
        qrFound = q != null
        if (q == null) return
        form = form.copy(
            merchantNif = q.merchantNif,
            buyerNif = q.buyerNif.ifEmpty { form.buyerNif },
            date = q.date,
            total = RcFormat.moneyInput(q.total),
            vat = RcFormat.moneyInput(q.vat),
            atcud = q.atcud,
            docNo = q.docNo,
        )
        extra = QrExtra(q.vatLines, q.vatExempt, q.docType)
        q.filled.forEach { from[it] = "qr" }
        if (q.buyerNif.isEmpty() && form.buyerNif.isNotEmpty()) from["buyer_nif"] = "history"
        if (q.date == RcFormat.ymd(LocalDate.now()) && form.time.isEmpty()) {
            val n = LocalTime.now()
            form = form.copy(time = "${RcFormat.pad(n.hour)}:${RcFormat.pad(n.minute)}")
        }
        fillFromHistory()
    }

    fun touched(key: String) {
        if (from[key] == "qr" || from[key] == "history") from[key] = "edited"
        if (from[key] == null) from[key] = "manual"
        errors.remove(key)
    }

    /** Imagem/PDF escolhido: lê o QR, reduz e passa ao formulário. */
    fun loadPicked(uri: Uri) {
        phase = "reading"
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val bmp = ReceiptsImages.load(context, uri)
                    val qr = ReceiptsImages.decodeBitmap(bmp)
                    val jpeg = ReceiptsImages.toJpeg(bmp)
                    bmp.recycle()
                    Triple(jpeg, qr, ReceiptsImages.decodeSampled(jpeg, 900))
                }
            }
            result.onSuccess { (jpeg, qr, small) ->
                photo = jpeg
                preview = small
                if (isEdit) {
                    if (mode == "qr" && qr != null && editing == null) applyQr(qr)
                } else {
                    val keepMode = mode
                    if (keepMode == "qr") applyQr(qr)
                }
                phase = "form"
            }.onFailure { e ->
                vm.toast(if (e is ReceiptsImages.TooLargeException || e is ReceiptsImages.BadFileException) rt("scan.bad_file") else rt("scan.read_failed"))
                phase = if (isEdit || photo != null || mode == "manual") "form" else "camera"
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) loadPicked(uri) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> hasCamera = ok; asked = true }
    LaunchedEffect(phase) {
        if (phase == "camera" && !hasCamera && !asked) { vm.expectExternal(); askCamera.launch(Manifest.permission.CAMERA) }
    }
    fun openPicker() { vm.expectExternal(); picker.launch(arrayOf("image/*", "application/pdf")) }

    // Recibos parecidos já guardados (mesmo ATCUD, ou NIF + data + total [+ hora]).
    val duplicate = remember(form, state.items, editing) {
        val nif = digits(form.merchantNif)
        val atcud = form.atcud.trim()
        val total = RcFormat.parseMoney(form.total)
        state.active.firstOrNull { r ->
            if (r.locked || r.id == editing?.id) false
            else if (atcud.isNotEmpty() && r.atcud.isNotEmpty() && r.atcud == atcud) true
            else nif.isNotEmpty() && r.merchantNif == nif && r.date == form.date && total != null && total != 0L && r.total == total && (form.time.isEmpty() || r.time.isEmpty() || r.time == form.time)
        }
    }

    fun validate(): Boolean {
        errors.clear()
        val f = form
        if (f.merchant.isBlank()) errors["merchant"] = rt("scan.err_required")
        val mnif = digits(f.merchantNif)
        if (mnif.isEmpty()) errors["merchant_nif"] = rt("scan.err_required")
        else if (!AtQr.validNif(mnif)) errors["merchant_nif"] = rt("scan.err_nif")
        val bnif = digits(f.buyerNif)
        if (bnif.isNotEmpty() && !AtQr.validNif(bnif)) errors["buyer_nif"] = rt("scan.err_nif")
        val d = RcFormat.parseYmd(f.date)
        if (d == null) errors["date"] = rt("scan.err_required")
        else if (d.isAfter(LocalDate.now())) errors["date"] = rt("scan.err_future")
        val total = RcFormat.parseMoney(f.total)
        if (total == null || total == 0L) errors["total"] = rt("scan.err_amount")
        val vat = if (f.vat.isBlank()) 0L else RcFormat.parseMoney(f.vat)
        if (vat == null || (total != null && total != 0L && vat > total)) errors["vat"] = rt("scan.err_vat")
        return errors.isEmpty()
    }

    fun save() {
        if (busy || !validate()) return
        val f = form
        val ed = editing
        val fromQr = mode == "qr" && (qrFound || ed?.source == "qr")
        val vat = if (f.vat.isBlank()) 0L else RcFormat.parseMoney(f.vat) ?: 0L
        val fields: Map<String, JsonElement> = mapOf(
            "merchant" to JsonPrimitive(f.merchant.trim().take(120)),
            "merchant_nif" to JsonPrimitive(digits(f.merchantNif)),
            "buyer_nif" to JsonPrimitive(digits(f.buyerNif)),
            "date" to JsonPrimitive(f.date),
            "time" to JsonPrimitive(f.time),
            "total" to JsonPrimitive(RcFormat.parseMoney(f.total) ?: 0L),
            "vat" to JsonPrimitive(vat),
            "vat_exempt" to JsonPrimitive(extra.vatExempt && vat == 0L),
            "vat_lines" to JsonArray(extra.vatLines.map { JsonObject(mapOf("rate" to JsonPrimitive(it.rate), "base" to JsonPrimitive(it.base), "vat" to JsonPrimitive(it.vat))) }),
            "atcud" to JsonPrimitive(f.atcud.trim()),
            "doc_no" to JsonPrimitive(f.docNo),
            "doc_type" to JsonPrimitive(extra.docType),
            "spend_cat" to JsonPrimitive(f.spendCat),
            "irs_cat" to JsonPrimitive(f.irsCat),
            "multi_activity" to JsonPrimitive(f.multi),
            "validated" to JsonPrimitive(if (ed != null) ed.validated && f.multi else false),
            "source" to JsonPrimitive(if (fromQr) "qr" else "manual"),
            "note" to JsonPrimitive(f.note.trim().take(500)),
        )
        // Na edição mantém campos que esta versão não conhece (vindos da web).
        val payload = JsonObject((ed?.payload ?: JsonObject(emptyMap())) + fields)
        scope.launch {
            when (val res = vm.save(ed?.id, payload, ed?.revision, photo)) {
                is RcSaveResult.Saved -> { photo = null; onSaved(res.receipt.id) }
                is RcSaveResult.PhotoFailed -> editing = res.receipt
                RcSaveResult.Failed -> Unit
            }
        }
    }

    BackHandler(enabled = phase == "form" && !isEdit && (photo != null || mode == "manual")) { reset(); phase = "camera" }

    when (phase) {
        "camera" -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (hasCamera) {
                RcCameraView(
                    torch = torch,
                    onQr = { text, bmp ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        hint = rt("app.scan_found")
                        scope.launch {
                            val prepared = withContext(Dispatchers.Default) {
                                runCatching { bmp?.let { ReceiptsImages.toJpeg(it) } }.getOrNull()?.let { it to ReceiptsImages.decodeSampled(it, 900) }
                            }
                            photo = prepared?.first
                            preview = prepared?.second
                            bmp?.recycle()
                            mode = "qr"
                            applyQr(text)
                            phase = "form"
                        }
                    },
                    onInvalid = { hint = rt("app.not_at_qr") },
                    onShot = { bmp ->
                        scope.launch {
                            // JPEG + miniatura fora da main (descodificar 1600 px na main dá solavanco).
                            val prepared = withContext(Dispatchers.Default) {
                                runCatching { ReceiptsImages.toJpeg(bmp).let { it to ReceiptsImages.decodeSampled(it, 900) } }.getOrNull()
                            }
                            bmp.recycle()
                            if (prepared == null) { vm.toast(rt("scan.read_failed")); return@launch }
                            photo = prepared.first
                            preview = prepared.second
                            mode = "manual"
                            qrRead = true
                            phase = "form"
                        }
                    },
                )
                ScanFrame(found = hint == rt("app.scan_found"))
            } else {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(rt("app.camera_denied"), color = Color.White, textAlign = TextAlign.Center, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    RcPrimaryButton(rt("app.allow_camera"), null) {
                        vm.expectExternal()
                        if (asked) {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else askCamera.launch(Manifest.permission.CAMERA)
                    }
                }
            }
            // Topo: fechar · estado · lanterna.
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, rt("close"), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                if (hasCamera) IconButton(onClick = { torch = !torch }) { Icon(if (torch) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff, rt("app.torch"), tint = Color.White) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    hint ?: rt("app.point_qr"),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = Color(0xFF5EEAD4), modifier = Modifier.size(14.dp))
                    Text(rt("app.read_on_phone"), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DarkButton(rt("app.from_gallery"), Icons.Outlined.Image, Modifier.weight(1f)) { openPicker() }
                    DarkButton(rt("app.type_it"), Icons.Outlined.EditNote, Modifier.weight(1f)) { reset(); mode = "manual"; phase = "form" }
                }
            }
        }
        "reading" -> Box(Modifier.fillMaxSize().background(Color(0xFF1F2933)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF5EEAD4), strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text(rt("scan.reading"), color = Color.White)
            }
        }
        else -> Column(Modifier.fillMaxSize().imePadding()) {
            RcBackBar(if (isEdit) rt("scan.edit_title") else if (qrFound) rt("app.qr_read") else rt("scan.title"), onBack = {
                if (!isEdit && (photo != null || mode == "manual")) { reset(); phase = "camera" } else onClose()
            })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // Foto + estado da leitura.
                RcCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(64.dp, 86.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF7F5EF)).border(1.dp, p.line, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val b = preview
                            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Outlined.EditNote, null, tint = Color(0xFF9CA3AF))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            val (title, sub, tone) = when {
                                mode == "qr" && qrFound -> Triple(rt("app.qr_read"), rt("app.qr_read_sub"), "good")
                                mode == "qr" && qrRead -> Triple(rt("scan.not_found"), rt("scan.qr_missing"), "warn")
                                else -> Triple(rt("scan.step_typed"), rt("scan.manual_banner"), "muted")
                            }
                            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = when (tone) { "good" -> p.good; "warn" -> p.warn; else -> p.text })
                            Text(sub, fontSize = 12.5.sp, color = p.muted)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(rt("app.retake"), color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, modifier = Modifier.clickable {
                                    if (isEdit) openPicker() else { reset(); phase = "camera" }
                                })
                                Text(rt("scan.replace"), color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, modifier = Modifier.clickable { openPicker() })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                RcCard {
                    FormField(rt("field.merchant"), form.merchant, from["merchant"], errors["merchant"], required = true, placeholder = rt("scan.merchant_ph"),
                        hint = if (from["merchant"] != "history" && qrFound) rt("scan.merchant_hint") else null) { form = form.copy(merchant = it); touched("merchant") }
                    RcDivider(Modifier.padding(start = 16.dp))
                    FormField(rt("field.merchant_nif"), form.merchantNif, from["merchant_nif"], errors["merchant_nif"], required = true, mono = true, placeholder = "500 000 000", keyboard = KeyboardType.Number) {
                        form = form.copy(merchantNif = it.take(16)); touched("merchant_nif")
                        if (digits(it).length == 9) fillFromHistory()
                    }
                    RcDivider(Modifier.padding(start = 16.dp))
                    FormField(rt("field.your_nif"), form.buyerNif, from["buyer_nif"], errors["buyer_nif"], mono = true, placeholder = rt("scan.buyer_ph"), keyboard = KeyboardType.Number) {
                        form = form.copy(buyerNif = it.take(16)); touched("buyer_nif")
                    }
                    val members = state.meta.household.filter { it.nif.isNotEmpty() && it.nif != digits(form.buyerNif) }
                    if (members.isNotEmpty()) {
                        FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            members.forEach { m ->
                                Text(
                                    "${m.name.ifBlank { rt("household.you") }} · ${RcFormat.maskNif(m.nif)}", fontSize = 12.sp, color = p.text,
                                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, p.line, RoundedCornerShape(999.dp))
                                        .clickable { form = form.copy(buyerNif = m.nif); touched("buyer_nif") }.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    RcDivider(Modifier.padding(start = 16.dp))
                    FormField(rt("field.date"), form.date, from["date"], errors["date"], required = true, placeholder = "AAAA-MM-DD", readOnly = true, onClick = { pickDate = true }) {}
                    RcDivider(Modifier.padding(start = 16.dp))
                    FormField(rt("field.time"), form.time, from["time"], null, placeholder = "--:--", readOnly = true, onClick = { pickTime = true }) {}
                    RcDivider(Modifier.padding(start = 16.dp))
                    FormField(rt("field.total"), form.total, from["total"], errors["total"], required = true, mono = true, placeholder = "0,00", keyboard = KeyboardType.Decimal) {
                        form = form.copy(total = it.take(14)); touched("total")
                    }
                    RcDivider(Modifier.padding(start = 16.dp))
                    FormField(rt("field.vat"), form.vat, from["vat"], errors["vat"], mono = true, placeholder = "0,00", keyboard = KeyboardType.Decimal,
                        hint = if (extra.vatExempt) rt("scan.exempt") else null) { form = form.copy(vat = it.take(14)); touched("vat") }
                    RcDivider(Modifier.padding(start = 16.dp))
                    FormField(rt("field.atcud"), form.atcud, from["atcud"], null, mono = true, placeholder = "ABCD1234-1") { form = form.copy(atcud = it.take(70)); touched("atcud") }
                }

                // Categoria sugerida · Change.
                Spacer(Modifier.height(12.dp))
                RcCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RcDot(spendColor(form.spendCat, p.dark), 12.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text("${catLongLabel(form.spendCat)} · ${irsPt(form.irsCat)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
                            val s = suggestion
                            val why = when {
                                s == null || catTouched -> null
                                s.kind != null -> "${rt("scan.suggested")} (${rt("kind.${s.kind}")})"
                                s.source == "history" -> "${rt("scan.suggested")} (${rt("scan.like_before")})"
                                else -> rt("scan.suggested")
                            }
                            if (why != null) Text(why, fontSize = 12.5.sp, color = p.muted)
                        }
                        Text(rt("app.change"), color = p.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showCats = !showCats })
                    }
                    if (showCats) {
                        Spacer(Modifier.height(12.dp))
                        Text(rt("detail.spending_category"), fontSize = 12.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RcCategories.SPEND_KEYS.forEach { c ->
                                RcChip(catLongLabel(c), null, form.spendCat == c, spendColor(c, p.dark)) {
                                    val old = form.spendCat
                                    catTouched = true
                                    // Mudar a despesa à mão leva o bucket IRS por defeito (se ainda era o da anterior).
                                    form = form.copy(spendCat = c, irsCat = if (form.irsCat == RcCategories.defaultIrsFor(old)) RcCategories.defaultIrsFor(c) else form.irsCat)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(rt("detail.category"), fontSize = 12.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RcCategories.IRS_ALL.forEach { k ->
                                RcChip("${irsLabel(k)} · ${irsPt(k)}", null, form.irsCat == k, irsColor(k, p.dark)) { catTouched = true; form = form.copy(irsCat = k) }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp).clickable { catTouched = true; form = form.copy(multi = !form.multi) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(form.multi, null, colors = CheckboxDefaults.colors(checkedColor = p.accent))
                            Column {
                                Text(rt("detail.multi"), fontSize = 14.sp, color = p.text)
                                Text(rt("detail.multi_hint"), fontSize = 12.sp, color = p.muted)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                RcCard {
                    FormField(rt("field.note"), form.note, null, null, placeholder = rt("scan.note_ph"), singleLine = false) { form = form.copy(note = it.take(500)) }
                }

                // Avisos.
                duplicate?.let { d ->
                    Spacer(Modifier.height(12.dp))
                    Warn(rt("scan.duplicate", "date" to RcDates.short(RcFormat.parseYmd(d.date))), p.warn)
                }
                if (form.buyerNif.isBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Warn(rt("scan.no_buyer_nif"), p.bad)
                }
                Spacer(Modifier.height(24.dp))
            }
            // Rodapé: Retake / Save.
            Row(Modifier.fillMaxWidth().background(p.card).navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isEdit) RcSecondaryButton(rt("cancel"), null, Modifier.weight(1f)) { onClose() }
                else RcSecondaryButton(rt("app.retake"), null, Modifier.weight(1f)) { reset(); phase = "camera" }
                RcPrimaryButton(if (duplicate != null) rt("scan.save_anyway") else rt("scan.save"), null, Modifier.weight(1.4f), enabled = !busy) { save() }
            }
        }
    }

    if (pickDate) {
        val initial = RcFormat.parseYmd(form.date) ?: LocalDate.now()
        val ds = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let { ms ->
                        form = form.copy(date = RcFormat.ymd(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()))
                        touched("date")
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(rt("cancel")) } },
        ) { DatePicker(ds) }
    }
    if (pickTime) {
        val now = runCatching { LocalTime.parse(form.time) }.getOrNull() ?: LocalTime.now()
        val ts = rememberTimePickerState(now.hour, now.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { form = form.copy(time = "${RcFormat.pad(ts.hour)}:${RcFormat.pad(ts.minute)}"); touched("time"); pickTime = false }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { form = form.copy(time = ""); pickTime = false }) { Text(rt("app.delete")) }
                    TextButton(onClick = { pickTime = false }) { Text(rt("cancel")) }
                }
            },
            text = { TimePicker(ts) },
        )
    }
}

@Composable
private fun Warn(text: String, color: Color) {
    val p = LocalRc.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = if (p.dark) 0.15f else 0.09f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WarningAmber, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = p.text)
    }
}

@Composable
private fun DarkButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Campo do formulário: rótulo · origem (QR / à mão / editado / histórico) · erro. */
@Composable
private fun FormField(
    label: String,
    value: String,
    origin: String?,
    error: String?,
    required: Boolean = false,
    mono: Boolean = false,
    placeholder: String = "",
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    onClick: (() -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    val p = LocalRc.current
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label + if (required) " *" else "", fontSize = 12.5.sp, color = p.muted, modifier = Modifier.weight(1f))
            if (origin != null) {
                val (txt, tone) = when (origin) {
                    "qr" -> rt("scan.from_qr") to "good"
                    "history" -> rt("scan.from_history") to "muted"
                    "edited" -> rt("scan.from_edited") to "warn"
                    else -> rt("scan.from_manual") to "muted"
                }
                RcPill(txt, tone)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (readOnly) {
            Text(value.ifEmpty { placeholder }, fontSize = 16.sp, color = if (value.isEmpty()) p.soft else p.text, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
        } else {
            Box {
                if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, color = p.soft, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
                BasicTextField(
                    value, onChange,
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 2,
                    textStyle = TextStyle(fontSize = 16.sp, color = p.text, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (error != null) Text(error, fontSize = 12.sp, color = p.bad, modifier = Modifier.padding(top = 3.dp))
        else if (hint != null) Text(hint, fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Moldura do leitor (cantos teal; verde-água cheio quando encontra o QR). */
@Composable
private fun ScanFrame(found: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val side = size.minDimension * 0.68f
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2.4f
        val c = if (found) Color(0xFF2DD4BF) else Color.White
        val len = side * 0.16f
        val w = 4.dp.toPx()
        listOf(
            Offset(left, top) to Offset(1f, 1f), Offset(left + side, top) to Offset(-1f, 1f),
            Offset(left, top + side) to Offset(1f, -1f), Offset(left + side, top + side) to Offset(-1f, -1f),
        ).forEach { (o, d) ->
            drawLine(c, o, Offset(o.x + len * d.x, o.y), w)
            drawLine(c, o, Offset(o.x, o.y + len * d.y), w)
        }
        if (found) drawRoundRect(c.copy(alpha = 0.18f), Offset(left, top), androidx.compose.ui.geometry.Size(side, side), CornerRadius(18.dp.toPx()))
        else drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(left, top), androidx.compose.ui.geometry.Size(side, side), CornerRadius(18.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}

/**
 * CameraX: pré-visualização + análise (QR a cada frame, só o mais recente) + captura.
 * QR da AT → tira a foto em resolução de captura e chama [onQr] uma vez.
 */
@Composable
private fun RcCameraView(torch: Boolean, onQr: (String, Bitmap?) -> Unit, onInvalid: () -> Unit, onShot: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val onQrNow by rememberUpdatedState(onQr)
    val onInvalidNow by rememberUpdatedState(onInvalid)
    val onShotNow by rememberUpdatedState(onShot)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val busy = remember { AtomicBoolean(false) }
    val main = remember { Handler(Looper.getMainLooper()) }

    fun grab(done: (Bitmap?) -> Unit) {
        val exec = ContextCompat.getMainExecutor(context)
        capture.takePicture(exec, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rot = image.imageInfo.rotationDegrees
                val bmp = runCatching { image.toBitmap() }.getOrNull()
                image.close()
                done(bmp?.let { ReceiptsImages.rotate(it, rot) } ?: previewView.bitmap)
            }
            override fun onError(exception: ImageCaptureException) { done(previewView.bitmap) }
        })
    }

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var lastInvalid = 0L
        // Listener, posts e onDispose correm todos na main: sair do Scan antes de o
        // provider chegar não pode ligar a câmara depois (nem chamar onQr fora do ecrã).
        var disposed = false
        var frame = ByteArray(0)
        future.addListener({
            if (disposed) return@addListener
            val pr = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = pr
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val reader = ReceiptsImages.newReader()
            analysis.setAnalyzer(executor) { img ->
                try {
                    if (!busy.get()) {
                        val plane = img.planes[0]
                        val buf = plane.buffer
                        // Um buffer por tamanho de frame (não 1 MB novo a cada frame).
                        if (frame.size != buf.remaining()) frame = ByteArray(buf.remaining())
                        buf.get(frame)
                        val text = ReceiptsImages.decodeFrame(reader, frame, plane.rowStride, img.width, img.height)
                        if (text != null) {
                            if (AtQr.parse(text) != null) {
                                if (busy.compareAndSet(false, true)) main.post {
                                    if (!disposed) grab { bmp -> if (!disposed) onQrNow(text, bmp) }
                                }
                            } else if (System.currentTimeMillis() - lastInvalid > 2000) {
                                lastInvalid = System.currentTimeMillis()
                                main.post { if (!disposed) onInvalidNow() }
                            }
                        }
                    }
                } finally {
                    img.close()
                }
            }
            runCatching {
                pr.unbindAll()
                camera = pr.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, capture)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
            camera = null
            executor.shutdown()
        }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        // Disparo manual (talão sem QR): foto → formulário à mão.
        Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 190.dp).size(68.dp).clip(CircleShape)
                .border(4.dp, Color.White, CircleShape).padding(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f))
                .clickable { if (busy.compareAndSet(false, true)) grab { bmp -> if (bmp != null) onShotNow(bmp) else busy.set(false) } },
        )
    }
}
