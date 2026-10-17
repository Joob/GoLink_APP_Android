package co.golink.tester.domain.search

import co.golink.tester.ui.i18n.tr
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pesquisa em linguagem natural SEM IA: interpreta no telemóvel frases como
 * "PDFs grandes de março" ou "photos from last week" em filtros de metadados
 * (tipo, extensão, datas, tamanho). Ao servidor só vão os filtros e o texto que
 * sobra para o nome — nada passa por serviços de IA.
 *
 * Mesmas regras de resources/js/services/searchQueryParser.js (web) — mudar
 * uma é mudar a outra. Línguas: PT, EN, FR, ES.
 */
data class SearchFilters(
    val kind: String? = null,
    val types: List<String> = emptyList(),
    val extensions: List<String> = emptyList(),
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val sizeMin: Long? = null,
    val sizeMax: Long? = null,
) {
    val isEmpty: Boolean
        get() = kind == null && types.isEmpty() && extensions.isEmpty() &&
            dateFrom == null && dateTo == null && sizeMin == null && sizeMax == null
}

data class ParsedSearch(
    /** Texto que sobra para procurar no nome (sem filtros: a frase original). */
    val text: String,
    val filters: SearchFilters,
    /** Rótulos do que foi interpretado, para mostrar ao utilizador. */
    val chips: List<String>,
) {
    val hasFilters: Boolean get() = !filters.isEmpty
}

object SearchQueryParser {
    private const val MB = 1024L * 1024
    private const val GB = 1024L * MB
    private const val LARGE_BYTES = 100 * MB
    private const val SMALL_BYTES = 1 * MB

    private val DOCUMENTS = listOf("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "pages", "xls", "xlsx", "ods", "csv", "numbers", "ppt", "pptx", "odp", "key")

    private class TypeWord(val words: Set<String>, val kind: String? = null, val types: List<String> = emptyList(), val extensions: List<String> = emptyList(), val label: String)

    private val TYPE_WORDS = listOf(
        TypeWord(setOf("pasta", "pastas", "folder", "folders", "dossier", "dossiers", "carpeta", "carpetas"), kind = "folder", label = "Pastas"),
        TypeWord(setOf("imagem", "imagens", "foto", "fotos", "fotografia", "fotografias", "image", "images", "photo", "photos", "picture", "pictures", "imagen", "imagenes"), types = listOf("image"), label = "Imagens"),
        TypeWord(setOf("video", "videos", "filme", "filmes", "movie", "movies", "film", "films", "pelicula", "peliculas"), types = listOf("video"), label = "Vídeos"),
        TypeWord(setOf("audio", "audios", "musica", "musicas", "music", "musique", "musiques", "som", "sons", "song", "songs", "chanson", "chansons", "cancion", "canciones"), types = listOf("audio"), label = "Áudio"),
        TypeWord(setOf("documento", "documentos", "document", "documents", "doc", "docs"), extensions = DOCUMENTS, label = "Documentos"),
        TypeWord(setOf("pdf", "pdfs"), extensions = listOf("pdf"), label = "PDF"),
        TypeWord(setOf("word"), extensions = listOf("doc", "docx", "odt"), label = "Word"),
        TypeWord(setOf("excel", "planilha", "planilhas", "spreadsheet", "spreadsheets", "tableur", "tableurs"), extensions = listOf("xls", "xlsx", "ods", "csv"), label = "Folhas de cálculo"),
        TypeWord(setOf("powerpoint", "apresentacao", "apresentacoes", "presentation", "presentations", "presentacion", "presentaciones", "diapositivos", "slides"), extensions = listOf("ppt", "pptx", "odp", "key"), label = "Apresentações"),
        TypeWord(setOf("zip", "zips", "comprimido", "comprimidos", "archive", "archives", "compressed"), extensions = listOf("zip", "rar", "7z", "tar", "gz"), label = "Arquivos comprimidos"),
    )

    private val KNOWN_EXTENSIONS = DOCUMENTS.toSet() + setOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic", "svg", "bmp", "tiff", "mp4", "mov", "avi", "mkv", "webm",
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "zip", "rar", "7z", "tar", "gz", "apk", "exe", "dmg", "iso", "json", "xml", "html",
    )
    private val AMBIGUOUS_EXTENSIONS = setOf("key", "pages", "numbers", "md")

    private val LARGE_WORDS = setOf("grande", "grandes", "large", "big", "enorme", "enormes", "huge", "gros", "grosse", "grosses", "grand", "grands")
    private val SMALL_WORDS = setOf("pequeno", "pequenos", "pequena", "pequenas", "small", "tiny", "petit", "petits", "petite", "petites")
    private val MORE_WORDS = setOf("maior", "maiores", "mais", "acima", "more", "larger", "bigger", "over", "above", "plus", "superieur", "superieurs", "mas", "mayor", "mayores", "encima", ">")
    private val LESS_WORDS = setOf("menor", "menores", "menos", "abaixo", "less", "smaller", "under", "below", "moins", "inferieur", "inferieurs", "debajo", "<")

    /** Só nomes completos: "mar" ou "mai" são palavras comuns. */
    private val MONTHS = mapOf(
        "janeiro" to 1, "fevereiro" to 2, "marco" to 3, "abril" to 4, "maio" to 5, "junho" to 6, "julho" to 7, "agosto" to 8, "setembro" to 9, "outubro" to 10, "novembro" to 11, "dezembro" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6, "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "janvier" to 1, "fevrier" to 2, "mars" to 3, "avril" to 4, "mai" to 5, "juin" to 6, "juillet" to 7, "aout" to 8, "septembre" to 9, "octobre" to 10, "novembre" to 11, "decembre" to 12,
        "enero" to 1, "febrero" to 2, "marzo" to 3, "mayo" to 5, "junio" to 6, "julio" to 7, "septiembre" to 9, "setiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12,
    )

    private val RELATIVE = listOf(
        listOf("semana passada", "last week", "semaine derniere", "semaine passee", "semana pasada") to "last_week",
        listOf("esta semana", "this week", "cette semaine") to "this_week",
        listOf("mes passado", "last month", "mois dernier", "mes pasado") to "last_month",
        listOf("este mes", "this month", "ce mois-ci", "ce mois") to "this_month",
        listOf("ano passado", "last year", "annee derniere", "ano pasado") to "last_year",
        listOf("este ano", "this year", "cette annee") to "this_year",
        listOf("aujourd'hui", "aujourdhui", "hoje", "today", "hoy") to "today",
        listOf("ontem", "yesterday", "hier", "ayer") to "yesterday",
    )

    private val RELATIVE_LABELS = mapOf(
        "today" to "Hoje", "yesterday" to "Ontem", "this_week" to "Esta semana", "last_week" to "Semana passada",
        "this_month" to "Este mês", "last_month" to "Mês passado", "this_year" to "Este ano", "last_year" to "Ano passado",
    )

    private val STOP_WORDS = setOf(
        "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", "o", "a", "os", "as", "meu", "meus", "minha", "minhas", "que", "com", "desde", "entre", "e",
        "the", "of", "in", "on", "from", "my", "than", "with", "since", "and", "all",
        "le", "la", "les", "des", "du", "mes", "en", "dans", "avec", "depuis", "et",
        "el", "los", "las", "del", "mis", "con", "y",
    )
    private val GENERIC_WORDS = setOf("ficheiro", "ficheiros", "arquivo", "arquivos", "file", "files", "fichier", "fichiers", "archivo", "archivos")
    private val RANGE_PREPOSITIONS = setOf("em", "in", "en", "durante", "during", "pendant", "desde", "since", "depuis")

    private val LAST_DAYS = Regex(" (?:ultimos|ultimas|last|derniers|dernieres|past) (\\d{1,3}) (?:dias|days|jours) ")
    private val SIZE = Regex(" (\\S+)(?: (?:que|than|de|a))? (\\d+(?:[.,]\\d+)?) ?(kb|mb|gb|ko|mo|go)\\b ?")
    private val YEAR = Regex("^(19|20)\\d{2}$")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun clean(token: String) = token.removePrefix(".").trimEnd('?', '!', ',', ';', ':')

    fun parse(input: String, today: LocalDate = LocalDate.now()): ParsedSearch {
        var text = " " + normalize(input).replace(Regex("\\s+"), " ").trim() + " "
        val original = input.trim().split(Regex("\\s+"))
        val chips = mutableListOf<String>()
        var kind: String? = null
        val types = linkedSetOf<String>()
        val extensions = linkedSetOf<String>()
        var dateFrom: LocalDate? = null
        var dateTo: LocalDate? = null
        var sizeMin: Long? = null
        var sizeMax: Long? = null

        // 1) Datas relativas
        loop@ for ((phrases, range) in RELATIVE) {
            for (phrase in phrases) {
                if (dateFrom == null && text.contains(" $phrase ")) {
                    val (from, to) = relativeRange(range, today)
                    dateFrom = from; dateTo = to
                    chips += RELATIVE_LABELS.getValue(range).tr()
                    text = text.replaceFirst(" $phrase ", " ")
                    break@loop
                }
            }
        }

        // 2) "últimos 7 dias"
        if (dateFrom == null) {
            LAST_DAYS.find(text)?.let { m ->
                val n = m.groupValues[1].toLong()
                dateFrom = today.minusDays(n - 1); dateTo = today
                chips += "Últimos %d dias".tr().format(n)
                text = text.replaceFirst(m.value, " ")
            }
        }

        // 3) Tamanho explícito: "maior que 10 mb", "< 500 kb"
        SIZE.find(text)?.let { m ->
            val word = m.groupValues[1]
            if (word in MORE_WORDS || word in LESS_WORDS) {
                val bytes = sizeToBytes(m.groupValues[2], m.groupValues[3])
                if (bytes != null) {
                    if (word in MORE_WORDS) { sizeMin = bytes; chips += "> " + formatSize(bytes) }
                    else { sizeMax = bytes; chips += "< " + formatSize(bytes) }
                    text = text.replaceFirst(m.value, " ")
                }
            }
        }

        // 4) Palavra a palavra
        val tokens = text.trim().split(" ").filter { it.isNotEmpty() }
        val rest = mutableListOf<String>()
        var month: Int? = null
        var year: Int? = null
        var yearFromPreposition = false

        tokens.forEachIndexed { i, token ->
            val word = clean(token)
            val type = TYPE_WORDS.firstOrNull { word in it.words }
            when {
                type != null -> {
                    type.kind?.let { kind = it }
                    types += type.types
                    extensions += type.extensions
                    chips += type.label.tr()
                }
                word in KNOWN_EXTENSIONS && (token.startsWith(".") || word !in AMBIGUOUS_EXTENSIONS) -> {
                    extensions += word
                    chips += ".$word"
                }
                word in LARGE_WORDS && sizeMin == null -> { sizeMin = LARGE_BYTES; chips += "Grandes (> 100 MB)".tr() }
                word in SMALL_WORDS && sizeMax == null -> { sizeMax = SMALL_BYTES; chips += "Pequenos (< 1 MB)".tr() }
                word in MONTHS && dateFrom == null -> month = MONTHS.getValue(word)
                YEAR.matches(word) && dateFrom == null -> {
                    year = word.toInt()
                    yearFromPreposition = tokens.getOrNull(i - 1) in RANGE_PREPOSITIONS || month != null
                }
                else -> rest += word
            }
        }

        val otherFilters = kind != null || types.isNotEmpty() || extensions.isNotEmpty() ||
            dateFrom != null || sizeMin != null || sizeMax != null
        val m = month
        val y = year
        if (m != null) {
            val resolvedYear = y ?: if (m > today.monthValue) today.year - 1 else today.year
            val first = LocalDate.of(resolvedYear, m, 1)
            dateFrom = first; dateTo = first.withDayOfMonth(first.lengthOfMonth())
            chips += first.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " $resolvedYear"
        } else if (y != null && (yearFromPreposition || otherFilters)) {
            dateFrom = LocalDate.of(y, 1, 1); dateTo = LocalDate.of(y, 12, 31)
            chips += y.toString()
        } else if (y != null) {
            rest += y.toString()
        }

        val filters = SearchFilters(
            kind = kind,
            types = types.toList(),
            extensions = extensions.toList(),
            dateFrom = dateFrom?.toString(),
            dateTo = dateTo?.toString(),
            sizeMin = sizeMin,
            sizeMax = sizeMax,
        )

        // Sem filtros reconhecidos: a pesquisa fica exatamente como antes.
        if (filters.isEmpty) return ParsedSearch(input.trim(), filters, emptyList())

        val words = rest.filter { it !in STOP_WORDS && it !in GENERIC_WORDS }.toSet()
        val kept = original.filter { clean(normalize(it)) in words }
        return ParsedSearch(kept.joinToString(" "), filters, chips)
    }

    private fun relativeRange(range: String, today: LocalDate): Pair<LocalDate, LocalDate> {
        val weekday = today.dayOfWeek.value - 1 // segunda = 0
        return when (range) {
            "today" -> today to today
            "yesterday" -> today.minusDays(1) to today.minusDays(1)
            "this_week" -> today.minusDays(weekday.toLong()) to today
            "last_week" -> today.minusDays(weekday + 7L) to today.minusDays(weekday + 1L)
            "this_month" -> today.withDayOfMonth(1) to today
            "last_month" -> today.minusMonths(1).withDayOfMonth(1) to today.withDayOfMonth(1).minusDays(1)
            "this_year" -> today.withDayOfYear(1) to today
            else -> LocalDate.of(today.year - 1, 1, 1) to LocalDate.of(today.year - 1, 12, 31)
        }
    }

    private fun sizeToBytes(value: String, unit: String): Long? {
        val n = value.replace(',', '.').toDoubleOrNull() ?: return null
        return when (unit.first()) {
            'g' -> (n * GB).toLong()
            'k' -> (n * 1024).toLong()
            else -> (n * MB).toLong()
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= GB -> String.format(Locale.ROOT, "%.1f GB", bytes.toDouble() / GB).replace(".0 ", " ")
        bytes >= MB -> String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / MB).replace(".0 ", " ")
        else -> "${bytes / 1024} KB"
    }
}
