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
    private val keys: co.golink.tester.data.encryption.E2EKeyManager,
) {
    suspend fun createTeamFolder(name: String, invitations: List<TeamInvitation>): Result<Unit> = runCatching {
        val response = api.createTeamFolder(CreateTeamFolderRequest(name = name, invitations = invitations))
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
    }

    suspend fun convertToTeamFolder(folderId: String, invitations: List<TeamInvitation>): Result<Unit> = runCatching {
        val response = api.convertToTeamFolder(folderId, ConvertToTeamFolderRequest(invitations = invitations))
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
    }

    suspend fun dissolve(id: String): Result<Unit> = runCatching {
        val response = api.dissolveTeamFolder(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun leave(id: String): Result<Unit> = runCatching {
        val response = api.leaveTeamFolder(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun listTeamFolder(
        id: String?,
        page: Int,
        perPage: Int = PAGE_SIZE,
        query: co.golink.tester.domain.browse.BrowseQuery = co.golink.tester.domain.browse.BrowseQuery(),
    ): Result<PagedItems> = runCatching {
        val targetId = id ?: ROOT
        val response = api.browseTeamFolder(targetId, page = page.toString(), perPage = perPage, type = query.type, sort = query.sort, direction = query.direction)
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
        val body = response.body() ?: error("Resposta vazia")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { body.toPaged(page) }
    }

    suspend fun listSharedWithMe(
        id: String?,
        page: Int,
        perPage: Int = PAGE_SIZE,
        query: co.golink.tester.domain.browse.BrowseQuery = co.golink.tester.domain.browse.BrowseQuery(),
    ): Result<PagedItems> = runCatching {
        val targetId = id ?: ROOT
        val response = api.browseSharedWithMe(targetId, page = page.toString(), perPage = perPage, type = query.type, sort = query.sort, direction = query.direction)
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
        val body = response.body() ?: error("Resposta vazia")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { body.toPaged(page) }
    }

    private fun BrowseListResponse.toPaged(requestedPage: Int): PagedItems {
        // E2E Fase 2: itens criados no espaço privado antes de a pasta ser Team
        // Folder têm o nome selado à chave do dono — decifra (o dono consegue;
        // os outros membros ficam com o placeholder), como na web.
        val items = data.map { entry ->
            val plain = keys.openNameOrNull(entry.data.attributes.name_encrypted)
            if (plain != null) entry.data.copy(attributes = entry.data.attributes.copy(name = plain)).toItem()
            else entry.data.toItem()
        }
        val last = meta?.paginate?.last_page ?: requestedPage
        val current = meta?.paginate?.current_page ?: requestedPage
        val members = (meta?.teamFolder?.data?.toItem() as? co.golink.tester.domain.browse.BrowseItem.Folder)?.members
        val root = meta?.root?.data?.let { rootEntry ->
            val plain = keys.openNameOrNull(rootEntry.attributes.name_encrypted)
            (if (plain != null) rootEntry.copy(attributes = rootEntry.attributes.copy(name = plain)) else rootEntry).toItem()
        } as? co.golink.tester.domain.browse.BrowseItem.Folder
        return PagedItems(items = items, currentPage = current, lastPage = last, teamMembers = members, stats = meta?.stats, root = root)
    }

    companion object {
        const val ROOT = "root"
        const val PAGE_SIZE = 20
    }
}
