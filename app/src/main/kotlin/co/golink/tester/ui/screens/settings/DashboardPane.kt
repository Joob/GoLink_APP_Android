package co.golink.tester.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.admin.DashboardResponse
import co.golink.tester.ui.i18n.tr
import kotlin.math.roundToInt

private val OnlineColor = Color(0xFF12B76A)
private val GuestColor = Color(0xFFF2994A)

@Composable
fun DashboardPane(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        state.error != null -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                state.error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = viewModel::load, shape = RoundedCornerShape(12.dp)) {
                Text("Tentar de novo".tr())
            }
        }
        else -> state.data?.let { DashboardContent(it) }
    }
}

@Composable
private fun DashboardContent(d: DashboardResponse) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        UsersCard(d)
        Spacer(Modifier.height(16.dp))
        TrafficCard(d)
        Spacer(Modifier.height(16.dp))
        AppCard(d)
        Spacer(Modifier.height(32.dp))
    }
}

// ===========================================================================
// Utilizadores
// ===========================================================================

@Composable
private fun UsersCard(d: DashboardResponse) {
    val total = d.users.total.coerceAtLeast(1)
    val premium = d.users.usersPremiumTotal
    val premiumFraction = (premium.toFloat() / total).coerceIn(0f, 1f)
    val sweep by animateFloatAsState(
        targetValue = premiumFraction * 360f,
        animationSpec = tween(700),
        label = "users",
    )
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    AnalyticsCard {
        Text(
            "Utilizadores".tr(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(118.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 22f)
                    val inset = 12f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(
                        color = track,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke,
                    )
                    drawArc(
                        color = VisitorsColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        d.users.total.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Total".tr(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                LegendValue("Premium", premium, VisitorsColor)
                Spacer(Modifier.height(10.dp))
                LegendValue("Online", d.users.online, OnlineColor)
                Spacer(Modifier.height(10.dp))
                LegendValue("Convidados online".tr(), d.users.guests, GuestColor)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "%d%% da base é premium".tr().format((premiumFraction * 100).roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

// ===========================================================================
// Armazenamento e tráfego
// ===========================================================================

@Composable
private fun TrafficCard(d: DashboardResponse) {
    val upload = d.disk.upload.total
    val download = d.disk.download.total
    val top = maxOf(sizeToBytes(upload), sizeToBytes(download), 1.0)

    AnalyticsCard {
        Text(
            "Armazenamento e tráfego".tr(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigValue("Em uso".tr(), d.disk.used ?: "—", VisitorsColor, Modifier.weight(1f))
            BigValue("Ganhos".tr(), d.app.earnings ?: "—", OnlineColor, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        TrafficBar("Upload total".tr(), upload, (sizeToBytes(upload) / top).toFloat(), VisitorsColor)
        Spacer(Modifier.height(10.dp))
        TrafficBar("Download total".tr(), download, (sizeToBytes(download) / top).toFloat(), VisitsColor)
    }
}

@Composable
private fun BigValue(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrafficBar(label: String, value: String?, fraction: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0.02f, 1f),
        animationSpec = tween(600),
        label = "traffic",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(animated).height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

// ===========================================================================
// Aplicação
// ===========================================================================

@Composable
private fun AppCard(d: DashboardResponse) {
    AnalyticsCard {
        Text(
            "Aplicação".tr(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        AppRow("Versão".tr(), d.app.version ?: "—")
        AppRow("Licença".tr(), d.app.license ?: "—")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Cron",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(
                text = if (d.app.cron.isRunning) "A correr".tr() else "Parado".tr(),
                color = if (d.app.cron.isRunning) OnlineColor else VisitsColor,
            )
        }
    }
}

@Composable
private fun AppRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

/** "48.93MB" -> bytes, só para dar proporção às barras de tráfego. */
private fun sizeToBytes(raw: String?): Double {
    if (raw.isNullOrBlank()) return 0.0
    val match = Regex("([\\d.,]+)\\s*([KMGTP]?B)", RegexOption.IGNORE_CASE).find(raw) ?: return 0.0
    val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0.0
    val factor = when (match.groupValues[2].uppercase()) {
        "KB" -> 1024.0
        "MB" -> 1024.0 * 1024
        "GB" -> 1024.0 * 1024 * 1024
        "TB" -> 1024.0 * 1024 * 1024 * 1024
        "PB" -> 1024.0 * 1024 * 1024 * 1024 * 1024
        else -> 1.0
    }
    return number * factor
}
