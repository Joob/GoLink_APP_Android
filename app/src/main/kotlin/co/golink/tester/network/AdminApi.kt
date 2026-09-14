package co.golink.tester.network

import co.golink.tester.domain.admin.AdminUsersResponse
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
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminApi {
    @GET("api/admin/dashboard")
    suspend fun dashboard(): Response<DashboardResponse>

    @GET("api/admin/analytics/summary")
    suspend fun analytics(@Query("range") range: String = "7d"): Response<AnalyticsResponse>

    @GET("api/admin/users")
    suspend fun users(@Query("page") page: Int = 1): Response<AdminUsersResponse>

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

    @DELETE("api/admin/invite-registers/{id}")
    suspend fun deleteInvite(@Path("id") id: String): Response<ResponseBody>
}
