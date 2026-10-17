package co.golink.tester.network

import co.golink.tester.domain.browse.BrowseListResponse
import co.golink.tester.domain.browse.FolderFingerprint
import co.golink.tester.domain.browse.NavigationSection
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BrowseApi {
    @GET("api/browse/folders/{id}")
    suspend fun browseFolder(
        @Path("id") id: String,
        @Query("page") page: String = "all",
        @Query("per_page") perPage: Int? = null,
        // Filtro por tipo (chips) e ordenação no servidor, como na web.
        @Query("type") type: String? = null,
        @Query("sort") sort: String? = null,
        @Query("direction") direction: String? = null,
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
        // Filtro por tipo (chips) e ordenação no servidor, como na web.
        @Query("type") type: String? = null,
        @Query("sort") sort: String? = null,
        @Query("direction") direction: String? = null,
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

    // Cartões dos telemóveis (nome, último envio, totais, pausa por aparelho).
    @GET("api/browse/mobile-backup/summary")
    suspend fun mobileBackupSummary(): Response<co.golink.tester.domain.backup.BackupSummary>

    @POST("api/browse/mobile-backup/devices/{id}/pause")
    suspend fun pauseBackupDevice(
        @Path("id") id: String,
        @Body body: co.golink.tester.domain.backup.PauseDeviceBody,
    ): Response<okhttp3.ResponseBody>

    @GET("api/search")
    suspend fun search(
        @Query("query") query: String?,
        @Query("filter") filter: String? = null,
        // Filtros do parser local (SearchQueryParser) — sem IA.
        @Query("kind") kind: String? = null,
        @Query("types[]") types: List<String>? = null,
        @Query("extensions[]") extensions: List<String>? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("size_min") sizeMin: Long? = null,
        @Query("size_max") sizeMax: Long? = null,
    ): Response<BrowseListResponse>

    // E2E Fase 2 — pesquisa client-side: entradas completas para os ids que o
    // cliente encontrou ao filtrar o índice de nomes cifrados localmente.
    @POST("api/search/encrypted-ids")
    suspend fun searchEncryptedIds(
        @Body body: co.golink.tester.domain.encryption.SearchByIdsBody,
    ): Response<BrowseListResponse>
}
