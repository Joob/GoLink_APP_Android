package co.golink.tester.data.uploadrequest

import co.golink.tester.domain.uploadrequest.CreateUploadRequestBody
import co.golink.tester.network.UploadRequestApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRequestRepository @Inject constructor(
    private val api: UploadRequestApi,
) {
    suspend fun createFileRequest(
        name: String?,
        email: String?,
        notes: String?,
        folderId: String?,
    ): Result<String> = runCatching {
        val response = api.createFileRequest(
            CreateUploadRequestBody(
                name = name?.takeIf { it.isNotBlank() },
                email = email?.takeIf { it.isNotBlank() },
                notes = notes?.takeIf { it.isNotBlank() },
                folder_id = folderId,
            )
        )
        check(response.isSuccessful) { "HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300)}" }
        response.body()?.data?.attributes?.token ?: error("Token not returned")
    }
}
