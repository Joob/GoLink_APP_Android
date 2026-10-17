package co.golink.tester.network

import co.golink.tester.domain.activity.ItemActivityResponse
import co.golink.tester.domain.activity.RecentActivityResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ActivityApi {
    @GET("api/activity/recent")
    suspend fun recent(
        @Query("section") section: String? = null,
        @Query("page") page: Int? = null,
        @Query("per_page") perPage: Int? = null,
    ): Response<RecentActivityResponse>

    // Feed único (lotes de uploads + eventos), paginado por cursor — como a web.
    @GET("api/activity/feed")
    suspend fun feed(
        @Query("cursor") cursor: String? = null,
        @Query("per_page") perPage: Int = 20,
    ): Response<co.golink.tester.domain.activity.FeedResponse>

    // Últimos 7 dias + candidatos a duplicados.
    @GET("api/activity/summary")
    suspend fun summary(): Response<co.golink.tester.domain.activity.ActivitySummaryResponse>

    @GET("api/activity/item/{type}/{id}")
    suspend fun item(
        @Path("type") type: String,
        @Path("id") id: String,
        @Query("page") page: Int = 1,
    ): Response<ItemActivityResponse>
}
