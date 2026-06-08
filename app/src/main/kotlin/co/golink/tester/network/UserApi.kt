package co.golink.tester.network

import co.golink.tester.domain.user.UserResponse
import retrofit2.Response
import retrofit2.http.GET

interface UserApi {
    @GET("api/user")
    suspend fun me(): Response<UserResponse>
}
