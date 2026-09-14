package co.golink.tester.network

import co.golink.tester.domain.user.DeleteAccountRequest
import co.golink.tester.domain.user.DeletionProgressResponse
import co.golink.tester.domain.user.SendDeletionCodeRequest
import co.golink.tester.domain.user.SimpleMessageResponse
import co.golink.tester.domain.user.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST

interface UserApi {
    @GET("api/user")
    suspend fun me(): Response<UserResponse>

    // ── Eliminação de conta (mesmo fluxo da web) ─────────────────────────
    // 1. Código de 6 dígitos por email (válido 30 min).
    @POST("api/user/account/send-deletion-code")
    suspend fun sendDeletionCode(@Body body: SendDeletionCodeRequest): Response<SimpleMessageResponse>

    // 2. Confirmar: suspende a conta e agenda o DeleteUserAccountJob no backend.
    @HTTP(method = "DELETE", path = "api/user/account", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest): Response<SimpleMessageResponse>

    // 3. Polling do progresso — único endpoint autorizado durante a suspensão
    // de eliminação; 401/403 aqui significa que o job terminou e revogou o token.
    @GET("api/user/account/progress")
    suspend fun deletionProgress(): Response<DeletionProgressResponse>
}
