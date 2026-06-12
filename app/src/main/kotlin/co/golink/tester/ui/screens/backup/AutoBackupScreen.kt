package co.golink.tester.ui.screens.backup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.backup.AutoBackupManager
import co.golink.tester.data.backup.BackupRunProgress
import co.golink.tester.data.upload.UploadTask
import co.golink.tester.ui.theme.BrandGreen
import co.golink.tester.ui.theme.BrandGreenDark
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import java.text.DateFormat
import java.util.Date

private val BackupAccent = BrandGreen
private val BackupAccentDeep = BrandGreenDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupScreen(
    onBack: () -> Unit,
    viewModel: AutoBackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allTasks by viewModel.uploadTasks.collectAsStateWithLifecycle()
    // Only show backup-tagged uploads here — user-initiated uploads belong in
    // the browser's global banner.
    val tasks = remember(allTasks) { allTasks.filter { it.mobileBackup } }
    val runState by viewModel.runState.collectAsStateWithLifecycle()
    val runProgress by viewModel.runProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Calculado a pedido (não remember): inclui o áudio quando essa fonte está
    // activa, para o pedido na activação cobrir tudo o que vai ser lido.
    fun requiredPermissions(): Array<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            return arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return buildList {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            if (state.includeAudios) add(Manifest.permission.READ_MEDIA_AUDIO)
            // POST_NOTIFICATIONS is asked together so the foreground-service
            // progress notification can actually appear on Android 13+.
            add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    // Permissions that are *required* for the worker to function. POST_NOTIFICATIONS
    // is desirable but the upload still runs without it, so we don't gate on it.
    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasPermissions(): Boolean = mediaPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var hasPerm by remember { mutableStateOf(hasPermissions()) }

    // Downloads/Documentos: no Android 11+ o MediaStore só mostra ficheiros
    // multimédia a apps de terceiros — PDFs, ZIPs, etc. ficam de fora do
    // backup sem o "Acesso a todos os ficheiros" (MANAGE_EXTERNAL_STORAGE).
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    var hasAllFiles by remember { mutableStateOf(hasAllFilesAccess()) }

    // O acesso é concedido num ecrã do sistema sem resultado próprio —
    // re-verificamos quando o utilizador regressa.
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasAllFiles = hasAllFilesAccess()
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val direct = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        )
        runCatching { allFilesLauncher.launch(direct) }.onFailure {
            // Alguns OEMs não expõem o ecrã por-app — cai na lista geral.
            runCatching {
                allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    // Encadeado a seguir ao diálogo de fotos/vídeos: se Downloads/Documentos
    // estão activos, o ecrã de "Acesso a todos os ficheiros" aparece logo na
    // activação em vez de só ao mexer nesses toggles.
    fun maybeRequestAllFilesAccess() {
        if ((state.includeDocuments || state.includeDownloads) && !hasAllFilesAccess()) {
            requestAllFilesAccess()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        hasPerm = mediaPermissions.all { results[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (hasPerm) {
            viewModel.enable()
            maybeRequestAllFilesAccess()
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backups Automáticos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.enabled) {
                OnboardingCard(
                    onActivate = {
                        if (hasPermissions()) {
                            viewModel.enable()
                            maybeRequestAllFilesAccess()
                        } else {
                            permissionLauncher.launch(requiredPermissions())
                        }
                    },
                )
            } else {
                ActiveStatusCard(
                    backedUpCount = state.backedUpCount,
                    lastBackupAt = state.lastBackupAt,
                    runState = runState,
                    runProgress = runProgress,
                    tasks = tasks,
                    onRunNow = viewModel::runNow,
                    onDisable = viewModel::disable,
                )
                state.lastError?.let { err ->
                    ErrorCard(message = err, onDismiss = viewModel::dismissError, onRetry = viewModel::runNow)
                }
                // runProgress.total > 0 mantém o cartão montado entre lotes —
                // desmontar/remontar a cada pausa era parte do flicker.
                if (tasks.isNotEmpty() || runProgress.total > 0) {
                    UploadProgressCard(
                        tasks = tasks,
                        runProgress = runProgress,
                        onCancel = viewModel::cancelTask,
                        onRetry = viewModel::retryTask,
                        onRetryFailed = viewModel::retryFailed,
                        onOverwrite = viewModel::overwriteTask,
                        onSkip = viewModel::skipTask,
                        onDismiss = viewModel::dismissTasks,
                    )
                }
                SettingsCard(
                    allowWifi = state.allowWifi,
                    allowCellular = state.allowCellular,
                    chargingOnly = state.chargingOnly,
                    onToggleWifi = viewModel::setAllowWifi,
                    onToggleCellular = viewModel::setAllowCellular,
                    onToggleCharging = viewModel::setChargingOnly,
                )
                ContentSourcesCard(
                    includeImages = state.includeImages,
                    includeVideos = state.includeVideos,
                    includeAudios = state.includeAudios,
                    includeDocuments = state.includeDocuments,
                    includeDownloads = state.includeDownloads,
                    onToggleImages = viewModel::setIncludeImages,
                    onToggleVideos = viewModel::setIncludeVideos,
                    onToggleAudios = { enabled ->
                        viewModel.setIncludeAudios(enabled)
                        // Áudio precisa de permissão própria no Android 13+.
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
                        }
                    },
                    onToggleDocuments = { enabled ->
                        viewModel.setIncludeDocuments(enabled)
                        if (enabled && !hasAllFiles) requestAllFilesAccess()
                    },
                    onToggleDownloads = { enabled ->
                        viewModel.setIncludeDownloads(enabled)
                        if (enabled && !hasAllFiles) requestAllFilesAccess()
                    },
                    allFilesAccess = hasAllFiles,
                    onRequestAllFilesAccess = ::requestAllFilesAccess,
                )
                RescanCard(onReset = viewModel::resetCursors)
            }
        }
    }
}

@Composable
private fun OnboardingCard(onActivate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroIllustration()
            Spacer(Modifier.height(20.dp))
            Text(
                "Encripta e faz backup das tuas fotos e vídeos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            FeatureRow(
                icon = Icons.Outlined.Lock,
                title = "Protege as tuas memórias",
                description = "As fotos são encriptadas ponto-a-ponto, garantindo total privacidade.",
            )
            Spacer(Modifier.height(14.dp))
            FeatureRow(
                icon = Icons.Outlined.Refresh,
                title = "Backups automáticos",
                description = "Cópia regular por Wi-Fi mantendo a qualidade original.",
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onActivate,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BackupAccent),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Backup, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Activar backup", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun HeroIllustration() {
    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 140.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        BackupAccent.copy(alpha = 0.22f),
                        BackupAccentDeep.copy(alpha = 0.18f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PhotoCamera,
            contentDescription = null,
            tint = BackupAccent,
            modifier = Modifier.size(72.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BackupAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = BackupAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActiveStatusCard(
    backedUpCount: Int,
    lastBackupAt: Long,
    runState: AutoBackupManager.RunState,
    runProgress: BackupRunProgress,
    tasks: List<UploadTask>,
    onRunNow: () -> Unit,
    onDisable: () -> Unit,
) {
    val activeCount = tasks.count { it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued }
    val doneCount = tasks.count { it.state == UploadTask.State.Completed }
    val totalBytes = tasks.sumOf { it.sizeBytes }.coerceAtLeast(1L)
    val uploadedBytes = tasks.sumOf { (it.progress * it.sizeBytes).toLong() }
    // Preferir o progresso reportado pelo worker: a lista de tarefas é podada
    // a cada lote, por isso contagens/bytes calculados sobre ela andam aos
    // saltos e até recuam.
    val overall = if (runProgress.total > 0)
        (runProgress.done.toFloat() / runProgress.total).coerceIn(0f, 1f)
    else
        (uploadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    val showProgress = runState == AutoBackupManager.RunState.Running || activeCount > 0

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Backup activo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val sub = when (runState) {
                        AutoBackupManager.RunState.Running -> when {
                            runProgress.total > 0 -> "A enviar ${runProgress.done} de ${runProgress.total}…"
                            activeCount > 0 -> "A enviar $doneCount de ${tasks.size}…"
                            else -> "A preparar a galeria…"
                        }
                        AutoBackupManager.RunState.Waiting -> "A aguardar rede / condições…"
                        AutoBackupManager.RunState.Idle -> if (lastBackupAt > 0)
                            "Última verificação: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastBackupAt))}"
                        else "À espera da primeira verificação…"
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = true, onCheckedChange = { onDisable() })
            }

            // Live aggregate progress bar — visible whenever the worker is
            // actually running or there are queued/uploading tasks. The
            // percentage is byte-weighted so a few large files don't make the
            // bar jump backwards as smaller items complete.
            if (showProgress) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { overall },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = BackupAccent,
                    trackColor = BackupAccent.copy(alpha = 0.15f),
                )
                Spacer(Modifier.height(6.dp))
                val pct = (overall * 100).toInt().coerceIn(0, 100)
                Text(
                    when {
                        runProgress.total > 0 -> "$pct% · ${runProgress.done} de ${runProgress.total} ficheiros"
                        tasks.isNotEmpty() -> "$pct% · $doneCount de ${tasks.size} ficheiros"
                        else -> "A preparar…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BackupAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = BackupAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Itens copiados", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("$backedUpCount ficheiros enviados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRunNow,
                enabled = runState == AutoBackupManager.RunState.Idle,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BackupAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (runState == AutoBackupManager.RunState.Running) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("A enviar…", color = Color.White, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Fazer backup agora", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format("%.1f %s", value, units[i])
}

@Composable
private fun SettingsCard(
    allowWifi: Boolean,
    allowCellular: Boolean,
    chargingOnly: Boolean,
    onToggleWifi: (Boolean) -> Unit,
    onToggleCellular: (Boolean) -> Unit,
    onToggleCharging: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            // Toggles independentes: podem estar ambos activos (backup em
            // qualquer rede). O ViewModel impede desligar o último.
            ToggleRow(
                icon = Icons.Outlined.Wifi,
                title = "Wi-Fi",
                description = "Fazer backup quando ligado a Wi-Fi.",
                checked = allowWifi,
                onCheckedChange = onToggleWifi,
            )
            Divider()
            ToggleRow(
                icon = Icons.Outlined.SignalCellularAlt,
                title = "Dados móveis",
                description = "Fazer backup também por dados móveis.",
                checked = allowCellular,
                onCheckedChange = onToggleCellular,
            )
            Divider()
            ToggleRow(
                icon = Icons.Outlined.BatteryChargingFull,
                title = "Apenas a carregar",
                description = "Faz backup quando o dispositivo está ligado à corrente.",
                checked = chargingOnly,
                onCheckedChange = onToggleCharging,
            )
        }
    }
}

@Composable
private fun ContentSourcesCard(
    includeImages: Boolean,
    includeVideos: Boolean,
    includeAudios: Boolean,
    includeDocuments: Boolean,
    includeDownloads: Boolean,
    onToggleImages: (Boolean) -> Unit,
    onToggleVideos: (Boolean) -> Unit,
    onToggleAudios: (Boolean) -> Unit,
    onToggleDocuments: (Boolean) -> Unit,
    onToggleDownloads: (Boolean) -> Unit,
    allFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                "Conteúdos a incluir",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            ToggleRow(
                icon = Icons.Outlined.Image,
                title = "Imagens",
                description = "Fotos da câmara, screenshots e outras imagens.",
                checked = includeImages,
                onCheckedChange = onToggleImages,
            )
            Divider()
            ToggleRow(
                icon = Icons.Outlined.Videocam,
                title = "Vídeos",
                description = "Os vídeos podem ocupar mais espaço e tempo.",
                checked = includeVideos,
                onCheckedChange = onToggleVideos,
            )
            Divider()
            ToggleRow(
                icon = Icons.Outlined.MusicNote,
                title = "Áudios",
                description = "Música e gravações guardadas no dispositivo.",
                checked = includeAudios,
                onCheckedChange = onToggleAudios,
            )
            Divider()
            ToggleRow(
                icon = Icons.Outlined.Description,
                title = "Documentos",
                description = "Ficheiros na pasta Documentos.",
                checked = includeDocuments,
                onCheckedChange = onToggleDocuments,
            )
            Divider()
            ToggleRow(
                icon = Icons.Outlined.Download,
                title = "Downloads",
                description = "Ficheiros na pasta Downloads.",
                checked = includeDownloads,
                onCheckedChange = onToggleDownloads,
            )
            if ((includeDocuments || includeDownloads) && !allFilesAccess) {
                Divider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                ) {
                    Text(
                        "Sem o acesso a todos os ficheiros, só fotos, vídeos e áudios destas pastas entram no backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRequestAllFilesAccess) { Text("Permitir") }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = BackupAccent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        modifier = Modifier.padding(start = 52.dp),
    )
}

@Composable
private fun UploadProgressCard(
    tasks: List<UploadTask>,
    runProgress: BackupRunProgress,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRetryFailed: () -> Unit,
    onOverwrite: (String) -> Unit,
    onSkip: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val active = tasks.count { it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued }
    val done = tasks.count { it.state == UploadTask.State.Completed }
    val failed = tasks.count { it.state == UploadTask.State.Failed }
    val conflict = tasks.count { it.state == UploadTask.State.Conflict }
    val total = tasks.size
    val totalBytes = tasks.sumOf { it.sizeBytes }.coerceAtLeast(1L)
    val uploadedBytes = tasks.sumOf { (it.progress * it.sizeBytes).toLong() }
    // A lista é podada por lote — os bytes/contagens dela recuam. Com o worker
    // a correr usamos o progresso real da execução.
    val overall = if (runProgress.total > 0)
        (runProgress.done.toFloat() / runProgress.total).coerceIn(0f, 1f)
    else
        (uploadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BackupAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Backup, contentDescription = null, tint = BackupAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Enquanto a execução decorre (runProgress.total > 0) o
                    // título fica fixo em "A enviar…": entre lotes o nº de
                    // tarefas activas cai momentaneamente a zero e o texto
                    // saltava para "Concluído" e de volta — flicker.
                    val title = when {
                        runProgress.total > 0 ->
                            "A enviar… (${runProgress.done} de ${runProgress.total})"
                        active > 0 -> "A enviar… ($done de $total)"
                        failed > 0 -> "$failed falha(s)"
                        conflict > 0 -> "$conflict conflito(s)"
                        done == total && total > 0 -> "Concluído ($done)"
                        else -> "Em curso"
                    }
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatBytes(uploadedBytes)} de ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (active == 0 && runProgress.total == 0) {
                    TextButton(onClick = onDismiss) { Text("Limpar") }
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { overall },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = BackupAccent,
                trackColor = BackupAccent.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(14.dp))
            // Lista própria com scroll, altura limitada e keys estáveis (id).
            // Ordenação estável: em curso primeiro, depois conflitos/falhas e
            // por fim os concluídos — com animateItem() as linhas deslizam
            // para a nova posição em vez de saltar (era o "flickering").
            // (LazyColumn dentro de verticalScroll é válido porque a altura
            // está limitada pelo heightIn.)
            val ordered = remember(tasks) {
                tasks.sortedBy { task ->
                    when (task.state) {
                        UploadTask.State.Uploading -> 0
                        UploadTask.State.Queued -> 1
                        UploadTask.State.Conflict, UploadTask.State.Failed -> 2
                        else -> 3
                    }
                }
            }
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .lazyListScrollbar(listState, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ordered, key = { it.id }) { task ->
                    Box(modifier = Modifier.animateItem()) {
                        UploadTaskRow(
                            task = task,
                            onCancel = { onCancel(task.id) },
                            onRetry = { onRetry(task.id) },
                            onOverwrite = { onOverwrite(task.id) },
                            onSkip = { onSkip(task.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (failed > 0) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onRetryFailed,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tentar novamente os falhados")
                }
            }
        }
    }
}

@Composable
private fun UploadTaskRow(
    task: UploadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                task.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            when (task.state) {
                UploadTask.State.Uploading -> Text(
                    "${(task.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = BackupAccent,
                    fontWeight = FontWeight.SemiBold,
                )
                UploadTask.State.Completed -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = BackupAccent,
                    modifier = Modifier.size(16.dp),
                )
                UploadTask.State.Failed -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                UploadTask.State.Conflict -> Text(
                    "Conflito",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                UploadTask.State.Queued -> Text(
                    "Em espera",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UploadTask.State.Cancelled -> Icon(
                    Icons.Outlined.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        when (task.state) {
            UploadTask.State.Uploading -> {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = BackupAccent,
                    trackColor = BackupAccent.copy(alpha = 0.15f),
                )
            }
            UploadTask.State.Failed -> {
                task.errorMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row {
                    TextButton(onClick = onRetry) { Text("Tentar novamente") }
                    TextButton(onClick = onCancel) { Text("Cancelar") }
                }
            }
            UploadTask.State.Conflict -> {
                Row {
                    TextButton(onClick = onOverwrite) { Text("Substituir") }
                    TextButton(onClick = onSkip) { Text("Ignorar") }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun RescanCard(onReset: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Re-verificar a galeria", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Faz com que o backup volte a analisar todas as fotos e vídeos do telemóvel (não duplica — o servidor recusa ficheiros já enviados).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Voltar a verificar a galeria")
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Backup com erro",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BackupAccent),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tentar de novo", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Limpar") }
            }
        }
    }
}

// Barra de scroll fina para LazyColumn — o Compose não desenha scrollbars e,
// dentro de uma página que também faz scroll, a tabela parecia estática.
private fun Modifier.lazyListScrollbar(state: LazyListState, color: Color): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || info.totalItemsCount <= visible.size) return@drawWithContent
    val itemAvg = visible.sumOf { it.size } / visible.size.toFloat()
    val totalHeight = info.totalItemsCount * itemAvg
    if (totalHeight <= size.height) return@drawWithContent
    val barHeight = (size.height * (size.height / totalHeight)).coerceAtLeast(24.dp.toPx())
    val scrolled = state.firstVisibleItemIndex * itemAvg + state.firstVisibleItemScrollOffset
    val y = (scrolled / (totalHeight - size.height)).coerceIn(0f, 1f) * (size.height - barHeight)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - 3.dp.toPx(), y),
        size = Size(3.dp.toPx(), barHeight),
        cornerRadius = CornerRadius(1.5.dp.toPx()),
    )
}
