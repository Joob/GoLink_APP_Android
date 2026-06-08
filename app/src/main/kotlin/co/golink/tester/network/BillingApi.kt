package co.golink.tester.network

import co.golink.tester.domain.billing.CheckoutResponse
import co.golink.tester.domain.billing.PlansResponse
import co.golink.tester.domain.billing.StripeCheckoutRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface BillingApi {
    @GET("api/subscriptions/plans")
    suspend fun listPlans(): Response<PlansResponse>

    @POST("api/stripe/checkout")
    suspend fun stripeCheckout(@Body body: StripeCheckoutRequest): Response<CheckoutResponse>
}
