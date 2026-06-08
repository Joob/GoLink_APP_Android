package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.auth.CheckAccountRequest
import co.golink.tester.domain.auth.CheckAccountResponse
import co.golink.tester.domain.auth.ForgotPasswordRequest
import co.golink.tester.domain.auth.LoginData
import co.golink.tester.domain.auth.LoginRequest
import co.golink.tester.domain.auth.OtpStatus
import co.golink.tester.domain.auth.PendingTokenResponse
import co.golink.tester.domain.auth.RegisterRequest
import co.golink.tester.domain.auth.SocialiteUrl
import co.golink.tester.domain.auth.ValidateOtpRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AuthApi {
    @POST("api/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<LoginData>>

    @POST("api/register")
    suspend fun register(@Body body: RegisterRequest): Response<ApiEnvelope<Unit>>

    @POST("api/logout")
    suspend fun logout(): Response<Unit>

    @POST("api/password/recover")
    suspend fun recoverPassword(@Body body: ForgotPasswordRequest): Response<ApiEnvelope<Unit>>

    @POST("api/user/check")
    suspend fun checkAccount(@Body body: CheckAccountRequest): Response<CheckAccountResponse>

    @GET("api/user/otp-status")
    suspend fun otpStatus(): Response<OtpStatus>

    @POST("api/user/send-otp-code")
    suspend fun sendOtp(): Response<ApiEnvelope<Unit>>

    @POST("api/user/validate-otp-code")
    suspend fun validateOtp(@Body body: ValidateOtpRequest): Response<Unit>

    @GET("api/socialite/{provider}/redirect")
    suspend fun socialiteRedirect(@Path("provider") provider: String): Response<ApiEnvelope<SocialiteUrl>>

    @GET("api/socialite/pending-token")
    suspend fun socialitePendingToken(
        @Header("Cookie") cookie: String,
    ): Response<PendingTokenResponse>
}
