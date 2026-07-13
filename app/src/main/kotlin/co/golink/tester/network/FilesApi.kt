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
        @Part file: MultipartBody.Part,
        @Part("encrypted") encrypted: RequestBody? = null,
        @Part("wrapped_data_key") wrappedDataKey: RequestBody? = null,
        // E2E: tipo real do media (o servidor não o consegue inferir do ciphertext).
        @Part("media_type") mediaType: RequestBody? = null,
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
        @Part("overwrite_existing") overwriteExisting: RequestBody?,
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
    ): Response<okhttp3.ResponseBody>

    @POST("api/upload/remote")
    suspend fun remoteUpload(@Body body: co.golink.tester.domain.files.RemoteUploadRequest): Response<okhttp3.ResponseBody>
}
