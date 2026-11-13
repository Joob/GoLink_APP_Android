package co.golink.tester.data.receipts

import co.golink.tester.data.config.BackendUrlHolder
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Erro HTTP da API dos recibos (status 0 = sem rede). 409 traz `data` no corpo. */
class ReceiptsHttpException(
    val status: Int,
    override val message: String?,
    val body: JsonObject? = null,
) : IOException(message)

/** Pedidos a /api/receipts/… com o cliente autenticado (mesmo padrão do PassHttp). */
@Singleton
class ReceiptsHttp @Inject constructor(
    private val backend: BackendUrlHolder,
    @Named("authed") private val http: OkHttpClient,
) {
    private val jsonType = "application/json".toMediaType()
    private val binType = "application/octet-stream".toMediaType()

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()): JsonObject = json("GET", path, null, query)
    suspend fun post(path: String, body: JsonObject? = null): JsonObject = json("POST", path, body?.toString()?.toRequestBody(jsonType))
    suspend fun patch(path: String, body: JsonObject): JsonObject = json("PATCH", path, body.toString().toRequestBody(jsonType))
    suspend fun delete(path: String): JsonObject = json("DELETE", path, null)

    /** Foto cifrada em binário (nonce‖secretbox), sem base64. */
    suspend fun putBytes(path: String, bytes: ByteArray): JsonObject = json("PUT", path, bytes.toRequestBody(binType))

    suspend fun getBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        execute("GET", url(path, emptyMap()), null) { it.body?.bytes() ?: ByteArray(0) }
    }

    private suspend fun json(method: String, path: String, body: RequestBody?, query: Map<String, Any?> = emptyMap()): JsonObject =
        withContext(Dispatchers.IO) {
            val text = execute(method, url(path, query), body) { it.body?.string().orEmpty() }
            runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        }

    private fun url(path: String, query: Map<String, Any?>) =
        (backend.current.trimEnd('/') + "/api/receipts" + path).toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) }
        }.build()

    private fun <T> execute(method: String, url: okhttp3.HttpUrl, body: RequestBody?, read: (okhttp3.Response) -> T): T {
        val requestBody = body ?: if (method == "GET" || method == "DELETE") null else ByteArray(0).toRequestBody(null)
        val request = Request.Builder().url(url).method(method, requestBody).header("Accept", "application/json").build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw ReceiptsHttpException(0, "network")
        }
        return response.use {
            if (!it.isSuccessful) {
                val text = it.body?.string().orEmpty()
                val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                val message = (obj?.get("message") as? JsonPrimitive)?.takeIf { m -> m !is JsonNull }?.contentOrNull
                throw ReceiptsHttpException(it.code, message, obj)
            }
            read(it)
        }
    }
}
