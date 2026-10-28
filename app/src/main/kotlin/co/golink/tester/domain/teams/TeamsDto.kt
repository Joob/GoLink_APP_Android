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
