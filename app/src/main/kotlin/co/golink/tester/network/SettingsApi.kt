package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.settings.AccessToken
import co.golink.tester.domain.settings.CreateTokenRequest
import co.golink.tester.domain.settings.CreateTokenResponse
import co.golink.tester.domain.settings.SecurityEventsResponse
import co.golink.tester.domain.settings.SessionsResponse
import co.golink.tester.domain.settings.StorageResponse
import co.golink.tester.domain.settings.UpdatePasswordRequest
import co.golink.tester.domain.settings.UpdateProfileFieldRequest
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

interface SettingsApi {
    @GET("api/user/storage")
    suspend fun storage(): Response<StorageResponse>

    @GET("api/user/storage/insights")
    suspend fun storageInsights(): Response<co.golink.tester.domain.settings.StorageInsightsResponse>

    @GET("api/user/sessions")
    suspend fun sessions(): Response<SessionsResponse>

    /** Só a contagem (badge do menu): o servidor não decifra as sessões. */
    @GET("api/user/sessions")
    suspend fun sessionsCount(@Query("count") count: Int = 1): Response<co.golink.tester.domain.settings.SessionsCountResponse>

    @DELETE("api/user/sessions/all")
    suspend fun revokeAllSessions(): Response<ApiEnvelope<Unit>>

    @DELETE("api/user/sessions/{id}")
    suspend fun revokeSession(@Path("id") id: String): Response<ApiEnvelope<Unit>>

    @GET("api/user/security-events")
    suspend fun securityEvents(@Query("page") page: Int = 1): Response<SecurityEventsResponse>

    @POST("api/user/password")
    suspend fun updatePassword(@Body body: UpdatePasswordRequest): Response<ApiEnvelope<Unit>>

    @POST("api/user/email/change-request")
    suspend fun requestEmailChange(
        @Body body: co.golink.tester.domain.settings.EmailChangeRequest,
    ): Response<co.golink.tester.domain.settings.EmailChangeResponse>

    @POST("api/user/email/change-confirm")
    suspend fun confirmEmailChange(
        @Body body: co.golink.tester.domain.settings.EmailChangeConfirmRequest,
    ): Response<co.golink.tester.domain.settings.EmailChangeResponse>

    @POST("api/user/account/deletion/cancel")
    suspend fun cancelScheduledDeletion(): Response<ApiEnvelope<Unit>>

    @PATCH("api/user/settings")
    suspend fun updateProfileField(@Body body: UpdateProfileFieldRequest): Response<ApiEnvelope<Unit>>

    @Multipart
    @POST("api/user/avatar")
    suspend fun updateAvatar(@Part avatar: okhttp3.MultipartBody.Part): Response<okhttp3.ResponseBody>

    @GET("api/user/mobile-backup/setting")
    suspend fun getMobileBackupSetting(): Response<co.golink.tester.domain.settings.MobileBackupSettingResponse>

    @POST("api/user/mobile-backup/setting")
    suspend fun setMobileBackupEnabled(
        @Body body: co.golink.tester.domain.settings.MobileBackupSettingRequest,
    ): Response<okhttp3.ResponseBody>


    @GET("api/user/tokens")
    suspend fun listTokens(): Response<List<AccessToken>>

    @POST("api/user/tokens")
    suspend fun createToken(@Body body: CreateTokenRequest): Response<CreateTokenResponse>

    @DELETE("api/user/tokens/{id}")
    suspend fun deleteToken(@Path("id") id: Long): Response<ApiEnvelope<Unit>>

    // 2FA (TOTP) — rotas próprias para token (as da Fortify só servem a web).
    @POST("api/user/two-factor/enable")
    suspend fun enableTwoFactor(@Body body: co.golink.tester.domain.settings.TwoFactorStepUp): Response<co.golink.tester.domain.settings.TwoFactorSetupResponse>

    @POST("api/user/two-factor/confirm")
    suspend fun confirmTwoFactor(@Body body: co.golink.tester.domain.settings.TwoFactorCode): Response<co.golink.tester.domain.settings.TwoFactorCodesResponse>

    @retrofit2.http.HTTP(method = "DELETE", path = "api/user/two-factor", hasBody = true)
    suspend fun disableTwoFactor(@Body body: co.golink.tester.domain.settings.TwoFactorStepUp): Response<okhttp3.ResponseBody>

    @POST("api/user/two-factor/recovery-codes")
    suspend fun recoveryCodes(@Body body: co.golink.tester.domain.settings.TwoFactorStepUp): Response<co.golink.tester.domain.settings.TwoFactorCodesResponse>

    @POST("api/user/two-factor/recovery-codes/regenerate")
    suspend fun regenerateRecoveryCodes(@Body body: co.golink.tester.domain.settings.TwoFactorStepUp): Response<co.golink.tester.domain.settings.TwoFactorCodesResponse>
}
