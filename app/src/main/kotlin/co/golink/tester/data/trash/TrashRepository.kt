package co.golink.tester.data.trash

import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.toItem
import co.golink.tester.domain.files.ItemRef
import co.golink.tester.domain.trash.DumpTrashResponse
import co.golink.tester.domain.trash.RestoreTrashRequest
import co.golink.tester.network.TrashApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepository @Inject constructor(
    private val api: TrashApi,
    private val keys: E2EKeyManager,
    private val notifications: co.golink.tester.data.notifications.NotificationsRepository,
) {
    // O viewer restaura/apaga a partir do preview do Lixo: a lista volta a carregar.
    private val _changes = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: kotlinx.coroutines.flow.SharedFlow<Unit> = _changes

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    /** Lixo com contagens (chips) e filtro/ordenação no servidor. */
    suspend fun listPaged(
        query: co.golink.tester.domain.browse.BrowseQuery = co.golink.tester.domain.browse.BrowseQuery(),
        page: Int = 1,
        perPage: Int = co.golink.tester.data.browse.BrowseRepository.PAGE_SIZE,
    ): Result<co.golink.tester.data.browse.PagedItems> = runCatching {
        // Paginado como a web: o lixo pode ter milhares de itens (antes page=all).
        val response = api.list(page = page.toString(), perPage = perPage, type = query.type, sort = query.sort, direction = query.direction)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val items = body.data.map { entry ->
                val plain = keys.openNameOrNull(entry.data.attributes.name_encrypted)
                if (plain != null) entry.data.copy(attributes = entry.data.attributes.copy(name = plain)).toItem()
                else entry.data.toItem()
            }
            val paginate = body.meta?.paginate
            co.golink.tester.data.browse.PagedItems(
                items = items,
                currentPage = paginate?.current_page ?: page,
                lastPage = paginate?.last_page ?: page,
                total = paginate?.total ?: items.size,
                stats = body.meta?.stats,
            )
        }
    }

    suspend fun restore(items: List<BrowseItem>): Result<Unit> = runCatching {
        val payload = RestoreTrashRequest(
            items = items.map {
                ItemRef(id = it.id, type = if (it is BrowseItem.Folder) "folder" else "file")
            },
        )
        val response = api.restore(payload)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        notifyChanged()
        // Notificações dos ficheiros restaurados voltam a aparecer.
        notifications.refresh()
    }

    // Apaga até `limit` itens do lixo e devolve deleted/remaining para o progresso.
    suspend fun dumpBatch(limit: Int): Result<DumpTrashResponse> = runCatching {
        val response = api.dump(limit)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body() ?: error("Resposta vazia")
    }
}
