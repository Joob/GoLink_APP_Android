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
 * válida: conta suspensa (403 account_suspended) ou sessão revogada
 * (401 session_revoked, ex. force-logout do admin).
 *
 * Sem isto o token continuava guardado e a app ficava presa a receber erros.
 * O corpo da resposta é reencapsulado porque só pode ser lido uma vez.
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
        if (request.url.encodedPath.contains("/api/login")) return response

        // Durante a eliminação de conta a suspensão é esperada (lockdown) — o
        // ecrã de progresso é que decide quando terminar a sessão.
        if (deletionGuard.active) return response

        val body = response.peekBody(PEEK_LIMIT).string()
        if (body.contains(ACCOUNT_SUSPENDED) || body.contains(SESSION_REVOKED)) {
            tokenStore.clear()
        }

        return response
    }

    private companion object {
        const val PEEK_LIMIT = 64L * 1024L
        const val ACCOUNT_SUSPENDED = "account_suspended"
        const val SESSION_REVOKED = "session_revoked"
    }
}
