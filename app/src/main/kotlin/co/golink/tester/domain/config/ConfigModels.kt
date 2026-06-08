package co.golink.tester.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfigResponse(
    val app: AppInfo? = null,
    val social_logins: SocialLogins? = null,
    val registration: RegistrationConfig? = null,
    val recaptcha: RecaptchaConfig? = null,
)

@Serializable
data class AppInfo(
    val host: String? = null,
    val api: String? = null,
    val locale: String? = null,
    val name: String? = null,
    val description: String? = null,
    val installation: String? = null,
)

@Serializable
data class SocialLogins(
    val is_google_allowed: Int = 0,
    val is_google_configured: Int = 0,
    val is_github_allowed: Int = 0,
    val is_github_configured: Int = 0,
    val is_microsoft_allowed: Int = 0,
    val is_microsoft_configured: Int = 0,
) {
    val google: Boolean get() = is_google_allowed == 1 && is_google_configured == 1
    val github: Boolean get() = is_github_allowed == 1 && is_github_configured == 1
    val microsoft: Boolean get() = is_microsoft_allowed == 1 && is_microsoft_configured == 1
    val any: Boolean get() = google || github || microsoft
}

@Serializable
data class RegistrationConfig(
    val allowed: Int = 1,
    val verification: Int = 0,
) {
    val isAllowed: Boolean get() = allowed == 1
    val requiresVerification: Boolean get() = verification == 1
}

@Serializable
data class RecaptchaConfig(
    val allowed: Int = 0,
    val is_configured: Int = 0,
    val client_id: String? = null,
)
