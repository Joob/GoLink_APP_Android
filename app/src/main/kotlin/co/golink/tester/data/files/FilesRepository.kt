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
import co.golink.tester.data.encryption.E2EShareService
import co.golink.tester.data.encryption.EncryptedFileCodec
import co.golink.tester.network.FilesApi
import co.golink.tester.network.UserEncryptionApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

private const val NAME_PLACEHOLDER = "•"

@Singleton
class FilesRepository @Inject constructor(
    private val api: FilesApi,
    private val keys: E2EKeyManager,
    private val shareService: E2EShareService,
    private val userEncryptionApi: UserEncryptionApi,
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
        // Pasta criada dentro de uma pasta já partilhada: o visitante do link só
        // lê o nome se for registado com a chave dessa partilha.
        if (enc != null) shareService.registerItemNameForOwnerShares(folder.id, name)
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
        // Renomear dentro de uma pasta partilhada: o nome antigo continuava no link.
        if (enc != null) shareService.registerItemNameForOwnerShares(item.id, newName)
        if (enc == null) result else when (result) {
            is BrowseItem.Folder -> result.copy(name = newName)
            is BrowseItem.File -> result.copy(name = newName)
        }
    }

    /**
     * Reescreve o conteúdo de um ficheiro de texto. O id não muda — partilhas,
     * favoritos e a data key E2E mantêm-se. Num ficheiro cifrado o texto é
     * cifrado aqui com a data key que o ficheiro já tem: o servidor recebe
     * ciphertext opaco.
     */
    suspend fun updateTextContent(file: BrowseItem.File, text: String): Result<Unit> = runCatching {
        var bytes = text.toByteArray(Charsets.UTF_8)

        if (file.encrypted) {
            val wrapped = userEncryptionApi.fileKey(file.id).body()?.wrapped_data_key
                ?: error("Sem chave para este ficheiro")
            val dataKey = keys.openFileDataKey(wrapped)
            bytes = EncryptedFileCodec.encrypt(bytes, dataKey)
        }

        val part = MultipartBody.Part.createFormData(
            "content",
            file.basename.ifBlank { file.name },
            bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
        )
        val response = api.updateFileContent(
            file.id,
            part,
            if (file.encrypted) "1".toRequestBody("text/plain".toMediaTypeOrNull())
            else "0".toRequestBody("text/plain".toMediaTypeOrNull()),
        )
        check(response.isSuccessful) {
            when (response.code()) {
                507 -> "Sem espaço de armazenamento disponível"
                403 -> "Sem permissão para editar este ficheiro"
                else -> "HTTP ${response.code()}"
            }
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
