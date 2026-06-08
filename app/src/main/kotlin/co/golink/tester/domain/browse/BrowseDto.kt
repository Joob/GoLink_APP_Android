package co.golink.tester.domain.browse

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BrowseListResponse(
    val data: List<BrowseEntryEnvelope> = emptyList(),
    val meta: BrowseMeta? = null,
)

@Serializable
data class BrowseEntryEnvelope(
    val data: BrowseEntry,
)

@Serializable
data class BrowseEntry(
    val id: String,
    val type: String,
    val attributes: BrowseAttributes,
    val relationships: BrowseRelationships? = null,
)

@Serializable
data class BrowseRelationships(
    val shared: SharedRelationship? = null,
    val members: TeamMembersRelationship? = null,
)

@Serializable
data class TeamMembersRelationship(
    val data: List<TeamMemberEnvelope> = emptyList(),
)

@Serializable
data class TeamMemberEnvelope(
    val data: TeamMemberEntry,
)

@Serializable
data class TeamMemberEntry(
    val id: String,
    val type: String,
    val attributes: TeamMemberAttributes,
)

@Serializable
data class TeamMemberAttributes(
    val email: String,
    val name: String? = null,
    val avatar: String? = null,
    val color: String? = null,
    val permission: String? = null,
)

@Serializable
data class SharedRelationship(
    val data: SharedRefData? = null,
)

@Serializable
data class SharedRefData(
    val id: String? = null,
    val type: String? = null,
    val attributes: SharedRefAttributes? = null,
)

@Serializable
data class SharedRefAttributes(
    val permission: String? = null,
    val protected: Boolean? = null,
    val item_id: String? = null,
    val protectedPasswordShow: Boolean? = null,
    val expire_in: Int? = null,
    val token: String? = null,
    val link: String? = null,
    val type: String? = null,
)

@Serializable
data class BrowseAttributes(
    val name: String,
    val basename: String? = null,
    val mimetype: String? = null,
    val filesize: String? = null,
    val file_url: String? = null,
    val thumbnail: JsonElement? = null,
    val color: String? = null,
    val emoji: JsonElement? = null,
    val isTeamFolder: Boolean = false,
    val items: Int? = null,
    val trashed_items: Int? = null,
    val parent_id: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val deleted_at: String? = null,
)

@Serializable
data class BrowseMeta(
    val paginate: PaginateMeta? = null,
    val root: BrowseEntryEnvelope? = null,
)

@Serializable
data class PaginateMeta(
    val total: Int? = null,
    @SerialName("currentPage") val current_page: Int? = null,
    @SerialName("perPage") val per_page: Int? = null,
    @SerialName("lastPage") val last_page: Int? = null,
)

@Serializable
data class NavigationSection(
    val location: String,
    val name: String,
    val folders: List<NavFolder> = emptyList(),
    val isMovable: Boolean = false,
    val isOpen: Boolean = false,
)

@Serializable
data class NavFolder(
    val id: String,
    val parent_id: String? = null,
    val name: String,
    val team_folder: Boolean? = null,
    val folders: List<NavFolder> = emptyList(),
)
