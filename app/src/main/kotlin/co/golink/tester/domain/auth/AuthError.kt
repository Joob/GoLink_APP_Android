package co.golink.tester.domain.auth

sealed class AuthError(message: String) : Exception(message) {
    class InvalidCredentials(message: String = "Credenciais inválidas") : AuthError(message)
    class AccountNotFound(message: String = "Conta não encontrada") : AuthError(message)
    class EmailNotVerified(message: String = "Email não verificado") : AuthError(message)
    class RegistrationDisabled(message: String = "Registo desactivado") : AuthError(message)
    class OtpInvalid(message: String = "Código inválido ou expirado") : AuthError(message)
    class Validation(val fields: Map<String, List<String>>, message: String) : AuthError(message)
    class Network(message: String) : AuthError(message)
    class Server(val statusCode: Int, message: String) : AuthError("$message (HTTP $statusCode)")
    class Unknown(message: String) : AuthError(message)
}
