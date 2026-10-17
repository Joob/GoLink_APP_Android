package co.golink.tester.ui.screens.support

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.support.AgentStats
import co.golink.tester.domain.support.SupportDashboard
import co.golink.tester.domain.support.SupportOptions
import co.golink.tester.ui.i18n.tr

private val CreatedColor = Color(0xFF00BC7E)
private val ClosedColor = Color(0xFF3B6CFF)

/**
 * Dashboard do Support Desk (espelha resources/js/views/SupportDesk/Dashboard.vue):
 * KPIs com atalhos para a lista filtrada, tempos de resposta, criados/fechados
 * no período, distribuição por categoria/prioridade, tickets parados e equipa.
 *
 * onOpenList(status, assigned, priority) abre a lista com esses filtros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportDashboardScreen(
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onOpenList: (status: String?, assigned: String?, priority: String?) -> Unit,
    viewModel: SupportDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support Desk") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { onOpenList(null, null, null) }) {
                        Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "Tickets".tr())
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(
                        selected = state.range == days,
                        onClick = { viewModel.setRange(days) },
                        label = { Text("%d dias".tr().format(days)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            val data = state.data
            if (data == null) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
                return@Column
            }

            Kpis(data, onOpenList)
            Spacer(Modifier.height(16.dp))
            Times(data)
            Spacer(Modifier.height(16.dp))
            Period(data)
            Spacer(Modifier.height(16.dp))
            Breakdown("Por categoria".tr(), SupportOptions.CATEGORIES.map { categoryLabel(it) to (data.by_category[it] ?: 0) })
            Spacer(Modifier.height(16.dp))
            Breakdown("Por prioridade".tr(), SupportOptions.PRIORITIES.map { priorityLabel(it) to (data.by_priority[it] ?: 0) })

            if (data.stale_tickets.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card("Sem resposta há mais de 24h".tr()) {
                    data.stale_tickets.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenTicket(t.id) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(t.subject, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(t.user_name, priorityLabel(t.priority), formatDate(t.last_message_at)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            data.agents?.takeIf { it.isNotEmpty() }?.let { agents ->
                Spacer(Modifier.height(16.dp))
                Card("Equipa".tr()) {
                    agents.forEachIndexed { i, agent ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        AgentRow(agent)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Kpis(data: SupportDashboard, onOpenList: (String?, String?, String?) -> Unit) {
    val k = data.kpis
    val items = listOf(
        Triple("Aguarda suporte".tr(), k.awaiting_support) { onOpenList("open", null, null) },
        Triple("Aguarda utilizador".tr(), k.awaiting_user) { onOpenList("pending", null, null) },
        Triple("Sem responsável".tr(), k.unassigned) { onOpenList("open", "unassigned", null) },
        Triple("Atribuídos a mim".tr(), k.mine) { onOpenList("open", "me", null) },
        Triple("Por ler".tr(), k.unread) { onOpenList("open", null, null) },
        Triple("Prioridade alta".tr(), k.high_priority) { onOpenList("open", null, "high") },
        Triple("Sem resposta há +24h".tr(), k.stale) { onOpenList("open", null, null) },
        Triple("Criados hoje".tr(), k.created_today) { onOpenList("open", null, null) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value, onClick) ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Times(data: SupportDashboard) {
    val t = data.times
    Card("Tempos de resposta".tr()) {
        Metric("Primeira resposta (média)".tr(), formatMinutes(t.avg_first_response_minutes))
        Metric("Primeira resposta (mediana)".tr(), formatMinutes(t.median_first_response_minutes))
        Metric("Respondidos em 24h".tr(), t.responded_within_24h_pct?.let { "$it%" } ?: "—")
        Metric("Resolução (média)".tr(), t.avg_resolution_hours?.let { formatMinutes((it * 60).toInt()) } ?: "—")
    }
}

@Composable
private fun Period(data: SupportDashboard) {
    Card("Últimos %d dias".tr().format(data.range)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(CreatedColor, "Criados".tr(), data.kpis.created)
            Legend(ClosedColor, "Fechados".tr(), data.kpis.closed)
        }
        Spacer(Modifier.height(12.dp))
        val created = data.series.created
        val closed = data.series.closed
        val max = (created + closed).maxOrNull()?.coerceAtLeast(1) ?: 1
        val baseline = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = created.size.coerceAtLeast(1)
            val slot = size.width / n
            val bar = (slot * 0.38f).coerceAtLeast(1f)
            created.forEachIndexed { i, c ->
                val x = i * slot + slot / 2
                val hc = size.height * c / max
                val hd = size.height * (closed.getOrElse(i) { 0 }) / max
                if (hc > 0) drawRoundRect(CreatedColor, Offset(x - bar, size.height - hc), Size(bar, hc), CornerRadius(bar / 3))
                if (hd > 0) drawRoundRect(ClosedColor, Offset(x, size.height - hd), Size(bar, hd), CornerRadius(bar / 3))
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        }
        if (data.series.labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(data.series.labels.first(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(data.series.labels.last(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Breakdown(title: String, rows: List<Pair<String, Int>>) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Card(title) {
        rows.forEach { (label, value) ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(value.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(value.toFloat() / max)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(CreatedColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentRow(agent: AgentStats) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        SupportAvatar(agent.name, agent.avatar, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(agent.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    agent.title,
                    "%d abertos".tr().format(agent.open_assigned),
                    "%d respostas".tr().format(agent.replies),
                    "%d fechados".tr().format(agent.closed),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatMinutes(agent.avg_first_response_minutes), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Legend(color: Color, label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$label: $value", style = MaterialTheme.typography.bodySmall)
    }
}

/** 45 min · 3h 20m · 2d 4h */
private fun formatMinutes(minutes: Int?): String = when {
    minutes == null -> "—"
    minutes < 60 -> "$minutes min"
    minutes < 24 * 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes / (24 * 60)}d ${(minutes % (24 * 60)) / 60}h"
}
