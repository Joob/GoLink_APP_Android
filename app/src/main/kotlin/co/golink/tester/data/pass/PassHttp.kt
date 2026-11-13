package co.golink.tester.data.pass

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
import okhttp3.RequestBody.Companion.toRequestBody

/** Erro HTTP da API do Pass (status 0 = sem rede). `body` = corpo JSON (409 traz `data`). */
class PassHttpException(
    val status: Int,
    override val message: String?,
    val body: JsonObject? = null,
    val retryAfter: Int? = null,
) : IOException(message) {
    /** `code` do corpo (422 de quota: "pass_quota" / "pass_item_too_large"). */
    val code: String? get() = (body?.get("code") as? JsonPrimitive)?.contentOrNull
}

/** Pedidos a /api/pass/… com o cliente autenticado (mesmo padrão de NotesHttp). */
@Singleton
class PassHttp @Inject constructor(
    private val backend: BackendUrlHolder,
    @Named("authed") private val http: OkHttpClient,
) {
    private val jsonType = "application/json".toMediaType()

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()): JsonObject = json("GET", path, null, query)
    suspend fun post(path: String, body: JsonObject? = null): JsonObject = json("POST", path, body)
    suspend fun patch(path: String, body: JsonObject? = null): JsonObject = json("PATCH", path, body)
    suspend fun delete(path: String, body: JsonObject? = null): JsonObject = json("DELETE", path, body)

    /** Corpo em texto (o proxy de fugas devolve text/plain). */
    suspend fun getText(path: String): String = send("GET", path, null, emptyMap())

    private suspend fun json(method: String, path: String, body: JsonObject?, query: Map<String, Any?> = emptyMap()): JsonObject {
        val text = send(method, path, body, query)
        return runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    }

    private suspend fun send(method: String, path: String, body: JsonObject?, query: Map<String, Any?>): String =
        withContext(Dispatchers.IO) {
            val url = (backend.current.trimEnd('/') + "/api/pass" + path).toHttpUrl().newBuilder().apply {
                query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) }
            }.build()
            val requestBody = when {
                body != null -> body.toString().toRequestBody(jsonType)
                method == "GET" || method == "DELETE" -> null
                else -> ByteArray(0).toRequestBody(null)
            }
            val request = Request.Builder().url(url).method(method, requestBody).header("Accept", "application/json").build()
            val response = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw PassHttpException(0, "network")
            }
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                    val message = (obj?.get("message") as? JsonPrimitive)?.takeIf { m -> m !is JsonNull }?.contentOrNull
                    throw PassHttpException(it.code, message, obj, it.header("Retry-After")?.toIntOrNull())
                }
                text
            }
        }
}
