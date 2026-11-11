package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.golink.tester.domain.billing.BillingCard
import co.golink.tester.domain.billing.BillingDetails
import co.golink.tester.domain.billing.BillingSummary
import co.golink.tester.domain.billing.Invoice
import co.golink.tester.domain.billing.Plan
import co.golink.tester.domain.user.User
import co.golink.tester.ui.i18n.bt
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Billing no Android: SÓ LEITURA (política de pagamentos da Play).
 * Sem checkout, mudança de plano, cartões, cancelamento nem "Pagar agora", e
 * sem nenhum link/URL para a web. Só: ver plano/uso/planos/cartões/faturas,
 * descarregar faturas pagas e editar os dados de faturação.
 */

@Composable
internal fun BillingPane(
    user: User?,
    summary: BillingSummary?,
    plans: List<Plan>,
    isLoading: Boolean,
    invoices: List<Invoice>,
    hasMoreInvoices: Boolean,
    isLoadingInvoices: Boolean,
    downloadingInvoice: String?,
    onLoadMoreInvoices: () -> Unit,
    onDownload: (Invoice?) -> Unit,
    isSavingDetails: Boolean,
    onSaveDetails: (Map<String, String>) -> Unit,
) {
    PaneColumn {
        if (user?.isPastDue == true || user?.isPaymentSuspended == true) {
            PaymentOverdueBanner(isSuspended = user.isPaymentSuspended, daysLeft = user.daysLeftToPay)
        }
        if (summary == null) {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            WebOnlyNote()
            return@PaneColumn
        }

        CurrentPlanCard(summary, plans)
        PlansSection(summary, plans)
        if (summary.cards.isNotEmpty()) CardsSection(summary.cards)
        InvoicesSection(summary, invoices, hasMoreInvoices, isLoadingInvoices, downloadingInvoice, onLoadMoreInvoices, onDownload)
        BillingDetailsSection(summary.billing, isSavingDetails, onSaveDetails)
        WebOnlyNote()
    }
}

// ── Plano atual + uso ───────────────────────────────────────────────────

@Composable
private fun CurrentPlanCard(summary: BillingSummary, plans: List<Plan>) {
    val plan = summary.plan
    val usage = summary.usage
    val percent = usage.storage_percent.coerceIn(0.0, 100.0)
    val defaultCard = summary.cards.firstOrNull { it.is_default } ?: summary.cards.firstOrNull()

    PaneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SettingsAccent.copy(alpha = 0.14f)),
            ) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = SettingsAccent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(bt("current_plan"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        plan?.name ?: bt("free"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    when {
                        plan == null -> Unit
                        plan.is_past_due -> SettingsBadge(bt("past_due"), SettingsColors.Red)
                        plan.is_cancelled -> SettingsBadge(bt("cancelled"), SettingsColors.Slate)
                        else -> SettingsBadge(bt("active"), SettingsColors.Green)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(plan?.price ?: bt("free"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (plan == null) bt("always") else perInterval(plan.interval),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Linha de funcionalidades: espaço · membros · tamanho por ficheiro.
        val features = if (plan != null) {
            featureLine(plan.storageGb, plan.members, uploadLabel(plan.uploadGb))
        } else {
            featureLine(summary.free.storage_gb, summary.free.team_members.toDouble(), uploadBytesLabel(summary.free.upload_bytes))
        }
        Text(features, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (plan != null) {
            val line = when {
                plan.is_cancelled -> plan.ends_at?.let { bt("ends", "date" to day(it)) }
                else -> plan.renews_at?.let { date ->
                    listOfNotNull(
                        bt("renews", "date" to day(date)),
                        defaultCard?.let { bt("charged_to", "card" to cardLabel(it)) },
                    ).joinToString(" · ")
                }
            }
            line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bt("usage"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                bt("percent_used", "p" to percent.roundToInt()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = usageColor(percent),
            )
        }
        Text(
            bt("used_of", "used" to (usage.storage_used_formatted ?: "0 B"), "total" to (usage.storage_total_formatted ?: "∞")),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LinearProgressIndicator(
            progress = { (percent / 100.0).toFloat() },
            color = usageColor(percent),
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        )

        // Aviso âmbar ≥ 80 %: só texto (a mudança de plano é feita na web).
        if (percent >= 80) {
            val recommended = plans.firstOrNull { it.id == summary.recommended_plan_id }
            if (recommended != null) {
                SettingsNotice(
                    bt("warn_storage", "p" to percent.roundToInt(), "plan" to recommended.name, "size" to gbLabel(recommended.storageGb)),
                    SettingsColors.Amber,
                    Icons.Outlined.Info,
                )
            }
        }
    }
}

// ── Planos (só consulta: sem seleção nem botão) ─────────────────────────

@Composable
private fun PlansSection(summary: BillingSummary, plans: List<Plan>) {
    if (plans.isEmpty()) return
    val current = summary.plan
    var interval by remember(current?.interval) { mutableStateOf(if (current?.interval == "year") "year" else "month") }
    val hasYearly = plans.any { it.interval == "year" }
    val hasMonthly = plans.any { it.interval == "month" }
    val recommendedName = plans.firstOrNull { it.id == summary.recommended_plan_id }?.name

    // "N meses grátis": compara o anual com 12× o mensal do mesmo nome.
    val monthsFree = plans.filter { it.interval == "year" }.mapNotNull { y ->
        val m = plans.firstOrNull { it.interval == "month" && it.name == y.name }?.amount ?: return@mapNotNull null
        val ya = y.amount ?: return@mapNotNull null
        if (m <= 0) null else ((m * 12 - ya) / m).roundToInt()
    }.maxOrNull() ?: 0

    PaneSection(bt("plans_title"))
    PaneCard {
        if (hasYearly && hasMonthly) {
            val yearly = if (monthsFree >= 1) {
                "${bt("yearly")} · ${if (monthsFree == 1) bt("month_free") else bt("months_free", "n" to monthsFree)}"
            } else {
                bt("yearly")
            }
            SettingsSegmented(listOf("month" to bt("monthly"), "year" to yearly), interval) { interval = it }
        }

        val shown = plans.filter { it.interval == interval || !(hasYearly && hasMonthly) }.sortedBy { it.amount ?: 0.0 }
        Column {
            PlanRow(
                name = bt("free"),
                details = featureLine(summary.free.storage_gb, summary.free.team_members.toDouble(), uploadBytesLabel(summary.free.upload_bytes)),
                price = bt("free"),
                per = bt("always"),
                isCurrent = current == null,
                fitsYou = false,
            )
            shown.forEach { p ->
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                PlanRow(
                    name = p.name,
                    details = featureLine(p.storageGb, p.members, uploadLabel(p.uploadGb)),
                    price = p.price.orEmpty(),
                    per = perInterval(p.interval),
                    isCurrent = current?.id == p.id,
                    fitsYou = recommendedName != null && p.name == recommendedName && current?.id != p.id,
                )
            }
        }
        Text(bt("donation_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanRow(name: String, details: String, price: String, per: String, isCurrent: Boolean, fitsYou: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (isCurrent) SettingsBadge(bt("current").uppercase(), SettingsColors.Green)
                if (fitsYou) SettingsBadge(bt("fits_you").uppercase(), SettingsColors.Amber)
            }
            Spacer(Modifier.height(2.dp))
            Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(per, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Métodos de pagamento (só leitura) ───────────────────────────────────

@Composable
private fun CardsSection(cards: List<BillingCard>) {
    PaneSection(bt("pm_title"))
    PaneCard {
        Text(bt("pm_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            cards.forEachIndexed { i, card ->
                if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    CardVisual(card.brand)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cardLabel(card), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        card.expires?.let {
                            Text(
                                if (card.is_expired) bt("pm_expired") else bt("pm_expires", "date" to it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (card.is_default) SettingsBadge(bt("pm_default"), SettingsColors.Green) else SettingsBadge(bt("pm_backup"), SettingsColors.Slate)
                        when {
                            card.is_expired -> SettingsBadge(bt("pm_expired"), SettingsColors.Red)
                            card.expires_soon -> SettingsBadge(bt("pm_expires_soon"), SettingsColors.Amber)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardVisual(brand: String?) {
    val colors = when (brand?.lowercase()) {
        "visa" -> listOf(Color(0xFF1A1F71), Color(0xFF3B4CCA))
        "mastercard" -> listOf(Color(0xFF2B2B2B), Color(0xFFEB5E28))
        "amex", "american express" -> listOf(Color(0xFF0E6EB8), Color(0xFF3FA9F5))
        else -> listOf(Color(0xFF334155), Color(0xFF64748B))
    }
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = Modifier.size(width = 46.dp, height = 30.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(colors)).padding(4.dp),
    ) {
        Text(brandName(brand).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ── Faturas ─────────────────────────────────────────────────────────────

@Composable
private fun InvoicesSection(
    summary: BillingSummary,
    invoices: List<Invoice>,
    hasMore: Boolean,
    isLoading: Boolean,
    downloading: String?,
    onLoadMore: () -> Unit,
    onDownload: (Invoice?) -> Unit,
) {
    val canZip = invoices.any { it.hasPdf } && downloading == null
    PaneSection(bt("inv_title"), action = if (canZip) bt("inv_zip") else null, onAction = { onDownload(null) })
    PaneCard {
        val donated = summary.donated
        if (donated.invoices > 0 && donated.since != null) {
            val params = arrayOf("n" to donated.invoices, "total" to (donated.total_formatted ?: ""), "date" to day(donated.since))
            Text(
                if (donated.invoices == 1) bt("inv_summary_one", *params) else bt("inv_summary", *params),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (downloading == "all") LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp), color = SettingsAccent)

        when {
            invoices.isEmpty() && isLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            invoices.isEmpty() -> Text(bt("inv_empty"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Column {
                invoices.forEachIndexed { i, inv ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    InvoiceRow(inv, downloading = downloading == inv.id, canDownload = downloading == null) { onDownload(inv) }
                }
                if (hasMore) {
                    TextButton(onClick = onLoadMore, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(bt("show_more"), color = SettingsAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceRow(inv: Invoice, downloading: Boolean, canDownload: Boolean, onDownload: () -> Unit) {
    // Sem "Pagar agora" na app: uma fatura falhada só mostra o estado.
    val (label, color) = when {
        inv.refunded -> bt("st_refunded") to SettingsColors.Slate
        inv.status == "completed" -> bt("st_paid") to SettingsColors.Green
        inv.status == "error" -> bt("st_failed") to SettingsColors.Red
        inv.status == "cancelled" -> bt("st_cancelled") to SettingsColors.Slate
        else -> inv.status to SettingsColors.Slate
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(inv.title.ifBlank { bt("col_description") }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(inv.createdAtIso?.let(::day) ?: inv.createdAt, inv.reference.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(inv.price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            SettingsBadge(label, color)
        }
        if (inv.hasPdf) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onDownload, enabled = canDownload) {
                        Icon(Icons.Outlined.Download, contentDescription = bt("download_pdf"), tint = SettingsAccent)
                    }
                }
            }
        }
    }
}

// ── Dados de faturação (editáveis: não é pagamento) ─────────────────────

@Composable
private fun BillingDetailsSection(details: BillingDetails, isSaving: Boolean, onSave: (Map<String, String>) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    PaneSection(bt("bd_title"), action = if (editing) null else bt("edit"), onAction = { editing = true })
    PaneCard {
        Text(bt("bd_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editing) {
            BillingDetailsForm(details, isSaving, onCancel = { editing = false }) { changes ->
                onSave(changes)
                editing = false
            }
        } else {
            DetailRow(bt("bd_name"), details.name?.ifBlank { null } ?: details.profile_name)
            DetailRow(bt("bd_vat"), details.vat_number)
            DetailRow(bt("bd_address"), addressLines(details))
            DetailRow(bt("bd_email"), details.invoice_email?.ifBlank { null } ?: details.account_email)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(
            value?.ifBlank { null } ?: bt("not_added"),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (value.isNullOrBlank()) FontWeight.Normal else FontWeight.SemiBold,
            color = if (value.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ColumnScope.BillingDetailsForm(
    details: BillingDetails,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    val original = mapOf(
        "billing_name" to details.name.orEmpty(),
        "vat_number" to details.vat_number.orEmpty(),
        "address" to details.address.orEmpty(),
        "postal_code" to details.postal_code.orEmpty(),
        "city" to details.city.orEmpty(),
        "state" to details.state.orEmpty(),
        "country" to details.country.orEmpty(),
        "invoice_email" to details.invoice_email.orEmpty(),
    )
    var values by remember(details) { mutableStateOf(original) }
    fun set(key: String, v: String) { values = values + (key to v) }
    val changes = values.filter { (k, v) -> v.trim() != original[k]?.trim() }.mapValues { it.value.trim() }

    SettingsField(bt("bd_name"), values.getValue("billing_name"), { set("billing_name", it) }, placeholder = details.profile_name)
    SettingsField(bt("bd_vat"), values.getValue("vat_number"), { set("vat_number", it) })
    SettingsField(bt("street"), values.getValue("address"), { set("address", it) })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsField(bt("postal_code"), values.getValue("postal_code"), { set("postal_code", it) }, modifier = Modifier.weight(1f))
        SettingsField(bt("city"), values.getValue("city"), { set("city", it) }, modifier = Modifier.weight(1f))
    }
    SettingsField(bt("state"), values.getValue("state"), { set("state", it) })
    Column {
        Text(bt("country"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        CountryDropdown(value = values.getValue("country"), onValueChange = { set("country", it) })
    }
    SettingsField(
        bt("bd_email"),
        values.getValue("invoice_email"),
        { set("invoice_email", it) },
        keyboardType = KeyboardType.Email,
        supportingText = details.account_email?.let { bt("bd_email_hint", "email" to it) },
        imeAction = ImeAction.Done,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel, enabled = !isSaving) { Text(bt("cancel")) }
        SettingsPrimaryButton(
            text = bt("save"),
            onClick = { onSave(changes) },
            enabled = changes.isNotEmpty(),
            loading = isSaving,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Nota fixa: sem link, URL nem botão (anti-steering da Play) ──────────

@Composable
private fun WebOnlyNote() {
    SettingsNotice(bt("web_only"), SettingsColors.Slate, Icons.Outlined.Info)
}

// ── Formatação (igual a billingFormat.js; base 1024) ────────────────────

private fun gbLabel(gb: Double): String = when {
    gb < 0 -> bt("unlimited")
    gb >= 1024 -> "${trim(gb / 1024)} TB"
    else -> "${trim(gb)} GB"
}

private fun trim(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v).trimEnd('0').trimEnd('.')

private fun bytesLabel(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "${trim(v)} ${units[i]}"
}

private fun uploadLabel(gb: Double): String =
    if (gb <= 0) bt("no_file_limit") else bt("files_up_to", "size" to gbLabel(gb))

private fun uploadBytesLabel(bytes: Long?): String =
    if (bytes == null || bytes <= 0) bt("no_file_limit") else bt("files_up_to", "size" to bytesLabel(bytes))

private fun membersLabel(n: Double): String =
    if (n < 0) "${bt("unlimited")} · ${bt("col_members").lowercase()}" else bt("members_label", "n" to n.toLong())

private fun featureLine(storageGb: Double, members: Double, upload: String): String =
    listOf(bt("storage_label", "size" to gbLabel(storageGb)), membersLabel(members), upload).joinToString(" · ")

private fun usageColor(percent: Double): Color = when {
    percent >= 95 -> SettingsColors.Red
    percent >= 80 -> SettingsColors.Amber
    else -> SettingsAccent
}

private fun perInterval(interval: String?): String = if (interval == "year") bt("per_year") else bt("per_month")

private fun brandName(brand: String?): String = when (brand?.lowercase()) {
    null, "" -> "Card"
    "amex", "american express" -> "Amex"
    "mastercard" -> "Mastercard"
    else -> brand.replaceFirstChar { it.uppercase() }
}

private fun cardLabel(card: BillingCard): String = "${brandName(card.brand)} •••• ${card.last4.orEmpty()}"

private fun day(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault(iso.take(10))

private fun addressLines(d: BillingDetails): String? {
    val country = d.country?.takeIf { it.length == 2 }?.let { Locale("", it).displayCountry } ?: d.country
    return listOf(
        d.address,
        listOfNotNull(d.postal_code?.ifBlank { null }, d.city?.ifBlank { null }).joinToString(" "),
        d.state,
        country,
    ).mapNotNull { it?.trim()?.ifBlank { null } }.joinToString(", ").ifBlank { null }
}
