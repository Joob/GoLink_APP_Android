package co.golink.tester.network

import co.golink.tester.domain.config.AppConfigResponse
import retrofit2.Response
import retrofit2.http.GET

interface ConfigApi {
    @GET("api/config")
    suspend fun config(): Response<AppConfigResponse>
}
