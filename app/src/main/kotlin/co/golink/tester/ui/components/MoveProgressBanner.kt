package co.golink.tester.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.golink.tester.data.files.MoveJob
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.ui.i18n.tr

/** Painel "A mover…" (por cima do de uploads): um bloco por lote. */
@Composable
fun MoveProgressBanner(
    jobs: List<MoveJob>,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onOpenFolder: (MoveJob) -> Unit,
) {
    if (jobs.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        jobs.forEach { job ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            MoveJobBlock(job, onRetry, onDismiss, onOpenFolder)
        }
    }
}

@Composable
private fun MoveJobBlock(
    job: MoveJob,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onOpenFolder: (MoveJob) -> Unit,
) {
    val n = job.items.size
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error

    // "ainda a trabalhar…" ao fim de 3 s, para não parecer parado.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(job.state, job.startedAt) {
        while (job.state == MoveJob.State.Moving) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val slow = job.state == MoveJob.State.Moving && now - job.startedAt >= 3_000

    val title = when (job.state) {
        MoveJob.State.Moving -> if (n == 1) "A mover 1 item".tr() else "A mover %d itens".tr().format(n)
        MoveJob.State.Done -> if (n == 1) "1 item movido".tr() else "%d itens movidos".tr().format(n)
        MoveJob.State.Failed -> "Não foi possível mover".tr()
    }
    val subtitle = when (job.state) {
        MoveJob.State.Moving -> "para %s".tr().format(job.toName) + if (slow) " · " + "ainda a trabalhar…".tr() else ""
        MoveJob.State.Done -> "em %s".tr().format(job.toName)
        MoveJob.State.Failed -> when {
            !job.retryable -> job.error.orEmpty().tr()
            !job.error.isNullOrBlank() -> "Verifica a ligação e tenta de novo".tr() + " (${job.error})"
            else -> "Verifica a ligação e tenta de novo".tr()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when (job.state) {
                        MoveJob.State.Failed -> error.copy(alpha = 0.12f)
                        MoveJob.State.Done -> primary.copy(alpha = 0.18f)
                        MoveJob.State.Moving -> primary.copy(alpha = 0.12f)
                    },
                ),
        ) {
            when (job.state) {
                MoveJob.State.Moving -> SlidingMoveIcon()
                MoveJob.State.Done -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
                MoveJob.State.Failed -> Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = error, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (job.state == MoveJob.State.Failed) error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onDismiss(job.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Fechar".tr(), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }

    if (job.state == MoveJob.State.Moving) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    if (job.state != MoveJob.State.Done) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            job.items.take(3).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item is BrowseItem.Folder) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                item.name.substringAfterLast('.', "").take(3).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(7f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (n > 3) Text("+ %d itens".tr().format(n - 3), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    when (job.state) {
        MoveJob.State.Failed -> Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            TextButton(onClick = { onDismiss(job.id) }) { Text("Fechar".tr()) }
            if (job.retryable) TextButton(onClick = { onRetry(job.id) }) { Text("Tentar de novo".tr(), fontWeight = FontWeight.SemiBold) }
        }
        MoveJob.State.Done -> Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            TextButton(onClick = { onOpenFolder(job) }) { Text("Abrir pasta".tr(), fontWeight = FontWeight.SemiBold) }
        }
        MoveJob.State.Moving -> Spacer(Modifier.height(4.dp))
    }
}

/** Ícone de mover a deslizar para a direita (em ciclo), como as setas do upload. */
@Composable
private fun SlidingMoveIcon() {
    val transition = rememberInfiniteTransition(label = "move")
    val t by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing), RepeatMode.Restart),
        label = "slide",
    )
    Box(Modifier.size(22.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        Icon(
            Icons.AutoMirrored.Outlined.DriveFileMove,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .offset(x = (t * 8).dp)
                .graphicsLayer { alpha = 1f - kotlin.math.abs(t) * 0.7f },
        )
    }
}
