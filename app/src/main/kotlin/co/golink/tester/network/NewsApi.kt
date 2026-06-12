package co.golink.tester.network

import co.golink.tester.domain.news.UpdateSettingRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query

interface NewsApi {
    @GET("api/settings")
    suspend fun newsSettings(
        @Query("column") column: String = "allowed_news|news_message",
    ): Response<Map<String, String?>>

    @PATCH("api/admin/settings")
    suspend fun updateSetting(@Body body: UpdateSettingRequest): Response<ResponseBody>
}
