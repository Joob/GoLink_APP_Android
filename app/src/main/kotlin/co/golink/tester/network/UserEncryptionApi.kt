package co.golink.tester.network

import co.golink.tester.domain.encryption.EncryptionStatusResponse
import co.golink.tester.domain.encryption.FileEncryptionKeyResponse
import co.golink.tester.domain.encryption.PublicKeyResponse
import co.golink.tester.domain.encryption.RecoveryBlobResponse
import co.golink.tester.domain.encryption.StoreUserEncryptionBody
import co.golink.tester.domain.encryption.UpdateSecretBody
import co.golink.tester.domain.encryption.UserEncryptionResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

interface UserEncryptionApi {
    @GET("api/user/encryption")
    suspend fun get(): Response<UserEncryptionResponse>

    @POST("api/user/encryption")
    suspend fun setup(@Body body: StoreUserEncryptionBody): Response<EncryptionStatusResponse>

    @GET("api/user/encryption/recovery")
    suspend fun recovery(): Response<RecoveryBlobResponse>

    @PUT("api/user/encryption/secret")
    suspend fun updateSecret(@Body body: UpdateSecretBody): Response<EncryptionStatusResponse>

    @GET("api/user/encryption/public-key/{id}")
    suspend fun publicKey(@Path("id") id: String): Response<PublicKeyResponse>

    @GET("api/file/{id}/encryption-key")
    suspend fun fileKey(@Path("id") id: String): Response<FileEncryptionKeyResponse>

    @GET("api/user/encryption/migration-status")
    suspend fun migrationStatus(): Response<co.golink.tester.domain.encryption.MigrationStatusResponse>

    // E2E Fase 2 — migração de nomes: lote em claro + gravar cifrados.
    @GET("api/user/encryption/plain-names")
    suspend fun plainNames(): Response<co.golink.tester.domain.encryption.PlainNamesResponse>

    @POST("api/user/encryption/encrypt-names")
    suspend fun encryptNames(
        @Body body: co.golink.tester.domain.encryption.EncryptNamesBody,
    ): Response<okhttp3.ResponseBody>

    @GET("api/user/encryption/name-migration-status")
    suspend fun nameMigrationStatus(): Response<co.golink.tester.domain.encryption.NameMigrationStatusResponse>

    // Índice de nomes cifrados (pesquisa client-side).
    @GET("api/user/encryption/name-index")
    suspend fun nameIndex(): Response<co.golink.tester.domain.encryption.NameIndexResponse>
}
