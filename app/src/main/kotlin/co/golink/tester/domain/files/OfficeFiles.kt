package co.golink.tester.domain.files

import co.golink.tester.domain.browse.BrowseItem

/**
 * Documentos que o editor de documentos (WebView em /editor-embed) abre e
 * grava. Espelha UpdateFileContentAction::OFFICE_EXTENSIONS no backend e
 * OFFICE_EXTENSIONS em resources/js/services/officeFiles.js.
 */
val OFFICE_EXTENSIONS = listOf("docx", "xlsx")

enum class OfficeKind(val extension: String, val mimeType: String, val asset: String) {
    DOC("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "office/blank.docx"),
    SHEET("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "office/blank.xlsx"),
}

/** Extensão real: a do basename (o nome pode vir cifrado). */
fun BrowseItem.File.officeExtension(): String? =
    listOf(basename, name, mimetype.orEmpty())
        .map { it.lowercase().substringAfterLast('.') }
        .firstOrNull { it in OFFICE_EXTENSIONS }

fun BrowseItem.File.isOfficeFile(): Boolean = officeExtension() != null

fun BrowseItem.File.officeKind(): OfficeKind? = OfficeKind.entries.firstOrNull { it.extension == officeExtension() }

/** Garante a extensão do tipo escolhido no nome escrito pelo utilizador. */
fun withOfficeExtension(name: String, kind: OfficeKind): String {
    val trimmed = name.trim().trimEnd('.')
    return if (trimmed.lowercase().endsWith(".${kind.extension}")) trimmed else "$trimmed.${kind.extension}"
}
