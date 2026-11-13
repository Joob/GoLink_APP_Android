package co.golink.tester.data.favourites

import co.golink.tester.domain.favourites.AddFavouritesRequest
import co.golink.tester.network.FavouritesApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavouritesRepository @Inject constructor(
    private val api: FavouritesApi,
) {
    suspend fun add(folderId: String): Result<Unit> = runCatching {
        val response = api.add(AddFavouritesRequest(ids = listOf(folderId)))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun remove(folderId: String): Result<Unit> = runCatching {
        val response = api.remove(folderId)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }
}
