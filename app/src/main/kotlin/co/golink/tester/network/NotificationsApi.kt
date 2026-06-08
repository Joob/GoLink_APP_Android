package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.notifications.NotificationsResponse
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface NotificationsApi {
    @GET("api/notifications")
    suspend fun list(): Response<NotificationsResponse>

    @POST("api/notifications/read")
    suspend fun markAllRead(): Response<ApiEnvelope<Unit>>

    @POST("api/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<ApiEnvelope<Unit>>

    @POST("api/notifications/{id}/delete")
    suspend fun delete(@Path("id") id: String): Response<ApiEnvelope<Unit>>

    @DELETE("api/notifications")
    suspend fun flushAll(): Response<ApiEnvelope<Unit>>
}
