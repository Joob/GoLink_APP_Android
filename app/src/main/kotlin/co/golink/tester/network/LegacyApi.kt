package co.golink.tester.network

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Legacy (routes/legacy.php, prefixo api/legacy). Por agora só o check-in. */
interface LegacyApi {
    @POST("api/legacy/checkin")
    suspend fun checkin(@Body body: JsonObject): Response<ResponseBody>
}
