package co.golink.tester.domain.encryption

import kotlinx.serialization.Serializable

@Serializable
data class UserEncryptionResponse(
    val configured: Boolean = false,
    val public_key: String? = null,
    val wrapped_sk_password: String? = null,
    val wrapped_sk_password_nonce: String? = null,
    val pwd_salt: String? = null,
    val kdf_ops: Long? = null,
    val kdf_mem: Long? = null,
    val kdf_alg: String? = null,
)

@Serializable
data class StoreUserEncryptionBody(
    val public_key: String,
    val wrapped_sk_password: String,
    val wrapped_sk_password_nonce: String,
    val pwd_salt: String,
    val wrapped_sk_recovery: String,
    val wrapped_sk_recovery_nonce: String,
    val kdf_ops: Long,
    val kdf_mem: Long,
    val kdf_alg: String,
)

@Serializable
data class RecoveryBlobResponse(
    val public_key: String,
    val wrapped_sk_recovery: String,
    val wrapped_sk_recovery_nonce: String,
)

@Serializable
data class UpdateSecretBody(
    val wrapped_sk_password: String,
    val wrapped_sk_password_nonce: String,
    val pwd_salt: String,
    val kdf_ops: Long,
    val kdf_mem: Long,
    val kdf_alg: String,
)

@Serializable
data class PublicKeyResponse(
    val user_id: String? = null,
    val public_key: String? = null,
)

@Serializable
data class FileEncryptionKeyResponse(
    val file_id: String? = null,
    val wrapped_data_key: String? = null,
)

@Serializable
data class EncryptionStatusResponse(
    val status: String? = null,
    val configured: Boolean? = null,
)

@Serializable
data class E2EKeyEntry(
    val user_id: String,
    val public_key: String? = null,
)

@Serializable
data class FolderMemberKeysResponse(
    val owner: E2EKeyEntry? = null,
    val recipients: List<E2EKeyEntry> = emptyList(),
)

@Serializable
data class FolderEncryptedFilesResponse(
    val file_ids: List<String> = emptyList(),
)

@Serializable
data class ShareKeyBody(
    val recipient_id: String,
    val wrapped_data_key: String,
)

@Serializable
data class MigrationStatusResponse(
    val configured: Boolean = false,
    val in_progress: Boolean = false,
    val percent: Int = 100,
    val total: Int = 0,
    val migrated: Int = 0,
    val remaining: Int = 0,
)
