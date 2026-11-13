package co.golink.tester.data.files

import co.golink.tester.ui.i18n.ServerMessages
import co.golink.tester.ui.i18n.tr

/**
 * Regras do servidor para nomes de pastas e ficheiros (CreateFolderRequest/RenameItemRequest):
 * até 255 caracteres, sem `/`, `\` nem caracteres de controlo, e nunca "." ou "..".
 */
object ItemNames {
    const val MAX_LENGTH = 255
    private val FORBIDDEN = Regex("""[/\\\u0000-\u001f]""")

    /** Mensagem traduzida do que está mal no nome, ou null se o servidor o aceita. */
    fun problem(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "O nome não pode ficar vazio".tr()
            n.length > MAX_LENGTH -> "O nome tem mais de %d caracteres".tr().format(MAX_LENGTH)
            FORBIDDEN.containsMatchIn(n) -> "O nome não pode ter / nem \\".tr()
            n == "." || n == ".." -> "Nome inválido".tr()
            else -> null
        }
    }

    /** Erro de um 422 ao criar/renomear: a mensagem do campo `name` (ou a geral), traduzida. */
    fun validationMessage(raw: String): String? {
        val body = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject }.getOrNull()
            ?: return null
        val errors = body["errors"] as? kotlinx.serialization.json.JsonObject
        val field = (errors?.get("name") ?: errors?.values?.firstOrNull()) as? kotlinx.serialization.json.JsonArray
        val text = ((field?.firstOrNull() ?: body["message"]) as? kotlinx.serialization.json.JsonPrimitive)?.content
        return ServerMessages.localize(text)
    }
}
