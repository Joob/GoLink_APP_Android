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
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.network.FilesApi
import javax.inject.Inject
import javax.inject.Singleton

private const val NAME_PLACEHOLDER = "•"

@Singleton
class FilesRepository @Inject constructor(
    private val api: FilesApi,
    private val keys: E2EKeyManager,
) {
    // E2E Fase 2: cifra o nome no espaço privado (encryptName) quando há chave.
    // A resposta traz o placeholder → repomos o nome em claro que definimos.
    suspend fun createFolder(name: String, parentId: String?, encryptName: Boolean = true): Result<BrowseItem.Folder> = runCatching {
        val enc = if (encryptName && keys.canSealNames) keys.sealName(name) else null
        val response = api.createFolder(
            CreateFolderRequest(
                name = if (enc != null) NAME_PLACEHOLDER else name,
                parent_id = parentId,
                name_encrypted = enc,
            )
        )
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val entry = response.body()?.data ?: error("Resposta vazia")
        val folder = entry.toItem() as BrowseItem.Folder
        if (enc != null) folder.copy(name = name) else folder
    }

    suspend fun rename(item: BrowseItem, newName: String, encryptName: Boolean = true): Result<BrowseItem> = runCatching {
        val type = if (item is BrowseItem.Folder) "folder" else "file"
        val enc = if (encryptName && keys.canSealNames) keys.sealName(newName) else null
        val response = api.rename(
            item.id,
            RenameItemRequest(
                name = if (enc != null) NAME_PLACEHOLDER else newName,
                type = type,
                name_encrypted = enc,
            )
        )
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val entry = response.body()?.data ?: error("Resposta vazia")
        val result = entry.toItem()
        if (enc == null) result else when (result) {
            is BrowseItem.Folder -> result.copy(name = newName)
            is BrowseItem.File -> result.copy(name = newName)
        }
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
