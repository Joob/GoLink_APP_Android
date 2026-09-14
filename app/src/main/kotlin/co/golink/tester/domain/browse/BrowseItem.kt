package co.golink.tester.domain.browse

import androidx.compose.runtime.Immutable
import co.golink.tester.domain.asEmoji
import co.golink.tester.domain.asUrl

@Immutable
data class ShareInfo(
    val token: String,
    val link: String?,
    val protected: Boolean,
    val permission: String?,
    // Em DIAS (o backend guarda horas em expire_in; convertemos na fronteira).
    val expireIn: Int?,
    val downloadLimit: Int? = null,
    val downloadCount: Int? = null,
)

/** Horas (expire_in do backend) → dias, arredondando para cima; null/<=0 → null. */
fun expireHoursToDays(hours: Int?): Int? =
    hours?.takeIf { it > 0 }?.let { (it + 23) / 24 }

@Immutable
data class TeamMember(
    val id: String,
    val email: String,
    val name: String?,
    val avatarUrl: String?,
    val color: String?,
    val permission: String?,
)

// @Immutable: estes modelos nunca são mutados após criação (a lista de membros
// é sempre substituída, nunca alterada). Sem isto o Compose tratava BrowseItem
// como instável por causa do List<TeamMember> e recompunha todas as linhas a
// cada mudança de estado — jank no scroll.
@Immutable
sealed interface BrowseItem {
    val id: String
    val name: String
    val parentId: String?
    val updatedAt: String?
    val createdAt: String?
    // ISO-8601 (UTC) para ordenação; createdAt/updatedAt são strings localizadas.
    val createdAtIso: String?
    val updatedAtIso: String?
    val share: ShareInfo?

    // E2E Fase 2: ciphertext do nome (quando cifrado). Guardado para poder
    // decifrar em contextos que não passam pelo BrowseRepository (ex.: favoritas).
    val nameEncrypted: String?

    @Immutable
    data class Folder(
        override val id: String,
        override val name: String,
        override val parentId: String?,
        override val updatedAt: String?,
        override val createdAt: String?,
        override val createdAtIso: String?,
        override val updatedAtIso: String?,
        override val share: ShareInfo?,
        val color: String?,
        val emoji: String?,
        val isTeamFolder: Boolean,
        val itemCount: Int?,
        val filesize: String?,
        val members: List<TeamMember> = emptyList(),
        override val nameEncrypted: String? = null,
    ) : BrowseItem

    @Immutable
    data class File(
        override val id: String,
        override val name: String,
        override val parentId: String?,
        override val updatedAt: String?,
        override val createdAt: String?,
        override val createdAtIso: String?,
        override val updatedAtIso: String?,
        override val share: ShareInfo?,
        val basename: String,
        val mimetype: String?,
        val type: String,
        val filesize: String?,
        val thumbnailUrl: String?,
        val fileUrl: String?,
        val encrypted: Boolean = false,
        override val nameEncrypted: String? = null,
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
        expireIn = expireHoursToDays(attrs.expire_in),
        downloadLimit = attrs.download_limit,
        downloadCount = attrs.download_count,
    )
}

fun BrowseEntry.toItem(): BrowseItem = if (type == "folder") {
    BrowseItem.Folder(
        id = id,
        name = attributes.name,
        parentId = attributes.parent_id,
        updatedAt = attributes.updated_at,
        createdAt = attributes.created_at,
        createdAtIso = attributes.created_at_iso,
        updatedAtIso = attributes.updated_at_iso,
        share = relationships.toShareInfo(),
        color = attributes.color,
        emoji = attributes.emoji.asEmoji(),
        isTeamFolder = attributes.isTeamFolder,
        itemCount = attributes.items,
        filesize = attributes.filesize,
        members = relationships.toMembers(),
        nameEncrypted = attributes.name_encrypted,
    )
} else {
    BrowseItem.File(
        id = id,
        name = attributes.name,
        parentId = attributes.parent_id,
        updatedAt = attributes.updated_at,
        createdAt = attributes.created_at,
        createdAtIso = attributes.created_at_iso,
        updatedAtIso = attributes.updated_at_iso,
        share = relationships.toShareInfo(),
        basename = attributes.basename ?: attributes.name,
        mimetype = attributes.mimetype,
        type = type,
        filesize = attributes.filesize,
        thumbnailUrl = attributes.thumbnail.asUrl(),
        fileUrl = attributes.file_url,
        encrypted = attributes.encrypted,
        nameEncrypted = attributes.name_encrypted,
    )
}
