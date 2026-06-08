package co.golink.tester.domain.uploadrequest

import kotlinx.serialization.Serializable

@Serializable
data class CreateUploadRequestBody(
    val name: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val folder_id: String? = null,
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
)
