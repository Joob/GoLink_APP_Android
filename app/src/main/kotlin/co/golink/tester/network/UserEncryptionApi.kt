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
}
