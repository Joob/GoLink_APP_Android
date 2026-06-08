package co.golink.tester.data.auth

import co.golink.tester.domain.auth.AuthError
import co.golink.tester.domain.auth.CheckAccountRequest
import co.golink.tester.domain.auth.CheckAccountResponse
import co.golink.tester.domain.auth.ForgotPasswordRequest
import co.golink.tester.domain.auth.LoginRequest
import co.golink.tester.domain.auth.OtpStatus
import co.golink.tester.domain.auth.RegisterRequest
import co.golink.tester.domain.auth.ValidateOtpRequest
import co.golink.tester.network.AuthApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    private val errorParser: ApiErrorParser,
) {
    suspend fun checkAccount(email: String): Result<CheckAccountResponse> = runCatching {
        val response = api.checkAccount(CheckAccountRequest(email = email))
        if (!response.isSuccessful) throw errorParser.parse(response)
        response.body() ?: throw AuthError.Unknown("Resposta vazia")
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val response = api.login(LoginRequest(email = email, password = password))
        if (!response.isSuccessful) throw errorParser.parse(response)
        val token = response.body()?.data?.token
            ?: throw AuthError.Unknown("Sem token na resposta")
        tokenStore.setPendingToken(token)
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        invitationToken: String? = null,
        recaptcha: String? = null,
    ): Result<Unit> = runCatching {
        val response = api.register(
            RegisterRequest(
                name = name,
                email = email,
                password = password,
                password_confirmation = passwordConfirmation,
                invitation_token = invitationToken,
                reCaptcha = recaptcha,
            )
        )
        if (!response.isSuccessful) {
            val parsed = errorParser.parse(response)
            if (response.code() == 401) throw AuthError.RegistrationDisabled(parsed.message ?: "Registo desactivado")
            throw parsed
        }
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun recoverPassword(email: String): Result<Unit> = runCatching {
        val response = api.recoverPassword(ForgotPasswordRequest(email = email))
        if (!response.isSuccessful) throw errorParser.parse(response)
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun sendOtp(): Result<Unit> = runCatching {
        val response = api.sendOtp()
        if (!response.isSuccessful && response.code() !in 409..429) {
            throw errorParser.parse(response)
        }
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun otpStatus(): Result<OtpStatus> = runCatching {
        val response = api.otpStatus()
        if (!response.isSuccessful) throw errorParser.parse(response)
        response.body() ?: throw AuthError.Unknown("Resposta vazia")
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun validateOtp(code: String): Result<Unit> = runCatching {
        val response = api.validateOtp(ValidateOtpRequest(otp_code = code))
        if (!response.isSuccessful) {
            if (response.code() == 422) throw AuthError.OtpInvalid()
            if (response.code() == 400) {
                tokenStore.markOtpValidated()
                return@runCatching
            }
            throw errorParser.parse(response)
        }
        tokenStore.markOtpValidated()
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun socialiteRedirectUrl(provider: String): Result<String> = runCatching {
        val response = api.socialiteRedirect(provider)
        if (!response.isSuccessful) throw errorParser.parse(response)
        response.body()?.data?.url ?: throw AuthError.Unknown("URL OAuth ausente")
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun adoptSocialiteToken(token: String) {
        tokenStore.setPendingToken(token)
    }

    suspend fun logout(): Result<Unit> = runCatching {
        runCatching { api.logout() }
        tokenStore.clear()
    }
}
