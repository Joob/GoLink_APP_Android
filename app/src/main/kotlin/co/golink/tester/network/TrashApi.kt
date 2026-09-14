package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.browse.BrowseListResponse
import co.golink.tester.domain.trash.DumpTrashResponse
import co.golink.tester.domain.trash.RestoreTrashRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TrashApi {
    @GET("api/browse/trash/{id}")
    suspend fun list(
        @Path("id") id: String = "root",
        @Query("page") page: String = "all",
    ): Response<BrowseListResponse>

    @POST("api/trash/restore")
    suspend fun restore(@Body body: RestoreTrashRequest): Response<ApiEnvelope<Unit>>

    // Dump em lotes: `limit` itens por chamada; devolve deleted/remaining para o
    // progresso 0–100%. Sem `limit` o servidor faz o dump completo (legacy).
    @DELETE("api/trash/dump")
    suspend fun dump(@Query("limit") limit: Int? = null): Response<DumpTrashResponse>
}
