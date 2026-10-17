package co.golink.tester.ui.common

import co.golink.tester.ui.i18n.I18n
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Formatadores partilhados pelo redesign (iguais aos da web: tamanhos com 2
// casas, datas curtas na língua da app e no fuso do dispositivo).

fun appLocale(): Locale = Locale.forLanguageTag(I18n.lang)

/** "4.80 GB", "21.66 MB", "212.84 KB", "743 B". */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 40 -> String.format(Locale.ROOT, "%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024))
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024))
    bytes >= 1L shl 10 -> String.format(Locale.ROOT, "%.2f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** ISO-8601 (UTC) → data/hora local. */
fun parseIsoLocal(iso: String?): ZonedDateTime? = iso?.let {
    runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
}

/** "14 out 2026". */
fun formatDay(iso: String?): String =
    parseIsoLocal(iso)?.format(DateTimeFormatter.ofPattern("d MMM yyyy", appLocale())).orEmpty()

/** "21:59". */
fun formatTime(iso: String?): String =
    parseIsoLocal(iso)?.format(DateTimeFormatter.ofPattern("HH:mm", appLocale())).orEmpty()

/** "14 out, 21:59". */
fun formatDayTime(iso: String?): String =
    parseIsoLocal(iso)?.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", appLocale())).orEmpty()

/** "setembro 2026" → com maiúscula: "Setembro 2026". */
fun formatMonth(iso: String?): String =
    parseIsoLocal(iso)?.format(DateTimeFormatter.ofPattern("LLLL yyyy", appLocale()))
        ?.replaceFirstChar { it.titlecase(appLocale()) }
        .orEmpty()

/** Há menos de [days] dias. */
fun isWithinDays(iso: String?, days: Long): Boolean {
    val t = parseIsoLocal(iso) ?: return false
    return java.time.Duration.between(t.toInstant(), java.time.Instant.now()).toDays() < days
}

/** "agora", "há 5 min", "há 2 h", "ontem", "há 3 dias", "14 out 2026". */
fun relativeAgo(iso: String?): String {
    val t = parseIsoLocal(iso) ?: return ""
    val mins = java.time.Duration.between(t.toInstant(), java.time.Instant.now()).toMinutes()
    return when {
        mins < 1 -> co.golink.tester.ui.i18n.I18n.t("agora")
        mins < 60 -> co.golink.tester.ui.i18n.I18n.t("há %d min").format(mins)
        mins < 24 * 60 -> co.golink.tester.ui.i18n.I18n.t("há %d h").format(mins / 60)
        mins < 48 * 60 -> co.golink.tester.ui.i18n.I18n.t("ontem")
        mins < 7 * 24 * 60 -> co.golink.tester.ui.i18n.I18n.t("há %d dias").format(mins / (24 * 60))
        else -> formatDay(iso)
    }
}
