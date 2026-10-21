package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.golink.tester.domain.admin.EventsResponse
import co.golink.tester.domain.admin.RealtimeResponse
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.admin.AdminBadge
import co.golink.tester.ui.screens.admin.AdminColors
import co.golink.tester.ui.screens.admin.AdminLoading
import co.golink.tester.ui.screens.admin.formatAdminDate
import kotlinx.coroutines.delay

// Separadores "Eventos" (product-usage) e "Tempo real" das Análises.

private const val REALTIME_POLL_MS = 15_000L

@Composable
internal fun EventsTab(data: EventsResponse?, loading: Boolean, onRetry: () -> Unit) {
    if (data == null) {
        if (loading) AdminLoading() else co.golink.tester.ui.screens.admin.AdminError("Não foi possível carregar".tr(), onRetry)
        return
    }
    var selected by remember(data.series.labels) { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (!data.enabled) {
            SettingsNotice("O registo de eventos está desligado no servidor.".tr(), AdminColors.Warning)
            Spacer(Modifier.height(12.dp))
        }
        AnalyticsCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCounter(
                    "Eventos".tr(),
                    selected.takeIf { it >= 0 }?.let { data.series.events.getOrNull(it) } ?: data.cards.total,
                    data.cards.totalChange.takeIf { selected < 0 },
                    VisitsColor,
                    Modifier.weight(1f),
                )
                MetricCounter("Tipos".tr(), data.cards.unique, null, VisitorsColor, Modifier.weight(1f))
                MetricCounter("Pessoas".tr(), data.cards.people, null, AdminColors.Info, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            if (data.series.labels.size >= 2) {
                TrendChart(
                    labels = data.series.labels.map { formatLabel(it, data.granularity) },
                    visitors = data.series.events,
                    visits = emptyList(),
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            } else {
                EmptyHint("Sem dados neste período".tr())
            }
        }
        RankedCard("Por evento".tr(), data.byName, "Evento".tr(), barColor = VisitsColor)
        RankedCard("Por categoria".tr(), data.byCategory, "Categoria".tr())
        if (data.recent.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            AnalyticsCard {
                Text("Eventos recentes".tr(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                data.recent.forEach { e ->
                    ActivityLine(
                        flag = flagEmoji(e.country_code),
                        title = e.name ?: "—",
                        subtitle = listOfNotNull(e.category, e.device_type, formatAdminDate(e.occurred_at)).joinToString(" · "),
                        isUser = e.type == "user",
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun RealtimeTab(data: RealtimeResponse?, refresh: suspend () -> Unit) {
    // O ciclo vive enquanto o separador está no ecrã: sair dele pára os pedidos.
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(REALTIME_POLL_MS)
        }
    }
    if (data == null) return AdminLoading()
    var selected by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        AnalyticsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AdminBadge("● " + "%d online agora".tr().format(data.cards.onlineNow), SettingsAccent)
                Spacer(Modifier.width(8.dp))
                Text("Atualiza a cada 15 s".tr(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MetricCounter(
                    "Visitantes".tr(),
                    selected.takeIf { it >= 0 }?.let { data.series.visitors.getOrNull(it) } ?: data.cards.visitors,
                    null, VisitorsColor, Modifier.weight(1f),
                )
                MetricCounter(
                    "Visitas".tr(),
                    selected.takeIf { it >= 0 }?.let { data.series.visits.getOrNull(it) } ?: data.cards.visits,
                    null, VisitsColor, Modifier.weight(1f),
                )
            }
            Text(
                selected.takeIf { it >= 0 }?.let { data.series.labels.getOrNull(it) } ?: "Últimos 30 minutos".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (data.series.labels.size >= 2) {
                TrendChart(
                    labels = data.series.labels.map { if (it == "now") "agora".tr() else it },
                    visitors = data.series.visitors,
                    visits = data.series.visits,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        AnalyticsCard {
            Text("Atividade recente".tr(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (data.activity.isEmpty()) EmptyHint("Sem visitas nos últimos 30 minutos".tr())
            data.activity.forEach { a ->
                ActivityLine(
                    flag = flagEmoji(a.country_code),
                    title = listOfNotNull(a.city, a.country_name).joinToString(", ").ifBlank { "—" },
                    subtitle = listOfNotNull(a.browser, a.os, a.referrer, a.visited_at?.takeLast(8)).joinToString(" · "),
                    isUser = a.type == "user",
                )
            }
        }
        if (data.cards.botsFiltered > 0) {
            Spacer(Modifier.height(8.dp))
            Text("%d bots filtrados".tr().format(data.cards.botsFiltered), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityLine(flag: String, title: String, subtitle: String, isUser: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(flag, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AdminBadge(if (isUser) "Utilizador".tr() else "Visitante".tr(), if (isUser) VisitorsColor else AdminColors.Neutral)
    }
}
