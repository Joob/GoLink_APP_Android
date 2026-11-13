package co.golink.tester.network

import retrofit2.Response
import retrofit2.http.GET

interface ApiService {
    @GET("api/ping")
    suspend fun ping(): Response<Unit>
}
