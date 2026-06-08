package co.golink.tester.data.auth

import co.golink.tester.domain.auth.AuthError
import co.golink.tester.domain.auth.ErrorBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import retrofit2.Response

@Singleton
class ApiErrorParser @Inject constructor(
    private val json: Json,
) {
    fun parse(response: Response<*>): AuthError {
        val code = response.code()
        val raw = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
        val body = runCatching { json.decodeFromString(ErrorBody.serializer(), raw) }.getOrNull()
        val message = body?.message ?: when (code) {
            401 -> "Não autorizado"
            403 -> "Acesso negado"
            404 -> "Recurso não encontrado"
            409 -> "Conflito"
            422 -> "Dados inválidos"
            429 -> "Demasiados pedidos"
            in 500..599 -> "Erro do servidor"
            else -> "Erro inesperado"
        }
        val fields = body?.errors
        return when {
            code == 401 -> AuthError.InvalidCredentials(message)
            code == 404 -> AuthError.AccountNotFound(message)
            code == 422 && !fields.isNullOrEmpty() -> AuthError.Validation(fields, message)
            code == 422 -> AuthError.Validation(emptyMap(), message)
            code in 500..599 -> AuthError.Server(code, message)
            else -> AuthError.Server(code, message)
        }
    }

    fun fromThrowable(t: Throwable): AuthError = when (t) {
        is AuthError -> t
        is java.io.IOException -> AuthError.Network(t.message ?: "Falha de rede")
        else -> AuthError.Unknown(t.message ?: t::class.java.simpleName)
    }
}
