package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.browse.BrowseListResponse
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

    @DELETE("api/trash/dump")
    suspend fun dump(): Response<ApiEnvelope<Unit>>
}
