package co.golink.tester.domain.share

import kotlinx.serialization.Serializable

@Serializable
data class CreateShareRequest(
    val id: String,
    val type: String,
    val isPassword: Boolean = false,
    val password: String? = null,
    val isPasswordShow: Boolean = false,
    val passwordShow: String? = null,
    val permission: String? = null,
    val expiration: Int? = null,
    val download_limit: Int? = null,
    // Visualização única (só ficheiros): 1 ou null. Exclusivo de download_limit.
    val view_limit: Int? = null,
    val emails: List<String>? = null,
)

@Serializable
data class UpdateShareRequest(
    val protected: Boolean,
    val protectedPasswordShow: Boolean,
    val password: String? = null,
    val passwordShow: String? = null,
    val permission: String? = null,
    val expiration: Int? = null,
    val download_limit: Int? = null,
    val view_limit: Int? = null,
)

@Serializable
data class RevokeSharesRequest(
    val tokens: List<String>,
)

@Serializable
data class ShareByEmailRequest(
    val emails: List<String>,
)

@Serializable
data class ShareResponseEnvelope(
    val data: ShareResponseData,
)

@Serializable
data class ShareResponseData(
    val id: String,
    val type: String,
    val attributes: ShareResponseAttributes,
)

@Serializable
data class ShareResponseAttributes(
    val permission: String? = null,
    val protected: Boolean? = null,
    val item_id: String,
    val protectedPasswordShow: Boolean? = null,
    val expire_in: Int? = null,
    val expires_at: String? = null,
    val download_limit: Int? = null,
    val download_count: Int? = null,
    val view_limit: Int? = null,
    val view_count: Int? = null,
    val token: String,
    val link: String? = null,
    val type: String,
)

@Serializable
data class QrCodeEnvelope(
    val type: String? = null,
    val message: String? = null,
    val data: QrCodeData? = null,
)

@Serializable
data class QrCodeData(
    val svg: String,
)
