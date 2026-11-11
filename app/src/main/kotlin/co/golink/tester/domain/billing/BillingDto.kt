package co.golink.tester.domain.billing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/*
 * Billing no Android é SÓ LEITURA: plano, uso, planos, cartões e faturas.
 * Não há checkout, mudança de plano, cartões nem cancelamento na app
 * (política de pagamentos da Play) — isso faz-se na versão web.
 */

@Serializable
data class PlansResponse(
    val data: List<PlanEnvelope> = emptyList(),
)

@Serializable
data class PlanEnvelope(
    val data: PlanData,
)

@Serializable
data class PlanData(
    val id: String,
    val type: String? = null,
    val attributes: PlanAttributes,
)

@Serializable
data class PlanAttributes(
    val name: String,
    val description: String? = null,
    val type: String? = null,
    val status: String? = null,
    val currency: String? = null,
    val price: String? = null,
    val amount: Double? = null,
    val interval: String? = null,
    val features: Map<String, JsonElement> = emptyMap(),
)

data class Plan(
    val id: String,
    val name: String,
    val description: String?,
    val price: String?,
    val amount: Double?,
    val interval: String?,
    // Feature key → valor numérico (GB / membros); -1 = ilimitado (mesmo contrato da web).
    val features: Map<String, Double>,
) {
    val storageGb: Double get() = features["max_storage_amount"] ?: 0.0
    val members: Double get() = features["max_team_members"] ?: 0.0
    val uploadGb: Double get() = features["max_upload_size"] ?: -1.0
}

fun PlanData.toPlan(): Plan = Plan(
    id = id,
    name = attributes.name,
    description = attributes.description,
    price = attributes.price,
    amount = attributes.amount,
    interval = attributes.interval,
    features = attributes.features.mapNotNull { (key, value) ->
        (value as? JsonPrimitive)?.doubleOrNull?.let { key to it }
    }.toMap(),
)

// ── GET api/user/billing ────────────────────────────────────────────────

@Serializable
data class BillingSummary(
    val subscription_type: String? = null,
    val plan: CurrentPlan? = null,
    val free: FreePlan = FreePlan(),
    val usage: BillingUsage = BillingUsage(),
    val cards: List<BillingCard> = emptyList(),
    val donated: Donated = Donated(),
    val failed_payments: Int = 0,
    val recommended_plan_id: String? = null,
    val billing: BillingDetails = BillingDetails(),
)

@Serializable
data class CurrentPlan(
    val id: String,
    val name: String,
    val interval: String? = null,
    val amount: Double? = null,
    val price: String? = null,
    val features: Map<String, JsonElement> = emptyMap(),
    val status: String? = null,
    val driver: String? = null,
    val is_cancelled: Boolean = false,
    val renews_at: String? = null,
    val ends_at: String? = null,
    val is_past_due: Boolean = false,
    val days_left_to_pay: Int = 0,
) {
    private fun feature(key: String, default: Double) =
        (features[key] as? JsonPrimitive)?.doubleOrNull ?: default

    val storageGb: Double get() = feature("max_storage_amount", 0.0)
    val members: Double get() = feature("max_team_members", 0.0)
    val uploadGb: Double get() = feature("max_upload_size", -1.0)
}

@Serializable
data class FreePlan(
    val storage_gb: Double = 0.0,
    val team_members: Int = 0,
    val upload_bytes: Long? = null,
)

@Serializable
data class BillingUsage(
    val storage_used: Long = 0,
    val storage_used_formatted: String? = null,
    val storage_total: Long? = null,
    val storage_total_formatted: String? = null,
    val storage_percent: Double = 0.0,
    val team_used: Int = 0,
    val team_total: Int? = null,
)

@Serializable
data class BillingCard(
    val id: String,
    val brand: String? = null,
    val last4: String? = null,
    val expires: String? = null,
    val is_default: Boolean = false,
    val is_expired: Boolean = false,
    val expires_soon: Boolean = false,
)

@Serializable
data class Donated(
    val total: Double = 0.0,
    val total_formatted: String? = null,
    val since: String? = null,
    val invoices: Int = 0,
)

@Serializable
data class BillingDetails(
    val name: String? = null,
    val profile_name: String? = null,
    val vat_number: String? = null,
    val invoice_email: String? = null,
    val account_email: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postal_code: String? = null,
    val country: String? = null,
)

// ── GET api/subscriptions/transactions ──────────────────────────────────

@Serializable
data class InvoicesResponse(
    val data: List<InvoiceEnvelope> = emptyList(),
    val meta: PageMeta? = null,
)

@Serializable
data class PageMeta(
    val current_page: Int = 1,
    val last_page: Int = 1,
)

@Serializable
data class InvoiceEnvelope(
    val data: InvoiceData,
)

@Serializable
data class InvoiceData(
    val id: String,
    val attributes: InvoiceAttributes,
)

@Serializable
data class InvoiceAttributes(
    val type: String? = null,
    val status: String? = null,
    val note: String? = null,
    val price: String? = null,
    val driver: String? = null,
    val created_at: String? = null,
    val created_at_iso: String? = null,
    val refunded: Boolean = false,
    val payable: Boolean = false,
    val has_invoice: Boolean = false,
    val invoice_number: String? = null,
)

data class Invoice(
    val id: String,
    val title: String,
    val price: String,
    val status: String,
    val refunded: Boolean,
    val hasPdf: Boolean,
    val reference: String,
    val createdAtIso: String?,
    val createdAt: String?,
)

fun InvoiceData.toInvoice(): Invoice = Invoice(
    id = id,
    title = attributes.note.orEmpty(),
    price = attributes.price.orEmpty(),
    status = attributes.status.orEmpty(),
    refunded = attributes.refunded,
    hasPdf = attributes.has_invoice,
    reference = listOfNotNull(
        attributes.invoice_number,
        when (attributes.driver) { "stripe" -> "Stripe"; "nowpayments" -> "Crypto"; else -> attributes.driver },
    ).joinToString(" · "),
    createdAtIso = attributes.created_at_iso,
    createdAt = attributes.created_at,
)
