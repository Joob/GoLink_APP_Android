package co.golink.tester.ui.screens.receipts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import co.golink.tester.data.receipts.AtQr
import co.golink.tester.data.receipts.IrsRules
import co.golink.tester.data.receipts.Member
import co.golink.tester.data.receipts.RcFormat
import co.golink.tester.data.receipts.Receipt
import co.golink.tester.ui.i18n.rt
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Exportações geradas no aparelho (= services/receipts/exporters.js): PDF de um recibo
 * (partilhar), PDF "IRS {ano} summary", CSV de 14 colunas com BOM e ZIP das fotos.
 */
object ReceiptsPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 48f

    private fun paint(size: Float, bold: Boolean = false, gray: Int = 20) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        color = android.graphics.Color.rgb(gray, gray, gray)
    }

    /** Corta o texto para caber em [maxW] (com reticências). */
    private fun fit(text: String, p: Paint, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        val n = p.breakText(text, true, maxW - p.measureText("…"), null)
        return text.take(n.coerceAtLeast(0)) + "…"
    }

    private fun Canvas.textRight(text: String, x: Float, y: Float, p: Paint) = drawText(text, x - p.measureText(text), y, p)

    /** Parte o texto em linhas de largura [maxW]. */
    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split('\n')) {
            var line = ""
            for (word in para.split(' ')) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(next) <= maxW || line.isEmpty()) line = next else { out += line; line = word }
            }
            out += line
        }
        return out
    }

    fun safeName(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|]+"), "-").take(60)

    private fun yearReceipts(all: List<Receipt>, year: Int): List<Receipt> =
        all.filter { !it.isTrashed && !it.locked && it.date.startsWith(year.toString()) }.sortedBy { it.date + it.time }

    // ---- Um recibo ---------------------------------------------------------------------

    fun receipt(dir: File, r: Receipt, household: List<Member>, photo: ByteArray?): File {
        dir.mkdirs()
        val doc = PdfDocument()
        try {
            var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
            var c = page.canvas
            var y = 64f
            c.drawText(fit(r.title.ifBlank { "—" }, paint(20f, true), W - 2 * M), M, y, paint(20f, true))
            y += 20
            val date = RcFormat.parseYmd(r.date)
            c.drawText(RcDates.long(date) + if (r.time.isNotEmpty()) " · ${r.time}" else "", M, y, paint(10f, gray = 110))
            y += 40
            c.drawText(RcFormat.money(r.total), M, y, paint(28f, true))
            y += 30

            val st = IrsRules.efStatus(r, household)
            val vatLabel = if (r.vatExempt || (r.vat == 0L && r.source == "qr")) rt("detail.vat_exempt")
            else "${rt("vat_short")}${AtQr.mainVatRate(r.vatLines, r.vat)?.let { " $it%" } ?: ""} · ${RcFormat.money(r.vat)}"
            val rows = listOfNotNull(
                rt("field.merchant_nif") to RcFormat.spacedNif(r.merchantNif),
                rt("field.your_nif") to (r.buyerNif.takeIf { it.isNotEmpty() }?.let(RcFormat::spacedNif) ?: rt("detail.missing")),
                rt("field.vat") to vatLabel,
                rt("app.irs_category") to "${irsLabel(r.irsCat)} · ${irsPt(r.irsCat)}",
                rt("detail.spending_category") to catLongLabel(r.spendCat),
                rt("nav.efatura") to rt("status.$st"),
                r.atcud.takeIf { it.isNotEmpty() }?.let { rt("field.atcud") to it },
                r.docNo.takeIf { it.isNotEmpty() }?.let { rt("field.doc_no") to it },
                r.note.takeIf { it.isNotEmpty() }?.let { rt("field.note") to it },
            )
            val label = paint(10f, gray = 110)
            val value = paint(11f, true)
            val line = paint(1f, gray = 225)
            for ((k, v) in rows) {
                c.drawLine(M, y - 14, W - M, y - 14, line)
                c.drawText(k, M, y, label)
                val lines = wrap(v, value, W - M - 190f)
                lines.forEachIndexed { i, l -> c.drawText(l, 190f, y + i * 14, value) }
                y += 22 + (lines.size - 1) * 14
            }

            val bmp = photo?.let { ReceiptsImages.decodeSampled(it, 2000) }
            if (bmp != null) {
                var top = y + 10
                var maxH = H - M - top
                if (maxH < 260) {
                    doc.finishPage(page)
                    page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 2).create())
                    c = page.canvas
                    top = M
                    maxH = H - 2 * M
                }
                val maxW = W - 2 * M
                val s = min(maxW / bmp.width, maxH / bmp.height)
                val w = bmp.width * s
                val h = bmp.height * s
                val left = (W - w) / 2
                c.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
                bmp.recycle()
            }
            doc.finishPage(page)
            val file = File(dir, "rc-${safeName(r.date)}_${safeName(r.title.ifBlank { "receipt" })}.pdf")
            file.outputStream().use { doc.writeTo(it) }
            return file
        } finally {
            doc.close()
        }
    }

    // ---- IRS summary -------------------------------------------------------------------

    fun irsSummary(out: OutputStream, receipts: List<Receipt>, year: Int, household: List<Member>) {
        val doc = PdfDocument()
        try {
            val est = IrsRules.estimate(receipts.filter { !it.isTrashed }, year, household)
            val rules = IrsRules.rulesFor(year)
            var n = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            var c = page.canvas
            var y = 56f
            c.drawText(rt("pdf.title", "year" to year), M, y, paint(18f, true))
            y += 18
            c.drawText(rt("pdf.generated", "date" to RcFormat.ymd(LocalDate.now())), M, y, paint(9f, gray = 110))
            y += 28
            c.drawText(rt("irs_page.estimated"), M, y, paint(11f, true))
            c.drawText(RcFormat.money(est.total), M, y + 26, paint(22f, true))
            c.drawText(rt("irs_page.from_expenses", "amount" to RcFormat.money(est.expenses), "count" to est.count), M, y + 42, paint(9f, gray = 90))
            y += 70

            val cols = floatArrayOf(M, 230f, 330f, 430f, W - M)
            val head = paint(8.5f, true, 110)
            listOf(rt("irs_page.col_category"), rt("irs_page.col_spent"), rt("pdf.col_limit"), rt("irs_page.col_deduction")).forEachIndexed { i, h ->
                val t = h.uppercase()
                if (i == 3) c.textRight(t, cols[4], y, head) else c.drawText(t, cols[i], y, head)
            }
            y += 8
            val rule = paint(1f, gray = 225)
            c.drawLine(M, y, W - M, y, rule)
            y += 18
            val body = paint(10f)
            for (row in est.rows) {
                c.drawText(fit("${irsLabel(row.key)} (${irsPt(row.key)})", body, cols[1] - cols[0] - 8), cols[0], y, body)
                c.drawText(if (row.key == "iva") "${RcFormat.money(row.vat)} ${rt("vat_short")}" else RcFormat.money(row.spent), cols[1], y, body)
                c.drawText("${((rules[row.key]?.rate ?: 0.0) * 100).roundToInt()}% · ${RcFormat.money(row.cap, whole = true)}", cols[2], y, body)
                c.textRight(RcFormat.money(row.deduction), cols[4], y, body)
                y += 20
            }
            c.drawLine(M, y - 8, W - M, y - 8, rule)
            val bold = paint(10f, true)
            c.drawText(rt("pdf.total"), cols[0], y + 6, bold)
            c.textRight(RcFormat.money(est.total), cols[4], y + 6, bold)
            y += 30
            val small = paint(8f, gray = 120)
            for (l in wrap(rt("irs_page.disclaimer"), small, W - 2 * M)) { c.drawText(l, M, y, small); y += 10 }
            y += 24

            c.drawText(rt("pdf.receipts"), M, y, paint(11f, true))
            y += 16
            val p60 = paint(8f, gray = 60)
            val p20 = paint(8f)
            val p90 = paint(8f, gray = 90)
            for (r in yearReceipts(receipts, year)) {
                if (y > H - M) {
                    doc.finishPage(page)
                    page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++n).create())
                    c = page.canvas
                    y = 56f
                }
                c.drawText(r.date, M, y, p60)
                c.drawText(fit(r.merchant.ifBlank { "—" }.take(38), p20, 185f), 110f, y, p20)
                c.drawText(r.merchantNif, 300f, y, p90)
                c.drawText(fit(irsLabel(r.irsCat), p90, 76f), 370f, y, p90)
                c.drawText(fit(rt("status.${IrsRules.efStatus(r, household)}"), p90, 70f), 450f, y, p90)
                c.textRight(RcFormat.money(r.total), W - M, y, p20)
                y += 13
            }
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    // ---- CSV ---------------------------------------------------------------------------

    /** = csvCell da web: entre aspas e sem virar fórmula (=, +, -, @ no início). */
    private fun csvCell(value: String): String {
        val numeric = Regex("^-?\\d+(\\.\\d+)?$").matches(value)
        val safe = if (!numeric && Regex("^[=+\\-@\\t\\r]").containsMatchIn(value)) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    private fun euros(cents: Long): String = String.format(java.util.Locale.ROOT, "%.2f", cents / 100.0)

    fun csv(out: OutputStream, receipts: List<Receipt>, year: Int, household: List<Member>): Int {
        val head = listOf("date", "time", "merchant", "merchant_nif", "buyer_nif", "total_eur", "vat_eur", "atcud", "document", "spending_category", "irs_category", "efatura", "source", "note")
        val list = yearReceipts(receipts, year)
        val rows = list.map { r ->
            listOf(
                r.date, r.time, r.merchant, r.merchantNif, r.buyerNif, euros(r.total), euros(r.vat), r.atcud, r.docNo,
                rt("cat.${r.spendCat}"), rt("irs.${r.irsCat.ifEmpty { "none" }}"), rt("status.${IrsRules.efStatus(r, household)}"), r.source, r.note,
            )
        }
        val text = "﻿" + (listOf(head) + rows).joinToString("\n") { row -> row.joinToString(",") { csvCell(it) } }
        out.write(text.toByteArray(Charsets.UTF_8))
        return list.size
    }

    // ---- ZIP das fotos ----------------------------------------------------------------

    /** Uma foto de cada vez (decifrada, escrita, largada). Nomes repetidos ganham " (1)"… */
    suspend fun photosZip(
        out: OutputStream,
        receipts: List<Receipt>,
        year: Int,
        load: suspend (String) -> ByteArray?,
        onProgress: (Int, Int) -> Unit,
    ): Int {
        val list = yearReceipts(receipts, year).filter { it.hasPhoto }
        var count = 0
        val used = HashSet<String>()
        ZipOutputStream(out).use { zip ->
            list.forEachIndexed { i, r ->
                val bytes = load(r.id)
                onProgress(i + 1, list.size)
                if (bytes == null) return@forEachIndexed
                val base = "${r.date}_${safeName(r.merchant.ifBlank { r.merchantNif.ifBlank { "receipt" } })}"
                var name = "$base.jpg"
                var k = 1
                while (!used.add(name)) name = "$base (${k++}).jpg"
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
                count++
            }
        }
        return count
    }
}
