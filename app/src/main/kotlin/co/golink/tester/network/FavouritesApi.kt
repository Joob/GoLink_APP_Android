package co.golink.tester.network

import co.golink.tester.domain.auth.ApiEnvelope
import co.golink.tester.domain.favourites.AddFavouritesRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface FavouritesApi {
    @POST("api/favourites")
    suspend fun add(@Body body: AddFavouritesRequest): Response<ApiEnvelope<Unit>>

    @DELETE("api/favourites/{id}")
    suspend fun remove(@Path("id") id: String): Response<ApiEnvelope<Unit>>
}
