package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.golink.tester.domain.admin.EncryptionMigrationRow
import co.golink.tester.domain.admin.TransactionData
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.admin.AdminAvatar
import co.golink.tester.ui.screens.admin.AdminBadge
import co.golink.tester.ui.screens.admin.AdminColors
import co.golink.tester.ui.screens.admin.AdminLoadMore

// Blocos do fundo do Dashboard da web: novos registos, últimas transações e
// estado da migração E2E dos primeiros utilizadores.

@Composable
internal fun DashboardExtras(
    state: DashboardViewModel.UiState,
    viewModel: DashboardViewModel,
    onOpenUser: (String) -> Unit,
) {
    if (state.newbies.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        AnalyticsCard {
            CardTitle("Novos registos".tr())
            state.newbies.forEach { u ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onOpenUser(u.id) }
                        .padding(vertical = 6.dp),
                ) {
                    AdminAvatar(u.avatar, size = 32)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(u.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    u.storage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (state.transactions.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        AnalyticsCard {
            CardTitle("Últimas transações".tr())
            state.transactions.forEach { TransactionLine(it) }
        }
    }

    if (state.migration.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        AnalyticsCard {
            CardTitle("Migração da encriptação".tr())
            Text(
                "Primeiros utilizadores: chave E2E configurada e ficheiros já cifrados.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            state.migration.forEach { MigrationLine(it) }
            if (state.migrationHasMore) {
                Spacer(Modifier.height(8.dp))
                AdminLoadMore(state.migrationLoading) { viewModel.loadMigration() }
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
internal fun TransactionLine(t: TransactionData) {
    val a = t.attributes
    val user = t.relationships?.user?.data?.attributes
    val (label, color) = when {
        a.is_refunded -> "Reembolsada".tr() to AdminColors.Info
        a.status == "completed" -> "Paga".tr() to SettingsAccent
        a.status == "cancelled" -> "Cancelada".tr() to AdminColors.Neutral
        else -> "Falhada".tr() to AdminColors.Danger
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(user?.name ?: a.note ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(a.created_at, a.driver).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(a.price ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            AdminBadge(label, color)
        }
    }
}

@Composable
private fun MigrationLine(r: EncryptionMigrationRow) {
    val m = r.migration
    val (label, color) = when (m.status) {
        "done" -> "Concluída".tr() to SettingsAccent
        "partial" -> "${m.percent ?: 0}%" to AdminColors.Warning
        "none" -> "Por cifrar".tr() to AdminColors.Danger
        else -> "Sem ficheiros".tr() to AdminColors.Neutral
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name ?: r.email ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        if (r.encryption_configured) "Chave configurada".tr() else "Sem chave".tr(),
                        "%d/%d ficheiros".tr().format(m.encrypted_files, m.total_files),
                        r.plain_names.takeIf { it > 0 }?.let { "%d nomes em claro".tr().format(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AdminBadge(label, color)
        }
        if (m.status == "partial") {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { (m.percent ?: 0) / 100f }, color = color, modifier = Modifier.fillMaxWidth())
        }
    }
}
