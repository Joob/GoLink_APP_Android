package co.golink.tester.network

import co.golink.tester.domain.admin.AdminCodeRequest
import co.golink.tester.domain.admin.AdminDeleteConfirm
import co.golink.tester.domain.admin.AdminDeleteRequest
import co.golink.tester.domain.admin.AdminEmailChangeRequest
import co.golink.tester.domain.admin.AdminUserListResponse
import co.golink.tester.domain.admin.AuditLogsResponse
import co.golink.tester.domain.admin.ChangeCapacityRequest
import co.golink.tester.domain.admin.ChangeRoleRequest
import co.golink.tester.domain.admin.CreateUserRequest
import co.golink.tester.domain.admin.DeletedUsersResponse
import co.golink.tester.domain.admin.UserStorageResponse
import co.golink.tester.domain.user.UserResponse
import co.golink.tester.domain.admin.AnalyticsResponse
import co.golink.tester.domain.admin.CreateInviteRequest
import co.golink.tester.domain.admin.DashboardResponse
import co.golink.tester.domain.admin.InviteRegistersResponse
import co.golink.tester.domain.admin.SuspendUserRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminApi {
    /** [days]: 7 | 30 | 45 | 90 (o servidor usa 45 por omissão). */
    @GET("api/admin/dashboard")
    suspend fun dashboard(@Query("days") days: Int = 45): Response<DashboardResponse>

    @GET("api/admin/dashboard/newbies")
    suspend fun newbies(): Response<co.golink.tester.domain.admin.AdminUserListResponse>

    @GET("api/admin/dashboard/transactions")
    suspend fun latestTransactions(): Response<co.golink.tester.domain.admin.TransactionsResponse>

    @GET("api/admin/dashboard/encryption-migration")
    suspend fun encryptionMigration(@Query("page") page: Int = 1): Response<co.golink.tester.domain.admin.EncryptionMigrationResponse>

    @GET("api/admin/analytics/realtime")
    suspend fun realtime(): Response<co.golink.tester.domain.admin.RealtimeResponse>

    @GET("api/admin/analytics/product-usage")
    suspend fun events(@Query("range") range: String = "7d"): Response<co.golink.tester.domain.admin.EventsResponse>

    @GET("api/admin/analytics/summary")
    suspend fun analytics(@Query("range") range: String = "7d"): Response<AnalyticsResponse>

    /** [status]: null (todos) | active | suspended. */
    @GET("api/admin/users")
    suspend fun users(
        @Query("page") page: Int = 1,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
    ): Response<AdminUserListResponse>

    @GET("api/admin/users/deleted")
    suspend fun deletedUsers(
        @Query("page") page: Int = 1,
        @Query("search") search: String? = null,
    ): Response<DeletedUsersResponse>

    @DELETE("api/admin/users/{id}/scheduled-deletion")
    suspend fun cancelScheduledDeletion(@Path("id") id: String): Response<ResponseBody>

    @POST("api/admin/users")
    suspend fun createUser(@Body body: CreateUserRequest): Response<UserResponse>

    @GET("api/admin/users/{id}")
    suspend fun user(@Path("id") id: String): Response<UserResponse>

    @PATCH("api/admin/users/{id}/role")
    suspend fun changeRole(@Path("id") id: String, @Body body: ChangeRoleRequest): Response<ResponseBody>

    @GET("api/admin/users/{id}/storage")
    suspend fun userStorage(@Path("id") id: String): Response<UserStorageResponse>

    @PATCH("api/admin/users/{id}/capacity")
    suspend fun changeCapacity(@Path("id") id: String, @Body body: ChangeCapacityRequest): Response<UserStorageResponse>

    @POST("api/admin/users/{id}/reset-password")
    suspend fun resetPassword(@Path("id") id: String): Response<ResponseBody>

    @POST("api/admin/users/{id}/email/change-request")
    suspend fun emailChangeRequest(@Path("id") id: String, @Body body: AdminEmailChangeRequest): Response<ResponseBody>

    @POST("api/admin/users/{id}/email/change-confirm")
    suspend fun emailChangeConfirm(@Path("id") id: String, @Body body: AdminCodeRequest): Response<ResponseBody>

    @POST("api/admin/users/{id}/delete-request")
    suspend fun deleteUserRequest(@Path("id") id: String, @Body body: AdminDeleteRequest): Response<ResponseBody>

    @HTTP(method = "DELETE", path = "api/admin/users/{id}", hasBody = true)
    suspend fun deleteUser(@Path("id") id: String, @Body body: AdminDeleteConfirm): Response<ResponseBody>

    @GET("api/admin/audit-logs")
    suspend fun auditLogs(
        @Query("page") page: Int = 1,
        @Query("action") action: String? = null,
        @Query("failed") failed: Int? = null,
    ): Response<AuditLogsResponse>

    @POST("api/admin/users/{id}/suspend")
    suspend fun suspendUser(
        @Path("id") id: String,
        @Body body: SuspendUserRequest,
    ): Response<ResponseBody>

    @DELETE("api/admin/users/{id}/suspend")
    suspend fun unsuspendUser(@Path("id") id: String): Response<ResponseBody>

    @GET("api/admin/invite-registers")
    suspend fun inviteRegisters(@Query("page") page: Int = 1): Response<InviteRegistersResponse>

    @POST("api/admin/invite-registers")
    suspend fun createInvite(@Body body: CreateInviteRequest): Response<ResponseBody>

    @POST("api/admin/invite-registers/{id}/resend")
    suspend fun resendInvite(@Path("id") id: String): Response<ResponseBody>

    @DELETE("api/admin/invite-registers/{id}")
    suspend fun deleteInvite(@Path("id") id: String): Response<ResponseBody>

    // --- Support Team (só admins) ---
    @GET("api/admin/support-team")
    suspend fun supportTeam(): Response<co.golink.tester.domain.admin.SupportTeamResponse>

    @GET("api/admin/support-team/search-users")
    suspend fun supportTeamSearch(@Query("q") q: String): Response<co.golink.tester.domain.admin.SupportTeamUserSearch>

    @POST("api/admin/support-team")
    suspend fun addAgent(@Body body: co.golink.tester.domain.admin.SupportTeamAgentRequest): Response<co.golink.tester.domain.admin.SupportTeamAgentResponse>

    @PATCH("api/admin/support-team/{id}")
    suspend fun updateAgent(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.SupportTeamAgentRequest): Response<co.golink.tester.domain.admin.SupportTeamAgentResponse>

    @DELETE("api/admin/support-team/{id}")
    suspend fun removeAgent(@Path("id") id: String): Response<ResponseBody>

    // --- FAQ (só admins) ---
    @GET("api/admin/faq")
    suspend fun faqs(): Response<co.golink.tester.domain.admin.FaqListResponse>

    @POST("api/admin/faq")
    suspend fun createFaq(@Body body: co.golink.tester.domain.admin.FaqRequest): Response<co.golink.tester.domain.admin.FaqEntryResponse>

    @PATCH("api/admin/faq/{id}")
    suspend fun updateFaq(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.FaqRequest): Response<co.golink.tester.domain.admin.FaqEntryResponse>

    @DELETE("api/admin/faq/{id}")
    suspend fun deleteFaq(@Path("id") id: String): Response<ResponseBody>

    @POST("api/admin/faq/{id}/translate")
    suspend fun translateFaq(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.FaqTranslateRequest): Response<co.golink.tester.domain.admin.FaqTranslateResponse>

    @POST("api/admin/faq/generate")
    suspend fun generateFaqs(@Body body: co.golink.tester.domain.admin.FaqGenerateRequest): Response<ResponseBody>

    // --- Pages ---
    @GET("api/admin/pages")
    suspend fun pages(): Response<co.golink.tester.domain.admin.PagesResponse>

    @GET("api/admin/pages/{slug}")
    suspend fun page(@Path("slug") slug: String): Response<co.golink.tester.domain.admin.PageEnvelope>

    @POST("api/admin/pages")
    suspend fun createPage(@Body body: co.golink.tester.domain.admin.CreatePageRequest): Response<co.golink.tester.domain.admin.PageEnvelope>

    @PATCH("api/admin/pages/{slug}")
    suspend fun updatePage(@Path("slug") slug: String, @Body body: co.golink.tester.domain.admin.SingleFieldRequest): Response<ResponseBody>

    @POST("api/admin/pages/{slug}/translate")
    suspend fun translatePage(@Path("slug") slug: String, @Body body: co.golink.tester.domain.admin.PageTranslateRequest): Response<co.golink.tester.domain.admin.PageTranslation>

    // --- Languages ---
    @GET("api/admin/languages")
    suspend fun languages(): Response<co.golink.tester.domain.admin.LanguagesResponse>

    @GET("api/admin/languages/{id}")
    suspend fun language(@Path("id") id: String): Response<co.golink.tester.domain.admin.LanguageEnvelope>

    @POST("api/admin/languages")
    suspend fun createLanguage(@Body body: co.golink.tester.domain.admin.CreateLanguageRequest): Response<co.golink.tester.domain.admin.LanguageEnvelope>

    @PATCH("api/admin/languages/{id}")
    suspend fun updateLanguage(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.SingleFieldRequest): Response<ResponseBody>

    @DELETE("api/admin/languages/{id}")
    suspend fun deleteLanguage(@Path("id") id: String): Response<ResponseBody>

    @PATCH("api/admin/languages/{id}/strings")
    suspend fun updateLanguageString(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.SingleFieldRequest): Response<ResponseBody>

    @POST("api/admin/languages/{id}/auto-translate")
    suspend fun autoTranslate(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.AutoTranslateRequest): Response<co.golink.tester.domain.admin.AutoTranslateResponse>

    /** Definições da app (name/value), ex. language = língua por omissão. */
    @PATCH("api/admin/settings")
    suspend fun updateSetting(@Body body: co.golink.tester.domain.admin.SingleFieldRequest): Response<ResponseBody>

    // --- Faturação (pacote saas-billing) ---
    @GET("api/subscriptions/admin/plans")
    suspend fun plans(@Query("page") page: Int = 1): Response<co.golink.tester.domain.admin.PlansResponse>

    @GET("api/subscriptions/admin/plans/{id}")
    suspend fun plan(@Path("id") id: String): Response<co.golink.tester.domain.admin.PlanEnvelope>

    @POST("api/subscriptions/admin/plans")
    suspend fun createFixedPlan(@Body body: co.golink.tester.domain.admin.CreateFixedPlanRequest): Response<co.golink.tester.domain.admin.PlanEnvelope>

    @PATCH("api/subscriptions/admin/plans/{id}")
    suspend fun updatePlan(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.UpdatePlanRequest): Response<co.golink.tester.domain.admin.PlanEnvelope>

    @PATCH("api/subscriptions/admin/plans/{id}/price")
    suspend fun updatePlanPrice(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.AmountRequest): Response<co.golink.tester.domain.admin.PlanEnvelope>

    @PATCH("api/subscriptions/admin/plans/{id}/features")
    suspend fun updatePlanFeatures(@Path("id") id: String, @Body body: Map<String, Int>): Response<ResponseBody>

    @DELETE("api/subscriptions/admin/plans/{id}")
    suspend fun deletePlan(@Path("id") id: String): Response<ResponseBody>

    @GET("api/subscriptions/admin/plans/synchronize")
    suspend fun synchronizePlans(): Response<ResponseBody>

    @GET("api/subscriptions/admin/plans/{id}/subscribers")
    suspend fun planSubscribers(@Path("id") id: String, @Query("page") page: Int = 1): Response<co.golink.tester.domain.admin.SubscriptionsResponse>

    @GET("api/subscriptions/admin")
    suspend fun subscriptions(@Query("page") page: Int = 1): Response<co.golink.tester.domain.admin.SubscriptionsResponse>

    @DELETE("api/subscriptions/admin/subscriptions/{id}")
    suspend fun deleteSubscription(@Path("id") id: String): Response<ResponseBody>

    @GET("api/subscriptions/admin/discounts")
    suspend fun discounts(): Response<co.golink.tester.domain.admin.DiscountsResponse>

    @GET("api/subscriptions/admin/discounts/generate-code")
    suspend fun generateDiscountCode(): Response<co.golink.tester.domain.admin.GeneratedCode>

    @POST("api/subscriptions/admin/discounts")
    suspend fun createDiscount(@Body body: co.golink.tester.domain.admin.DiscountRequest): Response<ResponseBody>

    @retrofit2.http.PUT("api/subscriptions/admin/discounts/{id}")
    suspend fun updateDiscount(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.DiscountRequest): Response<ResponseBody>

    @DELETE("api/subscriptions/admin/discounts/{id}")
    suspend fun deleteDiscount(@Path("id") id: String): Response<ResponseBody>

    @GET("api/subscriptions/admin/users/{id}/subscription")
    suspend fun userSubscription(@Path("id") id: String): Response<co.golink.tester.domain.admin.SubscriptionEnvelope>

    @POST("api/subscriptions/admin/users/{id}/credit")
    suspend fun creditUser(@Path("id") id: String, @Body body: co.golink.tester.domain.admin.AmountRequest): Response<ResponseBody>

    @DELETE("api/subscriptions/admin/transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: String): Response<ResponseBody>

    // --- Faturas (app) ---
    @GET("api/admin/transactions")
    suspend fun transactions(@Query("page") page: Int = 1, @Query("per_page") perPage: Int = 50): Response<co.golink.tester.domain.admin.TransactionsResponse>

    @GET("api/admin/users/{id}/transactions")
    suspend fun userTransactions(@Path("id") id: String, @Query("page") page: Int = 1): Response<co.golink.tester.domain.admin.TransactionsResponse>

    @POST("api/admin/transactions/{id}/refund")
    suspend fun refundTransaction(@Path("id") id: String): Response<ResponseBody>

    // --- Pagamentos ---
    @GET("api/admin/settings")
    suspend fun settings(@Query("column") columns: String): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @POST("api/admin/settings/payment-service")
    suspend fun storePaymentService(@Body body: co.golink.tester.domain.admin.PaymentServiceRequest): Response<ResponseBody>

    @POST("api/admin/settings/payment-service/test")
    suspend fun testPaymentService(@Body body: co.golink.tester.domain.admin.PaymentServiceRequest): Response<ResponseBody>

    // --- Definições do sistema ---
    @PATCH("api/admin/settings")
    suspend fun updateSettingNullable(@Body body: co.golink.tester.domain.admin.NullableFieldRequest): Response<ResponseBody>

    @retrofit2.http.Multipart
    @POST("api/admin/settings/image")
    suspend fun uploadSettingImage(
        @retrofit2.http.Part("name") name: okhttp3.RequestBody,
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part,
    ): Response<co.golink.tester.domain.admin.SettingImageResponse>

    @GET("api/admin/settings/flush-cache")
    suspend fun flushCache(): Response<ResponseBody>

    @POST("api/admin/settings/social-service")
    suspend fun storeSocialService(@Body body: co.golink.tester.domain.admin.SocialServiceRequest): Response<ResponseBody>

    @GET("api/admin/settings/env-credentials")
    suspend fun envCredentials(): Response<kotlinx.serialization.json.JsonObject>

    @GET("api/admin/ai")
    suspend fun aiOverview(): Response<co.golink.tester.domain.admin.AiOverview>

    @GET("api/admin/status")
    suspend fun serverStatus(): Response<kotlinx.serialization.json.JsonObject>

    @POST("api/admin/force-logout-all")
    suspend fun forceLogoutAll(): Response<co.golink.tester.domain.admin.MessageResponse>
}
