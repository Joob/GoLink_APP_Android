package co.golink.tester.data.billing

import co.golink.tester.domain.billing.NowPaymentsCheckoutRequest
import co.golink.tester.domain.billing.Plan
import co.golink.tester.domain.billing.StripeCheckoutRequest
import co.golink.tester.domain.billing.toPlan
import co.golink.tester.network.BillingApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepository @Inject constructor(
    private val api: BillingApi,
) {
    suspend fun listPlans(): Result<List<Plan>> = runCatching {
        val response = api.listPlans()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val envelopes = response.body()?.data ?: error("Resposta vazia")
        envelopes.map { it.data.toPlan() }
    }

    suspend fun createStripeCheckout(stripePriceId: String): Result<String> = runCatching {
        val response = api.stripeCheckout(StripeCheckoutRequest(planCode = stripePriceId))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.url ?: error("Sem URL no payload")
    }

    suspend fun createCryptoCheckout(planId: String): Result<String> = runCatching {
        val response = api.nowPaymentsCheckout(NowPaymentsCheckoutRequest(planId = planId))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.data?.invoice_url ?: error("Sem URL no payload")
    }
}
