package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.settings.AccessToken
import co.golink.tester.domain.settings.CreateTokenRequest
import co.golink.tester.domain.settings.CreateTokenResponse
import co.golink.tester.domain.settings.SessionsResponse
import co.golink.tester.domain.settings.StorageResponse
import co.golink.tester.domain.settings.TransactionsResponse
import co.golink.tester.domain.settings.UpdatePasswordRequest
import co.golink.tester.domain.settings.UpdateProfileFieldRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface SettingsApi {
    @GET("api/user/storage")
    suspend fun storage(): Response<StorageResponse>

    @GET("api/user/sessions")
    suspend fun sessions(): Response<SessionsResponse>

    @DELETE("api/user/sessions/all")
    suspend fun revokeAllSessions(): Response<ApiEnvelope<Unit>>

    @DELETE("api/user/sessions/{id}")
    suspend fun revokeSession(@Path("id") id: String): Response<ApiEnvelope<Unit>>

    @POST("api/user/password")
    suspend fun updatePassword(@Body body: UpdatePasswordRequest): Response<ApiEnvelope<Unit>>

    @PATCH("api/user/settings")
    suspend fun updateProfileField(@Body body: UpdateProfileFieldRequest): Response<ApiEnvelope<Unit>>

    @GET("api/user/transactions")
    suspend fun transactions(): Response<TransactionsResponse>

    @GET("api/user/tokens")
    suspend fun listTokens(): Response<List<AccessToken>>

    @POST("api/user/tokens")
    suspend fun createToken(@Body body: CreateTokenRequest): Response<CreateTokenResponse>

    @DELETE("api/user/tokens/{id}")
    suspend fun deleteToken(@Path("id") id: Long): Response<ApiEnvelope<Unit>>
}
