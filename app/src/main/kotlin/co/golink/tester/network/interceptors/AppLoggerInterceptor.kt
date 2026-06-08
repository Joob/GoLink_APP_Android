package co.golink.tester.network.interceptors

import co.golink.tester.data.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class AppLoggerInterceptor @Inject constructor(
    private val logger: AppLogger,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val start = System.nanoTime()
        return try {
            val res = chain.proceed(req)
            val ms = (System.nanoTime() - start) / 1_000_000
            logger.log("Http", "${res.code} (n ${ms}ms) -> ${req.method} ${req.url.encodedPath}")
            res
        } catch (e: Exception) {
            val ms = (System.nanoTime() - start) / 1_000_000
            logger.log("Http", "ERR (n ${ms}ms) -> ${req.method} ${req.url.encodedPath}: ${e.message}")
            throw e
        }
    }
}
