package co.golink.tester.network.interceptors

import co.golink.tester.data.config.BackendUrlHolder
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class HostRewriteInterceptor @Inject constructor(
    private val holder: BackendUrlHolder,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = holder.current
        val parsed = base.toHttpUrlOrNull()
            ?: throw IOException("Backend URL inválido: $base")

        val original = chain.request()
        val isPlaceholder = original.url.host == PLACEHOLDER_HOST
        val newUrl = if (isPlaceholder) {
            original.url.newBuilder()
                .scheme(parsed.scheme)
                .host(parsed.host)
                .port(parsed.port)
                .build()
        } else original.url

        val targetsBackend = newUrl.host == parsed.host
        val builder = original.newBuilder().url(newUrl)
        if (targetsBackend) {
            if (original.header("Accept") == null) builder.addHeader("Accept", "application/json")
            if (original.header("X-Requested-With") == null) builder.addHeader("X-Requested-With", "XMLHttpRequest")
        }
        return chain.proceed(builder.build())
    }

    companion object {
        const val PLACEHOLDER_HOST = "localhost"
    }
}
