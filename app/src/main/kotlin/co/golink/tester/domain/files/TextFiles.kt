package co.golink.tester.domain.files

import co.golink.tester.domain.browse.BrowseItem

/**
 * Extensões que o editor de texto sabe gravar. Espelha
 * UpdateFileContentAction::TEXT_EXTENSIONS no backend (e TEXT_EXTENSIONS em
 * resources/js/services/textFiles.js na web) — manter as listas em sincronia.
 * O viewer mostra mais tipos (xml, html) do que os que se podem gravar.
 */
val TEXT_EXTENSIONS = listOf("txt", "text", "md", "json", "csv", "log")

const val DEFAULT_TEXT_EXTENSION = "txt"

/**
 * As que mostramos ao utilizador. `.text` continua aceite (ficheiros antigos)
 * mas é um alias de `.txt` — listá-lo só confundia.
 */
val VISIBLE_TEXT_EXTENSIONS = TEXT_EXTENSIONS.filter { it != "text" }

/** ".txt, .md, .json, .csv, .log" — para a dica do diálogo de criação. */
fun visibleTextExtensionsLabel(): String = VISIBLE_TEXT_EXTENSIONS.joinToString(", ") { ".$it" }

fun hasTextExtension(name: String): Boolean {
    val lower = name.lowercase()
    return TEXT_EXTENSIONS.any { lower.endsWith(".$it") }
}

/** Garante que o nome escrito pelo utilizador acaba numa extensão de texto. */
fun withTextExtension(name: String): String {
    val trimmed = normalizeName(name)
    return if (hasTextExtension(trimmed)) trimmed else "$trimmed.$DEFAULT_TEXT_EXTENSION"
}

/**
 * Extensão escrita pelo utilizador que NÃO é de texto (ex.: "pdf"), ou null
 * quando o nome é aceitável. Sem extensão é aceitável — leva `.txt`.
 * O servidor recusa o resto com 422, por isso avisamos antes de criar.
 */
fun unsupportedExtension(name: String): String? {
    val trimmed = normalizeName(name)
    val dot = trimmed.lastIndexOf('.')
    if (dot <= 0 || hasTextExtension(trimmed)) return null
    return trimmed.substring(dot + 1).lowercase()
}

// Pontos finais soltos ("notas.") não são extensão nenhuma.
private fun normalizeName(name: String): String = name.trim().trimEnd('.')

/** O ficheiro pode ser gravado pelo editor (o servidor recusa o resto com 422). */
fun BrowseItem.File.isEditableText(): Boolean {
    val mt = mimetype?.lowercase().orEmpty()
    return mt == "text/plain" || mt in TEXT_EXTENSIONS || hasTextExtension(name) ||
        hasTextExtension(basename)
}
