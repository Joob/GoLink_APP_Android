package co.golink.tester.data.notes

import co.golink.tester.data.config.BackendUrlHolder
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Erro HTTP da API das Notes (status 0 = sem rede). `message` = campo "message" do corpo. */
class NotesHttpException(val status: Int, override val message: String?, val body: JsonObject? = null) : IOException(message)

/** Pedidos à API das Notes (/api/notes/…) com o cliente autenticado da app. */
@Singleton
class NotesHttp @Inject constructor(
    private val backend: BackendUrlHolder,
    @Named("authed") private val http: OkHttpClient,
) {
    private val jsonType = "application/json".toMediaType()

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()): JsonObject = send("GET", path, null, query)

    suspend fun post(path: String, body: JsonObject? = null): JsonObject = send("POST", path, body)

    suspend fun put(path: String, body: JsonObject? = null): JsonObject = send("PUT", path, body)

    suspend fun patch(path: String, body: JsonObject? = null): JsonObject = send("PATCH", path, body)

    suspend fun delete(path: String, body: JsonObject? = null): JsonObject = send("DELETE", path, body)

    private suspend fun send(method: String, path: String, body: JsonObject?, query: Map<String, Any?> = emptyMap()): JsonObject =
        withContext(Dispatchers.IO) {
            val url = (backend.current.trimEnd('/') + "/api/notes" + path).toHttpUrl().newBuilder().apply {
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
                throw NotesHttpException(0, "network")
            }
            response.use {
                val text = it.body?.string().orEmpty()
                val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull()
                val obj = parsed as? JsonObject
                if (!it.isSuccessful) {
                    val message = (obj?.get("message") as? JsonElement)?.takeIf { m -> m !is JsonNull }?.jsonPrimitive?.contentOrNull
                    throw NotesHttpException(it.code, message, obj)
                }
                obj ?: JsonObject(emptyMap())
            }
        }
}
