package co.golink.tester.domain.browse

import co.golink.tester.domain.asEmoji
import co.golink.tester.domain.asUrl

data class ShareInfo(
    val token: String,
    val link: String?,
    val protected: Boolean,
    val permission: String?,
    val expireIn: Int?,
)

data class TeamMember(
    val id: String,
    val email: String,
    val name: String?,
    val avatarUrl: String?,
    val color: String?,
    val permission: String?,
)

sealed interface BrowseItem {
    val id: String
    val name: String
    val parentId: String?
    val updatedAt: String?
    val createdAt: String?
    val share: ShareInfo?

    data class Folder(
        override val id: String,
        override val name: String,
        override val parentId: String?,
        override val updatedAt: String?,
        override val createdAt: String?,
        override val share: ShareInfo?,
        val color: String?,
        val emoji: String?,
        val isTeamFolder: Boolean,
        val itemCount: Int?,
        val filesize: String?,
        val members: List<TeamMember> = emptyList(),
    ) : BrowseItem

    data class File(
        override val id: String,
        override val name: String,
        override val parentId: String?,
        override val updatedAt: String?,
        override val createdAt: String?,
        override val share: ShareInfo?,
        val basename: String,
        val mimetype: String?,
        val type: String,
        val filesize: String?,
        val thumbnailUrl: String?,
        val fileUrl: String?,
    ) : BrowseItem
}

private fun BrowseRelationships?.toMembers(): List<TeamMember> =
    this?.members?.data?.map { envelope ->
        val entry = envelope.data
        TeamMember(
            id = entry.id,
            email = entry.attributes.email,
            name = entry.attributes.name,
            avatarUrl = entry.attributes.avatar,
            color = entry.attributes.color,
            permission = entry.attributes.permission,
        )
    } ?: emptyList()

private fun BrowseRelationships?.toShareInfo(): ShareInfo? {
    val attrs = this?.shared?.data?.attributes ?: return null
    val token = attrs.token ?: return null
    return ShareInfo(
        token = token,
        link = attrs.link,
        protected = attrs.protected ?: false,
        permission = attrs.permission,
        expireIn = attrs.expire_in,
    )
}

fun BrowseEntry.toItem(): BrowseItem = if (type == "folder") {
    BrowseItem.Folder(
        id = id,
        name = attributes.name,
        parentId = attributes.parent_id,
        updatedAt = attributes.updated_at,
        createdAt = attributes.created_at,
        share = relationships.toShareInfo(),
        color = attributes.color,
        emoji = attributes.emoji.asEmoji(),
        isTeamFolder = attributes.isTeamFolder,
        itemCount = attributes.items,
        filesize = attributes.filesize,
        members = relationships.toMembers(),
    )
} else {
    BrowseItem.File(
        id = id,
        name = attributes.name,
        parentId = attributes.parent_id,
        updatedAt = attributes.updated_at,
        createdAt = attributes.created_at,
        share = relationships.toShareInfo(),
        basename = attributes.basename ?: attributes.name,
        mimetype = attributes.mimetype,
        type = type,
        filesize = attributes.filesize,
        thumbnailUrl = attributes.thumbnail.asUrl(),
        fileUrl = attributes.file_url,
    )
}
