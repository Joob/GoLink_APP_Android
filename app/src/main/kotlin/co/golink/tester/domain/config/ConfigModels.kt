package co.golink.tester.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfigResponse(
    val app: AppInfo? = null,
    val social_logins: SocialLogins? = null,
    val registration: RegistrationConfig? = null,
    val ai: AiConfig? = null,
    val upload: UploadConfig? = null,
    val trash: TrashConfig? = null,
)

/** Dias no lixo antes de a limpeza diária do servidor apagar de vez (0 = nunca). */
@Serializable
data class TrashConfig(
    val retentionDays: Int = 30,
)

/**
 * Limite de envio por ficheiro (definido no admin). O servidor manda `limit` em
 * bytes, ou a string "undefined" quando não há limite — por isso JsonElement.
 */
@Serializable
data class UploadConfig(
    val limit: kotlinx.serialization.json.JsonElement? = null,
    val limitFormatted: kotlinx.serialization.json.JsonElement? = null,
) {
    val limitBytes: Long?
        get() = (limit as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
            ?.toLong()?.takeIf { it > 0L }
    val limitLabel: String?
        get() = (limitFormatted as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?.takeIf { it.isNotBlank() && it != "null" }
}

/** Funcionalidades de IA visíveis ao utilizador. */
@Serializable
data class AiConfig(
    /** Resposta rápida (Claude + centro de ajuda) no novo ticket. */
    val support_assist: Int = 0,
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

