package co.golink.tester.domain.trash

import co.golink.tester.domain.files.ItemRef
import kotlinx.serialization.Serializable

@Serializable
data class RestoreTrashRequest(
    val items: List<ItemRef>,
)
