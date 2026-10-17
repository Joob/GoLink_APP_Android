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
) {
    // O viewer restaura/apaga a partir do preview do Lixo: a lista volta a carregar.
    private val _changes = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: kotlinx.coroutines.flow.SharedFlow<Unit> = _changes

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    suspend fun list(): Result<List<BrowseItem>> = runCatching {
        val response = api.list()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        // E2E Fase 2: os itens do espaço privado chegam com o placeholder "•" e o
        // nome real em name_encrypted — decifra-o como nas outras listagens.
        response.body()?.data?.map { entry ->
            val plain = keys.openNameOrNull(entry.data.attributes.name_encrypted)
            if (plain != null) entry.data.copy(attributes = entry.data.attributes.copy(name = plain)).toItem()
            else entry.data.toItem()
        }.orEmpty()
    }

    /** Lixo com contagens (chips) e filtro/ordenação no servidor. */
    suspend fun listPaged(
        query: co.golink.tester.domain.browse.BrowseQuery = co.golink.tester.domain.browse.BrowseQuery(),
    ): Result<co.golink.tester.data.browse.PagedItems> = runCatching {
        val response = api.list(type = query.type, sort = query.sort, direction = query.direction)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val items = body.data.map { entry ->
                val plain = keys.openNameOrNull(entry.data.attributes.name_encrypted)
                if (plain != null) entry.data.copy(attributes = entry.data.attributes.copy(name = plain)).toItem()
                else entry.data.toItem()
            }
            co.golink.tester.data.browse.PagedItems(items = items, currentPage = 1, lastPage = 1, total = items.size, stats = body.meta?.stats)
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
    }

    // Apaga até `limit` itens do lixo e devolve deleted/remaining para o progresso.
    suspend fun dumpBatch(limit: Int): Result<DumpTrashResponse> = runCatching {
        val response = api.dump(limit)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body() ?: error("Resposta vazia")
    }
}
