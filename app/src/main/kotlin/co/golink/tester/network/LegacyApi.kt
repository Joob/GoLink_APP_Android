package co.golink.tester.network

import co.golink.tester.domain.legacy.*
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming


/** Legacy (routes/legacy.php, prefixo api/legacy). */
interface LegacyApi {
    /** Sinal de vida em segundo plano (source = android). */
    @POST("api/legacy/checkin")
    suspend fun checkin(@Body body: JsonObject): Response<ResponseBody>

    /** "Ainda estou aqui" (source = manual): devolve o cofre atualizado. */
    @POST("api/legacy/checkin")
    suspend fun checkinNow(@Body body: JsonObject): Response<LegacyCheckinResponse>

    @GET("api/legacy/vault")
    suspend fun vault(): Response<LegacyVaultResponse>

    @POST("api/legacy/vault")
    suspend fun createVault(@Body body: JsonObject): Response<LegacyDataResponse>

    @PATCH("api/legacy/vault")
    suspend fun updateVault(@Body body: JsonObject): Response<LegacyDataResponse>

    /** arm | disarm | pause | resume. */
    @POST("api/legacy/vault/{action}")
    suspend fun vaultAction(@Path("action") action: String, @Body body: JsonObject): Response<LegacyDataResponse>

    @GET("api/legacy/events")
    suspend fun events(@Query("category") category: String?, @Query("cursor") cursor: String?): Response<LegacyEventsResponse>

    @POST("api/legacy/events")
    suspend fun logEvent(@Body body: JsonObject): Response<ResponseBody>

    // ---- itens ----

    @POST("api/legacy/items")
    suspend fun createItem(@Body body: JsonObject): Response<LegacyItemResponse>

    @GET("api/legacy/items/{id}")
    suspend fun item(@Path("id") id: String): Response<LegacyItemPayloadResponse>

    @PATCH("api/legacy/items/{id}")
    suspend fun updateItem(@Path("id") id: String, @Body body: JsonObject): Response<LegacyItemResponse>

    @DELETE("api/legacy/items/{id}")
    suspend fun deleteItem(@Path("id") id: String): Response<ResponseBody>

    /** Só acrescenta, em lote: quem já tem o item fica como está. */
    @POST("api/legacy/items/grants")
    suspend fun addGrants(@Body body: JsonObject): Response<ResponseBody>

    @POST("api/legacy/items/{id}/files")
    suspend fun addFiles(@Path("id") id: String, @Body body: JsonObject): Response<ResponseBody>

    @GET("api/legacy/folder-manifest/{id}")
    suspend fun folderManifest(@Path("id") folderId: String, @Query("missing_for") missingFor: String?): Response<LegacyFolderManifestResponse>

    // ---- pessoas (Fase 3) ----

    /** Roster completo de uma vez: devolve {vault, guardians}. */
    @PUT("api/legacy/guardians")
    suspend fun syncGuardians(@Body body: JsonObject): Response<LegacyDataResponse>

    @POST("api/legacy/guardians/{id}/reinvite")
    suspend fun reinviteGuardian(@Path("id") id: String, @Body body: JsonObject = JsonObject(emptyMap())): Response<LegacyGuardianResponse>

    @POST("api/legacy/guardians/{id}/reconfirm")
    suspend fun reconfirmGuardian(@Path("id") id: String, @Body body: JsonObject): Response<LegacyGuardianResponse>

    @POST("api/legacy/recipients")
    suspend fun createRecipient(@Body body: JsonObject): Response<LegacyRecipientResponse>

    @PATCH("api/legacy/recipients/{id}")
    suspend fun updateRecipient(@Path("id") id: String, @Body body: JsonObject): Response<LegacyRecipientResponse>

    @DELETE("api/legacy/recipients/{id}")
    suspend fun deleteRecipient(@Path("id") id: String): Response<ResponseBody>

    /** Nova K_vault (só com rekey_required): devolve o cofre completo. */
    @POST("api/legacy/vault/rekey")
    suspend fun rekey(@Body body: JsonObject): Response<LegacyDataResponse>

    // ---- guardião (Fase 4) ----

    @GET("api/legacy/guarding")
    suspend fun guarding(): Response<LegacyGuardingListResponse>

    /** accept | decline. */
    @POST("api/legacy/guarding/{id}/{action}")
    suspend fun guardingAction(@Path("id") id: String, @Path("action") action: String, @Body body: JsonObject = JsonObject(emptyMap())): Response<ResponseBody>

    @POST("api/legacy/guarding/{id}/confirm")
    suspend fun guardingConfirm(@Path("id") id: String, @Body body: JsonObject): Response<LegacyGuardingRowResponse>

    @GET("api/legacy/guarding/{id}/release")
    suspend fun release(@Path("id") id: String): Response<LegacyReleaseResponse>

    @POST("api/legacy/guarding/{id}/contribute")
    suspend fun contribute(@Path("id") id: String, @Body body: JsonObject): Response<ResponseBody>

    @POST("api/legacy/guarding/{id}/recipients/{recipientId}/resend")
    suspend fun resendLink(@Path("id") id: String, @Path("recipientId") recipientId: String, @Body body: JsonObject = JsonObject(emptyMap())): Response<ResponseBody>

    // ---- páginas públicas (sem sessão) ----

    @GET("api/legacy/public/invite/{id}")
    suspend fun publicInvite(@Path("id") id: String): Response<LegacyInviteResponse>

    /** Só com o botão: os scanners de email abrem links (GET). */
    @POST("api/legacy/public/checkin/{token}")
    suspend fun publicCheckin(@Path("token") token: String, @Body body: JsonObject = JsonObject(emptyMap())): Response<LegacyPublicCheckinResponse>

    @GET("api/legacy/public/open/{token}")
    suspend fun publicOpen(@Path("token") token: String): Response<LegacyOpenResponse>

    @GET("api/legacy/public/open/{token}/items/{itemId}/files")
    suspend fun publicFiles(@Path("token") token: String, @Path("itemId") itemId: String, @Query("page") page: Int): Response<LegacyOpenFilesResponse>

    @Streaming
    @GET("api/legacy/public/open/{token}/items/{itemId}/files/{fileId}")
    suspend fun publicDownload(@Path("token") token: String, @Path("itemId") itemId: String, @Path("fileId") fileId: String): Response<ResponseBody>
}
