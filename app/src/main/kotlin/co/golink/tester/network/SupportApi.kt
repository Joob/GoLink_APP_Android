package co.golink.tester.network

import co.golink.tester.domain.support.ConfirmDeletionRequest
import co.golink.tester.domain.support.ConfirmDeletionResponse
import co.golink.tester.domain.support.MessageEnvelope
import co.golink.tester.domain.support.TicketEnvelope
import co.golink.tester.domain.support.TicketPage
import co.golink.tester.domain.support.UpdateTicketRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface SupportApi {
    // --- Utilizador (routes/api.php, prefixo support) ---
    @GET("api/support/tickets")
    suspend fun tickets(
        @Query("page") page: Int = 1,
        @Query("status") status: String? = null,
    ): Response<TicketPage>

    @GET("api/support/tickets/{id}")
    suspend fun ticket(@Path("id") id: String): Response<TicketEnvelope>

    @Multipart
    @POST("api/support/tickets")
    suspend fun createTicket(
        @Part("subject") subject: RequestBody,
        @Part("body") body: RequestBody,
        @Part("priority") priority: RequestBody,
        @Part("category") category: RequestBody,
        @Part attachments: List<MultipartBody.Part>,
    ): Response<TicketEnvelope>

    @Multipart
    @POST("api/support/tickets/{id}/messages")
    suspend fun reply(
        @Path("id") id: String,
        @Part("body") body: RequestBody?,
        @Part attachments: List<MultipartBody.Part>,
    ): Response<MessageEnvelope>

    @POST("api/support/assist")
    suspend fun assist(@Body body: co.golink.tester.domain.support.SupportAssistRequest): Response<co.golink.tester.domain.support.SupportAssistResponse>

    @POST("api/support/tickets/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<ResponseBody>

    @POST("api/support/tickets/{id}/close")
    suspend fun close(@Path("id") id: String): Response<ResponseBody>

    @POST("api/support/tickets/{id}/deletion-request/{rid}/confirm")
    suspend fun confirmDeletion(
        @Path("id") id: String,
        @Path("rid") requestId: String,
        @Body body: ConfirmDeletionRequest,
    ): Response<ConfirmDeletionResponse>

    @POST("api/support/tickets/{id}/deletion-request/{rid}/cancel")
    suspend fun cancelDeletion(@Path("id") id: String, @Path("rid") requestId: String): Response<ResponseBody>

    // --- Admin (routes/admin.php, prefixo admin/support) ---
    @GET("api/admin/support/tickets")
    suspend fun adminTickets(
        @Query("page") page: Int = 1,
        @Query("status") status: String? = null,
        @Query("q") query: String? = null,
        /** me | unassigned | <id do agente> */
        @Query("assigned") assigned: String? = null,
        @Query("priority") priority: String? = null,
        @Query("category") category: String? = null,
    ): Response<TicketPage>

    @GET("api/admin/support/counts")
    suspend fun adminCounts(): Response<co.golink.tester.domain.support.SupportCounts>

    @GET("api/admin/support/dashboard")
    suspend fun adminDashboard(@Query("range") range: Int = 30): Response<co.golink.tester.domain.support.SupportDashboard>

    @GET("api/admin/support/tickets/{id}")
    suspend fun adminTicket(@Path("id") id: String): Response<TicketEnvelope>

    @Multipart
    @POST("api/admin/support/tickets/{id}/messages")
    suspend fun adminReply(
        @Path("id") id: String,
        @Part("body") body: RequestBody?,
        @Part attachments: List<MultipartBody.Part>,
    ): Response<MessageEnvelope>

    @POST("api/admin/support/tickets/{id}/read")
    suspend fun adminMarkRead(@Path("id") id: String): Response<ResponseBody>

    @PATCH("api/admin/support/tickets/{id}")
    suspend fun adminUpdate(@Path("id") id: String, @Body body: UpdateTicketRequest): Response<ResponseBody>

    @GET("api/admin/support/agents")
    suspend fun adminAgents(): Response<co.golink.tester.domain.support.SupportAgentsResponse>

    /** Corpo cru: precisa de enviar `assigned_to: null` explícito para largar o ticket. */
    @PATCH("api/admin/support/tickets/{id}")
    suspend fun adminAssign(
        @Path("id") id: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<ResponseBody>

    @DELETE("api/admin/support/tickets/{id}")
    suspend fun adminDelete(@Path("id") id: String): Response<ResponseBody>

    // Copiloto (Claude)
    @POST("api/admin/support/tickets/{id}/ai/draft")
    suspend fun adminAiDraft(@Path("id") id: String): Response<co.golink.tester.domain.support.AiDraftResponse>

    @POST("api/admin/support/tickets/{id}/ai/summary")
    suspend fun adminAiSummary(@Path("id") id: String): Response<co.golink.tester.domain.support.AiSummaryResponse>

    @POST("api/admin/support/tickets/{id}/deletion-request")
    suspend fun adminRequestDeletion(@Path("id") id: String): Response<ResponseBody>

    @DELETE("api/admin/support/tickets/{id}/deletion-request/{rid}")
    suspend fun adminCancelDeletion(@Path("id") id: String, @Path("rid") requestId: String): Response<ResponseBody>
}
