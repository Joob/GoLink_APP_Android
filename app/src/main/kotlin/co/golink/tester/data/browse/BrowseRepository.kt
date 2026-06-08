package co.golink.tester.data.browse

import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.BrowseListResponse
import co.golink.tester.domain.browse.NavigationSection
import co.golink.tester.domain.browse.toItem
import co.golink.tester.network.BrowseApi
import javax.inject.Inject
import javax.inject.Singleton

data class PagedItems(
    val items: List<BrowseItem>,
    val currentPage: Int,
    val lastPage: Int,
)

@Singleton
class BrowseRepository @Inject constructor(
    private val api: BrowseApi,
) {
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

    suspend fun navigation(): Result<List<NavigationSection>> = runCatching {
        val response = api.navigation()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body().orEmpty()
    }

    suspend fun search(query: String): Result<List<BrowseItem>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        val response = api.search(query)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.data?.map { it.data.toItem() }.orEmpty()
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
