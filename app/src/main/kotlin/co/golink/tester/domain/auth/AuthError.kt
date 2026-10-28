package co.golink.tester.domain.auth

sealed class AuthError(message: String) : Exception(message) {
    class InvalidCredentials(message: String = "Credenciais inválidas") : AuthError(message)
    class AccountNotFound(message: String = "Conta não encontrada") : AuthError(message)
    class EmailNotVerified(message: String = "Email não verificado") : AuthError(message)
    class RegistrationDisabled(message: String = "Registo desactivado") : AuthError(message)
    class OtpInvalid(message: String = "Código inválido ou expirado") : AuthError(message)

    /**
     * 5 códigos errados seguidos: o servidor impõe um cooldown (HTTP 429).
     * `rateLimited` = espera entre envios (rate_limited), não tentativas falhadas.
     */
    class OtpCooldown(
        val remainingSeconds: Int,
        message: String = "Demasiadas tentativas",
        val rateLimited: Boolean = false,
    ) : AuthError(message)
    class Validation(val fields: Map<String, List<String>>, message: String) : AuthError(message)
    class Network(message: String) : AuthError(message)
    class Server(val statusCode: Int, message: String) : AuthError("$message (HTTP $statusCode)")
    class Unknown(message: String) : AuthError(message)
}
