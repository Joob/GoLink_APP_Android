package co.golink.tester.domain.files

import co.golink.tester.domain.browse.BrowseItem

/**
 * Documentos que o editor de documentos (WebView em /editor-embed) abre.
 * Espelha OFFICE_FORMATS em resources/js/services/officeFiles.js e
 * UpdateFileContentAction::OFFICE_EXTENSIONS no backend (onde se grava no lugar).
 *
 * odt/doc/rtf convertem-se no servidor (LibreOffice), por isso não abrem com E2E;
 * doc/rtf/xls são formatos antigos: a 1.ª gravação cria uma cópia docx/xlsx.
 */
enum class OfficeKind(val extension: String, val mimeType: String, val asset: String) {
    DOC("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "office/blank.docx"),
    SHEET("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "office/blank.xlsx"),
}

data class OfficeFormat(
    val extension: String,
    val kind: OfficeKind,
    val mimeType: String,
    /** Modelo vazio em assets/office (só os formatos que se podem criar). */
    val asset: String? = null,
    /** Convertido pelo LibreOffice no servidor: nunca com ficheiros cifrados. */
    val serverConverted: Boolean = false,
)

val OFFICE_FORMATS: List<OfficeFormat> = listOf(
    OfficeFormat("docx", OfficeKind.DOC, OfficeKind.DOC.mimeType, OfficeKind.DOC.asset),
    OfficeFormat("odt", OfficeKind.DOC, "application/vnd.oasis.opendocument.text", "office/blank.odt", serverConverted = true),
    OfficeFormat("doc", OfficeKind.DOC, "application/msword", serverConverted = true),
    OfficeFormat("rtf", OfficeKind.DOC, "application/rtf", serverConverted = true),
    OfficeFormat("xlsx", OfficeKind.SHEET, OfficeKind.SHEET.mimeType, OfficeKind.SHEET.asset),
    OfficeFormat("ods", OfficeKind.SHEET, "application/vnd.oasis.opendocument.spreadsheet", "office/blank.ods"),
    OfficeFormat("xls", OfficeKind.SHEET, "application/vnd.ms-excel"),
    OfficeFormat("csv", OfficeKind.SHEET, "text/csv", "office/blank.csv"),
)

val OFFICE_EXTENSIONS: List<String> = OFFICE_FORMATS.map { it.extension }

fun officeFormat(extension: String?): OfficeFormat? =
    OFFICE_FORMATS.firstOrNull { it.extension == extension?.lowercase() }

/** Formatos que se podem criar (o primeiro é o predefinido). */
fun createFormats(kind: OfficeKind): List<OfficeFormat> = OFFICE_FORMATS.filter { it.kind == kind && it.asset != null }

/** Tipo MIME pela extensão do nome (cópias, transferências). */
fun officeMimeFor(name: String): String = when (val ext = name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    else -> officeFormat(ext)?.mimeType ?: "application/octet-stream"
}

/** Extensão real: a do basename (o nome pode vir cifrado). */
fun BrowseItem.File.officeExtension(): String? =
    listOf(basename, name, mimetype.orEmpty())
        .map { it.lowercase().substringAfterLast('.') }
        .firstOrNull { it in OFFICE_EXTENSIONS }

/** Abre no editor; odt/doc/rtf cifrados não (a conversão no servidor veria o conteúdo). */
fun BrowseItem.File.isOfficeFile(): Boolean = officeFormat()?.let { !(it.serverConverted && encrypted) } ?: false

fun BrowseItem.File.officeFormat(): OfficeFormat? = officeFormat(officeExtension())

fun BrowseItem.File.officeKind(): OfficeKind? = officeFormat()?.kind

/** Garante a extensão do formato escolhido no nome escrito pelo utilizador. */
fun withOfficeExtension(name: String, format: OfficeFormat): String {
    val trimmed = name.trim().trimEnd('.')
    return if (trimmed.lowercase().endsWith(".${format.extension}")) trimmed else "$trimmed.${format.extension}"
}
