package co.golink.tester.network

import co.golink.tester.domain.browse.BrowseListResponse
import co.golink.tester.domain.teams.ConvertToTeamFolderRequest
import co.golink.tester.domain.teams.CreateTeamFolderRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TeamsApi {
    @POST("api/teams/folders")
    suspend fun createTeamFolder(@Body body: CreateTeamFolderRequest): Response<Unit>

    @POST("api/teams/folders/{folderId}/convert")
    suspend fun convertToTeamFolder(
        @Path("folderId") folderId: String,
        @Body body: ConvertToTeamFolderRequest,
    ): Response<Unit>

    @GET("api/teams/folders/{id}")
    suspend fun browseTeamFolder(
        @Path("id") id: String,
        @Query("page") page: String = "all",
        @Query("per_page") perPage: Int? = null,
    ): Response<BrowseListResponse>

    @GET("api/teams/shared-with-me/{id}")
    suspend fun browseSharedWithMe(
        @Path("id") id: String,
        @Query("page") page: String = "all",
        @Query("per_page") perPage: Int? = null,
    ): Response<BrowseListResponse>
}
