package co.golink.tester.data.auth

import co.golink.tester.domain.auth.AuthError
import co.golink.tester.domain.auth.CheckAccountRequest
import co.golink.tester.domain.auth.CheckAccountResponse
import co.golink.tester.domain.auth.ForgotPasswordRequest
import co.golink.tester.domain.auth.LoginRequest
import co.golink.tester.domain.auth.OtpStatus
import co.golink.tester.domain.auth.RegisterRequest
import co.golink.tester.domain.auth.ResetPasswordRequest
import co.golink.tester.domain.auth.ValidateOtpRequest
import co.golink.tester.network.AuthApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    private val errorParser: ApiErrorParser,
    private val e2eKeyManager: co.golink.tester.data.encryption.E2EKeyManager,
) {
    /**
     * Email como o servidor o espera: sem espaços nem caracteres invisíveis
     * (zero-width que alguns teclados/autofill inserem) e em minúsculas — o
     * servidor compara sem distinguir maiúsculas. Sem isto, um email com um
     * carácter invisível dava "conta não encontrada" apesar de existir.
     */
    private fun normalizeEmail(email: String): String =
        email.replace(Regex("[\\s\\u200B-\\u200D\\u2060\\uFEFF]"), "").lowercase()

    suspend fun checkAccount(email: String): Result<CheckAccountResponse> = runCatching {
        val response = api.checkAccount(CheckAccountRequest(email = normalizeEmail(email)))
        if (!response.isSuccessful) throw errorParser.parse(response)
        response.body() ?: throw AuthError.Unknown("Resposta vazia")
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val response = api.login(LoginRequest(email = normalizeEmail(email), password = password))
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
    /** Devolve se é preciso confirmar o email (null = servidor não indica). */
    ): Result<Boolean?> = runCatching {
        val response = api.register(
            RegisterRequest(
                name = name,
                email = normalizeEmail(email),
                password = password,
                password_confirmation = passwordConfirmation,
                invitation_token = invitationToken,
            )
        )
        if (!response.isSuccessful) {
            val parsed = errorParser.parse(response)
            if (response.code() == 401) throw AuthError.RegistrationDisabled(parsed.message ?: "Registo desactivado")
            throw parsed
        }
        response.body()?.data?.requires_verification
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun recoverPassword(email: String): Result<Unit> = runCatching {
        val response = api.recoverPassword(ForgotPasswordRequest(email = normalizeEmail(email)))
        if (!response.isSuccessful) throw errorParser.parse(response)
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun resetPassword(
        email: String,
        token: String,
        password: String,
        passwordConfirmation: String,
    ): Result<Unit> = runCatching {
        val response = api.resetPassword(
            ResetPasswordRequest(
                email = normalizeEmail(email),
                token = token,
                password = password,
                password_confirmation = passwordConfirmation,
            )
        )
        if (!response.isSuccessful) throw errorParser.parse(response)
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    /** Fetches the invited email for a registration invitation token (null if none). */
    suspend fun getInvitation(token: String): Result<String?> = runCatching {
        val response = api.getInvitation(token)
        if (!response.isSuccessful) throw errorParser.parse(response)
        response.body()?.data?.email
    }.recoverCatching { throw errorParser.fromThrowable(it) }

    suspend fun sendOtp(): Result<Unit> = runCatching {
        val response = api.sendOtp()
        if (response.isSuccessful) return@runCatching
        when (response.code()) {
            // valid_otp_exists: já há um código válido no email → seguir com ele.
            409 -> Unit
            // cooldown_active / rate_limited: nenhum código foi enviado.
            429 -> throw errorParser.parseOtpCooldown(response)
            else -> throw errorParser.parse(response)
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
            // 429: 5 tentativas falhadas seguidas → cooldown imposto pelo servidor.
            if (response.code() == 429) throw errorParser.parseOtpCooldown(response)
            if (response.code() == 422) throw AuthError.OtpInvalid()
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
        // Melhor esforço com limite: sem rede, sair não pode ficar preso ao timeout do OkHttp.
        runCatching { kotlinx.coroutines.withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { api.logout() } }
        e2eKeyManager.lock() // limpar a chave privada E2E da memória
        tokenStore.clear()
    }

    private companion object {
        const val LOGOUT_TIMEOUT_MS = 3_000L
    }
}
