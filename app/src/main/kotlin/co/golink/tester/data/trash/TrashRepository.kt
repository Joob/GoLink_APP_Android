package co.golink.tester.data.trash

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
) {
    suspend fun list(): Result<List<BrowseItem>> = runCatching {
        val response = api.list()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.data?.map { it.data.toItem() }.orEmpty()
    }

    suspend fun restore(items: List<BrowseItem>): Result<Unit> = runCatching {
        val payload = RestoreTrashRequest(
            items = items.map {
                ItemRef(id = it.id, type = if (it is BrowseItem.Folder) "folder" else "file")
            },
        )
        val response = api.restore(payload)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    // Apaga até `limit` itens do lixo e devolve deleted/remaining para o progresso.
    suspend fun dumpBatch(limit: Int): Result<DumpTrashResponse> = runCatching {
        val response = api.dump(limit)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body() ?: error("Resposta vazia")
    }
}
