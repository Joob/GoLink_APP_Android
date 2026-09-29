package co.golink.tester.ui.components

import co.golink.tester.ui.i18n.tr
import java.time.Instant

/** Opções de validade, em minutos — as mesmas da web (15m … 7d). */
internal val EXPIRATION_OPTIONS: List<Pair<Int, String>> = listOf(
    15 to "15 min",
    30 to "30 min",
    60 to "1 h",
    120 to "2 h",
    360 to "6 h",
    720 to "12 h",
    1440 to "1 d",
    2880 to "2 d",
    10080 to "7 d",
)

/** Tempo que falta até a partilha expirar. `expired` = já passou. */
data class ShareTimeLeft(val text: String, val expired: Boolean)

/**
 * O que FALTA, e não a duração escolhida: gastar a última visualização encurta
 * a validade, e a duração original deixa de dizer alguma coisa. null = não
 * expira (ou data ilegível).
 */
fun shareTimeLeft(expiresAt: String?, now: Long = System.currentTimeMillis()): ShareTimeLeft? {
    val end = expiresAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return null
    val ms = end - now
    if (ms <= 0) return ShareTimeLeft("Expirado".tr(), expired = true)

    val minutes = (ms + 59_999) / 60_000
    val text = when {
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> "${(minutes + 30) / 60} h"
        else -> "${(minutes + 12 * 60) / (24 * 60)} d"
    }
    return ShareTimeLeft(text, expired = false)
}
