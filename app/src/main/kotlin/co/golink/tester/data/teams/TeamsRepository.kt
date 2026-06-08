package co.golink.tester.data.teams

import co.golink.tester.data.browse.PagedItems
import co.golink.tester.domain.browse.BrowseListResponse
import co.golink.tester.domain.browse.toItem
import co.golink.tester.domain.teams.ConvertToTeamFolderRequest
import co.golink.tester.domain.teams.CreateTeamFolderRequest
import co.golink.tester.domain.teams.TeamInvitation
import co.golink.tester.network.TeamsApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeamsRepository @Inject constructor(
    private val api: TeamsApi,
) {
    suspend fun createTeamFolder(name: String, invitations: List<TeamInvitation>): Result<Unit> = runCatching {
        val response = api.createTeamFolder(CreateTeamFolderRequest(name = name, invitations = invitations))
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
    }

    suspend fun convertToTeamFolder(folderId: String, invitations: List<TeamInvitation>): Result<Unit> = runCatching {
        val response = api.convertToTeamFolder(folderId, ConvertToTeamFolderRequest(invitations = invitations))
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
    }

    suspend fun listTeamFolder(id: String?, page: Int, perPage: Int = PAGE_SIZE): Result<PagedItems> = runCatching {
        val targetId = id ?: ROOT
        val response = api.browseTeamFolder(targetId, page = page.toString(), perPage = perPage)
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
        (response.body() ?: error("Resposta vazia")).toPaged(page)
    }

    suspend fun listSharedWithMe(id: String?, page: Int, perPage: Int = PAGE_SIZE): Result<PagedItems> = runCatching {
        val targetId = id ?: ROOT
        val response = api.browseSharedWithMe(targetId, page = page.toString(), perPage = perPage)
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
        (response.body() ?: error("Resposta vazia")).toPaged(page)
    }

    private fun BrowseListResponse.toPaged(requestedPage: Int): PagedItems {
        val items = data.map { it.data.toItem() }
        val last = meta?.paginate?.last_page ?: requestedPage
        val current = meta?.paginate?.current_page ?: requestedPage
        return PagedItems(items = items, currentPage = current, lastPage = last)
    }

    companion object {
        const val ROOT = "root"
        const val PAGE_SIZE = 20
    }
}
