package co.golink.tester.domain.calendar

import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Evento Rápido: interpreta no aparelho frases como "Jantar amanhã às 20h
 * @Porto #família", "Geburtstag Anna 2. Februar 2027" ou "2月2日 誕生日".
 * Sem IA e sem rede — o evento grava-se cifrado pelo caminho normal. Os
 * nomes (meses, dias, "amanhã", AM/PM, sufixos de hora, unidades) vêm dos
 * dados CLDR do sistema através de [QuickLocaleSource].
 *
 * Espelho de resources/js/services/quickEventParser.js — mudar um é mudar o outro.
 */

/** Línguas CLDR consideradas (igual a QUICK_LOCALES na web). */
val QUICK_LOCALES = listOf(
    "af", "am", "ar", "az", "be", "bg", "bn", "bs", "ca", "cs", "cy", "da", "de", "el", "en", "es", "et", "eu", "fa", "fi", "fil", "fr",
    "ga", "gl", "gu", "he", "hi", "hr", "hu", "hy", "id", "is", "it", "ja", "ka", "kk", "km", "kn", "ko", "ky", "lo", "lt", "lv", "mk",
    "ml", "mn", "mr", "ms", "my", "nb", "ne", "nl", "pa", "pl", "pt", "ro", "ru", "si", "sk", "sl", "sq", "sr", "sv", "sw", "ta", "te",
    "th", "tr", "uk", "ur", "uz", "vi", "zh", "zu",
)

/** Textos tal e qual vêm do CLDR; a normalização e os filtros são do parser. */
class QuickLocaleRaw(
    /** Meses (longos/curtos, standalone e de formato) → 1..12. */
    val months: List<Pair<String, Int>> = emptyList(),
    /** Dias da semana longos → 1 (segunda)..7. */
    val weekdays: List<Pair<String, Int>> = emptyList(),
    val weekdaysShort: List<Pair<String, Int>> = emptyList(),
    /** Ontem/hoje/amanhã/depois de amanhã → -1..2. */
    val relative: List<Pair<String, Int>> = emptyList(),
    val am: List<String> = emptyList(),
    val pm: List<String> = emptyList(),
    /** Texto logo a seguir à hora ("13 Uhr", "13時"). */
    val hourSuffix: List<String> = emptyList(),
    /** Texto logo a seguir ao dia / ao ano numa data ("2日", "2027年", "2."). */
    val dayAffix: List<String> = emptyList(),
    val yearAffix: List<String> = emptyList(),
    val minuteUnits: List<String> = emptyList(),
    val hourUnits: List<String> = emptyList(),
    /** Para os alertas ("2 dias antes", "1 semana antes"). */
    val dayUnits: List<String> = emptyList(),
    val weekUnits: List<String> = emptyList(),
)

interface QuickLocaleSource {
    fun load(locale: String): QuickLocaleRaw
}

data class QuickCalendar(val id: String, val name: String)

/** Repetição percebida na frase: daily/weekly/monthly/yearly, a cada [interval], dias ISO (1 = segunda). */
data class QuickRepeat(val repeat: String, val interval: Int = 1, val days: List<Int> = emptyList())

data class QuickParse(
    val ok: Boolean = false,
    /** empty · no_date · no_day · invalid_date · no_title */
    val error: String? = null,
    val title: String = "",
    val location: String = "",
    val calendarId: String? = null,
    val unknownTag: String? = null,
    val allDay: Boolean = true,
    val startDay: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endDay: LocalDate? = null,
    val endTime: LocalTime? = null,
    val relative: Int? = null,
    val repeat: QuickRepeat? = null,
    /** Fim da repetição ("até 30 de junho" ou a 2.ª ponta de um intervalo). */
    val repeatUntil: LocalDate? = null,
    /** Minutos antes; null = não se disse nada (fica o alerta por omissão). */
    val alerts: List<Int>? = null,
    val guests: List<String> = emptyList(),
    val notes: String = "",
)

class QuickEventParser(private val source: QuickLocaleSource) {

    private class Data(
        val months: List<Pair<String, Int>>,
        val weekdays: List<Pair<String, Int>>,
        val rel: List<Pair<String, Int>>,
        val am: List<String>,
        val pm: List<String>,
        val hourSuffix: List<String>,
        val minuteUnits: List<String>,
        val hourUnits: List<String>,
        val dayUnits: List<String>,
        val weekUnits: List<String>,
        val dayAffix: List<String>,
        val yearAffix: List<String>,
    )

    private class Index(
        val words: List<Pair<String, Entry>>,
        val am: Set<String>,
        val pm: Set<String>,
        val hourSuffix: Set<String>,
        val minuteUnits: Set<String>,
        val hourUnits: Set<String>,
        val dayUnits: Set<String>,
        val weekUnits: Set<String>,
        val dayAffix: Set<String>,
        val yearAffix: Set<String>,
    ) {
        val lookup: Map<String, Entry> = words.toMap()
        // Só dependem do índice: compiladas uma vez, não a cada tecla.
        private val suffix = alt(hourSuffix)
        private val periods = alt(am + pm)
        private val tail = """(?:\s?($suffix))?(?:\s?($periods))?(?![\p{L}\d])"""
        private val dayAff = alt(dayAffix).ifEmpty { "(?!)" }
        private val yearAff = alt(yearAffix).ifEmpty { "(?!)" }
        val range = Regex("""(?<![\d:.])(\d{1,2})\s?[-–—~]\s?(\d{1,2})\s?(?:($suffix)|($periods))(?![\p{L}\d])""")
        // "10.30." no fim da frase continua a ser hora (só "10.30.5" não é).
        val clock = Regex("""(?:($periods)\s?)?(?<![\d:.])(\d{1,2})(?:[:h]|\.(?=\d{2}(?!\.\d)))(\d{2})(?!\d)$tail""")
        val hour = Regex("""(?:($periods)\s?)?(?<![\d:.])(\d{1,2})(?:\s?($suffix)(?:\s?($periods))?|\s?($periods))(?![\p{L}\d])""")
        val hourUnitRe = unit(hourUnits)
        val minuteUnitRe = unit(minuteUnits)
        val dayBefore = Regex("""(?<![\d:])(\d{1,2})(?:$dayAff)?""")
        val dayAfter = Regex("""^([\s\S]{0,8}?)(?<![\d:])(\d{1,2})(?![\d:])(?:$dayAff)?""")
        val yearAfter = Regex("""^([\s\S]{0,6}?)(?<!\d)((?:19|20|21)\d{2})(?!\d)(?:$yearAff)?""")
        val yearBefore = Regex("""(?<!\d)((?:19|20|21)\d{2})(?:$yearAff)?\s?$""")
        // "10 min antes", "lembrar 1 h antes", "remind me 2 days before"
        val alert = Regex(
            """(?:(?:$ALERT_PREFIX)\s+(?:(?:de|com|me|mich|moi|mi)\s+)?)?(?<![\d:.])(\d{1,3})\s?""" +
                """(?:(${alt(minuteUnits + listOf("m", "min"))})|(${alt(hourUnits + "h")})|(${alt(dayUnits).ifEmpty { "(?!)" }})|(${alt(weekUnits).ifEmpty { "(?!)" }}))""" +
                """\s+(?:$ALERT_BEFORE)(?![\p{L}\d])""",
        )
        // "1 a 7 de fevereiro": a ponta inicial antes do dia que pega no mês.
        val rangeBefore = Regex("""(?<![\d:.])(\d{1,2})(?:$dayAff)?(?:st|nd|rd|th|er|º|ª|\.)?(?:\s*[-–—~]\s*|\s+(?:$RANGE_ALT)\s+)$DAY_PREFIX${'$'}""")
        // "February 1-7": a ponta final depois do dia.
        val rangeAfter = Regex("""^(?:\s*[-–—~]\s*|\s+(?:$RANGE_ALT)\s+)$DAY_PREFIX(\d{1,2})(?![\d:])(?:$dayAff)?(?:st|nd|rd|th)?(?!\p{L})""")
        private val pmBare = pm.map(::bare).toSet()
        fun isPm(p: String) = bare(p) in pmBare

        private fun unit(list: Set<String>) =
            if (list.isEmpty()) null else Regex("""(?<![\d:.])(\d{1,3})\s?(${alt(list)})(?![\p{L}\d])""")
    }

    private data class Entry(val type: String, val v: Int, val prio: Boolean)
    private data class Found(val a: Int, val b: Int, val type: String, val v: Int, val prio: Boolean)
    private data class Dur(val pos: Int, val n: Int, val mult: Int)
    private data class Time(val pos: Int, val h: Int, val m: Int)
    private data class Ymd(val y: Int?, val m: Int, val d: Int, val a: Int = 0, val b: Int = 0)
    private data class Best(val a: Int, val b: Int, val d: Int, val gap: Int)

    private val localeCache = HashMap<String, Data>()
    private val indexCache = HashMap<String, Index>()

    @Synchronized
    private fun data(loc: String): Data = localeCache.getOrPut(loc) {
        val raw = runCatching { source.load(loc) }.getOrElse { QuickLocaleRaw() }
        val weekdays = mutableListOf<Pair<String, Int>>()
        raw.weekdays.forEach { (s, v) ->
            val w = norm(s)
            weekdays += w to v
            // "sexta-feira" → também "sexta".
            val head = w.split('-', ' ').first()
            if (head != w && head.length >= 4) weekdays += head to v
        }
        raw.weekdaysShort.forEach { (s, v) -> weekdays += norm(s) to v }
        Data(
            months = raw.months.map { norm(it.first) to it.second }.filterNot { it.first.all(Char::isDigit) },
            weekdays = weekdays,
            rel = raw.relative.map { norm(it.first) to it.second }.filterNot { r -> r.first.any { it in '0'..'9' } },
            am = raw.am.map(::norm).filter { it.isNotEmpty() },
            pm = raw.pm.map(::norm).filter { it.isNotEmpty() },
            hourSuffix = raw.hourSuffix.map(::norm).filter { s -> s.isNotEmpty() && s.none { it in '0'..'9' } }.distinct(),
            minuteUnits = raw.minuteUnits.map(::norm).filter { it.length > 1 }.distinct(),
            hourUnits = raw.hourUnits.map(::norm).filter { it.length > 1 }.distinct(),
            dayUnits = raw.dayUnits.map(::norm).filter { it.length > 1 }.distinct(),
            weekUnits = raw.weekUnits.map(::norm).filter { it.length > 1 }.distinct(),
            dayAffix = raw.dayAffix.mapNotNull(::affix).distinct(),
            yearAffix = raw.yearAffix.mapNotNull(::affix).distinct(),
        )
    }

    /** Sufixo junto ao número ("日", "년", "."): só 1–2 caracteres de escrita sem espaços ou ponto. */
    private fun affix(raw: String): String? {
        val w = raw.trim { it.isWhitespace() || Character.isSpaceChar(it) }
        return w.takeIf { it.isNotEmpty() && it.length <= 2 && (it == "." || NOSPACE.containsMatchIn(it)) }
    }

    /** Constrói já os dicionários (chamar fora da thread principal). */
    fun warmUp(priority: List<String>) {
        index(priorityLocales(priority))
    }

    /**
     * Línguas prioritárias: vale tudo (abreviaturas incluídas). Nas restantes só
     * nomes distintivos (≥4 letras, escrita sem espaços ou com algarismos).
     */
    @Synchronized
    private fun index(priority: List<String>): Index = indexCache.getOrPut(priority.joinToString(",")) {
        val order = priority + QUICK_LOCALES.filterNot { it in priority }
        val words = LinkedHashMap<String, Entry>()
        val am = HashSet<String>()
        val pm = HashSet<String>()
        val hourSuffix = hashSetOf("h", "点", "點")
        val minuteUnits = HashSet<String>()
        val hourUnits = HashSet<String>()
        val dayUnits = HashSet<String>()
        val weekUnits = HashSet<String>()
        val dayAffix = HashSet<String>()
        val yearAffix = HashSet<String>()
        fun distinct(w: String) = w.length >= 4 || (NOSPACE.containsMatchIn(w) && w.length >= 2) || w.any { it in '0'..'9' }
        for (loc in order) {
            val prio = loc in priority
            val d = data(loc)
            for ((type, list) in listOf("month" to d.months, "weekday" to d.weekdays, "rel" to d.rel)) {
                for ((w, v) in list) {
                    if (w.length < 2 || w in words) continue
                    if (!prio && !distinct(w)) continue
                    words[w] = Entry(type, v, prio)
                }
            }
            for (w in d.am + d.pm) if (prio || loc == "en" || NOSPACE.containsMatchIn(w)) (if (w in d.am) am else pm) += w
            val ok = { w: String -> prio || w.length >= 2 || NOSPACE.containsMatchIn(w) }
            d.hourSuffix.filter(ok).forEach { hourSuffix += it }
            d.minuteUnits.filter(ok).forEach { minuteUnits += it }
            d.hourUnits.filter(ok).forEach { hourUnits += it }
            d.dayUnits.filter(ok).forEach { dayUnits += it }
            d.weekUnits.filter(ok).forEach { weekUnits += it }
            dayAffix += d.dayAffix
            yearAffix += d.yearAffix
        }
        // "h" é sempre hora, nunca duração ("13h"); um sufixo de hora também não é unidade ("13 Uhr").
        hourUnits.removeAll(hourSuffix)
        hourUnits.remove("h")
        minuteUnits.remove("m")
        // Sufixos de ordinal ("1st", "2nd") não são unidades ("st" = hora noutras línguas).
        for (set in listOf(minuteUnits, hourUnits, dayUnits, weekUnits)) set.removeAll(ORDINAL_SUFFIXES)
        Index(
            words = words.entries.map { it.key to it.value }.sortedByDescending { it.first.length },
            am = am, pm = pm, hourSuffix = hourSuffix, minuteUnits = minuteUnits, hourUnits = hourUnits,
            dayUnits = dayUnits, weekUnits = weekUnits, dayAffix = dayAffix, yearAffix = yearAffix,
        )
    }

    fun parse(
        text: String,
        now: LocalDateTime = LocalDateTime.now(),
        locales: List<String> = emptyList(),
        /** Ordem das datas numéricas na língua do aparelho (2/3 = 2 de março?). */
        dayFirst: Boolean = true,
        calendars: List<QuickCalendar> = emptyList(),
        defaultCalendarId: String? = null,
        /** O próprio email não entra nos convidados. */
        selfEmail: String? = null,
    ): QuickParse {
        // Notas: tudo depois de "//". Emails → convidados, tirados antes do
        // "@local" para o "@" do email não passar por local.
        var raw = text
        var notes = ""
        NOTES_CUT.find(raw)?.let { m ->
            notes = raw.substring(m.range.first).trim().removePrefix("//").trim()
            raw = raw.substring(0, m.range.first)
        }
        val guests = mutableListOf<String>()
        val self = selfEmail.orEmpty().trim().lowercase()
        raw = EMAIL.replace(raw) { m ->
            val e = m.value.lowercase()
            if (e != self && e !in guests) guests += e
            MARK.toString()
        }
        fun extra(r: QuickParse) = r.copy(notes = notes, guests = guests.toList())
        // Com data antes do @, o local fica inteiro ("@Avenida 5 de Outubro");
        // sem data assim, o local acaba no 1.º token de data ("@Cais amanhã 13h").
        if ('@' in raw) {
            val masked = parseCore(raw, now, locales, dayFirst, calendars, defaultCalendarId, maskPlace = true)
            if (masked.ok || masked.error !in setOf("no_date", "no_day")) return extra(masked)
        }
        return extra(parseCore(raw, now, locales, dayFirst, calendars, defaultCalendarId, maskPlace = false))
    }

    private fun parseCore(
        text: String,
        now: LocalDateTime,
        locales: List<String>,
        dayFirst: Boolean,
        calendars: List<QuickCalendar>,
        defaultCalendarId: String?,
        maskPlace: Boolean,
    ): QuickParse {
        val base = QuickParse(calendarId = defaultCalendarId)
        if (text.isBlank()) return base.copy(error = "empty")

        val index = index(priorityLocales(locales))
        val (s, map) = normText(text)
        // Hashtags não contam para datas (#março2027 é um calendário).
        var w = HASHTAG.replace(s) { " ".repeat(it.value.length) }
        if (maskPlace) w = PLACE_MASK.replace(w) { " ".repeat(it.value.length) }
        val used = BooleanArray(w.length)
        val spans = mutableListOf<IntArray>()
        fun free(a: Int, b: Int) = (a until b).none { used[it] }
        fun take(a: Int, b: Int) {
            for (i in a until b) used[i] = true
            spans += intArrayOf(a, b)
        }
        fun isBound(i: Int) = i < 0 || i >= w.length || !LETTER.matches(w[i].toString())
        fun okEdges(a: Int, b: Int, word: String): Boolean {
            val first = word.first().toString()
            val lastCh = word.last().toString()
            val left = if (NOSPACE.containsMatchIn(first) || first[0] in '0'..'9') (a == 0 || w[a - 1] !in '0'..'9') else isBound(a - 1)
            val right = if (NOSPACE.containsMatchIn(lastCh)) true else isBound(b)
            return left && right
        }
        fun each(re: Regex, fn: (MatchResult) -> Unit) {
            for (m in re.findAll(w)) if (free(m.range.first, m.range.last + 1)) fn(m)
        }
        fun g(m: MatchResult, i: Int): String? = m.groups[i]?.value?.takeIf { it.isNotEmpty() }

        // 0) Alertas ("10 min antes", "lembrar 1 h antes") — antes das horas, senão "1 h" era 01:00.
        val alerts = mutableListOf<Int>()
        each(index.alert) { m ->
            // "20h antes do cinema" é a hora 20:00; só "1 h antes" (com espaço) ou "lembrar 1h antes" é alerta.
            if (g(m, 3) != null && COMPACT_H.containsMatchIn(m.value)) return@each
            val mult = when {
                g(m, 2) != null -> 1
                g(m, 3) != null -> 60
                g(m, 4) != null -> 1440
                else -> 10080
            }
            val minutes = m.groupValues[1].toInt() * mult
            if (minutes > MAX_ALERT_MINUTES || alerts.size >= 5) return@each
            if (minutes !in alerts) alerts += minutes
            take(m.range.first, m.range.last + 1)
        }

        // 0b) Repetição: "todas as semanas", "a cada 2 meses", "todas as segundas e quartas", "dias úteis"
        var repeat: QuickRepeat? = null
        each(REPEAT_RE) { m ->
            if (repeat != null) return@each
            val kind = REPEAT_KIND[m.groupValues[1].replace(WS, " ")] ?: return@each
            repeat = if (kind == "weekdays") QuickRepeat("weekly", 1, listOf(1, 2, 3, 4, 5)) else QuickRepeat(kind)
            take(m.range.first, m.range.last + 1)
        }
        if (repeat == null) each(EVERY_N_RE) { m ->
            val n = m.groupValues[1].toInt()
            val kind = UNIT_KIND[m.groupValues[2]]
            if (repeat != null || n !in 1..99 || kind == null) return@each
            repeat = QuickRepeat(kind, n)
            take(m.range.first, m.range.last + 1)
        }
        if (repeat == null) each(EVERY_WD_RE) { m ->
            if (repeat != null) return@each
            val weak = m.groupValues[1].replace(WS, " ") in WEAK_EVERY
            val days = mutableListOf<Int>()
            var pos = m.range.last + 1
            var end = pos
            while (true) {
                val t = TOKEN.matchAt(w, pos) ?: break
                val v = weekdayOf(index, t.value, weak) ?: break
                if (!free(pos, pos + t.value.length)) break
                if (v !in days) days += v
                pos += t.value.length
                end = pos
                val sep = LIST_SEP.matchAt(w, pos) ?: break
                pos += sep.value.length
            }
            if (days.isEmpty()) return@each
            repeat = QuickRepeat("weekly", 1, days.sorted())
            take(m.range.first, end)
        }

        // 1) Datas numéricas: 2027-02-02 · 2/2/2027 · 2.2. · 2.2.2027 (até duas: "1/2 a 7/2")
        val dates = mutableListOf<Ymd>()
        // Entre as duas pontas de um intervalo tem de haver uma ligação ("a", "to", "–", "bis"…).
        fun rangeGap(a: Int, b: Int): Boolean {
            // As horas já tiradas não contam ("1 fev 10h a 3 fev 18h").
            val gap = String(CharArray(b - a) { i -> if (used[a + i]) ' ' else w[a + i] })
            val words = gap.split(RANGE_SPLIT).filter { it.isNotEmpty() }
            if (!RANGE_DASH.containsMatchIn(gap) && words.none { it in RANGE_WORDS }) return false
            return words.all { it in RANGE_WORDS || it in RANGE_FILLER || it in CONNECTORS }
        }
        fun addDate(dt: Ymd): Boolean {
            if (dates.isEmpty()) {
                dates += dt
                return true
            }
            val prev = dates.last()
            if (dates.size >= 2 || dt.a < prev.b || !rangeGap(prev.b, dt.a)) return false
            take(prev.b, dt.a)
            dates += dt
            return true
        }
        each(ISO_DATE) { m ->
            val end = m.range.last + 1
            if (addDate(Ymd(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.range.first, end))) take(m.range.first, end)
        }
        each(NUM_DATE) { m ->
            val sep = m.groupValues[2]
            val year = g(m, 4)
            if (sep == "." && year == null && g(m, 5) == null) return@each // "13.30" é hora
            if (sep == "-" && year == null) return@each // "13-15h" é intervalo
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[3].toInt()
            val dm = dayFirst || sep == "."
            var day = if (dm) a else b
            var mon = if (dm) b else a
            // Com ponto é sempre D.M. ("10.30." é hora, não 30 de outubro).
            if (sep != "." && mon > 12 && day <= 12) day = mon.also { mon = day }
            if (mon !in 1..12 || day !in 1..31) return@each
            val y = year?.let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            val end = m.range.last + 1
            if (addDate(Ymd(y, mon, day, m.range.first, end))) take(m.range.first, end)
        }

        // 2) Horas: intervalo "13-15h", "13:30", "13h30", "13.30", "1pm", "午後1時", "13 Uhr"
        val times = mutableListOf<Time>()
        fun hour(h0: String, p: String?): Int {
            var h = h0.toInt()
            if (p != null) {
                val pmv = index.isPm(p)
                h = if (h == 12) (if (pmv) 12 else 0) else if (pmv) h + 12 else h
            }
            return h
        }
        fun addTime(pos: Int, h: Int, mi: Int): Boolean {
            if (h > 23 || mi > 59) return false
            times += Time(pos, h, mi)
            return true
        }
        each(index.range) { m ->
            val p = g(m, 4)
            val h1 = hour(m.groupValues[1], p)
            val h2 = hour(m.groupValues[2], p)
            if (h1 > 23 || h2 > 23) return@each
            addTime(m.range.first, h1, 0)
            addTime(m.range.first + 1, h2, 0)
            take(m.range.first, m.range.last + 1)
        }
        each(index.clock) { m ->
            if (addTime(m.range.first, hour(m.groupValues[2], g(m, 1) ?: g(m, 5)), m.groupValues[3].toInt())) take(m.range.first, m.range.last + 1)
        }
        each(index.hour) { m ->
            if (g(m, 1) == null && g(m, 3) == null && g(m, 5) == null) return@each
            if (addTime(m.range.first, hour(m.groupValues[2], g(m, 1) ?: g(m, 4) ?: g(m, 5)), 0)) take(m.range.first, m.range.last + 1)
        }

        // 3) Duração: "30 min", "2 horas", "1 Stunde" (somam-se: "2 horas e 30 min")
        val durs = mutableListOf<Dur>()
        fun units(re: Regex?, mult: Int) {
            if (re == null) return
            each(re) { m ->
                durs += Dur(m.range.first, m.groupValues[1].toInt(), mult)
                take(m.range.first, m.range.last + 1)
            }
        }
        units(index.hourUnitRe, 60)
        units(index.minuteUnitRe, 1)
        durs.sortBy { it.pos }
        // Sem outra hora, "às 10 horas" / "a las 21 horas" é a hora de início.
        if (times.isEmpty()) {
            durs.firstOrNull { it.mult == 60 && it.n <= 23 }?.let { asClock ->
                addTime(asClock.pos, asClock.n, 0)
                durs.remove(asClock)
            }
        }
        times.sortBy { it.pos }
        val duration: Int? = if (durs.isEmpty()) null else durs.sumOf { it.n * it.mult }

        // 4) Palavras: meses, dias da semana, hoje/amanhã (a mais comprida primeiro)
        val found = mutableListOf<Found>()
        for ((word, entry) in index.words) {
            var from = 0
            while (true) {
                val at = w.indexOf(word, from)
                if (at == -1) break
                val end = at + word.length
                if (free(at, end) && okEdges(at, end, word)) {
                    found += Found(at, end, entry.type, entry.v, entry.prio)
                    for (i in at until end) used[i] = true // reservado; spans só no fim
                }
                from = at + 1
            }
        }
        for (f in found) for (i in f.a until f.b) used[i] = false
        found.sortBy { it.a }

        // Mês + dia (+ ano) → data explícita. Meses das línguas da app/aparelho
        // primeiro; os das outras ("marta", "maja" — também nomes próprios) só
        // se não houver mais nada que dê o dia. Até duas datas (intervalo).
        var monthError = false
        fun tryMonth(f: Found): Boolean {
            if (!free(f.a, f.b)) return false
            var best: Best? = null
            // Antes: "2 de fevereiro", "2nd of February", "le 2 février", "2. Februar"
            val before = w.substring(maxOf(0, f.a - 14), f.a)
            for (m in index.dayBefore.findAll(before)) {
                val a = f.a - before.length + m.range.first
                val b = a + m.value.length
                val gap = before.substring(m.range.last + 1)
                val d = m.groupValues[1].toInt()
                if (d in 1..31 && GAP.matches(gap) && free(a, b)) best = Best(a, b + ordinal(gap), d, gap.length)
            }
            // Depois: "February 2nd", "2月2日", "febrero 2"
            val after = w.substring(f.b, minOf(w.length, f.b + 14))
            index.dayAfter.find(after)?.let { ma ->
                val d = ma.groupValues[2].toInt()
                val gap = ma.groupValues[1]
                if (d in 1..31 && GAP.matches(gap)) {
                    val a = f.b + gap.length
                    val b = f.b + ma.value.length
                    val isTime = IS_TIME.containsMatchIn(w.substring(b))
                    val cur = best
                    if (!isTime && free(a, b) && (cur == null || gap.length < cur.gap)) best = Best(a, b + ordinal(w.substring(b)), d, gap.length)
                }
            }
            val pick = best ?: return false
            var a = minOf(f.a, pick.a)
            var b = maxOf(f.b, pick.b)
            // Mês partilhado: "1 a 7 de fevereiro", "do dia 1 ao dia 7 de fevereiro", "February 1–7".
            var days = listOf(pick.d)
            if (pick.a < f.a) {
                index.rangeBefore.find(w.substring(maxOf(0, pick.a - 28), pick.a))?.let { pre ->
                    val d0 = pre.groupValues[1].toInt()
                    if (d0 in 1..pick.d && free(pick.a - pre.value.length, pick.a)) {
                        days = listOf(d0, pick.d)
                        a = pick.a - pre.value.length
                    }
                }
            } else {
                index.rangeAfter.find(w.substring(b, minOf(w.length, b + 28)))?.let { post ->
                    val d1 = post.groupValues[1].toInt()
                    if (d1 in pick.d..31 && free(b, b + post.value.length)) {
                        days = listOf(pick.d, d1)
                        b += post.value.length
                    }
                }
            }
            var y: Int? = null
            var ySpan: IntArray? = null
            val yAfter = index.yearAfter.find(w.substring(b, minOf(w.length, b + 12)))
            if (yAfter != null && GAP.matches(yAfter.groupValues[1]) && free(b + yAfter.groupValues[1].length, b + yAfter.value.length)) {
                y = yAfter.groupValues[2].toInt()
                ySpan = intArrayOf(b + yAfter.groupValues[1].length, b + yAfter.value.length)
            } else {
                index.yearBefore.find(w.substring(maxOf(0, a - 8), a))?.let { yb ->
                    if (free(a - yb.value.length, a)) {
                        y = yb.groupValues[1].toInt()
                        ySpan = intArrayOf(a - yb.value.length, a)
                    }
                }
            }
            if (dates.size + days.size > 2) return false
            val ys = ySpan
            val ea = if (ys != null) minOf(a, ys[0]) else a
            val eb = if (ys != null) maxOf(b, ys[1]) else b
            if (!addDate(Ymd(y, f.v, days[0], ea, eb))) return false
            if (days.size > 1) dates += Ymd(y, f.v, days[1], ea, eb)
            take(a, b)
            if (ys != null) take(ys[0], ys[1])
            return true
        }
        val months = found.filter { it.type == "month" }
        for (f in months.filter { it.prio }) {
            if (dates.size >= 2) break
            if (!tryMonth(f)) monthError = true
        }
        if (dates.isEmpty() && found.none { it.type != "month" }) {
            for (f in months.filterNot { it.prio }) if (tryMonth(f)) break
        }
        // "Friday, Dec 5": o dia da semana junto a uma data explícita também sai do título.
        if (dates.isNotEmpty()) found.filter { it.type == "weekday" && it.b - it.a >= 3 && free(it.a, it.b) }.forEach { take(it.a, it.b) }

        // "todas as segundas até 30 de junho": a data é o fim da repetição.
        var until: Ymd? = null
        if (repeat != null && dates.size == 1) {
            val d0 = dates[0]
            UNTIL_RE.find(w.substring(maxOf(0, d0.a - 24), d0.a))?.let { um ->
                take(d0.a - um.value.length, d0.a)
                until = dates.removeAt(0)
            }
        }

        val today = now.toLocalDate()
        fun fixed(dt: Ymd, y: Int): LocalDate? = runCatching { LocalDate.of(y, dt.m, dt.d) }.getOrNull()
        // Sem ano: a próxima vez que o dia existe e não passou (29/2 → próximo bissexto).
        fun nextFrom(dt: Ymd, from: LocalDate): LocalDate? {
            var y = from.year
            var c = fixed(dt, y)
            var n = 0
            while ((c == null || c.isBefore(from)) && n++ < 8) c = fixed(dt, ++y)
            return c
        }
        fun md(dt: Ymd) = dt.m * 100 + dt.d
        var day: LocalDate? = null
        var endDate: LocalDate? = null
        var relative: Int? = null
        if (dates.isNotEmpty()) {
            val d0 = dates[0]
            val d1 = dates.getOrNull(1)
            if (d1 != null && d0.y == null && d1.y == null) {
                // Intervalo sem ano: o primeiro que ainda não acabou ("1 a 7 de novembro" a 2/11 é este ano).
                var y = today.year
                for (n in 0 until 8) {
                    val c = fixed(d0, y)
                    val e = fixed(d1, y + if (md(d1) < md(d0)) 1 else 0)
                    if (c != null && e != null && !e.isBefore(today)) {
                        day = c
                        endDate = e
                        break
                    }
                    y++
                }
            } else {
                val y0 = d0.y ?: d1?.let { e -> e.y?.let { it - if (md(d0) > md(e)) 1 else 0 } }
                day = if (y0 == null) nextFrom(d0, today) else fixed(d0, y0)
                val s0 = day
                if (s0 != null && d1 != null) {
                    endDate = (if (d1.y != null) fixed(d1, d1.y) else nextFrom(d1, s0))?.takeIf { !it.isBefore(s0) }
                    if (endDate == null) day = null
                }
            }
            if (day == null) return base.copy(error = "invalid_date")
        } else {
            val rel = found.firstOrNull { it.type == "rel" && free(it.a, it.b) }
            val wd = found.firstOrNull { it.type == "weekday" && free(it.a, it.b) }
            val passed = times.isNotEmpty() && times[0].h * 60 + times[0].m <= now.hour * 60 + now.minute
            // "segunda 9h" dito à segunda às 10h → a próxima segunda.
            fun aheadOf(v: Int): Int {
                val ahead = (v - today.dayOfWeek.value + 7) % 7
                return if (ahead == 0 && passed) 7 else ahead
            }
            val rep = repeat
            if (rel != null) {
                take(rel.a, rel.b)
                day = today.plusDays(rel.v.toLong())
                relative = rel.v
            } else if (wd != null) {
                take(wd.a, wd.b)
                day = today.plusDays(aheadOf(wd.v).toLong())
            } else if (rep != null) {
                // "Ginásio todas as segundas 7h" / "Comprimido todos os dias 9h": começa já.
                day = if (rep.days.isNotEmpty()) today.plusDays(rep.days.minOf(::aheadOf).toLong())
                else if (!passed) today
                // "Comprimido todos os dias 9h" dito às 10h: a 1.ª ocorrência é a próxima, não uma já passada.
                else when (rep.repeat) {
                    "daily" -> today.plusDays(rep.interval.toLong())
                    "weekly" -> today.plusWeeks(rep.interval.toLong())
                    "monthly" -> today.plusMonths(rep.interval.toLong())
                    else -> today.plusYears(rep.interval.toLong())
                }
            }
        }
        var start = day ?: return base.copy(error = if (monthError) "no_day" else "no_date")

        // Repetição com intervalo ("todas as segundas de 1 de fev. a 30 de jun."): o fim é o fim da repetição.
        var untilDate: LocalDate? = null
        val u = until
        if (repeat != null && endDate != null) {
            untilDate = endDate
            endDate = null
        } else if (u != null) {
            untilDate = (if (u.y != null) fixed(u, u.y) else nextFrom(u, start))?.takeIf { !it.isBefore(start) }
                ?: return base.copy(error = "invalid_date")
        }
        // "todas as quintas a partir de 1 de fev." (uma segunda): começa na 1.ª quinta.
        val rd = repeat?.days.orEmpty()
        if (rd.isNotEmpty()) {
            while (start.dayOfWeek.value !in rd) start = start.plusDays(1)
            if (untilDate != null && untilDate.isBefore(start)) return base.copy(error = "invalid_date")
        }

        // 5) Título, local e calendário a partir do texto original (com maiúsculas e acentos).
        val sb = StringBuilder()
        var last = 0
        for (sp in spans.sortedBy { it[0] }) {
            val oa = map[sp[0]]
            val ob = map[sp[1]]
            if (oa < last) continue
            sb.append(text, last, oa).append(MARK)
            last = ob
        }
        sb.append(text.substring(last))
        var cleaned = sb.toString()

        val tags = mutableListOf<String>()
        cleaned = TAG.replace(cleaned) { tags += it.groupValues[1]; MARK.toString() }
        var location = ""
        PLACE.find(cleaned)?.let { m ->
            location = stripEdges(m.groupValues[1])
            cleaned = cleaned.replaceRange(m.range, MARK.toString())
        }
        var calendarId = defaultCalendarId
        var unknownTag: String? = null
        tags.firstOrNull()?.let { tag ->
            val pick = matchCalendar(tag, calendars)
            if (pick != null) calendarId = pick.id else unknownTag = tag
        }
        val title = cleanTitle(cleaned)
        val named = base.copy(location = location, calendarId = calendarId, unknownTag = unknownTag, relative = relative)
        if (title.isEmpty()) return named.copy(error = "no_title")

        // 6) Datas finais: sem hora → dia inteiro; sem fim → 1 h (ou a duração); intervalo → até ao último dia.
        val lastDay = endDate ?: start
        val done = named.copy(
            ok = true, title = title, repeat = repeat, repeatUntil = untilDate,
            alerts = alerts.sorted().takeIf { it.isNotEmpty() },
        )
        if (times.isEmpty()) return done.copy(startDay = start, endDay = lastDay)
        val startAt = start.atTime(times[0].h, times[0].m)
        var endAt = if (times.size > 1) lastDay.atTime(times[1].h, times[1].m) else lastDay.atTime(times[0].h, times[0].m).plusMinutes((duration ?: 60).toLong())
        if (times.size > 1 && !endAt.isAfter(startAt)) endAt = endAt.plusDays(1)
        return done.copy(
            allDay = false,
            startDay = startAt.toLocalDate(), startTime = startAt.toLocalTime(),
            endDay = endAt.toLocalDate(), endTime = endAt.toLocalTime(),
        )
    }

    /** "segundas" / "lundis" / "Mondays" / "segundas-feiras" → dia ISO; fraco ("às", "on") só com plural. */
    private fun weekdayOf(index: Index, token: String, weak: Boolean): Int? {
        val tries = listOf(token, token.replace(PLURAL_S, ""), token.removeSuffix("en"), token.removeSuffix("n"))
        for ((i, t) in tries.withIndex()) {
            // Fraco só com plural; "los lunes" já é plural (singular = plural em espanhol).
            if (weak && (i == 0 || t == token) && !(i == 0 && token.endsWith("s"))) continue
            val e = index.lookup[t]
            if (e != null && e.type == "weekday") return e.v
        }
        return null
    }

    companion object {
        private const val MARK = '\u0000'
        private val NOSPACE = Regex("[\u0E00-\u0EFF\u1000-\u109F\u1780-\u17FF\u3040-\u30FF\u3400-\u9FFF\uAC00-\uD7AF\uF900-\uFAFF]")
        private val LETTER = Regex("""[\p{L}\p{N}]""")
        private val MARKS = Regex("[\u0300-\u036F]")
        private val HASHTAG = Regex("""#[^\s#@]+""")
        private val ISO_DATE = Regex("""(?<![\d:])(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)""")
        private val NUM_DATE = Regex("""(?<![\d:.,])(\d{1,2})([/.-])(\d{1,2})(?:\2(\d{4}|\d{2})(?!\d)|(\.))?(?![\d:])""")
        private val PLACE_MASK = Regex("""@[^#@]*""")
        private val WS = Regex("""\s+""")
        private val BARE = Regex("""[.\s]""")
        private val CONN_PUNCT = Regex("""[,.;:'’]""")
        private val PUNCT_ONLY = Regex("""^[,.;:–—\-|]+$""")
        private val CAL_CLEAN = Regex("""[\s_-]""")
        private val IS_TIME = Regex("""^\s?h(?!\p{L})""")
        private val ORDINAL = Regex("""^[º°ª]|^\p{L}{1,3}(?![\p{L}\d])""")
        private val ESC = Regex("""[.*+?^${'$'}{}()|\[\]\\]""")
        private val ALT_DOT = Regex("""\\\.|\s""")
        private val TAG = Regex("""#([^\s#@\u0000]+)""")
        private val PLACE = Regex("""@([^#@\u0000]*)""")

        private fun bare(x: String) = norm(x).replace(BARE, "")
        private val GAP = Regex("""^[\s,.\-/º°ª]*(?:\p{L}{1,4}[\s,.º°ª]*){0,2}$""")
        private val EDGES = Regex("""^[\s,.;:–—\-|]+|[\s,.;:–—\-|]+$""")

        /** Palavras de ligação (sem acentos) que ficam soltas junto ao que se tirou do título. */
        private val CONNECTORS = (
            // pt
            "a as ao aos o os de do da dos das dia no na nos nas em para pelas pelos pela pelo ate e por durante entre proximo proxima deste desta neste nesta este esta " +
                // en
                "at on in of the from to for by until till next this and day " +
                // fr
                "au aux le la les du des en pour jusqu jusqua et prochain prochaine ce cette " +
                // es
                "al el la los las del en para por hasta y proximo proxima este esta " +
                // de
                "am um an im ab bis von vom den der die dem fur nachsten nachste nachster und diesen diese " +
                // it
                "alle il lo di del dal da per fino prossimo prossima " +
                // nl
                "om op van tot voor het volgende " +
                // "com ana@x.pt": o email sai, a ligação também
                "com with avec con mit met"
            ).split(' ').toSet()

        /*
         * Listas escritas à mão (o CLDR não tem "todas as semanas" nem "10 min antes"):
         * pt/en/es/fr/de/it/nl, sem acentos. Iguais na web.
         */

        /** Ligação entre as duas pontas de um intervalo ("1 a 7", "Feb 1 to 7", "1. bis 7."). */
        private const val RANGE_ALT = "a|ao|ate|to|until|till|thru|through|au|jusqu.?au|jusqu.?a|al|hasta(?: el)?|bis(?: zum| zur)?|fino al|tot(?: en met)?"
        private val RANGE_WORDS = "a ao ate to until till thru through au jusqu jusqua al hasta bis fino tot".split(' ').toSet()
        /** Palavras que podem ficar entre as duas datas de um intervalo. */
        private val RANGE_FILLER = "dia day jour tag giorno dag zum zur en met el le the o".split(' ').toSet()
        private const val DAY_PREFIX = """(?:(?:o|the|le|el|il|den|dem)\s+)?(?:(?:dia|day|jour|tag|giorno|dag)\s+)?"""
        /** "... até 30 de junho" numa repetição → fim da repetição. */
        private val UNTIL_RE = Regex("""(?<![\p{L}])(?:ate|until|till|thru|bis(?: zum| zur)?|jusqu.?au|jusqu.?a|hasta(?: el)?|fino al|tot(?: en met)?)\s+(?:(?:o|a|the|le|el|al|dia|day|den|dem)\s+)*${'$'}""")
        private val RANGE_SPLIT = Regex("""[\s,'’\-–—~]+""")
        private val RANGE_DASH = Regex("[-–—~]")

        private val REPEAT_PHRASES = mapOf(
            "weekdays" to "dias uteis|todos os dias uteis|nos dias uteis|em dias uteis|every weekday|weekdays|on weekdays|dias laborables|todos los dias laborables|entre semana|" +
                "jours ouvrables|tous les jours ouvrables|en semaine|werktags|an werktagen|giorni feriali|nei giorni feriali|werkdagen|op werkdagen|elke werkdag",
            "daily" to "todos os dias|diariamente|todo dia|every day|everyday|daily|each day|cada dia|todos los dias|" +
                "tous les jours|chaque jour|quotidiennement|taglich|jeden tag|ogni giorno|tutti i giorni|quotidianamente|dagelijks|elke dag|iedere dag",
            "weekly" to "todas as semanas|semanalmente|toda semana|every week|weekly|each week|cada semana|todas las semanas|" +
                "toutes les semaines|chaque semaine|hebdomadaire|wochentlich|jede woche|ogni settimana|tutte le settimane|settimanalmente|wekelijks|elke week|iedere week",
            "monthly" to "todos os meses|mensalmente|todo mes|every month|monthly|each month|cada mes|todos los meses|mensualmente|" +
                "tous les mois|chaque mois|mensuellement|monatlich|jeden monat|ogni mese|tutti i mesi|mensilmente|maandelijks|elke maand|iedere maand",
            "yearly" to "todos os anos|anualmente|todo ano|every year|yearly|annually|each year|cada ano|todos los anos|" +
                "tous les ans|chaque annee|annuellement|jahrlich|jedes jahr|ogni anno|tutti gli anni|annualmente|jaarlijks|elk jaar|ieder jaar",
        )
        private val REPEAT_KIND: Map<String, String> = LinkedHashMap<String, String>().apply {
            for ((kind, list) in REPEAT_PHRASES) for (p in list.split('|')) putIfAbsent(p, kind)
        }
        private val REPEAT_RE = Regex(
            "(?<![\\p{L}])(" + REPEAT_KIND.keys.sortedByDescending { it.length }.joinToString("|") { it.replace(" ", "\\s+") } + ")(?![\\p{L}])",
        )
        private val UNIT_KIND: Map<String, String> = LinkedHashMap<String, String>().apply {
            mapOf(
                "daily" to "dias dia days day jours jour tage tag giorni giorno dagen dag",
                "weekly" to "semanas semana weeks week semaines semaine wochen woche settimane settimana weken",
                "monthly" to "meses mes months month mois monate monat mesi mese maanden maand",
                "yearly" to "anos ano years year ans an jahre jahr anni anno jaar jaren",
            ).forEach { (kind, list) -> for (u in list.split(' ')) putIfAbsent(u, kind) }
        }
        /** "a cada 2 semanas", "every 3 months", "alle 2 Wochen". */
        private val EVERY_N_RE = Regex(
            "(?<![\\p{L}])(?:a\\s+cada|cada|every|tous\\s+les|toutes\\s+les|alle|ogni|elke|iedere|om\\s+de)\\s+(\\d{1,2})\\s+(" +
                UNIT_KIND.keys.sortedByDescending { it.length }.joinToString("|") + ")(?![\\p{L}])",
        )
        /** "todas as segundas", "every Monday", "cada lunes"; os fracos ("às", "on") só com plural ("às segundas", "on Mondays"). */
        private val EVERY_WD_RE = Regex(
            """(?<![\p{L}])(todas\s+as|todos\s+os|todos\s+los|todas\s+las|tous\s+les|toutes\s+les|tutti\s+i|tutte\s+le|todas|todos|toda|todo|every|each|cada|chaque|jeden|jede|ogni|elke|iedere|as|aos|nas|nos|on|los|las|les)\s+""",
        )
        private val WEAK_EVERY = setOf("as", "aos", "nas", "nos", "on", "los", "las", "les")
        private val TOKEN = Regex("""[\p{L}-]+""")
        private val LIST_SEP = Regex("""(?:\s*[,&]\s*|\s+(?:e|and|y|et|und|ed|en)\s+)""")
        /** "20h antes": número colado ao "h", sem "lembrar"/"remind" antes. */
        private val COMPACT_H = Regex("""^\d{1,3}h(?!\p{L})""")
        private val PLURAL_S = Regex("""s(?=-|${'$'})""")

        private const val ALERT_PREFIX =
            "lembrar|lembra|lembrete|lembra-me|lembrar-me|avisar|avisa-me|aviso|alerta|alertar|remind me|reminder|remind|alert|notify me|" +
                "rappel|rappelle-moi|rappelle moi|recordar|recordatorio|recuerdame|avisame|erinnerung|erinnere mich|erinnern|promemoria|ricorda|ricordami|avviso|herinnering|herinner me"
        private const val ALERT_BEFORE = "antes(?:\\s+(?:do|de|del)\\s+(?:inicio|comecar|empezar))?|before|beforehand|in advance|earlier|avant|plus tot|vorher|davor|im voraus|prima|in anticipo|van tevoren|ervoor|vooraf"
        private const val MAX_ALERT_MINUTES = 10080
        private val ORDINAL_SUFFIXES = setOf("st", "nd", "rd", "th")

        /** Notas: tudo depois de "//". */
        private val NOTES_CUT = Regex("""(?:^|\s)//""")
        /** Emails na frase → convidados (tirados antes do "@local"). */
        private val EMAIL = Regex("""(?<![\p{L}\p{N}._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}(?![\p{L}\p{N}])""")

        private fun normCp(cp: Int): String {
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) return " "
            if (Character.isDigit(cp)) Character.digit(cp, 10).takeIf { it >= 0 }?.let { return it.toString() }
            // NFC no fim: o NFD parte o Hangul em Jamo e "일"/"년" deixavam de bater.
            return Normalizer.normalize(MARKS.replace(Normalizer.normalize(String(Character.toChars(cp)).lowercase(), Normalizer.Form.NFD), ""), Normalizer.Form.NFC)
        }

        /** Texto normalizado + mapa índice normalizado → índice original. */
        fun normText(text: String): Pair<String, IntArray> {
            val sb = StringBuilder()
            val map = ArrayList<Int>()
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val n = normCp(cp)
                for (c in n) {
                    sb.append(c)
                    map += i
                }
                i += Character.charCount(cp)
            }
            map += text.length
            return sb.toString() to map.toIntArray()
        }

        fun norm(word: String): String =
            normText(word).first.replace(WS, " ").trim().removeSuffix(".")

        private fun esc(s: String) = s.replace(ESC) { "\\" + it.value }

        private fun alt(list: Collection<String>) =
            list.sortedByDescending { it.length }.joinToString("|") { w -> esc(w).replace(ALT_DOT) { "[.\\s]?" } }

        /** Letras de ordinal coladas ao número ("7th", "1er", "2º"): quantas tirar. */
        private fun ordinal(after: String): Int {
            val m = ORDINAL.find(after) ?: return 0
            // "1 to 7", "7 de": palavras de ligação não são ordinais.
            return if (NOSPACE.containsMatchIn(m.value) || m.value in RANGE_WORDS || m.value in CONNECTORS) 0 else m.value.length
        }

        private fun stripEdges(text: String) =
            EDGES.replace(text.replace(MARK, ' ').replace(WS, " "), "")

        /** Tira as marcas e as palavras de ligação que lhes ficaram encostadas. */
        private fun cleanTitle(cleaned: String): String {
            val words = cleaned.replace(MARK.toString(), " $MARK ").split(WS).filter { it.isNotEmpty() }
            val mark = MARK.toString()
            fun isConn(x: String) = norm(x).replace(CONN_PUNCT, "") in CONNECTORS || PUNCT_ONLY.matches(x)
            val keep = BooleanArray(words.size) { true }
            words.forEachIndexed { i, x ->
                if (x != mark) return@forEachIndexed
                keep[i] = false
                var j = i - 1
                while (j >= 0 && keep[j] && isConn(words[j])) keep[j--] = false
                // Depois da marca só se apaga se a seguir vier outra marca ou o fim ("amanhã às 13h").
                var k = i + 1
                while (k < words.size && isConn(words[k])) k++
                if (k >= words.size || words[k] == mark) for (n in i + 1 until k) keep[n] = false
            }
            val out = words.filterIndexed { i, _ -> keep[i] }.toMutableList()
            while (out.isNotEmpty() && isConn(out.last())) out.removeAt(out.size - 1)
            return stripEdges(out.joinToString(" "))
        }

        /** #fam → "Family"/"Família": prefixo sem acentos nem espaços. */
        fun matchCalendar(tag: String, calendars: List<QuickCalendar>): QuickCalendar? {
            val clean = { s: String -> norm(s).replace(CAL_CLEAN, "") }
            val t = clean(tag)
            if (t.isEmpty()) return null
            return calendars.firstOrNull { clean(it.name) == t }
                ?: calendars.firstOrNull { clean(it.name).startsWith(t) }
                ?: calendars.firstOrNull { clean(it.name).contains(t) }
        }

        /** Frase sem "// notas" nem emails (título do "Mais opções" quando não se percebeu a data). */
        fun stripExtras(text: String): String {
            val cut = NOTES_CUT.find(text)?.range?.first ?: text.length
            return stripEdges(EMAIL.replace(text.substring(0, cut), " "))
        }

        /** Códigos pedidos (pt-PT, zh-TW…) → códigos da lista, sem repetidos. */
        fun priorityLocales(locales: List<String>): List<String> {
            val out = mutableListOf<String>()
            for (raw in locales) {
                val b = raw.lowercase().split('-', '_').first()
                val code = when {
                    b in QUICK_LOCALES -> b
                    b == "no" || b == "nn" -> "nb"
                    b == "tl" -> "fil"
                    b == "iw" -> "he"
                    b == "in" -> "id"
                    else -> null
                }
                if (code != null && code !in out) out += code
            }
            return out
        }
    }
}
