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
    ): Response<BrowseEntryEnvelope>

    @Multipart
    @POST("api/upload/mobile-backup")
    suspend fun uploadMobileBackup(
        @Part("name") name: RequestBody,
        @Part("extension") extension: RequestBody,
        @Part("overwrite_existing") overwriteExisting: RequestBody?,
        // Pasta de origem no dispositivo ("Camera", "Screenshots", …) — o
        // backend usa-a para organizar o backup em subpastas por origem.
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
        @Part chunk: MultipartBody.Part,
    ): Response<okhttp3.ResponseBody>

    @POST("api/upload/remote")
    suspend fun remoteUpload(@Body body: co.golink.tester.domain.files.RemoteUploadRequest): Response<okhttp3.ResponseBody>
}
