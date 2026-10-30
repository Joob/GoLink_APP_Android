package co.golink.tester.ui.screens.notifications

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.LocalDarkTheme

/** Categoria → chip de filtro (igual ao categories.js da web; ordem = ordem dos chips). */
val NOTIFICATION_GROUPS: List<Pair<String, Set<String>>> = listOf(
    "security" to setOf("security-alert", "file-quarantined", "share-blocked", "abuse-report"),
    "sharing" to setOf("team-invitation", "file-comment"),
    "calendar" to setOf("calendar"),
    "files" to setOf("file-request", "remote-upload-done"),
    "billing" to setOf("billing-alert", "payment-alert", "insufficient-balance", "storage-full", "subscription-created", "gift"),
    "support" to setOf("support-ticket"),
    "news" to setOf("send-new-notifications"),
)

fun groupOf(category: String?): String? = NOTIFICATION_GROUPS.firstOrNull { category in it.second }?.first

fun groupLabel(group: String?): String = when (group) {
    "security" -> "Segurança".tr()
    "sharing" -> "Partilhas".tr()
    "calendar" -> "Calendário".tr()
    "files" -> "Ficheiros".tr()
    "billing" -> "Faturação".tr()
    "support" -> "Suporte".tr()
    "news" -> "Novidades".tr()
    else -> "Notificação".tr()
}

data class NotificationIcon(val icon: ImageVector, val fg: Color, val bg: Color)

/** Ícone e cores da categoria (a paleta das pills da web: 100/600 claro, 900·40%/400 escuro). */
@Composable
fun notificationIcon(category: String?): NotificationIcon {
    val dark = LocalDarkTheme.current
    fun tone(icon: ImageVector, light: Long, lightBg: Long, darkFg: Long, darkBg: Long) =
        if (dark) NotificationIcon(icon, Color(darkFg), Color(darkBg).copy(alpha = 0.4f))
        else NotificationIcon(icon, Color(light), Color(lightBg))

    val amber = { i: ImageVector -> tone(i, 0xFFD97706, 0xFFFEF3C7, 0xFFFBBF24, 0xFF78350F) }
    val rose = { i: ImageVector -> tone(i, 0xFFE11D48, 0xFFFFE4E6, 0xFFFB7185, 0xFF881337) }
    val sky = { i: ImageVector -> tone(i, 0xFF0284C7, 0xFFE0F2FE, 0xFF38BDF8, 0xFF0C4A6E) }
    return when (category) {
        "security-alert" -> amber(Icons.Outlined.Shield)
        "billing-alert", "payment-alert", "insufficient-balance" -> amber(Icons.Outlined.WarningAmber)
        "file-quarantined" -> rose(Icons.Outlined.Shield)
        "share-blocked", "abuse-report" -> rose(Icons.Outlined.WarningAmber)
        "storage-full" -> rose(Icons.Outlined.Storage)
        "calendar" -> rose(Icons.Outlined.Event)
        "team-invitation" -> tone(Icons.Outlined.PersonAdd, 0xFF4F46E5, 0xFFE0E7FF, 0xFF818CF8, 0xFF312E81)
        "file-request", "remote-upload-done" ->
            tone(Icons.Outlined.CloudUpload, 0xFF7C3AED, 0xFFEDE9FE, 0xFFA78BFA, 0xFF4C1D95)
        "file-comment" -> sky(Icons.Outlined.ChatBubbleOutline)
        "send-new-notifications" -> sky(Icons.Outlined.Campaign)
        "subscription-created" -> tone(Icons.AutoMirrored.Outlined.TrendingUp, 0xFF059669, 0xFFD1FAE5, 0xFF34D399, 0xFF064E3B)
        "gift" -> tone(Icons.Outlined.CardGiftcard, 0xFFDB2777, 0xFFFCE7F3, 0xFFF472B6, 0xFF831843)
        "support-ticket" -> tone(Icons.Outlined.SupportAgent, 0xFF0D9488, 0xFFCCFBF1, 0xFF2DD4BF, 0xFF134E4A)
        else -> tone(Icons.Outlined.Notifications, 0xFF6B7280, 0xFFF3F4F6, 0xFF9CA3AF, 0xFF1F2937)
    }
}
