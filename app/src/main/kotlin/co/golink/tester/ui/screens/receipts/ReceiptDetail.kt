package co.golink.tester.ui.screens.receipts

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.golink.tester.data.receipts.AtQr
import co.golink.tester.data.receipts.IrsRules
import co.golink.tester.data.receipts.RcCategories
import co.golink.tester.data.receipts.RcFormat
import co.golink.tester.data.receipts.Receipt
import co.golink.tester.data.receipts.ReceiptsState
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.rt
import kotlinx.serialization.json.JsonPrimitive

const val EFATURA_URL = "https://faturas.portaldasfinancas.gov.pt/"

/** Abre o portal no browser (voltar não pede o PIN da app). */
fun openEfatura(context: android.content.Context, vm: ReceiptsViewModel) {
    vm.expectExternal()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EFATURA_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceiptDetailPage(
    vm: ReceiptsViewModel,
    state: ReceiptsState,
    id: String,
    recat: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val p = LocalRc.current
    val r = state.item(id) ?: return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val household = state.meta.household
    val readonly = r.locked || r.isTrashed
    val status = remember(r, household) { if (r.locked) "pending" else IrsRules.efStatus(r, household) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var photo by remember(r.id) { mutableStateOf<Bitmap?>(null) }
    var photoLoading by remember(r.id) { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }

    // Foto trocada noutro aparelho sobe a revisão: volta a pedir.
    LaunchedEffect(r.id, r.hasPhoto, r.revision) {
        if (!r.hasPhoto || r.locked) { photo = null; return@LaunchedEffect }
        photoLoading = true
        photo = vm.photoBitmap(r.id, 1400)
        photoLoading = false
    }

    fun copyNif() {
        clipboard.setText(AnnotatedString(r.merchantNif))
        vm.toast(rt("efatura.copied"))
    }

    Column(Modifier.fillMaxSize()) {
        RcBackBar(r.title, onBack) {
            if (!readonly) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, rt("edit"), tint = p.text) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            // Cabeçalho: foto + valor.
            RcCard(padding = 16.dp) {
                Row {
                    Box(
                        Modifier.width(96.dp).height(132.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F5EF))
                            .border(1.dp, p.line, RoundedCornerShape(10.dp)).clickable(enabled = photo != null) { zoom = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = photo
                        when {
                            bmp != null -> Image(bmp.asImageBitmap(), rt("detail.photo"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            photoLoading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = p.accent)
                            else -> Column(Modifier.fillMaxSize().padding(8.dp)) {
                                Text(r.merchant.ifBlank { "—" }, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF374151), fontWeight = FontWeight.Bold, maxLines = 2)
                                Text("NIF ${r.merchantNif}", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF374151))
                                Spacer(Modifier.height(4.dp))
                                Text("TOTAL ${RcFormat.moneyInput(r.total)}", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF374151))
                                Text("IVA ${RcFormat.moneyInput(r.vat)}", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF374151))
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Outlined.QrCode2, null, tint = Color(0xFF1F2937), modifier = Modifier.align(Alignment.End).size(22.dp))
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(RcDates.long(RcFormat.parseYmd(r.date)) + if (r.time.isNotEmpty()) " · ${r.time}" else "", fontSize = 13.sp, color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        Text(if (r.locked) "—" else RcFormat.money(r.total), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                        val vatLabel = if (r.vatExempt || (r.vat == 0L && r.source == "qr")) rt("detail.vat_exempt")
                        else "${rt("vat_short")}${AtQr.mainVatRate(r.vatLines, r.vat)?.let { " $it%" } ?: ""} · ${RcFormat.money(r.vat)}"
                        Text(vatLabel, fontSize = 13.sp, color = p.muted)
                        Spacer(Modifier.height(8.dp))
                        RcPill(if (r.source == "qr") rt("detail.read_qr") else rt("detail.typed"), if (r.source == "qr") "good" else "muted")
                    }
                }
            }

            // Estado no e-Fatura.
            if (!r.locked) {
                Spacer(Modifier.height(12.dp))
                val tone = when (status) { "in" -> p.good; "validate" -> p.warn; "no_nif" -> p.bad; else -> p.muted }
                val irsPtName = irsPt(r.irsCat)
                val text = when (status) {
                    "in" -> if (r.irsCat == "none") rt("detail.state.in_none") else rt("detail.state.in_text", "cat" to irsPtName)
                    "validate" -> rt("detail.state.validate_text", "cat" to irsPtName)
                    "no_nif" -> {
                        val lost = IrsRules.lostBenefit(r, r.date.take(4).toIntOrNull() ?: 0)
                        rt("detail.state.no_nif_text") + if (lost > 0) " " + rt("detail.state.lost", "amount" to RcFormat.money(lost)) else ""
                    }
                    else -> rt("detail.state.pending_text", "date" to RcDates.short(IrsRules.merchantDeadline(r.date)?.toLocalDate()))
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tone.copy(alpha = if (p.dark) 0.14f else 0.09f))
                        .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(18.dp)).padding(16.dp),
                ) {
                    Text(rt("detail.state.${status}_title"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tone)
                    Spacer(Modifier.height(2.dp))
                    Text(text, fontSize = 13.5.sp, color = p.text, lineHeight = 19.sp)
                    if (status == "validate" && !readonly) {
                        Spacer(Modifier.height(12.dp))
                        RcPrimaryButton(rt("app.open_pick", "cat" to irsPtName), Icons.Outlined.OpenInNew, Modifier.fillMaxWidth()) { openEfatura(context, vm) }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RcSecondaryButton(rt("efatura.copy_nif"), Icons.Outlined.ContentCopy, Modifier.weight(1f)) { copyNif() }
                            RcSecondaryButton(rt("detail.mark_validated"), Icons.Outlined.Check, Modifier.weight(1f)) { vm.patch(r.id, "validated" to JsonPrimitive(true)) }
                        }
                    } else if (r.validated && r.multiActivity && !readonly) {
                        Text(
                            rt("detail.undo_validated"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text,
                            modifier = Modifier.padding(top = 8.dp).clickable { vm.patch(r.id, "validated" to JsonPrimitive(false)) },
                        )
                    }
                }
            }

            // Categoria de despesa (o "Not {cat}" da notificação abre aqui).
            RcSectionLabel(rt("detail.spending_category"))
            FlowRow(
                Modifier.fillMaxWidth().then(if (recat) Modifier.border(2.dp, p.accent, RoundedCornerShape(16.dp)).padding(8.dp) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RcCategories.SPEND_KEYS.forEach { c ->
                    RcChip(catLongLabel(c), null, r.spendCat == c, spendColor(c, p.dark)) {
                        if (!readonly && r.spendCat != c) vm.patch(r.id, "spend_cat" to JsonPrimitive(c))
                    }
                }
            }

            // Categoria IRS.
            RcSectionLabel(rt("detail.category"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RcCategories.IRS_ALL.forEach { k ->
                    RcChip(irsLabel(k), null, r.irsCat == k, irsColor(k, p.dark)) {
                        if (!readonly && r.irsCat != k) vm.patch(r.id, "irs_cat" to JsonPrimitive(k))
                    }
                }
            }
            Text(rt("detail.category_note"), Modifier.padding(start = 4.dp, top = 6.dp), fontSize = 12.sp, color = p.muted)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !readonly) {
                    vm.patch(r.id, "multi_activity" to JsonPrimitive(!r.multiActivity), "validated" to JsonPrimitive(false))
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(r.multiActivity, null, enabled = !readonly, colors = CheckboxDefaults.colors(checkedColor = p.accent))
                Column(Modifier.weight(1f)) {
                    Text(rt("detail.multi"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = p.text)
                    Text(rt("detail.multi_hint"), fontSize = 12.sp, color = p.muted)
                }
            }

            // Dados.
            Spacer(Modifier.height(12.dp))
            RcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { RcInfoRow(rt("field.merchant_nif"), RcFormat.spacedNif(r.merchantNif).ifEmpty { "—" }, mono = true) }
                    if (r.merchantNif.isNotEmpty()) IconButton(onClick = ::copyNif) { Icon(Icons.Outlined.ContentCopy, rt("efatura.copy_nif"), tint = p.muted, modifier = Modifier.size(18.dp)) }
                }
                RcDivider(Modifier.padding(start = 16.dp))
                val memberId = IrsRules.memberOf(r, household)
                val member = household.firstOrNull { it.id == memberId }
                val memberLabel = when {
                    member == null -> rt("detail.not_household")
                    member.id == "me" -> rt("detail.on_receipt")
                    else -> member.name.ifBlank { rt("detail.on_receipt") }
                }
                RcInfoRow(
                    rt("field.your_nif"),
                    if (r.buyerNif.isNotEmpty()) "${RcFormat.maskNif(r.buyerNif)} · $memberLabel" else rt("detail.missing"),
                    valueColor = if (status == "no_nif") p.bad else p.good, mono = true,
                )
                RcDivider(Modifier.padding(start = 16.dp))
                RcInfoRow(rt("field.vat"), if (r.vatExempt) rt("detail.vat_exempt") else RcFormat.money(r.vat))
                RcDivider(Modifier.padding(start = 16.dp))
                RcInfoRow(rt("app.irs_category"), "${irsLabel(r.irsCat)} · ${irsPt(r.irsCat)}")
                RcDivider(Modifier.padding(start = 16.dp))
                val year = r.date.take(4).toIntOrNull() ?: 0
                val counts = IrsRules.countsForIrs(r, household)
                RcInfoRow(
                    rt("app.counts"),
                    if (counts && r.irsCat != "none") rt("app.counts_value", "amount" to RcFormat.money(IrsRules.lostBenefit(r, year))) else "—",
                    valueColor = if (counts && r.irsCat != "none") p.good else p.muted,
                )
                RcDivider(Modifier.padding(start = 16.dp))
                RcInfoRow(rt("field.atcud"), r.atcud.ifEmpty { "—" }, mono = true)
                if (r.docNo.isNotEmpty()) {
                    RcDivider(Modifier.padding(start = 16.dp))
                    RcInfoRow(rt("field.doc_no"), r.docNo, mono = true)
                }
                if (r.note.isNotEmpty()) {
                    RcDivider(Modifier.padding(start = 16.dp))
                    RcInfoRow(rt("field.note"), r.note)
                }
            }

            // Ações.
            Spacer(Modifier.height(16.dp))
            if (r.isTrashed) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RcSecondaryButton(rt("restore"), Icons.Outlined.Restore, Modifier.weight(1f)) { vm.restore(r.id) }
                    RcSecondaryButton(rt("delete_forever"), Icons.Outlined.DeleteForever, Modifier.weight(1f), danger = true) { confirm = "forever" }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!r.locked) RcSecondaryButton(rt("app.share_pdf"), Icons.Outlined.PictureAsPdf, Modifier.weight(1f)) { vm.shareReceiptPdf(r) }
                    RcSecondaryButton(rt("app.delete"), Icons.Outlined.Delete, Modifier.weight(1f), danger = true) { confirm = "trash" }
                }
            }
        }
    }

    if (zoom && photo != null) {
        Dialog(onDismissRequest = { zoom = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { zoom = false }, contentAlignment = Alignment.Center) {
                ZoomableImage(photo!!)
            }
        }
    }

    confirm?.let { kind ->
        val name = r.merchant.ifBlank { r.merchantNif }
        ConfirmDialog(
            title = if (kind == "forever") rt("delete_forever_title") else rt("trash_title"),
            message = if (kind == "forever") rt("delete_forever_text", "name" to name) else rt("trash_text", "name" to name, "days" to state.trashDays),
            confirmText = if (kind == "forever") rt("delete_forever") else rt("move_to_trash"),
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                if (kind == "forever") vm.deleteForever(r.id, onBack) else vm.trash(r.id, onBack)
            },
        )
    }
}

/** Foto em grande com pinça para ampliar. */
@Composable
private fun ZoomableImage(bmp: Bitmap) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        bmp.asImageBitmap(), null,
        Modifier.fillMaxSize().padding(12.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        contentScale = ContentScale.Fit,
    )
}

/** Botão principal teal (largura total). */
@Composable
fun RcPrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalRc.current
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (enabled) p.accent else p.accent.copy(alpha = 0.4f)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (p.dark) Color(0xFF042F2E) else Color.White
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RcSecondaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalRc.current
    val fg = if (danger) p.bad else p.text
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)) }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
