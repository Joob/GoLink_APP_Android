package co.golink.tester.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    onOverwrite: (String) -> Unit = {},
    onSkip: (String) -> Unit = {},
    onViewFiles: () -> Unit = {},
) {
    if (tasks.isEmpty()) return
    var expanded by remember { mutableStateOf(true) }
    val active = tasks.count { it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued }
    val failed = tasks.count { it.state == UploadTask.State.Failed }
    val done = tasks.count { it.state == UploadTask.State.Completed }
    val total = tasks.size
    val totalBytes = tasks.sumOf { it.sizeBytes }
    val uploadedBytes = tasks.sumOf { (it.progress.toDouble() * it.sizeBytes).roundToLong() }
    val overallProgress = if (totalBytes > 0) uploadedBytes.toFloat() / totalBytes.toFloat() else 0f

    val bannerState = when {
        active > 0 -> BannerState.Uploading
        failed > 0 -> BannerState.Failed
        done == total && total > 0 -> BannerState.Done
        else -> BannerState.Empty
    }

    var startTime by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(bannerState) {
        if (bannerState == BannerState.Uploading) {
            if (startTime == 0L) startTime = System.currentTimeMillis()
            while (true) {
                withFrameMillis { now = it }
                kotlinx.coroutines.delay(500)
            }
        } else {
            startTime = 0L
        }
    }
    val etaSeconds: Long? = if (bannerState == BannerState.Uploading && startTime > 0L && uploadedBytes > 0L) {
        val elapsed = ((now - startTime).coerceAtLeast(1L)) / 1000.0
        val rate = uploadedBytes / elapsed
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
            total = total,
            totalBytes = totalBytes,
            uploadedBytes = uploadedBytes,
            etaSeconds = etaSeconds,
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
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    tasks.forEach { task ->
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
                    BannerFooter(
                        state = bannerState,
                        onPrimary = if (bannerState == BannerState.Failed) onRetryFailed else onViewFiles,
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
    total: Int,
    totalBytes: Long,
    uploadedBytes: Long,
    etaSeconds: Long?,
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
        BannerState.Uploading -> "A carregar ${done + active} de $total".let {
            // Show items currently being uploaded; the screenshot showed "A carregar 10 de 12"
            // (counting in-progress + done as in-flight progress indicator)
            "A carregar ${(done + 1).coerceAtMost(total)} de $total"
        }
        BannerState.Failed -> if (failed == 1) "1 ficheiro falhou" else "$failed ficheiros falharam"
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
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
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
                contentDescription = "Fechar",
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
        isConflict -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
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
            Icon(
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
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0f, 1f) },
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
                        contentDescription = "Tentar de novo",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remover",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            UploadTask.State.Uploading -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(task.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancelar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            UploadTask.State.Queued -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Em fila",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancelar",
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
                    Text("Substituir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                androidx.compose.material3.TextButton(onClick = onSkip, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("Não substituir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BannerFooter(
    state: BannerState,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val primaryLabel = if (state == BannerState.Failed) "Repetir falhados" else "Ver ficheiros"
    val secondaryLabel = if (state == BannerState.Failed) "Dispensar" else "Fechar"
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
    UploadTask.State.Failed -> task.errorMessage?.takeIf { it.isNotBlank() }?.let { "Falhou · $it" } ?: "Falhou · tentar de novo"
    UploadTask.State.Conflict -> "Já existe · substituir?"
    UploadTask.State.Uploading -> {
        val uploaded = (task.progress.toDouble() * task.sizeBytes).roundToLong()
        "${humanBytes(uploaded)} de ${humanBytes(task.sizeBytes)}"
    }
    UploadTask.State.Queued -> "Em espera · ${humanBytes(task.sizeBytes)}"
    UploadTask.State.Cancelled -> "Cancelado"
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
