package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.notifications.NotificationsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationsApi {
    @GET("api/notifications")
    suspend fun list(): Response<NotificationsResponse>

    /** Só o contador (badge em segundo plano). 404 em servidores antigos. */
    @GET("api/notifications/unread-count")
    suspend fun unreadCount(): Response<co.golink.tester.domain.notifications.UnreadCountResponse>

    /**
     * [before]: só as notificações até esta data (as que o cliente já viu);
     * [ids]: as desse mesmo segundo que o cliente viu (as outras desse segundo ficam).
     */
    @POST("api/notifications/read")
    suspend fun markAllRead(
        @Query("before") before: String? = null,
        @Query("ids[]") ids: List<String>? = null,
    ): Response<ApiEnvelope<Unit>>

    @POST("api/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<ApiEnvelope<Unit>>

    @POST("api/notifications/{id}/unread")
    suspend fun markUnread(@Path("id") id: String): Response<ApiEnvelope<Unit>>

    @POST("api/notifications/{id}/delete")
    suspend fun delete(@Path("id") id: String): Response<ApiEnvelope<Unit>>

    @DELETE("api/notifications/read")
    suspend fun deleteRead(
        @Query("before") before: String? = null,
        @Query("ids[]") ids: List<String>? = null,
    ): Response<ApiEnvelope<Unit>>

    /** Ações em massa (ids ou "todas as do filtro"); até 500 ids por pedido. */
    @POST("api/notifications/bulk")
    suspend fun bulk(@Body body: co.golink.tester.domain.notifications.BulkNotificationsRequest): Response<co.golink.tester.domain.notifications.BulkNotificationsResponse>

    @GET("api/notifications/stats")
    suspend fun stats(): Response<co.golink.tester.domain.notifications.NotificationStats>

    /** Lidas antes de [before] (a lida mais antiga que o cliente tem); [ids]: as desse segundo já vistas. */
    @GET("api/notifications/older")
    suspend fun older(
        @Query("before") before: String,
        @Query("ids[]") ids: List<String>,
        @Query("limit") limit: Int = 50,
    ): Response<co.golink.tester.domain.notifications.OlderNotificationsResponse>
}
