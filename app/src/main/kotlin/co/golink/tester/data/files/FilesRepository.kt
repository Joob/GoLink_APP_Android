package co.golink.tester.data.files

import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.toItem
import co.golink.tester.domain.files.CreateFolderRequest
import co.golink.tester.domain.files.DeleteItemRef
import co.golink.tester.domain.files.DeleteItemsRequest
import co.golink.tester.domain.files.ItemRef
import co.golink.tester.domain.files.MoveItemsRequest
import co.golink.tester.domain.files.RemoteUploadRequest
import co.golink.tester.domain.files.RenameItemRequest
import co.golink.tester.network.FilesApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilesRepository @Inject constructor(
    private val api: FilesApi,
) {
    suspend fun createFolder(name: String, parentId: String?): Result<BrowseItem.Folder> = runCatching {
        val response = api.createFolder(CreateFolderRequest(name = name, parent_id = parentId))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val entry = response.body()?.data ?: error("Resposta vazia")
        (entry.toItem() as BrowseItem.Folder)
    }

    suspend fun rename(item: BrowseItem, newName: String): Result<BrowseItem> = runCatching {
        val type = if (item is BrowseItem.Folder) "folder" else "file"
        val response = api.rename(item.id, RenameItemRequest(name = newName, type = type))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val entry = response.body()?.data ?: error("Resposta vazia")
        entry.toItem()
    }

    suspend fun delete(items: List<BrowseItem>, permanent: Boolean = false): Result<Unit> = runCatching {
        val payload = DeleteItemsRequest(items = items.map {
            DeleteItemRef(
                id = it.id,
                type = if (it is BrowseItem.Folder) "folder" else "file",
                force_delete = permanent,
            )
        })
        val response = api.remove(payload)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun move(items: List<BrowseItem>, toFolderId: String?): Result<Unit> = runCatching {
        val payload = MoveItemsRequest(
            to_id = toFolderId,
            items = items.map { ItemRef(it.id, if (it is BrowseItem.Folder) "folder" else "file") },
        )
        val response = api.move(payload)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun remoteUpload(urls: List<String>, parentId: String?): Result<Unit> = runCatching {
        val response = api.remoteUpload(RemoteUploadRequest(urls = urls, parent_id = parentId))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }
}
