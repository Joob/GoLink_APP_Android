package co.golink.tester.network

import co.golink.tester.domain.billing.BillingSummary
import co.golink.tester.domain.billing.InvoicesResponse
import co.golink.tester.domain.billing.PlansResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Billing só de leitura. De propósito NÃO há endpoints de checkout, troca de
 * plano, cartões ou cancelamento: pagamentos só na web (política da Play).
 */
interface BillingApi {
    @GET("api/user/billing")
    suspend fun summary(): Response<BillingSummary>

    @GET("api/subscriptions/plans")
    suspend fun listPlans(): Response<PlansResponse>

    @GET("api/subscriptions/transactions")
    suspend fun invoices(@Query("page") page: Int = 1): Response<InvoicesResponse>

    /** PDF de uma fatura paga (rota web; aceita o Bearer do Sanctum). */
    @Streaming
    @GET("invoices/{id}")
    suspend fun invoicePdf(@Path("id") id: String, @Query("format") format: String = "pdf"): Response<ResponseBody>

    /** Todas as faturas pagas em PDF, num ZIP. */
    @Streaming
    @GET("invoices/archive.zip")
    suspend fun invoicesZip(): Response<ResponseBody>
}
