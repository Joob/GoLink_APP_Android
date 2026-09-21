package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.browse.BrowseEntryEnvelope
import co.golink.tester.domain.files.CreateFolderRequest
import co.golink.tester.domain.files.DeleteItemsRequest
import co.golink.tester.domain.files.MoveItemsRequest
import co.golink.tester.domain.files.RenameItemRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface FilesApi {
    @POST("api/create-folder")
    suspend fun createFolder(@Body body: CreateFolderRequest): Response<BrowseEntryEnvelope>

    @PATCH("api/rename/{id}")
    suspend fun rename(
        @Path("id") id: String,
        @Body body: RenameItemRequest,
    ): Response<BrowseEntryEnvelope>

    @HTTP(method = "POST", path = "api/remove", hasBody = true)
    suspend fun remove(@Body body: DeleteItemsRequest): Response<ApiEnvelope<Unit>>

    @POST("api/move")
    suspend fun move(@Body body: MoveItemsRequest): Response<ApiEnvelope<Unit>>

    @Multipart
    @POST("api/upload")
    suspend fun upload(
        @Part("name") name: RequestBody,
        @Part("extension") extension: RequestBody,
        @Part("parent_id") parentId: RequestBody?,
        @Part("overwrite_existing") overwriteExisting: RequestBody?,
        // E2E: qual o ficheiro a substituir — com o nome cifrado o servidor não
        // o consegue encontrar pelo nome.
        @Part("overwrite_file_id") overwriteFileId: RequestBody? = null,
        @Part file: MultipartBody.Part,
        @Part("encrypted") encrypted: RequestBody? = null,
        @Part("wrapped_data_key") wrappedDataKey: RequestBody? = null,
        // E2E: tipo real do media (o servidor não o consegue inferir do ciphertext).
        @Part("media_type") mediaType: RequestBody? = null,
        // E2E Fase 2: nome cifrado (espaço privado) — `name` fica placeholder.
        @Part("name_encrypted") nameEncrypted: RequestBody? = null,
    ): Response<BrowseEntryEnvelope>

    // E2E: thumbnail cifrado (ciphertext opaco gerado no cliente com a data key).
    @POST("api/file/{id}/encrypted-thumbnail")
    suspend fun uploadEncryptedThumbnail(
        @Path("id") id: String,
        @Body body: RequestBody,
    ): Response<okhttp3.ResponseBody>

    @GET("api/file/{id}/encrypted-thumbnail")
    suspend fun downloadEncryptedThumbnail(
        @Path("id") id: String,
    ): Response<okhttp3.ResponseBody>

    // E2E: chaves públicas dos destinatários de uma pasta (dono + membros).
    @GET("api/folder/{id}/member-keys")
    suspend fun folderMemberKeys(
        @Path("id") id: String,
    ): Response<co.golink.tester.domain.encryption.FolderMemberKeysResponse>

    // E2E: ids dos ficheiros cifrados sob uma pasta (recursivo).
    @GET("api/folder/{id}/encrypted-file-ids")
    suspend fun folderEncryptedFileIds(
        @Path("id") id: String,
    ): Response<co.golink.tester.domain.encryption.FolderEncryptedFilesResponse>

    // E2E: partilhar a data key de um ficheiro com um destinatário registado.
    @POST("api/file/{id}/share-key")
    suspend fun shareFileKey(
        @Path("id") id: String,
        @Body body: co.golink.tester.domain.encryption.ShareKeyBody,
    ): Response<okhttp3.ResponseBody>

    // E2E: data keys (seladas ao próprio) de vários ficheiros num único pedido.
    @POST("api/files/encryption-keys")
    suspend fun fileEncryptionKeysBatch(
        @Body body: co.golink.tester.domain.encryption.FileIdsBody,
    ): Response<co.golink.tester.domain.encryption.FileEncryptionKeysBatchResponse>

    // E2E: regista as data keys re-embrulhadas com a chave de partilha da pasta.
    @POST("api/share/{token}/folder-keys")
    suspend fun storeFolderShareKeys(
        @Path("token") token: String,
        @Body body: co.golink.tester.domain.encryption.StoreFolderKeysBody,
    ): Response<okhttp3.ResponseBody>

    // E2E: cofre da chave da partilha (selada à pública do dono). Escrita única —
    // uma segunda devolve 409 com o blob que já lá está.
    @GET("api/share/{token}/owner-key")
    suspend fun shareOwnerKey(
        @Path("token") token: String,
    ): Response<co.golink.tester.domain.encryption.ShareOwnerKeyResponse>

    @POST("api/share/{token}/owner-key")
    suspend fun storeShareOwnerKey(
        @Path("token") token: String,
        @Body body: co.golink.tester.domain.encryption.StoreShareOwnerKeyBody,
    ): Response<okhttp3.ResponseBody>

    // E2E: tokens das partilhas do próprio cujo alvo é este item ou um ancestral
    // (para registar o nome de itens criados/renomeados DEPOIS da partilha).
    @GET("api/file/{id}/ancestor-share-tokens")
    suspend fun ancestorShareTokens(
        @Path("id") id: String,
    ): Response<co.golink.tester.domain.encryption.AncestorShareTokensResponse>

    // E2E nomes em partilhas: nomes dos descendentes (para o dono os re-cifrar
    // com a chave da partilha) e registo em lote por token.
    @GET("api/folder/{id}/descendant-names")
    suspend fun folderDescendantNames(
        @Path("id") id: String,
    ): Response<co.golink.tester.domain.encryption.DescendantNamesResponse>

    @POST("api/share/{token}/item-names")
    suspend fun storeShareItemNames(
        @Path("token") token: String,
        @Body body: co.golink.tester.domain.encryption.StoreShareItemNamesBody,
    ): Response<okhttp3.ResponseBody>

    @Multipart
    @POST("api/upload/mobile-backup")
    suspend fun uploadMobileBackup(
        @Part("name") name: RequestBody,
        @Part("extension") extension: RequestBody,
        @Part("overwrite_existing") overwriteExisting: RequestBody?,
        // Só a pasta de origem (Camera, Screenshots…). O servidor monta o
        // caminho por tipo (/Imagens/Camera, /Vídeos/Camera…) e cria/reutiliza
        // as pastas.
        @Part("folder") folder: RequestBody?,
        @Part file: MultipartBody.Part,
    ): Response<BrowseEntryEnvelope>

    @Multipart
    @POST("api/upload/chunks")
    suspend fun uploadChunk(
        @Part("name") name: RequestBody,
        @Part("extension") extension: RequestBody,
        @Part("parent_id") parentId: RequestBody?,
        @Part("is_last_chunk") isLastChunk: RequestBody,
        // Índice do chunk: o servidor grava cada parte no seu sítio, por isso um
        // reenvio (timeout) deixa de duplicar bytes e corromper o ficheiro.
        @Part("chunk_index") chunkIndex: RequestBody? = null,
        @Part("overwrite_existing") overwriteExisting: RequestBody?,
        @Part("overwrite_file_id") overwriteFileId: RequestBody? = null,
        // Backup por chunks (vídeos grandes): marca a origem; o servidor monta
        // o caminho por tipo a partir da pasta de origem ("folder").
        @Part("mobile_backup") mobileBackup: RequestBody? = null,
        @Part("folder") folder: RequestBody? = null,
        // Caminho explícito — usado só pelos uploads normais (web) de pastas.
        @Part("path") path: RequestBody? = null,
        @Part chunk: MultipartBody.Part,
        // E2E: ciphertext em chunks (o servidor monta e cifra no último chunk).
        @Part("encrypted") encrypted: RequestBody? = null,
        @Part("wrapped_data_key") wrappedDataKey: RequestBody? = null,
        @Part("media_type") mediaType: RequestBody? = null,
        // E2E Fase 2: nome cifrado (espaço privado) — `name` fica placeholder.
        @Part("name_encrypted") nameEncrypted: RequestBody? = null,
    ): Response<okhttp3.ResponseBody>

    @POST("api/upload/remote")
    suspend fun remoteUpload(@Body body: co.golink.tester.domain.files.RemoteUploadRequest): Response<okhttp3.ResponseBody>
}
