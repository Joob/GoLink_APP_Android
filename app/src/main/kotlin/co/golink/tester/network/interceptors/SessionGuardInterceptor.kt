package co.golink.tester.network.interceptors

import co.golink.tester.data.auth.TokenStore
import co.golink.tester.data.config.BackendUrlHolder
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Termina a sessão local quando o servidor sinaliza que ela deixou de ser
 * válida: conta suspensa (403 account_suspended) ou sessão revogada (401 com
 * session_revoked — force-logout do admin, sessão terminada noutro aparelho,
 * conta suspensa, password mudada; o servidor apaga o token e o 401 traz a flag).
 *
 * Sem isto o token continuava guardado e a app ficava presa a receber erros
 * (sessão zombie). O resto da limpeza (E2E, Pass, caches, cookies) corre no
 * [co.golink.tester.data.auth.SessionTerminator], chamado pelo TokenStore.clear().
 *
 * Só conta se o pedido levou o token ATUAL: a resposta atrasada de uma sessão
 * anterior (ou a revogação de um token antigo) não termina a sessão nova.
 */
@Singleton
class SessionGuardInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val backendUrlHolder: BackendUrlHolder,
    private val deletionGuard: co.golink.tester.data.auth.DeletionGuard,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code != 401 && response.code != 403) return response

        val backendHost = backendUrlHolder.current.toHttpUrlOrNull()?.host
        val targetsBackend = request.url.host == backendHost ||
            request.url.host == HostRewriteInterceptor.PLACEHOLDER_HOST
        if (!targetsBackend) return response

        // Nunca limpar durante o próprio login: um 401/403 aí é credenciais
        // erradas ou conta suspensa a tentar entrar, não uma sessão perdida.
        if (isLoginPath(request.url.encodedPath)) return response

        // Durante a eliminação de conta a suspensão é esperada (lockdown) — o
        // ecrã de progresso é que decide quando terminar a sessão.
        if (deletionGuard.active) return response

        val current = tokenStore.token ?: return response
        if (request.header("Authorization") != "Bearer $current") return response

        if (endsSession(response.code, response.peekBody(PEEK_LIMIT).string())) tokenStore.clear()

        return response
    }

    internal companion object {
        /** Pedidos do próprio login: um 401/403 aí nunca termina a sessão. */
        fun isLoginPath(path: String): Boolean = LOGIN_PATHS.any { path.contains(it) }

        /** Se um 401/403 do backend (com o token atual) significa sessão terminada. */
        fun endsSession(code: Int, body: String): Boolean =
            (code == 401 || code == 403) && (
                body.contains(ACCOUNT_SUSPENDED) ||
                    body.contains(SESSION_REVOKED) ||
                    // Servidor antigo: token apagado dá o 401 genérico do Laravel. Os 401
                    // próprios (ZIP/downloads em dunning, OTP web) têm outra mensagem.
                    (code == 401 && body.contains(LARAVEL_UNAUTHENTICATED) && !body.contains(OTP_REQUIRED))
                )

        private const val PEEK_LIMIT = 64L * 1024L
        private const val ACCOUNT_SUSPENDED = "account_suspended"
        private const val SESSION_REVOKED = "session_revoked"
        private const val OTP_REQUIRED = "otp_required"
        private const val LARAVEL_UNAUTHENTICATED = "\"message\":\"Unauthenticated.\""
        private val LOGIN_PATHS = listOf(
            "/api/login",
            "/api/user/validate-otp-code",
            "/api/passkeys/login/",
            "/api/socialite/",
        )
    }
}
