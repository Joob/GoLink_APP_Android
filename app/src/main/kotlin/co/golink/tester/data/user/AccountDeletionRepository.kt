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

    suspend fun sendCode(emailConfirmation: String): Result<String?> = runCatching {
        val response = api.sendDeletionCode(SendDeletionCodeRequest(emailConfirmation))
        if (!response.isSuccessful) throw httpError(response)
        response.body()?.message
    }

    suspend fun confirm(code: String): Result<String?> = runCatching {
        val response = api.deleteAccount(DeleteAccountRequest(code))
        if (!response.isSuccessful) throw httpError(response)
        response.body()?.message
    }

    suspend fun progress(): Result<DeletionProgressResponse> = runCatching {
        val response = api.deletionProgress()
        if (!response.isSuccessful) throw HttpException(response.code(), "HTTP ${response.code()}")
        response.body() ?: DeletionProgressResponse()
    }

    /** Extrai o "message" do JSON de erro do Laravel; cai no código HTTP. */
    private fun httpError(response: Response<*>): HttpException {
        val message = runCatching {
            val raw = response.errorBody()?.string().orEmpty()
            Json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()

        return HttpException(response.code(), message ?: "HTTP ${response.code()}")
    }
}
