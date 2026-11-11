package co.golink.tester.data.net

/**
 * runCatching para pedidos de rede: nunca engole o cancelamento da corrotina
 * (sair do ecrã cancela de facto o pedido) e uma falha de rede passa a uma
 * frase traduzida em vez de "Unable to resolve host…".
 */
inline fun <T> netCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: java.io.IOException) {
    Result.failure(java.io.IOException(co.golink.tester.ui.i18n.I18n.t("Sem ligação ao servidor. Tenta novamente."), e))
} catch (e: Exception) {
    Result.failure(e)
}

/** Mensagem genérica traduzida para um erro HTTP sem "message" no corpo. */
fun genericHttpMessage(): String = co.golink.tester.ui.i18n.I18n.t("Ocorreu um erro. Tenta novamente.")
