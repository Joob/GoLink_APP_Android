package co.golink.tester.domain.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val password_confirmation: String,
    val invitation_token: String? = null,
    val reCaptcha: String? = null,
)

@Serializable
data class CheckAccountRequest(
    val email: String,
)

@Serializable
data class CheckAccountResponse(
    val verified: Boolean? = null,
    val oauth_provider: String? = null,
    val language: String? = null,
    val name: String? = null,
    val avatar: String? = null,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

@Serializable
data class ValidateOtpRequest(
    val otp_code: String,
)

@Serializable
data class ApiEnvelope<T>(
    val type: String? = null,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
data class LoginData(
    val token: String,
)

@Serializable
data class SocialiteUrl(
    val url: String,
)

@Serializable
data class PendingTokenResponse(
    val token: String? = null,
    val name: String? = null,
    val avatar: String? = null,
)

@Serializable
data class OtpStatus(
    val attempts_used: Int = 0,
    val is_in_cooldown: Boolean = false,
    val remaining_cooldown: Long = 0,
    val is_otp_valid: Boolean = false,
    val remaining_validity: Long = 0,
    val can_send_otp: Boolean = true,
    val remaining_wait: Long = 0,
)

@Serializable
data class ErrorBody(
    val type: String? = null,
    val message: String? = null,
    val errors: Map<String, List<String>>? = null,
)
