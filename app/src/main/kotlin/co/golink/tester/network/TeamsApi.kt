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

    // Dono: dissolve (a pasta volta a ser pessoal). Membro: sai da equipa.
    @retrofit2.http.DELETE("api/teams/folders/{id}")
    suspend fun dissolveTeamFolder(@Path("id") id: String): Response<okhttp3.ResponseBody>

    @retrofit2.http.DELETE("api/teams/folders/{id}/leave")
    suspend fun leaveTeamFolder(@Path("id") id: String): Response<okhttp3.ResponseBody>

    @GET("api/teams/folders/{id}")
    suspend fun browseTeamFolder(
        @Path("id") id: String,
        @Query("page") page: String = "all",
        @Query("per_page") perPage: Int? = null,
        // Filtro por tipo (chips) e ordenação no servidor, como na web.
        @Query("type") type: String? = null,
        @Query("sort") sort: String? = null,
        @Query("direction") direction: String? = null,
    ): Response<BrowseListResponse>

    @GET("api/teams/shared-with-me/{id}")
    suspend fun browseSharedWithMe(
        @Path("id") id: String,
        @Query("page") page: String = "all",
        @Query("per_page") perPage: Int? = null,
        // Filtro por tipo (chips) e ordenação no servidor, como na web.
        @Query("type") type: String? = null,
        @Query("sort") sort: String? = null,
        @Query("direction") direction: String? = null,
    ): Response<BrowseListResponse>
}
