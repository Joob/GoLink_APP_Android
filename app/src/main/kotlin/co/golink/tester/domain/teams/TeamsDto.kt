package co.golink.tester.domain.teams

import kotlinx.serialization.Serializable

@Serializable
data class TeamInvitation(
    val email: String,
    val permission: String,
    val type: String = "invitation",
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
