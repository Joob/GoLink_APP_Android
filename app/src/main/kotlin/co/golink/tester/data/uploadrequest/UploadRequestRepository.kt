package co.golink.tester.data.uploadrequest

import co.golink.tester.data.config.BackendUrlHolder
import co.golink.tester.domain.uploadrequest.CreateUploadRequestBody
import co.golink.tester.network.UploadRequestApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRequestRepository @Inject constructor(
    private val api: UploadRequestApi,
    private val backendUrlHolder: BackendUrlHolder,
) {
    /**
     * Cria um pedido de ficheiros e devolve o link partilhável. O backend
     * responde com o id em `data.id` (não há `token` — daí o antigo erro
     * "Token not returned"). O URL segue o formato da Web: `<host>/request/<id>/upload`.
     */
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
        val id = response.body()?.data?.id ?: error("ID not returned")
        "${backendUrlHolder.current.trimEnd('/')}/request/$id/upload"
    }
}
