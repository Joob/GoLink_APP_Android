package co.golink.tester.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.graphics.graphicsLayer
import co.golink.tester.ui.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.golink.tester.data.upload.UploadTask
import kotlin.math.roundToLong

private enum class BannerState { Uploading, Failed, Done, Empty }

@Composable
fun UploadProgressBanner(
    tasks: List<UploadTask>,
    onDismiss: () -> Unit,
    onCancelTask: (String) -> Unit = {},
    onRetryTask: (String) -> Unit = {},
    onRetryFailed: () -> Unit = {},
    onRetryStalled: () -> Unit = {},
    onOverwrite: (String) -> Unit = {},
    onSkip: (String) -> Unit = {},
    onViewFiles: () -> Unit = {},
) {
    if (tasks.isEmpty()) return
    var expanded by remember { mutableStateOf(true) }
    val active = tasks.count { it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued }
    val failed = tasks.count { it.state == UploadTask.State.Failed }
    val stalled = tasks.count { it.state == UploadTask.State.Stalled }
    val done = tasks.count { it.state == UploadTask.State.Completed }
    val total = tasks.size
    // Geral por bytes só sobre o que conta para chegar a 100%: concluídos + em
    // envio/fila. Falhados, conflitos e parados ficam de fora (antes um falhado
    // deixava o geral preso abaixo de 100%).
    val counted = tasks.filter {
        it.state == UploadTask.State.Completed || it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued
    }
    val totalBytes = counted.sumOf { it.sizeBytes }
    val uploadedBytes = counted.sumOf {
        if (it.state == UploadTask.State.Completed) it.sizeBytes else (it.progress.toDouble() * it.sizeBytes).roundToLong()
    }
    val overallProgress = if (totalBytes > 0) uploadedBytes.toFloat() / totalBytes.toFloat() else 0f

    val bannerState = when {
        active > 0 -> BannerState.Uploading
        failed > 0 || stalled > 0 -> BannerState.Failed
        done == total && total > 0 -> BannerState.Done
        else -> BannerState.Empty
    }

    var startTime by remember { mutableLongStateOf(0L) }
    // Bytes já enviados quando esta sessão de envio começou (o ETA usa só os desta sessão).
    var startBytes by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(bannerState) {
        if (bannerState == BannerState.Uploading) {
            if (startTime == 0L) { startTime = System.currentTimeMillis(); startBytes = uploadedBytes }
            while (true) {
                // Mesma base que startTime (o tempo de frame vem do nanoTime).
                withFrameMillis { now = System.currentTimeMillis() }
                kotlinx.coroutines.delay(500)
            }
        } else {
            startTime = 0L
        }
    }
    val sessionBytes = (uploadedBytes - startBytes).coerceAtLeast(0L)
    val etaSeconds: Long? = if (bannerState == BannerState.Uploading && startTime > 0L && sessionBytes > 0L) {
        val elapsed = ((now - startTime).coerceAtLeast(1L)) / 1000.0
        val rate = sessionBytes / elapsed
        if (rate > 0) ((totalBytes - uploadedBytes) / rate).toLong().coerceAtLeast(0L) else null
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        BannerHeader(
            state = bannerState,
            active = active,
            done = done,
            failed = failed,
            stalled = stalled,
            total = total,
            totalBytes = totalBytes,
            uploadedBytes = uploadedBytes,
            etaSeconds = etaSeconds,
            encrypting = tasks.any { it.encrypted && it.state == UploadTask.State.Uploading },
            expanded = expanded,
            onToggleExpand = { expanded = !expanded },
            onDismiss = onDismiss,
        )
        if (bannerState == BannerState.Uploading) {
            LinearProgressIndicator(
                progress = { overallProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            // Uploads E2E cifram no dispositivo — efeito "a encriptar" (igual à Web).
            if (tasks.any { it.encrypted }) DecryptEffect(
                label = "A encriptar".tr(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                // ~4,5 linhas visíveis (a meia linha indica scroll). Lazy: com
                // dezenas de milhares de tarefas só compõe as que estão no ecrã.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 270.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        UploadRow(
                            task = task,
                            onCancel = { onCancelTask(task.id) },
                            onRetry = { onRetryTask(task.id) },
                            onOverwrite = { onOverwrite(task.id) },
                            onSkip = { onSkip(task.id) },
                        )
                    }
                }
                if (bannerState == BannerState.Failed || bannerState == BannerState.Done) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    // Só parados → "Reenviar parados (N)"; com falhados, repete os dois.
                    val onlyStalled = bannerState == BannerState.Failed && failed == 0
                    BannerFooter(
                        state = bannerState,
                        primaryOverride = if (onlyStalled) "Reenviar parados (%d)".tr().format(stalled) else null,
                        onPrimary = when {
                            bannerState != BannerState.Failed -> onViewFiles
                            onlyStalled -> onRetryStalled
                            else -> ({ onRetryFailed(); if (stalled > 0) onRetryStalled() })
                        },
                        onSecondary = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerHeader(
    state: BannerState,
    active: Int,
    done: Int,
    failed: Int,
    stalled: Int = 0,
    total: Int,
    totalBytes: Long,
    uploadedBytes: Long,
    etaSeconds: Long?,
    encrypting: Boolean = false,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (iconBg, iconTint, leadingIcon) = when (state) {
        BannerState.Uploading -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
            Icons.Outlined.CloudUpload,
        )
        BannerState.Failed -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.error,
            Icons.Outlined.WarningAmber,
        )
        BannerState.Done -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.primary,
            Icons.Filled.CheckCircle,
        )
        BannerState.Empty -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Outlined.CloudUpload,
        )
    }
    val title = when (state) {
        BannerState.Uploading -> "A carregar %d de %d".tr().format((done + 1).coerceAtMost(total), total)
        BannerState.Failed -> when {
            failed == 0 -> "%d parado(s)".tr().format(stalled)
            failed == 1 -> "1 ficheiro falhou"
            else -> "$failed ficheiros falharam"
        }
        BannerState.Done -> if (total == 1) "1 ficheiro carregado" else "$total ficheiros carregados"
        BannerState.Empty -> "Carregamento"
    }
    val subtitle = when (state) {
        BannerState.Uploading -> buildString {
            append(humanBytes(uploadedBytes))
            append(" de ")
            append(humanBytes(totalBytes))
            etaSeconds?.let { append(" · ~${humanEta(it)}") }
        }
        BannerState.Failed -> "$done de $total concluídos"
        BannerState.Done -> "${humanBytes(totalBytes)} · concluído"
        BannerState.Empty -> ""
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (state == BannerState.Uploading) {
            UploadRing(
                progress = if (totalBytes > 0) uploadedBytes.toFloat() / totalBytes else 0f,
                encrypting = encrypting,
            )
        } else Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Percentagem geral do upload (0–100%) bem visível no cabeçalho.
        if (state == BannerState.Uploading) {
            val pct = if (totalBytes > 0)
                (uploadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0
            Text(
                "$pct%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
        }
        if (state != BannerState.Empty) {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Encolher" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Fechar".tr(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun UploadRow(
    task: UploadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOverwrite: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    val isFailed = task.state == UploadTask.State.Failed
    val isConflict = task.state == UploadTask.State.Conflict
    val rowBg = when {
        isFailed -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        isConflict || task.state == UploadTask.State.Stalled -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Badge da extensão (APK, JSON, PDF…), como no painel da web.
            val ext = task.name.substringAfterLast('.', "").take(4).uppercase()
            if (!isFailed && ext.isNotEmpty()) {
                Text(ext, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Icon(
                Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = rowSubtitle(task),
                style = MaterialTheme.typography.labelSmall,
                color = if (isFailed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.state == UploadTask.State.Uploading) {
                Spacer(Modifier.height(6.dp))
                // Barra deste ficheiro (a cifrar: fração cifrada; depois, bytes enviados).
                LinearProgressIndicator(
                    progress = { (if (task.encrypting) task.encryptProgress else task.progress).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (task.state) {
            UploadTask.State.Completed -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            UploadTask.State.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Tentar de novo".tr(),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remover".tr(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            UploadTask.State.Uploading -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${((if (task.encrypting) task.encryptProgress else task.progress) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                // A finalizar: o servidor já junta as partes — cancelar não o desfaria.
                IconButton(onClick = onCancel, enabled = !task.finalizing, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancelar".tr(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            UploadTask.State.Queued -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Em fila".tr(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancelar".tr(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            UploadTask.State.Stalled -> Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("Reenviar".tr(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancelar".tr(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            UploadTask.State.Cancelled -> Text(
                "Cancelado",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UploadTask.State.Conflict -> Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = onOverwrite, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("Substituir".tr(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                androidx.compose.material3.TextButton(onClick = onSkip, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("Não substituir".tr(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BannerFooter(
    state: BannerState,
    primaryOverride: String? = null,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val primaryLabel = primaryOverride ?: if (state == BannerState.Failed) "Repetir falhados".tr() else "Ver ficheiros".tr()
    val secondaryLabel = if (state == BannerState.Failed) "Dispensar" else "Fechar".tr()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onPrimary)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                primaryLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onSecondary)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                secondaryLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun rowSubtitle(task: UploadTask): String = when (task.state) {
    UploadTask.State.Completed -> "Concluído · ${humanBytes(task.sizeBytes)}"
    UploadTask.State.Failed -> task.errorMessage?.takeIf { it.isNotBlank() }?.let { "Falhou · $it" } ?: "Falhou · tentar de novo".tr()
    UploadTask.State.Conflict -> "Já existe · substituir?".tr()
    UploadTask.State.Uploading -> if (task.waitingForNetwork) {
        "Sem ligação · retoma automaticamente".tr()
    } else if (task.finalizing) {
        "A finalizar…".tr()
    } else if (task.encrypting) {
        "%s · %d%%".format("A encriptar".tr(), (task.encryptProgress * 100).toInt())
    } else {
        val uploaded = (task.progress.toDouble() * task.sizeBytes).roundToLong()
        "${humanBytes(uploaded)} de ${humanBytes(task.sizeBytes)}"
    }
    UploadTask.State.Queued -> "Em espera · ${humanBytes(task.sizeBytes)}"
    UploadTask.State.Cancelled -> "Cancelado"
    UploadTask.State.Stalled -> "%s · %s".format("Parado — sem progresso".tr(), humanBytes(task.sizeBytes))
}

private fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

private fun humanEta(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    else -> "${seconds / 3600}h"
}

/**
 * Anel de progresso com duas setas a subir sem parar (upload) e, quando o
 * ficheiro vai cifrado, um cadeado a pulsar no canto — igual ao painel da web.
 */
@Composable
private fun UploadRing(progress: Float, encrypting: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val track = primary.copy(alpha = 0.15f)
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "upload-ring")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "arrows",
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(800),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "lock",
    )
    Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(track, 0f, 360f, useCenter = false, style = stroke)
            drawArc(primary, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false, style = stroke)
        }
        // Duas setas desfasadas numa janela recortada.
        Box(Modifier.size(15.dp).clip(RoundedCornerShape(2.dp))) {
            listOf(t, (t + 0.5f) % 1f).forEach { phase ->
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer {
                            translationY = (1f - 2f * phase) * size.height
                            alpha = when {
                                phase < 0.3f -> phase / 0.3f
                                phase > 0.7f -> (1f - phase) / 0.3f
                                else -> 1f
                            }
                        },
                )
            }
        }
        if (encrypting) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .size(17.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(primary),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
            }
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class UploadPillViewModel @javax.inject.Inject constructor(
    val uploadManager: co.golink.tester.data.upload.UploadManager,
) : androidx.lifecycle.ViewModel()

/**
 * Pílula global (Calendar, Pass, Notes, Legacy…): o envio continua em fundo e
 * aqui só se vê o progresso em pequeno; tocar leva aos Ficheiros, onde está o
 * painel completo com as ações. Só aparece com envios do browser em curso.
 */
@Composable
fun UploadMiniPill(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UploadPillViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val all by viewModel.uploadManager.tasks.collectAsStateWithLifecycle()
    val tasks = all.filterNot { it.mobileBackup }
    val active = tasks.count { it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued }
    if (active == 0) return
    val done = tasks.count { it.state == UploadTask.State.Completed }
    val counted = tasks.filter {
        it.state == UploadTask.State.Completed || it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued
    }
    val totalBytes = counted.sumOf { it.sizeBytes }
    val uploadedBytes = counted.sumOf {
        if (it.state == UploadTask.State.Completed) it.sizeBytes else (it.progress.toDouble() * it.sizeBytes).roundToLong()
    }
    val progress = if (totalBytes > 0) uploadedBytes.toFloat() / totalBytes.toFloat() else 0f
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(12.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
            .clickable(onClick = onOpen)
            .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UploadRing(progress = progress, encrypting = tasks.any { it.encrypted && it.state == UploadTask.State.Uploading })
        Spacer(Modifier.width(8.dp))
        Text(
            "A carregar %d de %d".tr().format((done + 1).coerceAtMost(tasks.size), tasks.size),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${(progress * 100).toInt().coerceIn(0, 100)}%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

