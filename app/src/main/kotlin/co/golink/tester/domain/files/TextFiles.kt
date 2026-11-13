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
 * Primeiro nome livre na pasta: "notas.txt" → "notas (1).txt". Criar nunca
 * deve cair no conflito "Substituir" do upload, que apagava o ficheiro existente.
 */
fun freeFileName(filename: String, takenNames: Collection<String>): String {
    val taken = takenNames.mapTo(HashSet()) { it.lowercase() }
    if (filename.lowercase() !in taken) return filename
    val dot = filename.lastIndexOf('.')
    val base = if (dot > 0) filename.substring(0, dot) else filename
    val ext = if (dot > 0) filename.substring(dot) else ""
    return generateSequence(1) { it + 1 }.map { "$base ($it)$ext" }.first { it.lowercase() !in taken }
}

// Pontos finais soltos ("notas.") não são extensão nenhuma.
private fun normalizeName(name: String): String = name.trim().trimEnd('.')

/** O ficheiro pode ser gravado pelo editor (o servidor recusa o resto com 422). */
fun BrowseItem.File.isEditableText(): Boolean {
    val mt = mimetype?.lowercase().orEmpty()
    return mt == "text/plain" || mt in TEXT_EXTENSIONS || hasTextExtension(name) ||
        hasTextExtension(basename)
}
