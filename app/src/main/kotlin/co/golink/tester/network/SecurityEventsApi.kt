package co.golink.tester.network

import co.golink.tester.domain.security.SecurityEventsResponse
import retrofit2.Response
import retrofit2.http.GET

interface SecurityEventsApi {
    @GET("api/user/security-events")
    suspend fun list(): Response<SecurityEventsResponse>
}
