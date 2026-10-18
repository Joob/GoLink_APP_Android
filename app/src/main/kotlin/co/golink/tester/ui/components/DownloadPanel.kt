package co.golink.tester.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import co.golink.tester.data.download.DownloadJob
import co.golink.tester.data.download.DownloadPhase
import co.golink.tester.data.download.DownloadTracker
import co.golink.tester.data.download.ZipDownloadService
import co.golink.tester.ui.common.formatBytes
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay

@HiltViewModel
class DownloadPanelViewModel @Inject constructor(val tracker: DownloadTracker) : ViewModel()

private val Blue = Color(0xFF2563EB)
private val Green = Color(0xFF15803D)

/**
 * Painel de transferências dentro da app (como na web): "A preparar transferência"
 * com os passos (zips), progresso com bytes e tempo restante, aviso de
 * desencriptação e Cancelar. As transferências continuam no serviço mesmo que a
 * app seja fechada.
 */
@Composable
fun DownloadPanel(modifier: Modifier = Modifier, viewModel: DownloadPanelViewModel = hiltViewModel()) {
    val jobs by viewModel.tracker.jobs.collectAsState()
    if (jobs.isEmpty()) return
    val context = LocalContext.current

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Os mais recentes ficam por baixo; no máximo 3 cartões.
        jobs.takeLast(3).forEach { job ->
            key(job.id) {
                JobCard(
                    job = job,
                    onCancel = { ZipDownloadService.cancel(context, job.id) },
                    onDismiss = { viewModel.tracker.dismiss(job.id) },
                )
            }
        }
    }
}

@Composable
private fun JobCard(job: DownloadJob, onCancel: () -> Unit, onDismiss: () -> Unit) {
    var expanded by remember(job.id) { mutableStateOf(true) }
    val finished = job.phase == DownloadPhase.Done || job.phase == DownloadPhase.Failed || job.phase == DownloadPhase.Cancelled
    // Concluída/cancelada fecha sozinha; a falhada espera pelo utilizador.
    LaunchedEffect(job.phase) {
        if (job.phase == DownloadPhase.Done || job.phase == DownloadPhase.Cancelled) {
            delay(4000)
            onDismiss()
        }
    }
    val percent = if (job.total > 0) ((job.loaded * 100) / job.total).toInt().coerceIn(0, 99) else null

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(job, percent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (job.phase) {
                            DownloadPhase.Preparing -> "A preparar transferência".tr()
                            DownloadPhase.Downloading -> "A transferir".tr() + " " + job.name
                            DownloadPhase.Done -> "Transferência concluída".tr()
                            DownloadPhase.Failed -> "Falha na transferência".tr()
                            DownloadPhase.Cancelled -> "Transferência cancelada".tr()
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle(job),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (job.phase == DownloadPhase.Downloading && percent != null) {
                    Text("$percent%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Blue)
                    Spacer(Modifier.width(6.dp))
                }
                if (!finished) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { expanded = !expanded }
                            .rotate(if (expanded) 0f else 180f),
                    )
                } else if (job.phase == DownloadPhase.Failed) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Fechar".tr(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(4.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && !finished,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    if (job.phase == DownloadPhase.Preparing && job.isZip) {
                        Steps()
                    } else {
                        LinearProgressIndicator(
                            progress = { if (job.total > 0) (job.loaded.toFloat() / job.total).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Blue,
                            trackColor = Blue.copy(alpha = 0.14f),
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        if (job.encrypted) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Blue.copy(alpha = 0.09f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Blue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    buildString {
                                        append("A desencriptar".tr())
                                        append(" ")
                                        append("no teu dispositivo enquanto transfere".tr())
                                    },
                                    fontSize = 12.sp,
                                    color = Blue,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Podes sair da app — a transferência continua.".tr(),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Cancelar".tr(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onCancel).padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Ring(job: DownloadJob, percent: Int?) {
    val spin = rememberInfiniteTransition(label = "dl-ring")
    val bob by spin.animateFloat(-2f, 2f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dl-bob")
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        when (job.phase) {
            DownloadPhase.Done -> Box(Modifier.size(46.dp).clip(CircleShape).background(Green.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Green, modifier = Modifier.size(24.dp))
            }
            DownloadPhase.Failed, DownloadPhase.Cancelled -> Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
            }
            else -> {
                if (percent != null && job.phase == DownloadPhase.Downloading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.size(46.dp),
                        color = Blue,
                        trackColor = Blue.copy(alpha = 0.14f),
                        strokeWidth = 3.dp,
                    )
                } else {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(46.dp),
                        color = Blue,
                        trackColor = Blue.copy(alpha = 0.14f),
                        strokeWidth = 3.dp,
                    )
                }
                // Seta a "descer" (animada) no centro.
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = null,
                    tint = Blue,
                    modifier = Modifier.size(20.dp).padding(top = (3 + bob).dp.coerceAtLeast(0.dp)),
                )
            }
        }
    }
}

// Passos do zip, como na web: obter → juntar → guardar.
@Composable
private fun Steps() {
    val steps = listOf(
        "A obter os ficheiros".tr(),
        "Juntar num ficheiro .zip".tr(),
        "Guardar no telemóvel".tr(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (i == 0) Blue else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (i == 0) Box(Modifier.size(8.dp).clip(CircleShape).background(Blue)) }
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (i == 0) Text("em curso".tr(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue)
            }
        }
    }
}

private fun subtitle(job: DownloadJob): String = when (job.phase) {
    DownloadPhase.Preparing -> job.name
    DownloadPhase.Downloading -> {
        val size = if (job.total > 0) "%s de %s".tr().format(formatBytes(job.loaded), formatBytes(job.total)) else formatBytes(job.loaded)
        val eta = eta(job)
        if (eta != null) "$size · $eta" else size
    }
    DownloadPhase.Done -> job.message ?: job.name
    DownloadPhase.Failed -> job.message ?: job.name
    DownloadPhase.Cancelled -> job.name
}

// "~1 h 29 min restante" pela velocidade média desde o início da transferência.
private fun eta(job: DownloadJob): String? {
    if (job.total <= 0 || job.loaded <= 0 || job.transferStartedAt == 0L) return null
    val elapsedMs = System.currentTimeMillis() - job.transferStartedAt
    if (elapsedMs < 2000) return null
    val bytesPerMs = job.loaded.toDouble() / elapsedMs
    if (bytesPerMs <= 0) return null
    val seconds = ((job.total - job.loaded) / bytesPerMs / 1000).toLong().coerceAtLeast(1)
    val text = when {
        seconds >= 3600 -> "%d h %d min".format(seconds / 3600, (seconds % 3600) / 60)
        seconds >= 60 -> "%d min".format(seconds / 60)
        else -> "%d s".format(seconds)
    }
    return "~$text " + "restante".tr()
}
