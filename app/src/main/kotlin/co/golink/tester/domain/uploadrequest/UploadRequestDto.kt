package co.golink.tester.domain.uploadrequest

import kotlinx.serialization.Serializable

@Serializable
data class CreateUploadRequestBody(
    val name: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val folder_id: String? = null,
    // Prazo: 0 = sem prazo, 1/7/30 dias (como na web).
    val expires_in_days: Int? = null,
)

@Serializable
data class UploadRequestEnvelope(
    val data: UploadRequestData,
)

@Serializable
data class UploadRequestData(
    val id: String? = null,
    val type: String? = null,
    val attributes: UploadRequestAttributes? = null,
)

@Serializable
data class UploadRequestAttributes(
    val token: String? = null,
    val name: String? = null,
    val expires_at: String? = null,
    val no_expiry: Boolean = false,
)
