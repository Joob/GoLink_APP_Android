package co.golink.tester.ui.screens.backup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import co.golink.tester.ui.i18n.tr
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.backup.AutoBackupManager
import co.golink.tester.data.backup.AutoBackupState
import co.golink.tester.data.backup.BackupCollection
import co.golink.tester.data.backup.BackupFolder
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
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val loadingFolders by viewModel.loadingFolders.collectAsStateWithLifecycle()
    val allTasks by viewModel.uploadTasks.collectAsStateWithLifecycle()
    // Only show backup-tagged uploads here — user-initiated uploads belong in
    // the browser's global banner.
    val tasks = remember(allTasks) { allTasks.filter { it.mobileBackup } }

    // O backup envia sempre cifrado: sem passphrase criada não pode ser ativado.
    // "Criar passphrase" abre o mesmo diálogo do ecrã inicial (E2EGate), que fica
    // montado até fechar sozinho (mostra a chave de recuperação no fim); quando
    // a encriptação fica configurada, o backup é ativado.
    val needsE2ESetup by viewModel.needsE2ESetup.collectAsStateWithLifecycle()
    val e2eConfigured by viewModel.e2eConfigured.collectAsStateWithLifecycle()
    var showE2ESetup by remember { mutableStateOf(false) }
    var enableAfterSetup by remember { mutableStateOf(false) }
    LaunchedEffect(e2eConfigured, enableAfterSetup) {
        if (e2eConfigured && enableAfterSetup) {
            enableAfterSetup = false
            viewModel.enable()
        }
    }
    if (needsE2ESetup && !showE2ESetup) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissE2ESetup,
            title = { Text("Encriptação necessária".tr()) },
            text = {
                Text(
                    "O backup automático envia as tuas fotos e vídeos sempre cifrados. Cria primeiro a tua passphrase de encriptação.".tr(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissE2ESetup()
                    enableAfterSetup = true
                    showE2ESetup = true
                }) { Text("Criar passphrase".tr()) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissE2ESetup) { Text("Cancelar".tr()) } },
        )
    }
    if (showE2ESetup) co.golink.tester.ui.screens.encryption.E2EGate()
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

    // Sincroniza com o servidor enquanto o ecrã está visível: se a Web pausar o
    // backup, o cartão "Activar backup" reaparece de imediato.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.startServerStateSync()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> viewModel.stopServerStateSync()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopServerStateSync()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup automático".tr(), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr())
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
                BackupHeroCard(
                    allowCellular = state.allowCellular,
                    backedUpCount = state.backedUpCount,
                    lastBackupAt = state.lastBackupAt,
                    runState = runState,
                    runProgress = runProgress,
                    tasks = tasks,
                    onRunNow = viewModel::backupNow,
                    onDisable = viewModel::disable,
                )
                state.lastError?.let { err ->
                    ErrorCard(message = err, onDismiss = viewModel::dismissError, onRetry = viewModel::runNow)
                }
                if (tasks.isNotEmpty()) {
                    RecentUploadsSection(
                        tasks = tasks,
                        onCancel = viewModel::cancelTask,
                        onRetry = viewModel::retryTask,
                        onRetryFailed = viewModel::retryFailed,
                        onOverwrite = viewModel::overwriteTask,
                        onSkip = viewModel::skipTask,
                    )
                }
                WhenSection(
                    useCellular = state.allowCellular,
                    chargingOnly = state.chargingOnly,
                    onUseCellular = viewModel::setUseCellular,
                    onToggleCharging = viewModel::setChargingOnly,
                )
                ContentSourcesCard(
                    state = state,
                    folders = folders,
                    loadingFolders = loadingFolders,
                    onToggleInclude = { collection, enabled ->
                        when (collection) {
                            BackupCollection.IMAGES -> viewModel.setIncludeImages(enabled)
                            BackupCollection.VIDEOS -> viewModel.setIncludeVideos(enabled)
                            BackupCollection.AUDIOS -> {
                                viewModel.setIncludeAudios(enabled)
                                // Áudio precisa de permissão própria no Android 13+.
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
                                }
                            }
                            BackupCollection.DOCUMENTS -> {
                                viewModel.setIncludeDocuments(enabled)
                                if (enabled && !hasAllFiles) requestAllFilesAccess()
                            }
                            BackupCollection.DOWNLOADS -> {
                                viewModel.setIncludeDownloads(enabled)
                                if (enabled && !hasAllFiles) requestAllFilesAccess()
                            }
                        }
                    },
                    onExpand = viewModel::loadFolders,
                    onToggleFolder = viewModel::toggleFolder,
                    onToggleAllFolders = viewModel::setAllFolders,
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
                "Encripta e faz backup das tuas fotos e vídeos".tr(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            FeatureRow(
                icon = Icons.Outlined.Lock,
                title = "Protege as tuas memórias".tr(),
                description = "As fotos são encriptadas ponto-a-ponto, garantindo total privacidade.".tr(),
            )
            Spacer(Modifier.height(14.dp))
            FeatureRow(
                icon = Icons.Outlined.Refresh,
                title = "Backups automáticos".tr(),
                description = "Cópia regular por Wi-Fi mantendo a qualidade original.".tr(),
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
                Text("Activar backup".tr(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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

// ─── Redesign (mockup): cartão verde de estado, últimos enviados, quando e o
// que guardar, avançado. Secções com título fora do cartão, cartões claros. ───

private val HeroTop = Color(0xFF1F7A4D)
private val HeroBottom = Color(0xFF14532D)

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: String? = null, subtitle: String? = null) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

// Quadrado de seleção ao estilo do mockup (verde com ✓, ou contorno).
@Composable
private fun SquareCheck(checked: Boolean, onClick: () -> Unit, onDark: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    val fill = when {
        onDark && checked -> Color.White
        checked -> BackupAccent
        else -> Color.Transparent
    }
    val stroke = when {
        onDark -> Color.White
        checked -> BackupAccent
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, stroke, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = if (onDark) HeroBottom else Color.White, modifier = Modifier.size(17.dp))
    }
}

private fun lastCheckLabel(at: Long): String {
    if (at <= 0) return "—"
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date(at))
    val day = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val now = java.util.Calendar.getInstance()
    val sameYear = day.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
    val diff = now.get(java.util.Calendar.DAY_OF_YEAR) - day.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && diff == 0 -> "Hoje, %s".tr().format(time)
        sameYear && diff == 1 -> "Ontem, %s".tr().format(time)
        else -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(at))
    }
}

@Composable
private fun BackupHeroCard(
    allowCellular: Boolean,
    backedUpCount: Int,
    lastBackupAt: Long,
    runState: AutoBackupManager.RunState,
    runProgress: BackupRunProgress,
    tasks: List<UploadTask>,
    onRunNow: () -> Unit,
    onDisable: () -> Unit,
) {
    val activeCount = tasks.count { it.state == UploadTask.State.Uploading || it.state == UploadTask.State.Queued }
    val running = runState == AutoBackupManager.RunState.Running || activeCount > 0
    val overall = if (runProgress.total > 0) (runProgress.done.toFloat() / runProgress.total).coerceIn(0f, 1f) else 0f
    val title = when {
        running -> "A fazer backup…".tr()
        runState == AutoBackupManager.RunState.Waiting -> if (allowCellular) "À espera de rede".tr() else "À espera de Wi-Fi".tr()
        else -> "Tudo em dia".tr()
    }
    val subtitle = when {
        running && runProgress.total > 0 -> "A enviar %d de %d ficheiros".tr().format(runProgress.done, runProgress.total)
        running -> "A preparar a galeria…".tr()
        allowCellular -> "Fotos guardadas automaticamente sempre que tens rede".tr()
        else -> "Fotos guardadas automaticamente quando tens Wi-Fi".tr()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(HeroTop, HeroBottom)))
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (running) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    else Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(8.dp))
                // Desmarcar = desligar o backup automático.
                SquareCheck(checked = true, onClick = onDisable, onDark = true)
            }
            if (running && runProgress.total > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { overall },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat("$backedUpCount", "ficheiros guardados".tr(), Modifier.weight(1f))
                HeroStat(lastCheckLabel(lastBackupAt), "última verificação".tr(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onRunNow,
                enabled = runState != AutoBackupManager.RunState.Running,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = HeroBottom,
                    disabledContainerColor = Color.White.copy(alpha = 0.6f),
                    disabledContentColor = HeroBottom.copy(alpha = 0.7f),
                ),
                contentPadding = PaddingValues(vertical = 13.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (runState == AutoBackupManager.RunState.Running) "A enviar…".tr() else "Fazer backup agora".tr(),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
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

// "Últimos enviados": 3 linhas; "Ver histórico" abre a lista completa (com scroll).
@Composable
private fun RecentUploadsSection(
    tasks: List<UploadTask>,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRetryFailed: () -> Unit,
    onOverwrite: (String) -> Unit,
    onSkip: (String) -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    val ordered = remember(tasks) {
        tasks.sortedBy { t ->
            when (t.state) {
                UploadTask.State.Uploading -> 0
                UploadTask.State.Conflict, UploadTask.State.Failed -> 1
                UploadTask.State.Queued -> 2
                else -> 3
            }
        }
    }
    val done = tasks.filter { it.state == UploadTask.State.Completed }
    val failed = tasks.count { it.state == UploadTask.State.Failed }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(
            "Últimos enviados".tr(),
            trailing = "%d ficheiros · %s".tr().format(done.size, formatBytes(done.sumOf { it.sizeBytes })),
        )
        SectionCard {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (showAll) 360.dp else 230.dp)
                    .lazyListScrollbar(listState, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            ) {
                val shown = if (showAll) ordered else ordered.take(3)
                items(shown, key = { it.id }) { task ->
                    Column(modifier = Modifier.animateItem()) {
                        RecentRow(
                            task = task,
                            onCancel = { onCancel(task.id) },
                            onRetry = { onRetry(task.id) },
                            onOverwrite = { onOverwrite(task.id) },
                            onSkip = { onSkip(task.id) },
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), modifier = Modifier.padding(start = 64.dp))
                    }
                }
            }
            if (failed > 0) {
                TextButton(onClick = onRetryFailed, modifier = Modifier.fillMaxWidth()) {
                    Text("Tentar novamente os falhados".tr(), fontWeight = FontWeight.SemiBold)
                }
            }
            if (ordered.size > 3) {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAll) "Mostrar menos".tr() else "Ver histórico".tr(), color = BackupAccent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RecentRow(
    task: UploadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit,
) {
    val err = MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            co.golink.tester.ui.components.FileTypeIcon(task.name, null, Modifier.height(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(task.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val (sub, color) = when (task.state) {
                UploadTask.State.Uploading -> "A enviar · %d%%".tr().format((task.progress * 100).toInt()) to BackupAccent
                UploadTask.State.Queued -> "Em espera".tr() to MaterialTheme.colorScheme.onSurfaceVariant
                UploadTask.State.Completed -> "Enviado · %s".tr().format(formatBytes(task.sizeBytes)) to MaterialTheme.colorScheme.onSurfaceVariant
                UploadTask.State.Failed -> (task.errorMessage?.takeIf { it.isNotBlank() } ?: "Falhou".tr()) to err
                UploadTask.State.Conflict -> "Nome repetido no servidor".tr() to err
                UploadTask.State.Cancelled -> "Cancelado".tr() to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(sub, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.state == UploadTask.State.Uploading) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = BackupAccent,
                    trackColor = BackupAccent.copy(alpha = 0.15f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            when (task.state) {
                UploadTask.State.Failed -> Row {
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Tentar novamente".tr()) }
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Cancelar".tr()) }
                }
                UploadTask.State.Conflict -> Row {
                    TextButton(onClick = onOverwrite, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Substituir".tr()) }
                    TextButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Ignorar".tr()) }
                }
                else -> Unit
            }
        }
        if (task.state == UploadTask.State.Completed) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Check, contentDescription = null, tint = BackupAccent, modifier = Modifier.size(18.dp))
        }
    }
}

// "Quando fazer backup": rádio Wi-Fi / Wi-Fi e dados móveis + só a carregar.
@Composable
private fun WhenSection(
    useCellular: Boolean,
    chargingOnly: Boolean,
    onUseCellular: (Boolean) -> Unit,
    onToggleCharging: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Quando fazer backup".tr())
        SectionCard {
            RadioRow("Só com Wi-Fi".tr(), "Não gasta dados móveis".tr(), selected = !useCellular) { onUseCellular(false) }
            Divider()
            RadioRow("Wi-Fi e dados móveis".tr(), "Pode gastar o teu tarifário".tr(), selected = useCellular) { onUseCellular(true) }
            Divider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCharging(!chargingOnly) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Só quando estiver a carregar".tr(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("Poupa bateria".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SquareCheck(checked = chargingOnly, onClick = { onToggleCharging(!chargingOnly) })
            }
        }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) BackupAccent.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) BackupAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(BackupAccent))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class CollectionSpec(
    val collection: BackupCollection,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val included: Boolean,
)

@Composable
private fun ContentSourcesCard(
    state: AutoBackupState,
    folders: Map<BackupCollection, List<BackupFolder>>,
    loadingFolders: Set<BackupCollection>,
    onToggleInclude: (BackupCollection, Boolean) -> Unit,
    onExpand: (BackupCollection) -> Unit,
    onToggleFolder: (BackupCollection, String, Boolean) -> Unit,
    onToggleAllFolders: (BackupCollection, Boolean) -> Unit,
    allFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
) {
    val specs = listOf(
        CollectionSpec(BackupCollection.IMAGES, Icons.Outlined.Image, "Fotos".tr(), "Câmara, screenshots e outras imagens".tr(), state.includeImages),
        CollectionSpec(BackupCollection.VIDEOS, Icons.Outlined.Videocam, "Vídeos".tr(), "Ocupam mais espaço e demoram mais".tr(), state.includeVideos),
        CollectionSpec(BackupCollection.AUDIOS, Icons.Outlined.MusicNote, "Áudio".tr(), "Música e gravações".tr(), state.includeAudios),
        CollectionSpec(BackupCollection.DOCUMENTS, Icons.Outlined.Description, "Documentos".tr(), "Pasta Documentos".tr(), state.includeDocuments),
        CollectionSpec(BackupCollection.DOWNLOADS, Icons.Outlined.Download, "Transferências".tr(), "Pasta Download".tr(), state.includeDownloads),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("O que guardar".tr(), subtitle = "Liga uma categoria e escolhe as pastas do telemóvel".tr())
        SectionCard {
            specs.forEachIndexed { i, s ->
                if (i > 0) Divider()
                CollectionSection(
                    icon = s.icon,
                    title = s.title,
                    description = s.description,
                    included = s.included,
                    folders = folders[s.collection],
                    loading = s.collection in loadingFolders,
                    selectedNames = state.selectedFolders[s.collection].orEmpty(),
                    onToggleInclude = { onToggleInclude(s.collection, it) },
                    onExpand = { onExpand(s.collection) },
                    onToggleFolder = { name, sel -> onToggleFolder(s.collection, name, sel) },
                    onToggleAll = { onToggleAllFolders(s.collection, it) },
                )
            }
            if ((state.includeDocuments || state.includeDownloads) && !allFilesAccess) {
                Divider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                ) {
                    Text(
                        "Sem o acesso a todos os ficheiros, só fotos, vídeos e áudios destas pastas entram no backup.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRequestAllFilesAccess) { Text("Permitir".tr()) }
                }
            }
        }
    }
}

// Categoria: ícone, título, "5 de 5 pastas · 1315 ficheiros", ⌃/⌄ e quadrado.
@Composable
private fun CollectionSection(
    icon: ImageVector,
    title: String,
    description: String,
    included: Boolean,
    folders: List<BackupFolder>?,
    loading: Boolean,
    selectedNames: Set<String>,
    onToggleInclude: (Boolean) -> Unit,
    onExpand: () -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // Categoria ligada: lê as pastas logo, para mostrar as contagens.
    LaunchedEffect(included) { if (included) onExpand() else expanded = false }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!included) {
                        onToggleInclude(true)
                        expanded = true
                    } else {
                        expanded = !expanded
                        if (expanded) onExpand()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (included) BackupAccent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val selected = folders?.filter { it.name in selectedNames }
                val (subtitle, warn) = when {
                    !included -> description to false
                    selectedNames.isEmpty() -> "Nenhuma pasta escolhida — toca para selecionar".tr() to true
                    folders != null && selected != null ->
                        "%d de %d pastas · %d ficheiros".tr().format(selected.size, folders.size, selected.sumOf { it.count }) to false
                    else -> "%d pasta(s) no backup".tr().format(selectedNames.size) to false
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warn) MaterialTheme.colorScheme.error else if (included) BackupAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (included) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Recolher".tr() else "Expandir".tr(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            SquareCheck(checked = included, onClick = { onToggleInclude(!included); if (!included) expanded = true })
        }
        AnimatedVisibility(visible = expanded && included) {
            FolderChecklist(
                folders = folders,
                loading = loading,
                selectedNames = selectedNames,
                onToggleFolder = onToggleFolder,
                onToggleAll = onToggleAll,
            )
        }
    }
}

@Composable
private fun FolderChecklist(
    folders: List<BackupFolder>?,
    loading: Boolean,
    selectedNames: Set<String>,
    onToggleFolder: (String, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(start = 54.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
    ) {
        when {
            loading && folders == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("A ler pastas…".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            folders.isNullOrEmpty() -> Text(
                "Sem pastas para esta categoria.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            else -> {
                val allSelected = folders.all { it.name in selectedNames }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Pastas no telemóvel".tr(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onToggleAll(!allSelected) }) {
                        Text(if (allSelected) "Desmarcar todas".tr() else "Marcar todas".tr(), color = BackupAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
                folders.forEach { folder ->
                    val checked = folder.name in selectedNames
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleFolder(folder.name, !checked) }
                            .padding(vertical = 8.dp),
                    ) {
                        SquareCheck(checked = checked, onClick = { onToggleFolder(folder.name, !checked) })
                        Spacer(Modifier.width(14.dp))
                        Text(folder.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${folder.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        modifier = Modifier.padding(start = 54.dp),
    )
}

// "Avançado": procurar de novo na galeria.
@Composable
private fun RescanCard(onReset: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Avançado".tr())
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onReset).padding(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Procurar de novo na galeria".tr(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Verifica outra vez as fotos e vídeos. Os que já estão guardados não são enviados de novo.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = BackupAccent)
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
                    "Backup com erro".tr(),
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
                    Text("Tentar de novo".tr(), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Limpar".tr()) }
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
