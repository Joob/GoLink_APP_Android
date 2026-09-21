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

// E2E: pedido em lote das data keys (seladas ao próprio) de vários ficheiros.
@Serializable
data class FileIdsBody(
    val file_ids: List<String>,
)

@Serializable
data class FileEncryptionKeyRow(
    val file_id: String,
    val wrapped_data_key: String,
)

@Serializable
data class FileEncryptionKeysBatchResponse(
    val keys: List<FileEncryptionKeyRow> = emptyList(),
)

// E2E: registo das data keys re-embrulhadas com a chave de partilha da pasta.
@Serializable
data class FolderShareKeyEntry(
    val file_id: String,
    val wrapped_data_key: String,
)

@Serializable
data class StoreFolderKeysBody(
    val keys: List<FolderShareKeyEntry>,
)

// E2E: a chave da partilha selada à pública do dono (cofre no servidor), para
// qualquer dispositivo dele recuperar a MESMA chave do #k=. `has_file_keys` =
// já há chaves registadas para o token (links distribuídos que uma nova mataria).
@Serializable
data class ShareOwnerKeyResponse(
    val wrapped_share_key: String? = null,
    val has_file_keys: Boolean = false,
)

@Serializable
data class StoreShareOwnerKeyBody(
    val wrapped_share_key: String,
)

@Serializable
data class AncestorShareTokensResponse(
    val tokens: List<String> = emptyList(),
)

// E2E nomes em partilhas: o nome re-cifrado com a chave da partilha (#k=), por
// item. Sem isto o visitante do link vê o placeholder em vez do nome.
@Serializable
data class ShareItemNameEntry(
    val id: String,
    val name_encrypted: String,
)

@Serializable
data class StoreShareItemNamesBody(
    val names: List<ShareItemNameEntry>,
)

@Serializable
data class DescendantNameEntry(
    val id: String,
    val type: String? = null,
    val name: String? = null,
    val name_encrypted: String? = null,
)

@Serializable
data class DescendantNamesResponse(
    val items: List<DescendantNameEntry> = emptyList(),
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

// E2E Fase 2 — migração de nomes.
@Serializable
data class PlainNamesResponse(
    val files: List<PlainNameItem> = emptyList(),
    val folders: List<PlainNameItem> = emptyList(),
    val remaining: Int = 0,
)

@Serializable
data class PlainNameItem(
    val id: String,
    val name: String,
)

@Serializable
data class EncryptedNameItem(
    val id: String,
    val name_encrypted: String,
)

@Serializable
data class EncryptNamesBody(
    val files: List<EncryptedNameItem> = emptyList(),
    val folders: List<EncryptedNameItem> = emptyList(),
)

// E2E Fase 2 — pesquisa client-side sobre nomes cifrados.
@Serializable
data class NameIndexResponse(
    val files: List<NameIndexItem> = emptyList(),
    val folders: List<NameIndexItem> = emptyList(),
)

@Serializable
data class NameIndexItem(
    val id: String,
    val type: String? = null,
    val name_encrypted: String,
)

@Serializable
data class SearchByIdsBody(
    val file_ids: List<String> = emptyList(),
    val folder_ids: List<String> = emptyList(),
)

@Serializable
data class NameMigrationStatusResponse(
    val total: Int = 0,
    val done: Int = 0,
    val remaining: Int = 0,
    val percent: Int = 100,
    val in_progress: Boolean = false,
    // Migração dos FICHEIROS ainda a decorrer — nomes só começam depois.
    val waiting_for_file_migration: Boolean = false,
)
