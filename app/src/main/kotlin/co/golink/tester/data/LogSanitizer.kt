package co.golink.tester.data

/**
 * Remove dados sensíveis das mensagens de log antes de serem guardadas/mostradas.
 * O log é visível ao utilizador em Definições → Segurança → Mostrar log, por
 * isso nunca deve conter tokens, ids internos ou emails completos.
 *
 * A ordem importa: emails primeiro (o domínio nunca é longo o suficiente para
 * ser apanhado como token), depois UUIDs (contêm hífens) e por fim os tokens
 * "soltos" longos.
 */
object LogSanitizer {
    private val EMAIL = Regex("""([\w.+-])[\w.+-]*@([\w.-]+\.[A-Za-z]{2,})""")
    private val UUID = Regex(
        """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""",
    )
    // Sequências alfanuméricas longas: tokens de partilha, hashes, bearer, etc.
    private val LONG_TOKEN = Regex("""[A-Za-z0-9_-]{24,}""")

    fun redact(message: String): String = message
        .replace(EMAIL) { m -> "${m.groupValues[1]}***@${m.groupValues[2]}" }
        .replace(UUID, "[id]")
        .replace(LONG_TOKEN, "[token]")
}
