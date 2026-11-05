package co.golink.tester.domain.teams

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

// encodeDefaults está desligado no Json: sem @EncodeDefault o "type" (obrigatório no servidor) não seguia.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TeamInvitation(
    val email: String,
    val permission: String,
    @EncodeDefault val type: String = "invitation",
)

@Serializable
data class CreateTeamFolderRequest(
    val name: String,
    val invitations: List<TeamInvitation>,
)

// PATCH api/teams/folders/{id}: lista completa (o que falta é removido).
@Serializable
data class TeamMemberUpdate(
    val id: String,
    val permission: String,
)

@Serializable
data class UpdateTeamFolderRequest(
    val members: List<TeamMemberUpdate>,
    val invitations: List<TeamInvitation>,
)

@Serializable
data class ConvertToTeamFolderRequest(
    val invitations: List<TeamInvitation>,
)

// Nomes de uma Team Folder selados à chave do dono (encrypted-names / clear-names).
@Serializable
data class TeamEncryptedName(
    val id: String,
    val type: String,
    val name_encrypted: String,
)

@Serializable
data class TeamEncryptedNamesResponse(
    val data: List<TeamEncryptedName> = emptyList(),
)

// Membros online de uma Team Folder (GET api/teams/folders/{id}/presence).
@Serializable
data class TeamPresenceResponse(
    val online: List<String> = emptyList(),
)

@Serializable
data class TeamClearName(
    val id: String,
    val type: String,
    val name: String,
)

@Serializable
data class TeamClearNamesRequest(
    val items: List<TeamClearName>,
)

// GET api/teams/invitations/{id}/preview (TeamInvitationResource).
@Serializable
data class TeamInvitationEnvelope(val data: TeamInvitationData)

@Serializable
data class TeamInvitationData(
    val id: String,
    val attributes: TeamInvitationAttributes,
    val relationships: TeamInvitationRelationships? = null,
)

@Serializable
data class TeamInvitationAttributes(
    val parent_id: String,
    val email: String,
    val status: String? = null,
    val permission: String? = null,
    val isExistedUser: Boolean = false,
)

@Serializable
data class TeamInvitationRelationships(val inviter: TeamInviterEnvelope? = null)

@Serializable
data class TeamInviterEnvelope(val data: TeamInviterData)

@Serializable
data class TeamInviterData(val id: String, val attributes: TeamInviterAttributes)

@Serializable
data class TeamInviterAttributes(val name: String? = null, val avatar: String? = null, val color: String? = null)
