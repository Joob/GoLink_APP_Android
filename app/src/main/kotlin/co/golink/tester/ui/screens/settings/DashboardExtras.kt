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
import co.golink.tester.ui.screens.admin.AdminTone
import co.golink.tester.ui.screens.admin.AdminSection
import co.golink.tester.ui.screens.admin.AdminListItem
import co.golink.tester.ui.screens.admin.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Receipt
import co.golink.tester.ui.screens.admin.AdminStatusPill
import co.golink.tester.ui.screens.admin.formatAdminDate
import androidx.compose.ui.unit.sp

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
        AdminSection("Novos registos".tr(), icon = Icons.Outlined.PersonAdd, tint = SettingsColors.Blue, padded = false) {
            state.newbies.forEachIndexed { i, u ->
                if (i > 0) SettingsRowDivider()
                AdminListItem(
                    title = u.name,
                    subtitle = u.email,
                    leading = { AdminAvatar(u.avatar, size = 32, name = u.name) },
                    trailing = if (u.storage != null) { { Text(u.storage.orEmpty(), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } else null,
                    onClick = { onOpenUser(u.id) },
                )
            }
        }
    }

    if (state.transactions.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        AdminSection("Últimas transações".tr(), icon = Icons.Outlined.Receipt, tint = SettingsColors.Amber, padded = false) {
            state.transactions.forEachIndexed { i, t ->
                if (i > 0) SettingsRowDivider()
                TransactionLine(t, inset = true)
            }
        }
    }

    if (state.migration.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        AdminSection(
            "Migração da encriptação".tr(),
            icon = Icons.Outlined.Lock,
            description = "Primeiros utilizadores: chave E2E configurada e ficheiros já cifrados.".tr(),
        ) {
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

/** Estado de uma fatura como pill da web. */
internal fun transactionTone(a: co.golink.tester.domain.admin.TransactionAttributes): Pair<String, AdminTone> = when {
    a.is_refunded -> "Reembolsada".tr() to AdminTone.Info
    a.status == "completed" -> "Paga".tr() to AdminTone.Success
    a.status == "cancelled" -> "Cancelada".tr() to AdminTone.Neutral
    else -> "Falhada".tr() to AdminTone.Danger
}

/**
 * Linha de fatura: avatar, nome, data · gateway, valor e pill de estado.
 * [inset] = dentro de uma lista agrupada/secção sem padding (margens próprias).
 */
@Composable
internal fun TransactionLine(t: TransactionData, onClick: (() -> Unit)? = null, inset: Boolean = false) {
    val a = t.attributes
    val user = t.relationships?.user?.data?.attributes
    val (label, tone) = transactionTone(a)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (inset) 16.dp else 0.dp, vertical = if (inset) 12.dp else 7.dp),
    ) {
        AdminAvatar(user?.avatarUrl, size = 32, name = user?.name ?: user?.email, tone = tone)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(user?.name ?: a.note ?: "—", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(formatAdminDate(a.created_at) ?: a.created_at, a.driver?.replaceFirstChar { it.uppercase() }).joinToString(" · "),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.price ?: "—", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            AdminStatusPill(label, tone)
        }
    }
}

@Composable
private fun MigrationLine(r: EncryptionMigrationRow) {
    val m = r.migration
    val (label, tone) = when (m.status) {
        "done" -> "Concluída".tr() to AdminTone.Success
        "partial" -> "${m.percent ?: 0}%" to AdminTone.Warning
        "none" -> "Por cifrar".tr() to AdminTone.Danger
        else -> "Sem ficheiros".tr() to AdminTone.Neutral
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
            AdminStatusPill(label, tone)
        }
        if (m.status == "partial") {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (m.percent ?: 0) / 100f },
                color = AdminTone.Warning.solid,
                trackColor = AdminTone.Warning.background(),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            )
        }
    }
}
