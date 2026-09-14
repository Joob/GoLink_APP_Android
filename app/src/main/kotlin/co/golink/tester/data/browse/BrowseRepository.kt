package co.golink.tester.data.browse

import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.browse.BrowseEntry
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.BrowseListResponse
import co.golink.tester.domain.browse.NavFolder
import co.golink.tester.domain.browse.NavigationSection
import co.golink.tester.domain.browse.toItem
import co.golink.tester.network.BrowseApi
import javax.inject.Inject
import javax.inject.Singleton

data class PagedItems(
    val items: List<BrowseItem>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int? = null,
)

@Singleton
class BrowseRepository @Inject constructor(
    private val api: BrowseApi,
    private val keys: E2EKeyManager,
    private val encryptionApi: co.golink.tester.network.UserEncryptionApi,
) {
    // E2E Fase 2 — pesquisa client-side: cache do índice de nomes cifrados e dos
    // nomes já decifrados (id → nome). Só em memória; expira em 60s.
    @Volatile private var nameIndexCache: co.golink.tester.domain.encryption.NameIndexResponse? = null
    @Volatile private var nameIndexAt: Long = 0
    private val decryptedNames = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        // Nomes decifrados em cache não podem sobreviver ao lock/logout.
        keys.addOnLock {
            nameIndexCache = null
            nameIndexAt = 0
            decryptedNames.clear()
        }
    }
    // E2E Fase 2: decifra o nome (se cifrado e a chave estiver disponível) antes
    // de mapear para o modelo de UI.
    private fun BrowseEntry.decItem(): BrowseItem {
        val plain = keys.openNameOrNull(attributes.name_encrypted)
        return if (plain != null) copy(attributes = attributes.copy(name = plain)).toItem() else toItem()
    }

    private fun NavFolder.dec(): NavFolder = copy(
        name = keys.openNameOrNull(name_encrypted) ?: name,
        folders = folders.map { it.dec() },
    )
    suspend fun listFolder(id: String?, page: Int, perPage: Int = PAGE_SIZE): Result<PagedItems> = runCatching {
        val targetId = id ?: ROOT
        val response = api.browseFolder(targetId, page = page.toString(), perPage = perPage)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        body.toPaged(page)
    }

    suspend fun listLatest(page: Int, perPage: Int = PAGE_SIZE): Result<PagedItems> = runCatching {
        val response = api.latest(page = page.toString(), perPage = perPage)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        body.toPaged(page)
    }

    suspend fun listShared(page: Int, perPage: Int = PAGE_SIZE): Result<PagedItems> = runCatching {
        val response = api.shared(page = page.toString(), perPage = perPage)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        body.toPaged(page)
    }

    suspend fun listMobileBackup(type: String, page: Int, perPage: Int = PAGE_SIZE): Result<PagedItems> = runCatching {
        val response = api.mobileBackup(type = type, page = page.toString(), perPage = perPage)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        body.toPaged(page)
    }

    // page="all" — o ecrã de backups não tem paginação; pedir só a página 1
    // escondia tudo o que passasse dos primeiros 20 itens.
    suspend fun listAllMobileBackup(type: String): Result<List<BrowseItem>> = runCatching {
        val response = api.mobileBackup(type = type, page = "all")
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        body.data.map { it.data.decItem() }
    }

    // Contagem por tipo para o badge do separador, sem transferir a lista.
    suspend fun countMobileBackup(type: String): Result<Int> = runCatching {
        val response = api.mobileBackup(type = type, page = "1", perPage = 1, withFolders = true, countOnly = true)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        (response.body() ?: error("Resposta vazia")).meta?.paginate?.total ?: 0
    }

    // Navegação nas abas de backup: devolve as pastas de origem (Camera,
    // Screenshots, …) e os ficheiros do nível pedido, filtrados por tipo no
    // servidor. parentId=null é a raiz do backup.
    suspend fun listMobileBackupTree(type: String, parentId: String?): Result<List<BrowseItem>> = runCatching {
        val response = api.mobileBackup(
            type = type,
            page = "all",
            withFolders = true,
            parentId = parentId,
        )
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        body.data.map { it.data.decItem() }
    }

    // Conteúdo completo de uma pasta (page="all") — usado para navegar nas
    // subpastas do backup (Camera, Screenshots, …) sem paginação.
    suspend fun listAllFolder(id: String): Result<List<BrowseItem>> = runCatching {
        val response = api.browseFolder(id, page = "all")
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        body.data.map { it.data.decItem() }
    }

    suspend fun folderFingerprint(id: String?): Result<String> = runCatching {
        val targetId = id ?: ROOT
        val response = api.folderFingerprint(targetId)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        (response.body() ?: error("Resposta vazia")).signature
    }

    suspend fun navigation(): Result<List<NavigationSection>> = runCatching {
        val response = api.navigation()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body().orEmpty().map { section -> section.copy(folders = section.folders.map { it.dec() }) }
    }

    suspend fun search(query: String): Result<List<BrowseItem>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        val response = api.search(query)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val serverResults = response.body()?.data?.map { it.data.decItem() }.orEmpty()

        // E2E Fase 2: a pesquisa server-side não vê nomes cifrados (placeholders);
        // junta os matches do índice de nomes cifrados decifrado localmente.
        val encryptedResults = runCatching { searchEncryptedNames(query) }.getOrDefault(emptyList())
        val seen = serverResults.map { it.id }.toSet()
        encryptedResults.filter { it.id !in seen } + serverResults
    }

    private suspend fun loadNameIndex(): co.golink.tester.domain.encryption.NameIndexResponse? {
        val now = System.currentTimeMillis()
        nameIndexCache?.let { if (now - nameIndexAt < 60_000) return it }
        val body = runCatching { encryptionApi.nameIndex().body() }.getOrNull() ?: return null
        nameIndexCache = body
        nameIndexAt = now
        return body
    }

    private fun decryptedNameOf(item: co.golink.tester.domain.encryption.NameIndexItem): String? {
        decryptedNames[item.id]?.let { return it }
        val name = keys.openNameOrNull(item.name_encrypted) ?: return null
        decryptedNames[item.id] = name
        return name
    }

    private suspend fun searchEncryptedNames(query: String): List<BrowseItem> {
        if (!keys.isUnlocked || query.trim().length < 2) return emptyList()
        val q = query.trim().lowercase()
        val index = loadNameIndex() ?: return emptyList()

        val fileIds = index.files.asSequence()
            .filter { decryptedNameOf(it)?.lowercase()?.contains(q) == true }
            .map { it.id }.take(15).toList()
        val folderIds = index.folders.asSequence()
            .filter { decryptedNameOf(it)?.lowercase()?.contains(q) == true }
            .map { it.id }.take(15).toList()
        if (fileIds.isEmpty() && folderIds.isEmpty()) return emptyList()

        val response = api.searchEncryptedIds(
            co.golink.tester.domain.encryption.SearchByIdsBody(file_ids = fileIds, folder_ids = folderIds)
        )
        if (!response.isSuccessful) return emptyList()
        return response.body()?.data?.map { it.data.decItem() }.orEmpty()
    }

    private fun BrowseListResponse.toPaged(requestedPage: Int): PagedItems {
        val items = data.map { it.data.decItem() }
        val last = meta?.paginate?.last_page ?: requestedPage
        val current = meta?.paginate?.current_page ?: requestedPage
        return PagedItems(items = items, currentPage = current, lastPage = last, total = meta?.paginate?.total)
    }

    companion object {
        const val ROOT = "root"
        const val PAGE_SIZE = 20
    }
}
