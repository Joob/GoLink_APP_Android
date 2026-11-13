package co.golink.tester.domain.favourites

import kotlinx.serialization.Serializable

@Serializable
data class AddFavouritesRequest(
    val ids: List<String>,
)
