package co.golink.tester.network.interceptors

import co.golink.tester.data.auth.TokenStore
import co.golink.tester.data.config.BackendUrlHolder
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val backendUrlHolder: BackendUrlHolder,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val backendHost = backendUrlHolder.current.toHttpUrlOrNull()?.host
        val targetsBackend = request.url.host == backendHost ||
            request.url.host == HostRewriteInterceptor.PLACEHOLDER_HOST
        if (!targetsBackend) return chain.proceed(request)
        val builder = request.newBuilder()
        if (request.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }
        if (request.header("X-Requested-With") == null) {
            builder.header("X-Requested-With", "XMLHttpRequest")
        }
        if (request.header("Authorization") == null) {
            tokenStore.token?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }
}
