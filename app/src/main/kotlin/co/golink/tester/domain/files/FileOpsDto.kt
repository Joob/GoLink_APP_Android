package co.golink.tester.domain.files

import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderRequest(
    val name: String,
    val parent_id: String? = null,
    // E2E Fase 2: nome cifrado (opcional). Presente → `name` fica placeholder.
    val name_encrypted: String? = null,
)

@Serializable
data class RenameItemRequest(
    val name: String,
    val type: String,
    val name_encrypted: String? = null,
)

@Serializable
data class MoveItemsRequest(
    val to_id: String? = null,
    val items: List<ItemRef>,
)

@Serializable
data class DeleteItemsRequest(
    val items: List<DeleteItemRef>,
)

@Serializable
data class ItemRef(
    val id: String,
    val type: String,
)

@Serializable
data class DeleteItemRef(
    val id: String,
    val type: String,
    val force_delete: Boolean,
)

@Serializable
data class RemoteUploadRequest(
    val urls: List<String>,
    val parent_id: String? = null,
)
