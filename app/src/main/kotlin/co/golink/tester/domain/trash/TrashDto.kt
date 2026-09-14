package co.golink.tester.domain.trash

import co.golink.tester.domain.files.ItemRef
import kotlinx.serialization.Serializable

@Serializable
data class RestoreTrashRequest(
    val items: List<ItemRef>,
)

// Resposta do dump em lotes: quantos itens foram apagados neste lote e quantos
// ainda faltam, para a UI mostrar um progresso 0–100% real.
@Serializable
data class DumpTrashResponse(
    val deleted: Int = 0,
    val remaining: Int = 0,
)
