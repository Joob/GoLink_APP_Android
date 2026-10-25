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

    /** Ids dos membros online agora (null se falhar — fica o que veio na listagem). */
    suspend fun onlineMembers(id: String): Set<String>? =
        runCatching { api.teamFolderPresence(id).online.toSet() }.getOrNull()

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

    suspend fun sharedWithMeFingerprint(id: String?): Result<String> = runCatching {
        val response = api.sharedWithMeFingerprint(id ?: ROOT)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        (response.body() ?: error("Resposta vazia")).signature
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

    // Throttle por raiz de equipa (o servidor sobe até à raiz e devolve a árvore
    // toda): no máximo uma passagem por NAMES_TTL_MS. Antes era uma vez por
    // sessão e por pasta aberta — nomes selados depois disso só eram repostos
    // ao reiniciar a app, e cada subpasta repetia a árvore inteira.
    private val namesClearedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Força nova passagem na próxima abertura (ex.: depois de criar/enviar itens). */
    fun invalidateClearedNames() = namesClearedAt.clear()

    /**
     * Dono de uma Team Folder: repõe em claro os nomes que ficaram selados à sua
     * chave (itens criados antes da conversão, ou uploads quando nenhum membro
     * tinha E2E). Sem isto os membros viam "•". Só o dono consegue abri-los; o
     * servidor aceita só do dono. Best-effort, silencioso. Paridade com a web
     * (e2eNames.clearTeamFolderNames). Devolve quantos nomes foram repostos.
     * [rootKey]: raiz da equipa quando conhecida (chave do throttle).
     */
    suspend fun clearEncryptedNames(folderId: String, rootKey: String = folderId): Int {
        if (!keys.canSealNames) return 0
        val now = System.currentTimeMillis()
        val last = namesClearedAt[rootKey]
        if (last != null && now - last < NAMES_TTL_MS) return 0
        namesClearedAt[rootKey] = now
        return runCatching {
            val rows = api.encryptedNames(folderId).body()?.data.orEmpty()
            val items = rows.mapNotNull { r ->
                keys.openNameOrNull(r.name_encrypted)?.let { co.golink.tester.domain.teams.TeamClearName(r.id, r.type, it) }
            }
            items.chunked(200).forEach { chunk ->
                check(api.clearNames(folderId, co.golink.tester.domain.teams.TeamClearNamesRequest(chunk)).isSuccessful)
            }
            items.size
        }.getOrElse {
            namesClearedAt.remove(rootKey) // tenta de novo numa próxima abertura
            0
        }
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
        private const val NAMES_TTL_MS = 2 * 60_000L
    }
}
