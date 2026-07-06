package co.golink.tester.network.interceptors

import android.os.Build
import co.golink.tester.BuildConfig
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
        // Identify as the Android app so the backend classifies login sessions
        // as "mobile" (it parses the User-Agent — "Android"/"Mobile" => mobile).
        // The "; <model> Build/<id>" shape lets it extract the device name too.
        if (request.header("User-Agent") == null) {
            builder.header("User-Agent", USER_AGENT)
        }
        if (request.header("Authorization") == null) {
            tokenStore.token?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }

    companion object {
        val USER_AGENT: String =
            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID}) " +
                "GoLinkApp/${BuildConfig.VERSION_NAME} Mobile"
    }
}
