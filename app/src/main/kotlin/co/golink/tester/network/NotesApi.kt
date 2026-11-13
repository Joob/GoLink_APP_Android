package co.golink.tester.network

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** Só o que é nativo nas Notes (o resto corre na WebView, pela ponte). */
interface NotesApi {
    /** Caixas com data entre from e to (YYYY-MM-DD) de todas as notas + as chaves para as decifrar. */
    @GET("api/notes/tasks")
    suspend fun tasks(@Query("from") from: String, @Query("to") to: String): Response<JsonObject>
}
