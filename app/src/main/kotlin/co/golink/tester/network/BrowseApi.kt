package co.golink.tester.network

import co.golink.tester.domain.browse.BrowseListResponse
import co.golink.tester.domain.browse.NavigationSection
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BrowseApi {
    @GET("api/browse/folders/{id}")
    suspend fun browseFolder(
        @Path("id") id: String,
        @Query("page") page: String = "all",
        @Query("per_page") perPage: Int? = null,
    ): Response<BrowseListResponse>

    @GET("api/browse/navigation")
    suspend fun navigation(): Response<List<NavigationSection>>

    @GET("api/browse/recents")
    suspend fun latest(
        @Query("page") page: String = "all",
        @Query("per_page") perPage: Int? = null,
    ): Response<BrowseListResponse>

    @GET("api/browse/share")
    suspend fun shared(
        @Query("page") page: String = "all",
        @Query("per_page") perPage: Int? = null,
    ): Response<BrowseListResponse>

    @GET("api/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("filter") filter: String? = null,
    ): Response<BrowseListResponse>
}
