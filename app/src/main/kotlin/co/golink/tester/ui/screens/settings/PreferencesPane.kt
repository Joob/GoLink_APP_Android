package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.golink.tester.domain.user.User
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.AppThemeMode

/** Formatos aceites pelo servidor (tokens CLDR; "" = automático pelo idioma). */
private val DATE_FORMATS = listOf("d MMM y", "dd/MM/y", "MM/dd/y", "y-MM-dd")

/**
 * Preferências da conta — o mesmo que a web em /user/settings/preferences:
 * tema, idioma, formato de data, início da semana e emails. Tema e idioma
 * aplicam-se já na app e vão para a conta (AccountPrefsSync); o resto grava
 * por PATCH com valor otimista ([overrides]) até o servidor responder.
 */
@Composable
internal fun PreferencesPane(
    user: User?,
    overrides: Map<String, String>,
    onUpdate: (name: String, value: String) -> Unit,
) {
    val context = LocalContext.current
    val locale = co.golink.tester.ui.common.appLocale()

    val dateFormat = overrides["date_format"] ?: user?.dateFormat.orEmpty()
    val weekStart = overrides["week_start"]?.toIntOrNull() ?: user?.weekStart ?: 1
    fun flag(name: String, server: Boolean) = overrides[name]?.let { it == "1" } ?: server

    // Exemplos com a data de hoje na língua da app (uma vez por língua).
    val samples = remember(locale) {
        val today = java.time.LocalDate.now()
        DATE_FORMATS.associateWith { p ->
            runCatching { today.format(java.time.format.DateTimeFormatter.ofPattern(p, locale)) }.getOrDefault(p)
        }
    }
    val dayName = remember(locale) {
        { d: java.time.DayOfWeek -> d.getDisplayName(java.time.format.TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SettingsSectionTitle("Aparência".tr())
        SettingsGroup {
            Box(Modifier.padding(8.dp)) {
                SettingsSegmented(
                    options = listOf(
                        AppThemeMode.Mode.SYSTEM to "Sistema".tr(),
                        AppThemeMode.Mode.LIGHT to "Claro".tr(),
                        AppThemeMode.Mode.DARK to "Escuro".tr(),
                    ),
                    selected = AppThemeMode.mode,
                    onSelect = { AppThemeMode.set(context, it) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("Idioma".tr())
        SettingsGroup {
            I18n.languages.forEachIndexed { i, lang ->
                ChoiceRow(
                    label = "${lang.flag}  ${lang.label}",
                    selected = I18n.lang == lang.code,
                    showDivider = i < I18n.languages.lastIndex,
                ) { I18n.set(context, lang.code) }
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("Formato de data".tr())
        SettingsGroup {
            ChoiceRow("Automático (pelo idioma)".tr(), selected = dateFormat.isBlank(), showDivider = true) {
                if (dateFormat.isNotBlank()) onUpdate("date_format", "")
            }
            DATE_FORMATS.forEachIndexed { i, p ->
                ChoiceRow(samples[p] ?: p, selected = dateFormat == p, showDivider = i < DATE_FORMATS.lastIndex) {
                    if (dateFormat != p) onUpdate("date_format", p)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("Início da semana".tr())
        SettingsGroup {
            Box(Modifier.padding(8.dp)) {
                SettingsSegmented(
                    options = listOf(
                        1 to dayName(java.time.DayOfWeek.MONDAY),
                        0 to dayName(java.time.DayOfWeek.SUNDAY),
                        6 to dayName(java.time.DayOfWeek.SATURDAY),
                    ),
                    selected = weekStart,
                    onSelect = { if (it != weekStart) onUpdate("week_start", it.toString()) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("Emails".tr())
        SettingsGroup {
            val c = SettingsColors.Blue
            SettingsToggleRow(
                title = "Novidades".tr(),
                subtitle = "Novas funcionalidades e anúncios.".tr(),
                checked = flag("news_emails", user?.newsEmails ?: true),
                onCheckedChange = { onUpdate("news_emails", if (it) "1" else "0") },
                icon = Icons.Outlined.Notifications, iconColor = c, showDivider = true,
            )
            SettingsToggleRow(
                title = "Dicas".tr(),
                subtitle = "Sugestões para tirares mais partido da conta.".tr(),
                checked = flag("tips_emails", user?.tipsEmails ?: false),
                onCheckedChange = { onUpdate("tips_emails", if (it) "1" else "0") },
                icon = Icons.Outlined.Lightbulb, iconColor = c, showDivider = true,
            )
            SettingsToggleRow(
                title = "Resumo semanal".tr(),
                subtitle = "À segunda-feira: o que enviaste e partilhaste.".tr(),
                checked = flag("weekly_summary", user?.weeklySummary ?: false),
                onCheckedChange = { onUpdate("weekly_summary", if (it) "1" else "0") },
                icon = Icons.Outlined.Insights, iconColor = c, showDivider = true,
            )
            SettingsToggleRow(
                title = "Segurança e conta".tr(),
                subtitle = "Avisos importantes. Não podem ser desligados.".tr(),
                checked = true,
                onCheckedChange = {},
                enabled = false,
                icon = Icons.Outlined.Email, iconColor = c,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Linha de escolha única com ✓ à direita. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, showDivider: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Check, contentDescription = null, tint = SettingsAccent)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
            )
        }
    }
}
