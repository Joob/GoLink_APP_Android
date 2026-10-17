package co.golink.tester.domain.activity

import androidx.compose.runtime.Immutable
import co.golink.tester.domain.browse.BrowseEntryEnvelope
import co.golink.tester.domain.browse.BrowseItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- GET api/activity/feed (igual à web: lotes de uploads + eventos) ------------

@Serializable
data class FeedBatchItemDto(
    val trashed: Boolean = false,
    val item: BrowseEntryEnvelope? = null,
)

@Serializable
data class FeedEntryDto(
    val id: String,
    // event | batch | shared_with_me
    val kind: String = "event",
    // my_uploads | team | shares | shared_with_me
    val category: String = "my_uploads",
    val action: String = "",
    val item_type: String? = null,
    val is_child: Boolean = false,
    val is_mine: Boolean = false,
    val created_at: String? = null,
    val metadata: JsonElement? = null,
    val actor: ActivityActorDto? = null,
    val item_owner_id: String? = null,
    val item_trashed: Boolean = false,
    val item: BrowseEntryEnvelope? = null,
    // Lote ("Carregaste 5 vídeos para Screenshots").
    val count: Int = 0,
    val trashed_count: Int = 0,
    val total_bytes: Long = 0,
    val types: Map<String, Int> = emptyMap(),
    val folder: BrowseEntryEnvelope? = null,
    val folder_owner_id: String? = null,
    val items: List<FeedBatchItemDto> = emptyList(),
)

@Serializable
data class FeedResponse(
    val data: List<FeedEntryDto> = emptyList(),
    val has_more: Boolean = false,
    val next_cursor: String? = null,
)

// ---- GET api/activity/summary -----------------------------------------------------

@Serializable
data class ActivitySummaryDto(
    val uploaded_7d: Int = 0,
    val folders_changed_7d: Int = 0,
    val duplicate_candidates: List<BrowseEntryEnvelope> = emptyList(),
)

@Serializable
data class ActivitySummaryResponse(val data: ActivitySummaryDto = ActivitySummaryDto())

// ---- Modelo de UI -----------------------------------------------------------------

@Immutable
data class FeedEntry(
    val id: String,
    val kind: String,
    val category: String,
    /** Base comum (ator, ação, data, item, pasta-mãe) — o mesmo das linhas antigas. */
    val base: ActivityEntry,
    val count: Int = 0,
    val totalBytes: Long = 0,
    val types: Map<String, Int> = emptyMap(),
    val folder: BrowseItem.Folder? = null,
    val folderOwnerId: String? = null,
    val files: List<BrowseItem> = emptyList(),
)

@Immutable
data class FeedPage(val entries: List<FeedEntry>, val hasMore: Boolean, val nextCursor: String?)

/** Ficheiros com o mesmo nome e tamanho (confirmado no cliente, nomes decifrados). */
@Immutable
data class DuplicateGroup(
    val name: String,
    val size: String?,
    val files: List<BrowseItem.File>,
    val folders: List<String>,
    /** Pasta de cada ficheiro (mesma ordem de [files]). */
    val fileFolders: List<String> = emptyList(),
)

@Immutable
data class ActivitySummary(val uploaded7d: Int, val foldersChanged7d: Int, val duplicates: List<DuplicateGroup>)
