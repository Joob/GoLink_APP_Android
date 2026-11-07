package co.golink.tester.network

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Passkeys (WebAuthn). As opções e as respostas vão em JSON cru: o Credential
 * Manager consome/produz exatamente o formato WebAuthn do servidor.
 */
interface PasskeyApi {
    // Login (sem sessão): token = true → token Sanctum com todas as abilities.
    @POST("api/passkeys/login/options")
    suspend fun loginOptions(): Response<JsonObject>

    @POST("api/passkeys/login/verify")
    suspend fun loginVerify(@Body body: JsonObject): Response<JsonObject>

    // Gestão das passkeys da conta
    @GET("api/user/passkeys")
    suspend fun list(): Response<JsonObject>

    /** body: { password } ou { passkey_token }; 423 = falta confirmar. */
    @POST("api/user/passkeys/options")
    suspend fun registerOptions(@Body body: JsonObject): Response<JsonObject>

    /** body: credential + o mesmo password/passkey_token do options (o token gasta-se aqui). */
    @POST("api/user/passkeys")
    suspend fun register(@Body body: JsonObject): Response<JsonObject>

    @PATCH("api/user/passkeys/{id}")
    suspend fun rename(@Path("id") id: String, @Body body: JsonObject): Response<JsonObject>

    /** body: { password } ou { passkey_token } (finalidade remove_passkey); 423 = falta confirmar. */
    @HTTP(method = "DELETE", path = "api/user/passkeys/{id}", hasBody = true)
    suspend fun delete(@Path("id") id: String, @Body body: JsonObject): Response<Unit>

    // Confirmar com passkey em vez da password (devolve passkey_token, 5 min)
    @POST("api/user/passkeys/confirm/options")
    suspend fun confirmOptions(): Response<JsonObject>

    @POST("api/user/passkeys/confirm")
    suspend fun confirm(@Body body: JsonObject): Response<JsonObject>
}
