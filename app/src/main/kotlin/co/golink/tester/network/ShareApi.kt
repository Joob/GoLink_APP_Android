package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.share.CreateShareRequest
import co.golink.tester.domain.share.QrCodeEnvelope
import co.golink.tester.domain.share.RevokeSharesRequest
import co.golink.tester.domain.share.ShareByEmailRequest
import co.golink.tester.domain.share.ShareResponseEnvelope
import co.golink.tester.domain.share.UpdateShareRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ShareApi {
    @POST("api/share")
    suspend fun create(@Body body: CreateShareRequest): Response<ShareResponseEnvelope>

    @PATCH("api/share/{token}")
    suspend fun update(
        @Path("token") token: String,
        @Body body: UpdateShareRequest,
    ): Response<ShareResponseEnvelope>

    @HTTP(method = "DELETE", path = "api/share/{token}", hasBody = true)
    suspend fun revoke(
        @Path("token") token: String,
        @Body body: RevokeSharesRequest,
    ): Response<ApiEnvelope<Unit>>

    @POST("api/share/{token}/email")
    suspend fun sendByEmail(
        @Path("token") token: String,
        @Body body: ShareByEmailRequest,
    ): Response<ApiEnvelope<Unit>>

    @GET("api/share/{token}/qr")
    suspend fun qrCode(@Path("token") token: String): Response<QrCodeEnvelope>
}
