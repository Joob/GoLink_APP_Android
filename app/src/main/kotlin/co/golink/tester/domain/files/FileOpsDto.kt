package co.golink.tester.domain.files

import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderRequest(
    val name: String,
    val parent_id: String? = null,
    // E2E Fase 2: nome cifrado (opcional). Presente → `name` fica placeholder.
    val name_encrypted: String? = null,
    // Cor da pasta (hex, ex. "#3b82f6"); null = cor do tema.
    val color: String? = null,
)

@Serializable
data class RenameItemRequest(
    val name: String,
    val type: String,
    val name_encrypted: String? = null,
    // Só pastas: aparência. JsonNull limpa (volta ao tema); ausente = não mexe.
    val color: kotlinx.serialization.json.JsonElement? = null,
    val emoji: kotlinx.serialization.json.JsonElement? = null,
)

/** Aparência de uma pasta: emoji (carácter) OU cor; ambos null = cor do tema. */
data class FolderAppearance(val color: String?, val emoji: String?)

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
