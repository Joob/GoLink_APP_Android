package co.golink.tester.domain.activity

import androidx.compose.runtime.Immutable
import co.golink.tester.domain.browse.BrowseEntryEnvelope
import co.golink.tester.domain.browse.BrowseItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ---- DTOs (GET api/activity/recent · GET api/activity/item/{type}/{id}) ----

@Serializable
data class ActivityActorDto(
    val id: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val color: String? = null,
)

@Serializable
data class ActivityRowDto(
    val id: String,
    val action: String,
    val item_type: String? = null,
    val is_child: Boolean = false,
    val is_mine: Boolean = false,
    val created_at: String? = null,
    // Só ids/flags (nunca nomes — E2E). Ex.: {"via":"share","permission":"can-edit"}
    val metadata: JsonElement? = null,
    val actor: ActivityActorDto? = null,
    val item_owner_id: String? = null,
    val item_trashed: Boolean = false,
    val item: BrowseEntryEnvelope? = null,
)

@Serializable
data class ActivitySectionDto(
    val items: List<ActivityRowDto> = emptyList(),
    val has_more: Boolean = false,
    val page: Int = 1,
)

@Serializable
data class RecentActivityResponse(
    val data: Map<String, ActivitySectionDto> = emptyMap(),
)

@Serializable
data class ItemActivityResponse(
    val data: List<ActivityRowDto> = emptyList(),
    val has_more: Boolean = false,
    val page: Int = 1,
)

// ---- Modelo de UI ----

/** Secções da Atividade recente, pela ordem do ecrã (igual à web). */
enum class ActivitySection(val key: String) {
    MY_UPLOADS("my_uploads"),
    SHARED_WITH_ME("shared_with_me"),
    TEAM_ACTIVITY("team_activity"),
    SHARE_ACTIVITY("share_activity"),
}

@Immutable
data class ActivityEntry(
    val id: String,
    val action: String,
    val isChild: Boolean,
    val isMine: Boolean,
    val createdAtIso: String?,
    val actorName: String?,
    val actorAvatar: String?,
    val actorColor: String?,
    val hasActor: Boolean,
    val via: String?,
    val permission: String?,
    val itemOwnerId: String?,
    val itemTrashed: Boolean,
    val item: BrowseItem?,
    // Nome da pasta-mãe já decifrado (null se raiz/indisponível).
    val parentName: String?,
)

@Immutable
data class ActivityPage(
    val entries: List<ActivityEntry>,
    val hasMore: Boolean,
    val page: Int,
)

private fun JsonElement?.str(key: String): String? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

fun ActivityRowDto.toEntry(item: BrowseItem?, parentName: String?): ActivityEntry = ActivityEntry(
    id = id,
    action = action,
    isChild = is_child,
    isMine = is_mine,
    createdAtIso = created_at,
    actorName = actor?.name,
    actorAvatar = actor?.avatar,
    actorColor = actor?.color,
    hasActor = actor != null,
    via = metadata.str("via"),
    permission = metadata.str("permission"),
    itemOwnerId = item_owner_id,
    itemTrashed = item_trashed,
    item = item,
    parentName = parentName,
)
