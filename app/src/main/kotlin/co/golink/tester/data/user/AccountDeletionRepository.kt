package co.golink.tester.data.user

import co.golink.tester.domain.user.DeleteAccountRequest
import co.golink.tester.domain.user.DeletionProgressResponse
import co.golink.tester.domain.user.SendDeletionCodeRequest
import co.golink.tester.network.UserApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

/**
 * Fluxo de eliminação de conta (espelha o self-service da web):
 * enviar código → confirmar (DELETE agenda o job no backend) → polling.
 */
@Singleton
class AccountDeletionRepository @Inject constructor(
    private val api: UserApi,
) {
    class HttpException(val statusCode: Int, message: String) : Exception(message)

    suspend fun sendCode(emailConfirmation: String): Result<String?> = co.golink.tester.data.net.netCatching {
        val response = api.sendDeletionCode(SendDeletionCodeRequest(emailConfirmation))
        if (!response.isSuccessful) throw httpError(response)
        response.body()?.message
    }

    /** Devolve a data (ISO) em que a conta será eliminada; null = eliminação imediata (servidor antigo). */
    suspend fun confirm(code: String): Result<String?> = co.golink.tester.data.net.netCatching {
        val response = api.deleteAccount(DeleteAccountRequest(code))
        if (!response.isSuccessful) throw httpError(response)
        response.body()?.takeIf { it.scheduled }?.scheduled_for
    }

    suspend fun cancelDeletion(): Result<Unit> = co.golink.tester.data.net.netCatching {
        val response = api.cancelDeletion()
        if (!response.isSuccessful) throw httpError(response)
    }

    suspend fun progress(): Result<DeletionProgressResponse> = co.golink.tester.data.net.netCatching {
        val response = api.deletionProgress()
        if (!response.isSuccessful) throw HttpException(response.code(), co.golink.tester.data.net.genericHttpMessage())
        response.body() ?: DeletionProgressResponse()
    }

    /** Extrai o "message" do JSON de erro do Laravel; cai no código HTTP. */
    private fun httpError(response: Response<*>): HttpException {
        val message = co.golink.tester.data.net.netCatching {
            val raw = response.errorBody()?.string().orEmpty()
            Json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()

        return HttpException(response.code(), message ?: co.golink.tester.data.net.genericHttpMessage())
    }
}
