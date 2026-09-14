package co.golink.tester.domain.billing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

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
    val meta: PlanMeta? = null,
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

@Serializable
data class PlanMeta(
    val driver_plan_id: Map<String, String> = emptyMap(),
)

@Serializable
data class StripeCheckoutRequest(
    val planCode: String,
)

@Serializable
data class CheckoutResponse(
    val type: String? = null,
    val message: String? = null,
    val url: String? = null,
)

// O checkout crypto usa o id do plano local: não existe plano espelhado no
// gateway, ao contrário do Stripe.
@Serializable
data class NowPaymentsCheckoutRequest(
    val planId: String,
)

@Serializable
data class NowPaymentsCheckoutResponse(
    val type: String? = null,
    val message: String? = null,
    val data: NowPaymentsCheckoutData? = null,
)

@Serializable
data class NowPaymentsCheckoutData(
    val invoice_url: String? = null,
)

data class Plan(
    val id: String,
    val name: String,
    val description: String?,
    val price: String?,
    val amount: Double?,
    val interval: String?,
    // Feature key → numeric value; -1 means unlimited (same contract as the web plans grid).
    val features: Map<String, Long>,
    val stripePriceId: String?,
)

fun PlanData.toPlan(): Plan = Plan(
    id = id,
    name = attributes.name,
    description = attributes.description,
    price = attributes.price,
    amount = attributes.amount,
    interval = attributes.interval,
    features = attributes.features.mapNotNull { (key, value) ->
        (value as? JsonPrimitive)?.longOrNull?.let { key to it }
    }.toMap(),
    stripePriceId = meta?.driver_plan_id?.get("stripe"),
)
