package co.golink.tester.network

import co.golink.tester.domain.browse.BrowseListResponse
import co.golink.tester.domain.browse.FolderFingerprint
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

    @GET("api/browse/folders/{id}/fingerprint")
    suspend fun folderFingerprint(
        @Path("id") id: String,
    ): Response<FolderFingerprint>

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

    @GET("api/browse/mobile-backup")
    suspend fun mobileBackup(
        @Query("type") type: String,
        @Query("page") page: String = "all",
        @Query("per_page") perPage: Int? = null,
        // with_folders=true: devolve as pastas de backup + ficheiros do nível
        // (parent_id). Sem isto (contagens) devolve os ficheiros em lista global.
        @Query("with_folders") withFolders: Boolean? = null,
        @Query("parent_id") parentId: String? = null,
        // count_only=true: só o total do tipo (badge), sem transferir a lista.
        @Query("count_only") countOnly: Boolean? = null,
    ): Response<BrowseListResponse>

    @GET("api/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("filter") filter: String? = null,
    ): Response<BrowseListResponse>
}
