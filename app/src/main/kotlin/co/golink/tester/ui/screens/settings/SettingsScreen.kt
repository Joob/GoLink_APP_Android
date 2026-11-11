package co.golink.tester.ui.screens.settings

import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Language
import co.golink.tester.domain.browse.BrowseItem
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import co.golink.tester.data.backup.AutoBackupManager
import co.golink.tester.data.backup.AutoBackupState
import co.golink.tester.data.backup.BackupCollection
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShieldMoon
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.outlined.Language
import co.golink.tester.data.LogEntry
import co.golink.tester.data.LogSanitizer
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.i18n.trPlural
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.settings.AccessToken
import co.golink.tester.ui.common.copySecretToClipboard
import co.golink.tester.domain.settings.SecurityEventData
import co.golink.tester.domain.settings.SessionItem
import co.golink.tester.domain.settings.StorageUsage
import co.golink.tester.domain.settings.TransactionItem
import co.golink.tester.domain.user.User
import coil.compose.AsyncImage

/** Política de passwords do servidor (Fortify\Rules\Password) e do registo. */
private const val MIN_PASSWORD_LENGTH = 8

private sealed interface SettingsRoute {
    data object Menu : SettingsRoute
    data object Profile : SettingsRoute
    data object Password : SettingsRoute
    data object Email : SettingsRoute
    data object ApiTokens : SettingsRoute
    data object Encryption : SettingsRoute
    data object Storage : SettingsRoute
    data object SecurityActivity : SettingsRoute
    data object Sessions : SettingsRoute
    data object Billing : SettingsRoute
    data object AppSecurity : SettingsRoute
    data object DeleteAccount : SettingsRoute
}

private val ALL_SETTINGS_ROUTES: List<SettingsRoute> = listOf(
    SettingsRoute.Menu, SettingsRoute.Profile, SettingsRoute.Password, SettingsRoute.Email,
    SettingsRoute.ApiTokens, SettingsRoute.Encryption, SettingsRoute.Storage, SettingsRoute.SecurityActivity,
    SettingsRoute.Sessions, SettingsRoute.Billing, SettingsRoute.AppSecurity, SettingsRoute.DeleteAccount,
)

/** O painel aberto sobrevive à ida ao visualizador/Atividade e à morte do processo. */
private val SettingsRouteSaver = androidx.compose.runtime.saveable.Saver<SettingsRoute, Int>(
    save = { ALL_SETTINGS_ROUTES.indexOf(it) },
    restore = { ALL_SETTINGS_ROUTES.getOrNull(it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    initialRoute: String? = null,
    onOpenAutoBackup: () -> Unit = {},
    onOpenSecurityActivity: () -> Unit = {},
    onOpenSupport: (admin: Boolean) -> Unit = {},
    // "Libertar espaço" (Armazenamento): volta aos ficheiros e abre o Lixo.
    onOpenTrash: () -> Unit = {},
    // Abrir um ficheiro no visualizador (maiores ficheiros do Armazenamento).
    onOpenFile: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val isUpdatingAvatar by viewModel.isUpdatingAvatar.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backupState by viewModel.autoBackupState.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val e2eUnlocked by viewModel.e2eUnlocked.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    // Painel aberto diretamente (gaveta / rodapé de armazenamento): o "voltar"
    // desse painel sai das Definições em vez de passar pelo menu.
    val entryRoute = remember(initialRoute) {
        when (initialRoute) {
            "billing" -> SettingsRoute.Billing
            "profile" -> SettingsRoute.Profile
            "storage" -> SettingsRoute.Storage
            else -> SettingsRoute.Menu
        }
    }
    var route by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = SettingsRouteSaver) {
        mutableStateOf<SettingsRoute>(entryRoute)
    }

    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHost.showSnackbar(it); viewModel.consumeToast() }
    }

    LaunchedEffect(route) {
        if (route == SettingsRoute.Billing && state.billing == null && !state.isLoadingBilling) {
            viewModel.loadBilling()
        }
        if (route == SettingsRoute.SecurityActivity && state.securityEvents.isEmpty() && !state.isLoadingSecurityEvents) {
            viewModel.loadSecurityEvents()
        }
        // Menu (contagem de dispositivos) e Sessões; os tokens só no painel deles.
        if ((route == SettingsRoute.Menu || route == SettingsRoute.Sessions) && state.sessions.isEmpty() && !state.isLoadingSessions) {
            viewModel.loadSessions()
        }
        if (route == SettingsRoute.ApiTokens && state.tokens.isEmpty() && !state.isLoadingTokens) {
            viewModel.loadTokens()
        }
    }

    // Sem compras na app (política de pagamentos da Play): o plano compra-se
    // no site; aqui só se mostra o plano atual e a utilização.

    BackHandler(enabled = route != SettingsRoute.Menu && route != entryRoute) {
        route = SettingsRoute.Menu
    }

    val title = when (route) {
        SettingsRoute.Menu -> "Definições".tr()
        SettingsRoute.Profile -> "Perfil".tr()
        SettingsRoute.Password -> "Password e 2FA".tr()
        SettingsRoute.Email -> "E-mail".tr()
        SettingsRoute.ApiTokens -> "Tokens API".tr()
        SettingsRoute.Encryption -> "Encriptação".tr()
        SettingsRoute.Storage -> "Armazenamento".tr()
        SettingsRoute.Sessions -> "Sessões ativas".tr()
        SettingsRoute.SecurityActivity -> "Atividade de segurança".tr()
        SettingsRoute.Billing -> "Plano e pagamentos".tr()
        SettingsRoute.AppSecurity -> "Bloqueio da app".tr()
        SettingsRoute.DeleteAccount -> "Apagar conta".tr()
    }

    Scaffold(
        containerColor = settingsScreenBackground(),
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsScreenBackground(),
                    scrolledContainerColor = settingsScreenBackground(),
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (route == SettingsRoute.Menu || route == entryRoute) onBack() else route = SettingsRoute.Menu
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr())
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Entrar num submenu desliza da direita; voltar ao menu, da esquerda.
            androidx.compose.animation.AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val forward = targetState != SettingsRoute.Menu
                    val dir = if (forward) 1 else -1
                    (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it * dir / 4 } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)))
                        .togetherWith(
                            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(260)) { -it * dir / 4 } +
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)),
                        )
                },
                label = "settings-route",
            ) { current ->
            when (current) {
                SettingsRoute.Menu -> MenuPane(
                    user = user,
                    storage = state.storage,
                    activeSessions = state.sessions.size,
                    twoFactorEnabled = state.twoFactorEnabled,
                    backup = backupState,
                    onNavigate = { route = it },
                    onOpenAutoBackup = onOpenAutoBackup,
                    onOpenSupport = onOpenSupport,
                    onLogout = viewModel::logout,
                    onUpdateAvatar = viewModel::updateAvatar,
                )
                SettingsRoute.Profile -> ProfilePane(
                    user = user,
                    isUpdating = state.isUpdatingProfile,
                    isUpdatingAvatar = isUpdatingAvatar,
                    onUpdateFields = viewModel::updateProfileFields,
                    onUpdateAvatar = viewModel::updateAvatar,
                    onRetry = { viewModel.refreshUser() },
                    onOpenEmail = { route = SettingsRoute.Email },
                )
                SettingsRoute.Password -> PasswordPane(
                    isLoading = state.isUpdatingPassword,
                    changedAt = state.passwordChangedAt,
                    onSubmit = viewModel::updatePassword,
                    hasPasskeys = user?.hasPasskeys == true,
                    socialiteAccount = user?.socialiteAccount == true,
                    twoFactorEnabled = state.twoFactorEnabled,
                    backendUrl = backendUrl,
                    onRefreshUser = viewModel::refreshUser,
                )
                SettingsRoute.Email -> EmailChangeSection(
                    currentEmail = user?.email.orEmpty(),
                    hasPasskeys = user?.hasPasskeys == true,
                    state = state,
                    onRequest = viewModel::requestEmailChange,
                    onRestartForResend = viewModel::restartEmailChangeForResend,
                    onConfirm = viewModel::confirmEmailChange,
                    onCancel = viewModel::cancelEmailChange,
                )
                SettingsRoute.ApiTokens -> ApiTokensPane(
                    tokens = state.tokens,
                    isLoadingTokens = state.isLoadingTokens,
                    newTokenPlaintext = state.newTokenPlaintext,
                    isCreatingToken = state.isCreatingToken,
                    createTokenError = state.createTokenError,
                    onClearCreateTokenError = viewModel::clearCreateTokenError,
                    onCreateToken = viewModel::createToken,
                    onDeleteToken = viewModel::deleteToken,
                    onConsumeNewToken = viewModel::consumeNewToken,
                )
                SettingsRoute.Encryption -> EncryptionPane(
                    e2eUnlocked = e2eUnlocked,
                    e2eFingerprint = if (e2eUnlocked) viewModel.e2eFingerprint() else null,
                    isChanging = state.isChangingPassphrase,
                    onVerifyCurrent = viewModel::verifyCurrentPassphrase,
                    requestCode = viewModel::requestPassphraseCode,
                    confirmCode = viewModel::confirmPassphraseCode,
                    onChangePassphrase = viewModel::changeEncryptionPassphrase,
                )
                SettingsRoute.Storage -> StoragePane(
                    storage = state.storage,
                    insights = state.storageInsights,
                    isLoading = state.isLoadingStorage,
                    onRefresh = { viewModel.loadStorage(); viewModel.loadStorageInsights() },
                    onFreeSpace = onOpenTrash,
                    onOpenBackups = onOpenAutoBackup,
                    onOpenFile = { f -> viewModel.prepareViewer(f); onOpenFile(f.id) },
                )
                SettingsRoute.Sessions -> SessionsPane(
                    sessions = state.sessions,
                    isLoading = state.isLoadingSessions,
                    onRevoke = viewModel::revokeSession,
                    onRevokeAll = viewModel::revokeAllSessions,
                    onRefresh = viewModel::loadSessions,
                )
                SettingsRoute.SecurityActivity -> SecurityActivityPane(
                    events = state.securityEvents,
                    isLoading = state.isLoadingSecurityEvents,
                    hasMore = state.hasMoreSecurityEvents,
                    onLoadMore = viewModel::loadMoreSecurityEvents,
                )
                SettingsRoute.Billing -> BillingPane(
                    user = user,
                    summary = state.billing,
                    plans = state.plans,
                    isLoading = state.isLoadingBilling,
                    invoices = state.invoices,
                    hasMoreInvoices = state.hasMoreInvoices,
                    isLoadingInvoices = state.isLoadingInvoices,
                    downloadingInvoice = state.downloadingInvoice,
                    onLoadMoreInvoices = { viewModel.loadInvoices() },
                    onDownload = viewModel::downloadInvoice,
                    isSavingDetails = state.isUpdatingProfile,
                    onSaveDetails = viewModel::saveBillingDetails,
                )
                SettingsRoute.DeleteAccount -> DeleteAccountPane(userEmail = user?.email, scheduledFor = user?.deletionScheduledFor)
                SettingsRoute.AppSecurity -> AppSecurityPane(
                    user = user,
                    biometricEnabled = state.biometricEnabled,
                    pinEnabled = state.pinEnabled,
                    hasPinSet = viewModel.hasPinSet,
                    onBiometricToggle = viewModel::setBiometricEnabled,
                    onEnablePin = viewModel::enablePin,
                    onChangePin = viewModel::changePin,
                    onDisableAppLock = viewModel::disableAppLock,
                    lockDelayMs = state.lockDelayMs,
                    onSetLockDelay = viewModel::setLockDelay,
                    hideInRecents = state.hideInRecents,
                    onSetHideInRecents = viewModel::setHideInRecents,
                    onClearCache = viewModel::clearCache,
                    cacheBytes = state.cacheBytes,
                    onLoadCacheSize = viewModel::loadCacheSize,
                    onOpenSecurityActivity = onOpenSecurityActivity,
                    logEntries = state.logEntries,
                    onRefreshLog = viewModel::refreshLog,
                    onClearLog = viewModel::clearLog,
                )
            }
            }

            // Overlay de saída: revoke da sessão atual (→ logout automático via
            // RootGate) ou revoke de todas as outras (fica logado no fim).
            state.logoutOverlay?.let { msg ->
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { },
                    properties = androidx.compose.ui.window.DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                    ),
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = co.golink.tester.ui.theme.BrandGreen)
                            Spacer(Modifier.height(16.dp))
                            Text(msg, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPane(
    user: User?,
    storage: co.golink.tester.domain.settings.StorageUsage?,
    activeSessions: Int,
    twoFactorEnabled: Boolean,
    backup: AutoBackupState,
    onNavigate: (SettingsRoute) -> Unit,
    onOpenAutoBackup: () -> Unit,
    onOpenSupport: (admin: Boolean) -> Unit,
    onLogout: () -> Unit,
    onUpdateAvatar: (ByteArray) -> Unit,
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var dangerZoneExpanded by remember { mutableStateOf(false) }

    // Avatar: escolher imagem → recortar no popup → confirmar → upload.
    var pickedImage by remember { mutableStateOf<android.net.Uri?>(null) }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) pickedImage = uri }

    pickedImage?.let { uri ->
        co.golink.tester.ui.components.dialogs.AvatarCropDialog(
            imageUri = uri,
            onCancel = { pickedImage = null },
            onConfirm = { jpeg ->
                pickedImage = null
                onUpdateAvatar(jpeg)
            },
        )
    }

    if (showLogoutConfirm) {
        co.golink.tester.ui.components.dialogs.ConfirmDialog(
            title = "Sair da Conta?".tr(),
            message = "Tens a certeza que queres sair da tua conta?".tr(),
            confirmText = "Sair".tr(),
            destructive = true,
            onDismiss = { showLogoutConfirm = false },
            onConfirm = onLogout,
        )
    }

    if (showDeleteAccountConfirm) {
        co.golink.tester.ui.components.dialogs.ConfirmDialog(
            title = "Apagar conta permanentemente?".tr(),
            message = "Todos os teus ficheiros, pastas e dados encriptados serão eliminados a 100%. Esta ação não pode ser desfeita.".tr(),
            confirmText = "Continuar".tr(),
            destructive = true,
            onDismiss = { showDeleteAccountConfirm = false },
            onConfirm = {
                showDeleteAccountConfirm = false
                onNavigate(SettingsRoute.DeleteAccount)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        user?.let { u ->
            // Cartão de perfil (mockup): avatar, nome, e-mail, › e o espaço usado
            // numa caixa interna com o plano e "Melhorar".
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = settingsCardColor(),
                border = settingsCardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
            Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(SettingsRoute.Profile) }
                    .padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            ) {
                // Clicar no avatar → escolher/recortar/enviar nova imagem.
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable {
                            avatarPicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    UserAvatar(u, textStyle = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        u.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        u.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SettingsChevron()
            }
            // Espaço usado, com a cor do nível (verde / âmbar / vermelho).
            storage?.let { st ->
                val barColor = when (st.level) {
                    co.golink.tester.domain.settings.StorageLevel.DANGER -> SettingsColors.Red
                    co.golink.tester.domain.settings.StorageLevel.WARNING -> SettingsColors.Amber
                    else -> SettingsAccent
                }
                val freePlan = u.planName.isNullOrBlank()
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(settingsInsetColor())
                        .clickable { onNavigate(SettingsRoute.Storage) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)) { append(st.used) }
                                withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" " + "de".tr() + " ") }
                                append(st.capacity)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${st.percentage.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = barColor,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (st.percentage / 100f).coerceIn(0f, 1f) },
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SettingsBadge(if (freePlan) "Plano gratuito".tr() else u.planName.orEmpty(), SettingsColors.Purple)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            }
            }
        }

        Spacer(Modifier.height(12.dp))

        AutoBackupFeaturedRow(backup = backup, onClick = onOpenAutoBackup)

        Spacer(Modifier.height(20.dp))

        SettingsGroup(title = "Conta".tr()) {
            SettingsNavRow(Icons.Outlined.Person, SettingsColors.Blue, "Perfil".tr(), "Nome, fotografia e fuso horário".tr()) { onNavigate(SettingsRoute.Profile) }
            SettingsNavRow(Icons.Outlined.Email, SettingsColors.Blue, "E-mail".tr(), user?.email ?: "Alterar o e-mail da conta".tr(), showDivider = false) { onNavigate(SettingsRoute.Email) }
        }

        Spacer(Modifier.height(20.dp))

        SettingsGroup(title = "Segurança".tr()) {
            val sec = SettingsColors.Amber
            SettingsNavRow(
                Icons.Outlined.Lock, sec, "Password e 2FA".tr(), "Password e verificação em dois passos".tr(),
                badge = if (twoFactorEnabled) {
                    { SettingsBadge("2FA ativa".tr(), SettingsAccent) }
                } else {
                    { SettingsBadge("2FA desligada".tr(), SettingsColors.Amber) }
                },
            ) { onNavigate(SettingsRoute.Password) }
            SettingsNavRow(Icons.Outlined.VpnKey, sec, "Encriptação".tr(), "Frase-passe e impressão digital da chave".tr()) { onNavigate(SettingsRoute.Encryption) }
            SettingsNavRow(
                Icons.Outlined.Devices, sec, "Sessões ativas".tr(), "Dispositivos com sessão iniciada".tr(),
                badge = if (activeSessions > 0) { { SettingsBadge(activeSessions.toString(), SettingsColors.Amber, round = true) } } else null,
            ) { onNavigate(SettingsRoute.Sessions) }
            SettingsNavRow(Icons.Outlined.Shield, sec, "Atividade de segurança".tr(), "Inícios de sessão e alterações".tr()) { onNavigate(SettingsRoute.SecurityActivity) }
            SettingsNavRow(Icons.Outlined.Fingerprint, sec, "Bloqueio da app".tr(), "Impressão digital e PIN".tr()) { onNavigate(SettingsRoute.AppSecurity) }
            SettingsNavRow(Icons.Outlined.Code, sec, "Tokens API".tr(), "Chaves de acesso pessoal".tr(), showDivider = false) { onNavigate(SettingsRoute.ApiTokens) }
        }

        Spacer(Modifier.height(20.dp))

        SettingsGroup(title = "Plano".tr()) {
            val hasSupport = user?.role != "admin"
            // Badges iguais à web: espaço ≥ 80 % (âmbar; vermelho ≥ 95) e pagamento em atraso.
            val pct = storage?.percentage?.toInt() ?: 0
            SettingsNavRow(
                Icons.Outlined.CloudQueue, SettingsColors.Teal, "Armazenamento".tr(), "Espaço usado e o que ocupa mais".tr(),
                badge = if (pct >= 80) { { SettingsBadge("$pct%", if (pct >= 95) SettingsColors.Red else SettingsColors.Amber) } } else null,
            ) { onNavigate(SettingsRoute.Storage) }
            val overdue = user?.isPastDue == true || user?.isPaymentSuspended == true
            SettingsNavRow(
                Icons.Outlined.CreditCard, SettingsColors.Purple, "Plano e pagamentos".tr(), "Plano, cartões e faturas".tr(),
                trailing = user?.planName?.takeIf { it.isNotBlank() && !overdue },
                badge = if (overdue) { { SettingsBadge("Em atraso".tr(), SettingsColors.Red) } } else null,
                showDivider = hasSupport,
            ) { onNavigate(SettingsRoute.Billing) }
            // Como na web: o admin não abre tickets. A Administração e o Support Desk
            // vivem agora na gaveta (toggle Drive | Admin / Support).
            if (hasSupport) {
                SettingsNavRow(Icons.Outlined.HelpOutline, SettingsColors.Pink, "Ajuda e suporte".tr(), "Perguntas frequentes e contacto".tr(), showDivider = false) { onOpenSupport(false) }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Terminar sessão: linha vermelha num cartão próprio (mockup).
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = settingsCardColor(),
            border = settingsCardBorder(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutConfirm = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                SettingsIconTile(Icons.Filled.Logout, SettingsColors.Red)
                Spacer(Modifier.width(14.dp))
                Text(
                    "Terminar sessão".tr(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SettingsColors.Red,
                )
            }
        }

        // Zona de perigo: escondida para admins e, para os restantes, atrás de um
        // link discreto que é preciso abrir antes de ver o botão de eliminação.
        if (user?.role != "admin") {
            Spacer(Modifier.height(40.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = { dangerZoneExpanded = !dangerZoneExpanded },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
            ) {
                Text("Zona de perigo".tr(), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (dangerZoneExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }

            AnimatedVisibility(visible = dangerZoneExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Esta ação é permanente e não pode ser revertida.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                    )
                    // Eliminação permanente da conta (fluxo igual à web).
                    SettingsOutlinedButton(
                        text = "Apagar conta permanentemente".tr(),
                        onClick = { showDeleteAccountConfirm = true },
                        destructive = true,
                        icon = Icons.Outlined.Delete,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "GoLink · v${co.golink.tester.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        modifier = Modifier.padding(start = 62.dp),
    )
}

@Composable
private fun AutoBackupFeaturedRow(backup: AutoBackupState, onClick: () -> Unit) {
    val on = backup.enabled
    // Só conta o que é mesmo enviado (a mesma regra do worker).
    val kinds = backup.activeCollections()
    val what = when (kinds) {
        emptySet<BackupCollection>() -> "nenhuma pasta escolhida".tr()
        setOf(BackupCollection.IMAGES) -> "fotos deste telemóvel".tr()
        setOf(BackupCollection.VIDEOS) -> "vídeos deste telemóvel".tr()
        setOf(BackupCollection.IMAGES, BackupCollection.VIDEOS) -> "fotos e vídeos deste telemóvel".tr()
        else -> "ficheiros deste telemóvel".tr()
    }
    val summary = when {
        !on && backup.lastError == AutoBackupManager.ACCOUNT_PAUSED_ERROR -> "Em pausa na Web · toca para retomar".tr()
        !on -> "Desativado · toca para configurar".tr()
        backup.devicePaused -> "Em pausa na Web · toca para retomar".tr()
        backup.needsAttention -> "Ativo · requer atenção".tr()
        else -> "Ativo · %s".tr().format(what)
    }
    // Ligado mas parado (pausa, atenção, nada escolhido) → subtítulo âmbar.
    val healthy = on && !backup.devicePaused && !backup.needsAttention && kinds.isNotEmpty()
    val subtitle = if (healthy && backup.lastBackupAt > 0L) {
        "Ativo · último backup %s".tr().format(
            co.golink.tester.ui.common.relativeAgo(java.time.Instant.ofEpochMilli(backup.lastBackupAt).toString()),
        )
    } else summary
    // Cartão (mockup): mosaico verde, estado e interruptor (abre o ecrã do backup,
    // onde estão as permissões e as pastas).
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = settingsCardColor(),
        border = settingsCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            SettingsIconTile(Icons.Outlined.CloudUpload, SettingsAccent)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Backup automático".tr(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (on && !healthy) SettingsColors.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            SettingsSwitch(checked = on, onCheckedChange = { onClick() })
        }
    }
}

/** Avatar redondo: fotografia ou iniciais em branco sobre escuro. */
@Composable
private fun UserAvatar(u: User, textStyle: androidx.compose.ui.text.TextStyle) {
    if (u.avatar != null) {
        AsyncImage(
            model = u.avatar,
            contentDescription = "Alterar imagem".tr(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFF14202B)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                u.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "?" },
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) = SettingsSectionTitle(text)

/** "hoje, 14:36" / "ontem, 09:10" / "12 out, 18:02" (data ISO do servidor). */
private fun friendlyWhen(iso: String): String {
    val t = co.golink.tester.ui.common.parseIsoLocal(iso) ?: return iso
    val time = t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    val today = java.time.LocalDate.now()
    return when (t.toLocalDate()) {
        today -> "hoje, %s".tr().format(time)
        today.minusDays(1) -> "ontem, %s".tr().format(time)
        else -> co.golink.tester.ui.common.formatDayTime(iso)
    }
}

/** Título de secção dos painéis (cinzento, fora do cartão) com ação opcional à direita. */
@Composable
internal fun PaneSection(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = SettingsAccent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Cartão branco dos painéis (padding interno opcional). */
@Composable
internal fun PaneCard(padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = settingsCardColor(),
        border = settingsCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = if (padded) Modifier.padding(16.dp) else Modifier,
            verticalArrangement = if (padded) Arrangement.spacedBy(12.dp) else Arrangement.Top,
            content = content,
        )
    }
}

/** Painel com formulário: conteúdo com scroll e, opcionalmente, botão fixo em baixo. */
@Composable
private fun PaneFormColumn(
    bottomBar: (@Composable ColumnScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
            Spacer(Modifier.height(16.dp))
        }
        if (bottomBar != null) SettingsBottomBar(bottomBar)
    }
}

@Composable
internal fun PaneColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProfilePane(
    user: User?,
    isUpdating: Boolean,
    isUpdatingAvatar: Boolean,
    onUpdateFields: (Map<String, String>) -> Unit,
    onUpdateAvatar: (ByteArray) -> Unit,
    onRetry: () -> Unit,
    onOpenEmail: () -> Unit,
) {
    if (user == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.TextButton(onClick = onRetry) { Text("Tentar de novo".tr()) }
        }
        return
    }

    // Cada campo volta ao valor do servidor quando este muda (após guardar ou alteração na web).
    var firstName by remember(user.id, user.firstName) { mutableStateOf(user.firstName.orEmpty()) }
    var lastName by remember(user.id, user.lastName) { mutableStateOf(user.lastName.orEmpty()) }
    // Telefone: indicativo + número; só se recompõe ("+351 912…") depois de mexer,
    // para um número antigo sem indicativo não contar como alteração.
    val parsedPhone = remember(user.id, user.phoneNumber) { splitPhone(user.phoneNumber.orEmpty()) }
    var dialIso by remember(user.id, user.phoneNumber) { mutableStateOf(parsedPhone.first) }
    var phoneLocal by remember(user.id, user.phoneNumber) { mutableStateOf(parsedPhone.second) }
    var phoneTouched by remember(user.id, user.phoneNumber) { mutableStateOf(false) }
    val phoneNumber = if (!phoneTouched) user.phoneNumber.orEmpty() else joinPhone(dialIso, phoneLocal)
    var timezone by remember(user.id, user.timezone) { mutableStateOf(user.timezone.orEmpty()) }

    // Só os campos alterados vão ao servidor; sem alterações o botão fica inativo.
    // Apelido e telefone podem ficar vazios; o nome não. Fuso compara-se pelo número ("1" == "1.0").
    val changes = listOf(
        "first_name" to (firstName to user.firstName.orEmpty()),
        "last_name" to (lastName to user.lastName.orEmpty()),
        "timezone" to (timezone to user.timezone.orEmpty()),
        "phone_number" to (phoneNumber to user.phoneNumber.orEmpty()),
    ).filter { (field, v) ->
        val (new, old) = v.first.trim() to v.second.trim()
        val changed = if (field == "timezone") {
            val (n, o) = new.toDoubleOrNull() to old.toDoubleOrNull()
            if (n != null && o != null) n != o else new != old
        } else new != old
        changed && (field != "first_name" || new.isNotEmpty())
    }.associate { (field, v) -> field to v.first.trim() }
    val dirty = changes.isNotEmpty()

    // Fotografia: escolher → recortar → enviar (igual ao menu).
    var pickedImage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) pickedImage = uri }
    pickedImage?.let { uri ->
        co.golink.tester.ui.components.dialogs.AvatarCropDialog(
            imageUri = uri,
            onCancel = { pickedImage = null },
            onConfirm = { jpeg -> pickedImage = null; onUpdateAvatar(jpeg) },
        )
    }
    val pickAvatar = {
        avatarPicker.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
    }

    Column(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
            Box(modifier = Modifier.size(100.dp)) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(CircleShape).clickable(enabled = !isUpdatingAvatar, onClick = pickAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    UserAvatar(user, textStyle = MaterialTheme.typography.headlineMedium)
                    if (isUpdatingAvatar) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Color.White)
                        }
                    }
                }
                // Botão da câmara (mockup): verde, com aro da cor do fundo.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // Alvo de 48dp sem mexer no círculo de 34dp (o offset compensa a margem).
                        .offset(7.dp, 7.dp)
                        .minimumInteractiveComponentSize()
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(settingsScreenBackground())
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(SettingsAccent)
                        .clickable(enabled = !isUpdatingAvatar, role = Role.Button, onClick = pickAvatar),
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Mudar fotografia".tr(), tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(user.email, user.planName?.takeIf { it.isNotBlank() } ?: "Plano gratuito".tr()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        PaneCard {
            SettingsField("Nome".tr(), firstName, { firstName = it })
            SettingsField("Apelido".tr(), lastName, { lastName = it }, placeholder = "ex. Silva".tr(), hint = "opcional".tr())
            Column {
                SettingsFieldLabel("Fuso horário".tr())
                Spacer(Modifier.height(6.dp))
                TimezoneDropdown(value = timezone, onValueChange = { timezone = it })
            }
            Column {
                SettingsFieldLabel("Telefone".tr(), "para recuperar a conta".tr())
                Spacer(Modifier.height(6.dp))
                PhoneField(
                    iso = dialIso,
                    number = phoneLocal,
                    onIso = { dialIso = it; phoneTouched = true },
                    onNumber = { phoneLocal = it; phoneTouched = true },
                )
            }
        }

        // O e-mail muda-se no ecrã "E-mail" (código de verificação).
        PaneCard(padded = false) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenEmail).padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("E-mail".tr(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SettingsActionText("Alterar".tr(), onClick = onOpenEmail)
            }
        }
    }
    SettingsBottomBar {
        SettingsPrimaryButton(
            text = if (dirty) "Guardar alterações".tr() else "Sem alterações".tr(),
            onClick = { onUpdateFields(changes) },
            enabled = dirty && !isUpdating,
            loading = isUpdating,
        )
    }
    }
}

private fun flagEmoji(iso: String): String =
    iso.uppercase().take(2).map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

/** "+351 912 345 678" → ("PT", "912 345 678"). Sem indicativo: PT e o número tal como está. */
private fun splitPhone(raw: String): Pair<String, String> {
    val v = raw.trim().let { if (it.startsWith("00")) "+" + it.drop(2) else it }
    if (!v.startsWith("+")) return "PT" to v
    val digits = v.filter { it.isDigit() || it == '+' }
    val match = DIAL_CODES.filter { digits.startsWith(it.second) }.maxByOrNull { it.second.length }
        ?: return "PT" to v
    // Tira o indicativo do texto original (mantém os espaços do resto).
    var seen = 0
    var cut = 0
    for ((i, c) in v.withIndex()) {
        if (c == '+' || c.isDigit()) seen++
        if (seen == match.second.length) { cut = i + 1; break }
    }
    return match.first to v.substring(cut).trim()
}

private fun joinPhone(iso: String, number: String): String {
    val n = number.trim()
    return if (n.isEmpty()) "" else "${dialCode(iso)} $n"
}

/** Indicativo (bandeira + código, abre a lista) e número, lado a lado. */
@Composable
private fun PhoneField(iso: String, number: String, onIso: (String) -> Unit, onNumber: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(108.dp)) {
            SettingsSelectBox(text = "${flagEmoji(iso)}  ${dialCode(iso)}", onClick = { expanded = true })
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                DIAL_CODES.forEach { (code, dial) ->
                    val name = countryName(code, COUNTRIES.firstOrNull { it.first == code }?.second ?: code)
                    DropdownMenuItem(
                        text = { Text("${flagEmoji(code)}  $name  $dial") },
                        onClick = { onIso(code); expanded = false },
                    )
                }
            }
        }
        SettingsField(
            label = "Telefone".tr(), // só para o TalkBack (showLabel = false)
            value = number,
            onValueChange = { v -> onNumber(v.filter { it.isDigit() || it == ' ' }) },
            placeholder = "912 345 678",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.weight(1f),
            showLabel = false,
        )
    }
}

@Composable
private fun TimezoneDropdown(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // "0", "0.0" e "+0" valem o mesmo deslocamento.
    val selectedLabel = TIMEZONES.firstOrNull { it.first == value || (value.toDoubleOrNull() != null && it.first.toDouble() == value.toDoubleOrNull()) }?.second
        ?: value
    Box(modifier = Modifier.fillMaxWidth()) {
        SettingsSelectBox(
            text = selectedLabel,
            extra = timeAtOffset(value)?.let { "agora %s".tr().format(it) },
            onClick = { expanded = true },
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            TIMEZONES.forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onValueChange(v); expanded = false },
                )
            }
        }
    }
}

@Composable
internal fun CountryDropdown(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = COUNTRIES.firstOrNull { it.first.equals(value, ignoreCase = true) }?.let { countryName(it.first, it.second) } ?: value
    Box(modifier = Modifier.fillMaxWidth()) {
        SettingsSelectBox(text = selectedLabel, onClick = { expanded = true })
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            COUNTRIES.forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(countryName(v, label)) },
                    onClick = { onValueChange(v); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun PasswordPane(
    isLoading: Boolean,
    changedAt: Long,
    onSubmit: (current: String, new: String, passkeyToken: String?) -> Unit,
    hasPasskeys: Boolean,
    socialiteAccount: Boolean,
    twoFactorEnabled: Boolean,
    backendUrl: String,
    onRefreshUser: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var show2faInfo by remember { mutableStateOf(false) }
    if (show2faInfo) {
        SettingsDialog(
            title = "Verificação em dois passos".tr(),
            message = if (twoFactorEnabled)
                "A verificação em dois passos está ativa nesta conta. Para a desativar ou ver os códigos de recuperação, abre as definições da conta no browser.".tr()
            else
                "Por segurança, a configuração (código QR e códigos de recuperação) é feita no browser. Depois de ativar, toca em \"Atualizar estado\".".tr(),
            icon = Icons.Outlined.Security,
            confirmText = "Abrir no browser".tr(),
            onConfirm = {
                uriHandler.openUri(backendUrl.trimEnd('/') + "/user/settings/password")
                show2faInfo = false
            },
            dismissText = "Fechar".tr(),
            onDismiss = { show2faInfo = false },
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SettingsActionText("Atualizar estado".tr(), onClick = { onRefreshUser(); show2faInfo = false })
            }
        }
    }
    var current by remember { mutableStateOf("") }
    var passkeyToken by remember { mutableStateOf<String?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    // Só limpa depois de o servidor aceitar: com a password atual errada os campos ficam.
    LaunchedEffect(changedAt) {
        if (changedAt > 0L) { current = ""; newPassword = ""; confirmation = ""; passkeyToken = null }
    }
    val mismatch = newPassword.isNotEmpty() && confirmation.isNotEmpty() && newPassword != confirmation
    // 8 = mesma política do registo e do servidor (Fortify\Rules\Password).
    val tooShort = newPassword.isNotEmpty() && newPassword.length < MIN_PASSWORD_LENGTH
    val canSubmit = !isLoading && (current.isNotBlank() || passkeyToken != null) &&
        newPassword.length >= MIN_PASSWORD_LENGTH && newPassword == confirmation

    // Força simples (4 traços): comprimento, maiúsculas+minúsculas, números, símbolos.
    val strength = listOf(
        newPassword.length >= MIN_PASSWORD_LENGTH,
        newPassword.any { it.isUpperCase() } && newPassword.any { it.isLowerCase() },
        newPassword.any { it.isDigit() },
        newPassword.any { !it.isLetterOrDigit() },
    ).count { it }

    PaneFormColumn(
        bottomBar = {
            SettingsPrimaryButton(
                text = "Guardar nova password".tr(),
                onClick = {
                    onSubmit(current, newPassword, passkeyToken)
                    passkeyToken = null // uso único: se falhar, confirma-se de novo
                },
                enabled = canSubmit,
                loading = isLoading,
            )
        },
    ) {
        PasskeysSection(socialiteAccount = socialiteAccount, hasPasskeys = hasPasskeys)

        PaneSection("Proteção da conta".tr())
        PaneCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = null,
                    tint = if (twoFactorEnabled) SettingsAccent else Color(0xFFB45309),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Verificação em dois passos".tr(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (twoFactorEnabled) "Ligada".tr() else "Desligada".tr(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (twoFactorEnabled) SettingsAccent else Color(0xFFB45309),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Pede um código além da password. Configura-se no browser.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingsPrimaryButton(
                text = if (twoFactorEnabled) "Gerir no browser".tr() else "Ativar no browser".tr(),
                onClick = { show2faInfo = true },
            )
        }

        PaneSection("Mudar password".tr())
        PaneCard {
            PasskeyConfirmRow(purpose = "password", hasPasskeys = hasPasskeys, confirmed = passkeyToken != null, onToken = { passkeyToken = it })
            if (passkeyToken == null) {
                SettingsField("Password atual".tr(), current, { current = it }, isPassword = true)
            }
            Column {
                SettingsField("Nova password".tr(), newPassword, { newPassword = it }, isPassword = true, isError = tooShort, hint = "mínimo 8 caracteres".tr())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { i ->
                        val on = newPassword.isNotEmpty() && i < strength
                        val c = when {
                            !on -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            strength <= 1 -> MaterialTheme.colorScheme.error
                            strength == 2 -> Color(0xFFF59E0B)
                            else -> SettingsAccent
                        }
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    }
                }
                if (tooShort) {
                    Spacer(Modifier.height(4.dp))
                    Text("Mínimo 8 caracteres".tr(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            SettingsField(
                "Confirmar nova password".tr(), confirmation, { confirmation = it }, isPassword = true,
                isError = mismatch, supportingText = if (mismatch) "Não coincide".tr() else null,
                imeAction = ImeAction.Done,
                onImeAction = { if (canSubmit) { onSubmit(current, newPassword, passkeyToken); passkeyToken = null } },
            )
            Text(
                "Por segurança, terminamos a sessão nos outros dispositivos.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Login · App Android" / "Login · Web"; tokens criados à mão mantêm o nome. */
private fun tokenTitle(token: AccessToken): String {
    val client = token.source?.client ?: "api"
    if (client == "api") return token.name
    val kind = if (token.name == "oauth-login") "Login social".tr() else "Login".tr()
    return kind + " · " + (if (client == "android") "App Android" else "Web").tr()
}

/** Android: aparelho + ID da instalação; Web: browser + sistema. */
private fun tokenDetail(token: AccessToken): String? {
    val src = token.source ?: return "Criado manualmente".tr()
    val parts = when (src.client) {
        "android" -> listOf(src.device, src.device_id?.let { "ID %s".tr().format(it) })
        "web" -> listOf(src.browser, src.device)
        else -> return "Criado manualmente".tr()
    }
    return parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }
}

@Composable
private fun ApiTokensPane(
    tokens: List<AccessToken>,
    isLoadingTokens: Boolean,
    newTokenPlaintext: String?,
    isCreatingToken: Boolean,
    createTokenError: String?,
    onClearCreateTokenError: () -> Unit,
    onCreateToken: (name: String, password: String?, passkeyToken: String?) -> Unit,
    onDeleteToken: (Long) -> Unit,
    onConsumeNewToken: () -> Unit,
) {
    var showCreateTokenDialog by remember { mutableStateOf(false) }
    var newTokenName by remember { mutableStateOf("") }
    // Step-up exigido pelo servidor (create_token): password atual ou passkey.
    var tokenPassword by remember { mutableStateOf("") }
    var tokenPasskey by remember { mutableStateOf<String?>(null) }
    var revoking by remember { mutableStateOf<AccessToken?>(null) }

    val context = LocalContext.current

    val closeCreate = {
        showCreateTokenDialog = false; newTokenName = ""; tokenPassword = ""; tokenPasskey = null
        onClearCreateTokenError()
    }
    // Criado: o token de passkey foi gasto e o diálogo fecha (o token novo aparece no painel).
    LaunchedEffect(newTokenPlaintext) { if (newTokenPlaintext != null && showCreateTokenDialog) closeCreate() }

    if (showCreateTokenDialog) {
        SettingsDialog(
            title = "Novo token de acesso".tr(),
            message = "Dá-lhe um nome para o reconheceres mais tarde (ex.: script de backup).".tr(),
            icon = Icons.Outlined.VpnKey,
            confirmText = "Criar".tr(),
            // O servidor exige min:3 no nome.
            confirmEnabled = !isCreatingToken && newTokenName.trim().length >= 3 && (tokenPasskey != null || tokenPassword.isNotEmpty()),
            onConfirm = { onCreateToken(newTokenName.trim(), tokenPassword, tokenPasskey) },
            onDismiss = { if (!isCreatingToken) closeCreate() },
        ) {
            SettingsField("Nome do token".tr(), newTokenName, { newTokenName = it }, hint = "mínimo 3 caracteres".tr())
            PasskeyConfirmRow(purpose = "create_token", confirmed = tokenPasskey != null, onToken = { tokenPasskey = it })
            if (tokenPasskey == null) {
                SettingsField(
                    "Password atual".tr(), tokenPassword, { tokenPassword = it }, isPassword = true, hint = "para confirmar que és tu".tr(),
                    imeAction = ImeAction.Done,
                )
            }
            createTokenError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    revoking?.let { token ->
        SettingsDialog(
            title = "Revogar este token?".tr(),
            message = if (token.is_current) {
                "É o token desta sessão: revogá-lo termina a sessão nesta app.".tr()
            } else {
                "As apps ou scripts que o usam deixam de ter acesso.".tr()
            },
            icon = Icons.Outlined.VpnKey,
            confirmText = "Revogar".tr(),
            destructive = true,
            onConfirm = { revoking = null; onDeleteToken(token.id) },
            onDismiss = { revoking = null },
        )
    }

    PaneColumn {
        PaneSection("Tokens de acesso pessoal".tr(), action = "+ " + "Criar".tr(), onAction = { showCreateTokenDialog = true })
        // Token novo: mostrado uma única vez.
        newTokenPlaintext?.let { token ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SettingsAccent.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, SettingsAccent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Token criado — guarda-o agora, não será mostrado novamente.".tr(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(token, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { copySecretToClipboard(context, "API token", token); onConsumeNewToken() }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar".tr(), tint = SettingsAccent)
                        }
                    }
                }
            }
        }
        PaneCard(padded = false) {
            if (tokens.isEmpty()) {
                Text(
                    if (isLoadingTokens) "A carregar…".tr() else "Ainda não criaste tokens.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            tokens.forEachIndexed { i, token ->
                val client = token.source?.client ?: "api"
                val used = token.last_used_at?.let { "Último uso: %s".tr().format(friendlyWhen(it)) }
                    ?: token.created_at?.let { "Criado: %s".tr().format(friendlyWhen(it)) }
                    ?: "Nunca usado".tr()
                SettingsListRow(
                    icon = when (client) {
                        "android" -> Icons.Outlined.Smartphone
                        "web" -> Icons.Outlined.Language
                        else -> Icons.Outlined.Code
                    },
                    title = tokenTitle(token) + if (token.is_current) " · " + "Este dispositivo".tr() else "",
                    subtitle = listOfNotNull(tokenDetail(token), used).joinToString("\n"),
                    showDivider = i < tokens.lastIndex,
                ) {
                    SettingsPillButton("Revogar".tr(), onClick = { revoking = token }, color = SettingsColors.Red)
                }
            }
        }
    }
}

/**
 * Alterar email: password actual + novo email → código (2 min) enviado ao
 * novo endereço. Countdown e limpeza automática do código como no OTP de login.
 */
@Composable
private fun EmailChangeSection(
    currentEmail: String,
    hasPasskeys: Boolean,
    state: SettingsUiState,
    onRequest: (email: String, password: String, passkeyToken: String?) -> Unit,
    onRestartForResend: () -> Unit,
    onConfirm: (code: String) -> Unit,
    onCancel: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passkeyToken by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableStateOf(0) }
    val inCodeStep = state.emailChangeStep == "code"

    LaunchedEffect(state.emailChangeExpiresAt, inCodeStep) {
        if (!inCodeStep) return@LaunchedEffect
        code = ""
        while (true) {
            secondsLeft = ((state.emailChangeExpiresAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            if (secondsLeft == 0) { code = ""; break }
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(state.emailChangeStep) {
        if (state.emailChangeStep == "idle" && !state.isChangingEmail && state.emailChangeError == null) {
            editing = false; newEmail = ""; password = ""; code = ""; passkeyToken = null
        }
    }

    val formOpen = editing || inCodeStep
    val canSubmit = !state.isChangingEmail && if (inCodeStep) code.length == 6 && secondsLeft > 0
        else android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail.trim()).matches() && (password.isNotBlank() || passkeyToken != null)
    val cancel = { onCancel(); editing = false; newEmail = ""; password = ""; code = ""; passkeyToken = null }

    PaneFormColumn(
        bottomBar = if (!formOpen) null else { {
            SettingsPrimaryButton(
                text = if (inCodeStep) "Confirmar alteração".tr() else "Enviar código".tr(),
                onClick = {
                    if (inCodeStep) onConfirm(code)
                    else { onRequest(newEmail, password, passkeyToken); passkeyToken = null } // uso único no servidor
                },
                enabled = canSubmit,
                loading = state.isChangingEmail,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (inCodeStep) {
                    SettingsActionText(
                        "Reenviar código".tr(),
                        // Confirmado com passkey (token já gasto, sem password): volta ao 1.º passo.
                        onClick = {
                            if (password.isNotEmpty()) onRequest(state.emailChangeTarget, password, null)
                            else { newEmail = state.emailChangeTarget; editing = true; onRestartForResend() }
                        },
                        enabled = !state.isChangingEmail,
                    )
                    Spacer(Modifier.width(16.dp))
                }
                SettingsActionText("Cancelar".tr(), onClick = cancel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } },
    ) {
        PaneSection("E-mail da conta".tr())

        if (!formOpen) {
            PaneCard(padded = false) {
                SettingsListRow(icon = Icons.Outlined.Email, title = currentEmail) {
                    SettingsActionText("Alterar".tr(), onClick = { editing = true })
                }
            }
            return@PaneFormColumn
        }

        PaneCard {
            if (!inCodeStep) {
                SettingsField("Novo endereço de email".tr(), newEmail, { newEmail = it }, keyboardType = KeyboardType.Email, placeholder = "nome@exemplo.com")
                PasskeyConfirmRow(purpose = "email", hasPasskeys = hasPasskeys, confirmed = passkeyToken != null, onToken = { passkeyToken = it })
                if (passkeyToken == null) {
                    SettingsField("Password atual".tr(), password, { password = it }, isPassword = true, hint = "para confirmar que és tu".tr(), imeAction = ImeAction.Done)
                }
            } else {
                Text(
                    "Enviámos um código de 6 dígitos para %s.".tr().format(state.emailChangeTarget),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsField(
                    "Código de confirmação".tr(), code, { v -> code = v.filter { it.isDigit() }.take(6) },
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    hint = "6 dígitos".tr(),
                    supportingText = if (secondsLeft > 0) "O código expira em %ds".tr().format(secondsLeft)
                        else "O código expirou. Pede um novo.".tr(),
                )
            }

            state.emailChangeError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    isError: Boolean = false,
    errorText: String? = null,
) = SettingsField(label, value, onChange, isPassword = true, isError = isError, supportingText = errorText)

@Composable
private fun StoragePane(
    storage: StorageUsage?,
    insights: co.golink.tester.domain.settings.StorageInsights?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onFreeSpace: () -> Unit,
    onOpenBackups: () -> Unit,
    onOpenFile: (BrowseItem.File) -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }
    val rangeOptions = remember {
        listOf(7 to "Últimos 7 dias".tr(), 14 to "Últimos 14 dias".tr(), 30 to "Últimos 30 dias".tr(), 45 to "Últimos 45 dias".tr())
    }
    var rangeIndex by remember { mutableStateOf(rangeOptions.lastIndex) }
    var rangeMenu by remember { mutableStateOf(false) }
    val rangeDays = rangeOptions[rangeIndex].first

    PaneColumn {
        if (storage == null) {
            PaneCard {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) CircularProgressIndicator() else Text("Sem dados".tr(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@PaneColumn
        }

        data class TypeRow(val label: String, val usage: co.golink.tester.domain.settings.StorageTypeUsage?, val color: Color)
        val types = listOf(
            TypeRow("Vídeos".tr(), storage.videos, Color(0xFF6D28D9)),
            TypeRow("Imagens".tr(), storage.images, Color(0xFF2563EB)),
            TypeRow("Outros".tr(), storage.others, Color(0xFF94A3B8)),
            TypeRow("Documentos".tr(), storage.documents, Color(0xFF15803D)),
            TypeRow("Áudio".tr(), storage.audios, Color(0xFFD4A017)),
        ).filter { it.usage?.used != null }
        val free = if (storage.capacityBytes != null && storage.usedBytes != null)
            co.golink.tester.ui.common.formatBytes((storage.capacityBytes - storage.usedBytes).coerceAtLeast(0L)) else null

        // Total usado, barra segmentada por tipo e legenda.
        PaneCard {
            Column {
                Text(storage.used, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text(
                    "usados de %s · %d%%".tr().format(storage.capacity, storage.percentage.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            ) {
                types.forEach { t ->
                    val f = ((t.usage?.percentage ?: 0f) / 100f).coerceIn(0f, 1f)
                    if (f > 0f) Box(Modifier.fillMaxHeight().weight(f).background(t.color))
                }
                val rest = (1f - types.sumOf { ((it.usage?.percentage ?: 0f) / 100f).toDouble() }.toFloat()).coerceAtLeast(0f)
                if (rest > 0f) Spacer(Modifier.weight(rest))
            }
            Column {
                // Parte de cada tipo no espaço usado (barra fina + %).
                types.forEach { t ->
                    val share = if (storage.percentage > 0f) ((t.usage?.percentage ?: 0f) / storage.percentage).coerceIn(0f, 1f) else 0f
                    StorageLegendRow(t.color, t.label, t.usage?.used.orEmpty(), share = share)
                }
                if (free != null) StorageLegendRow(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), "Livre".tr(), free, muted = true)
            }
        }

        // Transferências: enviado / recebido com mini-gráficos.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                "Transferências".tr(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = settingsCardColor(),
                    border = settingsCardBorderStroke(),
                    modifier = Modifier.clickable { rangeMenu = true },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)) {
                        Text(rangeOptions[rangeIndex].second, style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                androidx.compose.material3.DropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
                    rangeOptions.forEachIndexed { idx, (_, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { rangeIndex = idx; rangeMenu = false })
                    }
                }
            }
        }
        PaneCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrafficColumn("Enviado".tr(), storage.trafficUpload ?: "—", Color(0xFF2563EB),
                    storage.trafficChartUpload?.mapNotNull { it.percentage }?.takeLast(rangeDays), Modifier.weight(1f))
                TrafficColumn("Recebido".tr(), storage.trafficDownload ?: "—", SettingsAccent,
                    storage.trafficChartDownload?.mapNotNull { it.percentage }?.takeLast(rangeDays), Modifier.weight(1f))
            }
        }

        if (insights != null) {
            // Resumo da conta: totais e quanto está cifrado.
            PaneSection("Resumo".tr())
            PaneCard {
                val encPct = if (insights.filesCount > 0) insights.encryptedFilesCount * 100 / insights.filesCount else 0
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageStat("%,d".format(insights.filesCount), "ficheiros".tr(), Modifier.weight(1f))
                    StorageStat("%,d".format(insights.foldersCount), "pastas".tr(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageStat("%,d".format(insights.sharesCount), "partilhas ativas".tr(), Modifier.weight(1f))
                    StorageStat("$encPct%", "cifrado ponta-a-ponta".tr(), Modifier.weight(1f))
                }
            }

            PaneSection("Lixo e backups".tr())
            PaneCard(padded = false) {
                SettingsListRow(
                    icon = Icons.Outlined.Delete,
                    title = "Lixo".tr(),
                    subtitle = "%d ficheiros · %s".tr().format(insights.trashCount, co.golink.tester.ui.common.formatBytes(insights.trashBytes)),
                    showDivider = true,
                    onClick = onFreeSpace,
                ) { SettingsChevron() }
                SettingsListRow(
                    icon = Icons.Outlined.Smartphone,
                    title = "Backups do telemóvel".tr(),
                    subtitle = "%d ficheiros · %s".tr().format(insights.backupCount, co.golink.tester.ui.common.formatBytes(insights.backupBytes)),
                    onClick = onOpenBackups,
                ) { SettingsChevron() }
            }

            if (insights.largest.isNotEmpty()) {
                PaneSection("Maiores ficheiros".tr())
                PaneCard(padded = false) {
                    insights.largest.forEachIndexed { i, f ->
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFile(f) }
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                            ) {
                                Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                                    co.golink.tester.ui.components.FileTypeIcon(f, Modifier.height(24.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    f.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(f.filesize.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (i < insights.largest.lastIndex) {
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), modifier = Modifier.padding(start = 52.dp, end = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        PaneCard(padded = false) {
            SettingsListRow(
                icon = Icons.Outlined.Delete,
                title = "Libertar espaço".tr(),
                subtitle = "Ver ficheiros grandes e o Lixo".tr(),
                onClick = onFreeSpace,
            ) { SettingsChevron() }
        }
    }
}

@Composable
private fun StorageLegendRow(color: Color, label: String, value: String, muted: Boolean = false, share: Float? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (share != null) {
            Text(
                if (share > 0f && share < 0.01f) "<1%" else "${(share * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
    if (share != null) {
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .padding(start = 20.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceAtLeast(0.005f)).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
    }
}

@Composable
private fun StorageStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrafficColumn(label: String, value: String, color: Color, data: List<Float>?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (!data.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Sparkline(data = data, color = color, modifier = Modifier.fillMaxWidth().height(32.dp))
        }
    }
}

@Composable
private fun Sparkline(data: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val max = data.max().coerceAtLeast(1f)
        val path = Path()
        data.forEachIndexed { i, v ->
            val x = i / (data.size - 1f) * size.width
            val y = size.height - (v / max) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun SessionsPane(
    sessions: List<SessionItem>,
    isLoading: Boolean,
    onRevoke: (String) -> Unit,
    onRevokeAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    var pendingRevoke by remember { mutableStateOf<SessionItem?>(null) }
    var showRevokeAll by remember { mutableStateOf(false) }

    pendingRevoke?.let { s ->
        SettingsDialog(
            title = if (s.is_current) "Terminar sessão neste telemóvel?".tr() else "Revogar esta sessão?".tr(),
            message = if (s.is_current)
                "Vais ser desconectado deste dispositivo imediatamente.".tr()
            else "A sessão será terminada e o dispositivo terá de iniciar sessão novamente.".tr(),
            icon = if (s.isMobileSession()) Icons.Filled.Smartphone else Icons.Outlined.Devices,
            destructive = true,
            confirmText = "Revogar".tr(),
            onConfirm = { onRevoke(s.id); pendingRevoke = null },
            onDismiss = { pendingRevoke = null },
        )
    }
    if (showRevokeAll) {
        SettingsDialog(
            title = "Terminar outras sessões?".tr(),
            message = "Todas as outras sessões serão revogadas. Continuarás autenticado neste dispositivo.".tr(),
            icon = Icons.Filled.Logout,
            destructive = true,
            confirmText = "Terminar todas".tr(),
            onConfirm = { onRevokeAll(); showRevokeAll = false },
            onDismiss = { showRevokeAll = false },
        )
    }

    val current = sessions.firstOrNull { it.is_current }
    val others = sessions.filter { !it.is_current }
        .sortedByDescending { it.last_activity_at ?: it.login_at ?: "" }
    val ordered = listOfNotNull(current) + others

    PaneColumn {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                if (sessions.size == 1) "1 dispositivo com sessão iniciada na tua conta.".tr()
                else trPlural("%d dispositivo com sessão iniciada na tua conta.", "%d dispositivos com sessão iniciada na tua conta.", sessions.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SettingsAccent)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Atualizar".tr(), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (isLoading && sessions.isEmpty()) {
            PaneCard { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SettingsAccent) } }
        }

        if (ordered.isNotEmpty()) {
            PaneCard(padded = false) {
                ordered.forEachIndexed { i, s ->
                    SessionRow(s, onRevoke = { pendingRevoke = s })
                    if (i < ordered.lastIndex) SettingsRowDivider(68.dp)
                }
            }
        }

        Text(
            "Não reconheces um dispositivo? Termina-lhe a sessão e muda a password.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (others.isNotEmpty()) {
            SettingsOutlinedButton(
                text = "Terminar sessão nos outros dispositivos".tr(),
                onClick = { showRevokeAll = true },
                destructive = true,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Linha de uma sessão: mosaico do tipo, nome, chip na atual, meta e "Terminar". */
@Composable
private fun SessionRow(s: SessionItem, onRevoke: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(settingsInsetColor()),
            contentAlignment = Alignment.Center,
        ) { Icon(sessionIcon(s), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sessionTitle(s),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (s.is_current) {
                    Spacer(Modifier.width(6.dp))
                    SettingsBadge("Este telemóvel".tr(), SettingsAccent)
                }
            }
            Text(
                sessionMeta(s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!s.is_current) {
            Spacer(Modifier.width(10.dp))
            SettingsPillButton("Terminar".tr(), onClick = onRevoke)
        }
    }
}

/** Telemóvel / portátil / browser genérico (globo). */
private fun sessionIcon(s: SessionItem): ImageVector {
    if (s.isMobileSession()) return Icons.Filled.Smartphone
    val text = listOfNotNull(s.device, s.browser).joinToString(" ").lowercase()
    return when {
        listOf("windows", "mac", "linux", "chrome os", "chromeos").any { it in text } -> Icons.Outlined.Laptop
        else -> Icons.Outlined.Language
    }
}

/** "Chrome em Windows" / "Pixel 8" — nome curto do dispositivo. */
private fun sessionTitle(s: SessionItem): String {
    val device = s.device?.takeIf { it.isNotBlank() }
    val browser = s.browser?.takeIf { it.isNotBlank() && it != device }
    return when {
        device != null && browser != null && !s.isMobileSession() -> "%s · %s".format(browser, device)
        device != null -> device
        browser != null -> browser
        else -> "Dispositivo desconhecido".tr()
    }
}

/** "Lisboa, PT · há 2 h" (sem localização, o IP). */
private fun sessionMeta(s: SessionItem): String = listOfNotNull(
    s.location?.takeIf { it.isNotBlank() } ?: s.ip_address?.takeIf { it.isNotBlank() },
    if (s.is_current) "ativa agora".tr()
    else (s.last_activity_at ?: s.login_at)?.let { co.golink.tester.ui.common.relativeAgo(it) }?.takeIf { it.isNotBlank() },
).joinToString(" · ")

/**
 * A session is shown as mobile when the backend labels it non-"web", OR when
 * it is the current session — because this list is being viewed from the
 * Android app, so the active session is necessarily this phone.
 */
private fun SessionItem.isMobileSession(): Boolean =
    is_current || (platform ?: "").lowercase() != "web"

@Composable
private fun SecurityActivityPane(
    events: List<SecurityEventData>,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
) {
    co.golink.tester.ui.screens.security.SecurityActivityContent(
        items = events.map { e ->
            co.golink.tester.ui.screens.security.SecurityItemUi(e.id, e.attributes.event, e.attributes.created_at, e.attributes.ip, e.attributes.user_agent)
        },
        isLoading = isLoading,
        hasMore = hasMore,
        onLoadMore = onLoadMore,
    )
}

@Composable
private fun EncryptionPane(
    e2eUnlocked: Boolean,
    e2eFingerprint: String?,
    isChanging: Boolean,
    onVerifyCurrent: suspend (String) -> Boolean,
    requestCode: suspend () -> co.golink.tester.domain.encryption.SecretCodeResponse,
    confirmCode: suspend (String) -> String,
    onChangePassphrase: (String, String, (Boolean, Boolean) -> Unit) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    // Passos: "current" (passphrase atual) → "code" (email) → "new".
    var step by remember { mutableStateOf("current") }
    var currentPass by remember { mutableStateOf("") }
    // Depois de verificada, só fica o SHA-256 (para "tem de ser diferente da atual").
    var currentHash by remember { mutableStateOf<String?>(null) }
    var currentError by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var changeToken by remember { mutableStateOf<String?>(null) }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    val minLen = 12
    val tooShort = newPass.isNotEmpty() && newPass.length < minLen
    val sameAsCurrent = newPass.isNotEmpty() && currentHash != null && sha256Hex(newPass) == currentHash
    val mismatch = confirmPass.isNotEmpty() && newPass != confirmPass
    val canSubmit = newPass.length >= minLen && !sameAsCurrent && newPass == confirmPass && !isChanging && changeToken != null
    fun resetFlow() {
        step = "current"; currentPass = ""; currentHash = null; currentError = null; changeToken = null; newPass = ""; confirmPass = ""
    }

    PaneColumn {
        Text(
            "Os ficheiros são cifrados neste telemóvel antes de saírem. Só tu tens a chave.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // Estado: desbloqueada neste telemóvel (verde) ou bloqueada (âmbar).
        val stateTint = if (e2eUnlocked) SettingsAccent else Color(0xFFB45309)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = stateTint.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, stateTint.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(stateTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Lock, contentDescription = null, tint = stateTint, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Encriptação ponta-a-ponta ativa".tr(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (e2eUnlocked) "Desbloqueada neste telemóvel".tr() else "Bloqueada — desbloqueia para gerir a chave".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = stateTint,
                    )
                }
            }
        }

        // Impressão digital da chave pública (deteta troca de chave).
        PaneSection("Impressão digital da chave".tr())
        PaneCard {
            Text(
                "Confirma que é igual nos teus outros dispositivos — um código diferente pode indicar troca de chave.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (e2eFingerprint != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        e2eFingerprint,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                    )
                }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(e2eFingerprint)) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copiar impressão digital".tr(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    "Desbloqueia a encriptação para veres a impressão digital.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB45309),
                )
            }
        }

        // Mudar frase-passe: re-embrulha a chave privada com o novo segredo.
        // NÃO re-cifra ficheiros — continuam acessíveis com a nova frase-passe.
        PaneSection("Frase-passe".tr())
        PaneCard {
            Text(
                "Muda a frase-passe que desbloqueia a tua encriptação. Os ficheiros não são re-cifrados e continuam acessíveis.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!e2eUnlocked) {
                Text(
                    "Desbloqueia a encriptação primeiro para poderes mudar a frase-passe.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB45309),
                )
            } else {
                // Passos: Atual → Código → Nova.
                val stepIndex = when (step) { "current" -> 0; "code" -> 1; else -> 2 }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Atual".tr(), "Código".tr(), "Nova".tr()).forEachIndexed { k, label ->
                        Column(Modifier.weight(1f)) {
                            Box(
                                Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (k <= stepIndex) SettingsAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${k + 1}. $label",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (k == stepIndex) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (k == stepIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                when (step) {
                    "current" -> {
                        PasswordField(
                            label = "Frase-passe atual".tr(),
                            value = currentPass,
                            onChange = { currentPass = it; currentError = null },
                            isError = currentError != null,
                            errorText = currentError,
                        )
                        SettingsPrimaryButton(
                            text = "Continuar".tr(),
                            onClick = {
                                verifying = true
                                scope.launch {
                                    val ok = runCatching { onVerifyCurrent(currentPass) }.getOrDefault(false)
                                    verifying = false
                                    if (ok) {
                                        currentHash = sha256Hex(currentPass)
                                        currentPass = ""
                                        step = "code"
                                    } else {
                                        currentError = "A frase-passe atual está incorreta.".tr()
                                    }
                                }
                            },
                            enabled = currentPass.isNotEmpty(),
                            loading = verifying,
                        )
                    }
                    "code" -> co.golink.tester.ui.components.SecretCodeStep(
                        requestCode = requestCode,
                        confirmCode = confirmCode,
                        onConfirmed = { token -> changeToken = token; step = "new" },
                    )
                    else -> {
                        PasswordField(
                            label = "Nova frase-passe".tr(),
                            value = newPass,
                            onChange = { newPass = it },
                            isError = tooShort || sameAsCurrent,
                            errorText = when {
                                tooShort -> "Mínimo %d caracteres".tr().format(minLen)
                                sameAsCurrent -> "A nova frase-passe tem de ser diferente da atual.".tr()
                                else -> "Mínimo %d caracteres".tr().format(minLen)
                            },
                        )
                        PasswordField(
                            label = "Confirmar nova frase-passe".tr(),
                            value = confirmPass,
                            onChange = { confirmPass = it },
                            isError = mismatch,
                            errorText = if (mismatch) "Não coincide".tr() else null,
                        )
                        SettingsPrimaryButton(
                            text = "Mudar frase-passe".tr(),
                            onClick = {
                                onChangePassphrase(newPass, changeToken.orEmpty()) { ok, expired ->
                                    when {
                                        ok -> resetFlow()
                                        expired -> { changeToken = null; step = "code" }
                                    }
                                }
                            },
                            enabled = canSubmit,
                            loading = isChanging,
                        )
                    }
                }
                if (step != "current") {
                    OutlinedButton(
                        onClick = ::resetFlow,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancelar".tr(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        // Como funciona (resumo curto, sem jargão).
        PaneSection("Como funciona".tr())
        PaneCard(padded = false) {
            SettingsListRow(icon = Icons.Outlined.Lock, title = "Cifrado no teu telemóvel".tr(), subtitle = "O servidor só guarda dados ilegíveis.".tr(), showDivider = true)
            SettingsListRow(icon = Icons.Outlined.VpnKey, title = "Só tu tens a chave".tr(), subtitle = "Desbloqueada com a tua frase-passe.".tr(), showDivider = true)
            SettingsListRow(icon = Icons.Outlined.Shield, title = "Partilhas continuam privadas".tr(), subtitle = "A chave do link vai no próprio link, nunca para o servidor.".tr())
        }
    }
}

@Composable
private fun AppSecurityPane(
    user: User?,
    biometricEnabled: Boolean,
    pinEnabled: Boolean,
    hasPinSet: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    onEnablePin: (String) -> Unit,
    onChangePin: (String, String, (PinCheck) -> Unit) -> Unit,
    onDisableAppLock: (String?, (PinCheck) -> Unit) -> Unit,
    lockDelayMs: Long,
    onSetLockDelay: (Long) -> Unit,
    hideInRecents: Boolean,
    onSetHideInRecents: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    cacheBytes: Long?,
    onLoadCacheSize: () -> Unit,
    onOpenSecurityActivity: () -> Unit,
    logEntries: List<LogEntry>,
    onRefreshLog: () -> Unit,
    onClearLog: () -> Unit,
) {
    val context = LocalContext.current
    val biometricAvailable = remember {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // PIN setup dialog state
    var pinStep by remember { mutableStateOf(0) } // 0=hidden, 1=enter, 2=confirm
    var pinFirst by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // PIN a ser verificado (PBKDF2 fora da main thread): botões desligados.
    var pinBusy by remember { mutableStateOf(false) }
    fun pinCheckMessage(r: PinCheck): String = when {
        r is PinCheck.LockedOut && r.seconds > 60 -> "Demasiadas tentativas. Tenta daqui a %d min.".tr().format((r.seconds + 59) / 60)
        r is PinCheck.LockedOut -> "Demasiadas tentativas. Aguarda %d s.".tr().format(r.seconds)
        else -> "PIN incorreto".tr()
    }

    // PIN disable dialog
    var showPinDisable by remember { mutableStateOf(false) }
    var pinDisableInput by remember { mutableStateOf("") }
    var pinDisableError by remember { mutableStateOf<String?>(null) }

    // Log dialog
    var showLog by remember { mutableStateOf(false) }

    // PIN: definir (1) → confirmar (2), e desativar — popups das Definições.
    val resetPin = { pinStep = 0; pinInput = ""; pinFirst = ""; pinError = null }
    if (pinStep == 1) {
        SettingsDialog(
            title = "Definir PIN".tr(),
            message = "Introduz um PIN de 4 dígitos para bloquear a aplicação.".tr(),
            icon = Icons.Outlined.Lock,
            confirmText = "Seguinte".tr(),
            confirmEnabled = pinInput.length == 4,
            onConfirm = { pinFirst = pinInput; pinInput = ""; pinStep = 2 },
            onDismiss = resetPin,
        ) {
            SettingsPinInput(pinInput, { pinInput = it; pinError = null }, isError = pinError != null)
        }
    }
    if (pinStep == 2) {
        SettingsDialog(
            title = "Confirmar PIN".tr(),
            message = "Repete o PIN para confirmar.".tr(),
            icon = Icons.Outlined.Lock,
            confirmText = "Ativar PIN".tr(),
            confirmEnabled = pinInput.length == 4,
            onConfirm = {
                if (pinInput != pinFirst) {
                    pinError = "Os PINs não coincidem".tr(); pinInput = ""
                } else {
                    onEnablePin(pinInput); resetPin()
                }
            },
            onDismiss = resetPin,
        ) {
            SettingsPinInput(pinInput, { pinInput = it; pinError = null }, isError = pinError != null)
            pinError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
    if (showPinDisable) {
        val close = { showPinDisable = false; pinDisableInput = ""; pinDisableError = null }
        SettingsDialog(
            title = "Desativar bloqueio".tr(),
            message = "Introduz o PIN atual para desativar o bloqueio.".tr(),
            icon = Icons.Outlined.Lock,
            destructive = true,
            confirmText = "Desativar".tr(),
            confirmEnabled = pinDisableInput.length == 4 && !pinBusy,
            onConfirm = {
                pinBusy = true
                onDisableAppLock(pinDisableInput) { r ->
                    pinBusy = false
                    if (r != PinCheck.Ok) { pinDisableError = pinCheckMessage(r); pinDisableInput = "" } else close()
                }
            },
            onDismiss = close,
        ) {
            SettingsPinInput(pinDisableInput, { pinDisableInput = it; pinDisableError = null }, isError = pinDisableError != null)
            pinDisableError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
    // Mudar PIN: atual (1) → novo (2) → confirmar (3).
    var changeStep by remember { mutableStateOf(0) }
    var changeCurrent by remember { mutableStateOf("") }
    var changeNew by remember { mutableStateOf("") }
    var changeInput by remember { mutableStateOf("") }
    var changeError by remember { mutableStateOf<String?>(null) }
    val closeChange = { changeStep = 0; changeCurrent = ""; changeNew = ""; changeInput = ""; changeError = null }
    if (changeStep in 1..3) {
        SettingsDialog(
            title = when (changeStep) { 1 -> "PIN atual".tr(); 2 -> "Novo PIN".tr(); else -> "Confirmar PIN".tr() },
            message = when (changeStep) {
                1 -> "Introduz o PIN atual.".tr()
                2 -> "Escolhe um PIN novo de 4 dígitos.".tr()
                else -> "Repete o PIN para confirmar.".tr()
            },
            icon = Icons.Outlined.Lock,
            confirmText = if (changeStep == 3) "Mudar PIN".tr() else "Seguinte".tr(),
            confirmEnabled = changeInput.length == 4 && !pinBusy,
            onConfirm = {
                when (changeStep) {
                    1 -> { changeCurrent = changeInput; changeInput = ""; changeStep = 2 }
                    2 -> { changeNew = changeInput; changeInput = ""; changeStep = 3 }
                    else -> if (changeInput != changeNew) {
                        changeError = "Os PINs não coincidem".tr(); changeInput = ""
                    } else {
                        pinBusy = true
                        onChangePin(changeCurrent, changeNew) { r ->
                            pinBusy = false
                            if (r == PinCheck.Ok) {
                                closeChange()
                            } else {
                                // PIN atual errado: volta ao primeiro passo.
                                changeCurrent = ""; changeNew = ""; changeInput = ""; changeStep = 1
                                changeError = pinCheckMessage(r)
                            }
                        }
                    }
                }
            },
            onDismiss = closeChange,
        ) {
            SettingsPinInput(changeInput, { changeInput = it; changeError = null }, isError = changeError != null)
            changeError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }

    var confirmClearCache by remember { mutableStateOf(false) }
    if (confirmClearCache) {
        SettingsDialog(
            title = "Limpar cache?".tr(),
            message = "Apaga as miniaturas e os ficheiros temporários guardados neste telemóvel. Os teus ficheiros não são apagados.".tr(),
            icon = Icons.Outlined.Delete,
            confirmText = "Limpar cache".tr(),
            onConfirm = { confirmClearCache = false; onClearCache() },
            onDismiss = { confirmClearCache = false },
        )
    }

    // Log dialog
    if (showLog) {
        LogDialog(user = user, entries = logEntries, onClear = onClearLog, onDismiss = { showLog = false })
    }

    fun confirmBiometric(title: String, subtitle: String, onOk: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onOk()
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Cancelar".tr())
                .build(),
        )
    }
    val enableBiometric = {
        confirmBiometric("Activar biométrico".tr(), "Confirma a tua identidade para activar".tr()) { onBiometricToggle(true) }
    }
    val bioOn = biometricEnabled && biometricAvailable
    LaunchedEffect(Unit) { onLoadCacheSize() }

    val lockOn = pinEnabled || bioOn
    val toggleLock: (Boolean) -> Unit = { want ->
        if (want) {
            if (!lockOn) pinStep = 1
        } else if (hasPinSet) {
            showPinDisable = true
        } else if (bioOn) {
            // Só impressão digital: confirmar antes de desligar o bloqueio.
            confirmBiometric("Desligar bloqueio".tr(), "Confirma a tua identidade para desligar".tr()) { onDisableAppLock(null) {} }
        } else {
            onDisableAppLock(null) {}
        }
    }

    PaneColumn {
        PaneCard(padded = false) {
            SettingsToggleRow(
                title = "Bloqueio da app".tr(),
                subtitle = "Pedir desbloqueio ao abrir o GoLink".tr(),
                checked = lockOn,
                onCheckedChange = toggleLock,
                icon = Icons.Outlined.Fingerprint,
                iconColor = SettingsAccent,
                bold = true,
            )
        }

        if (lockOn) {
            PaneSection("Desbloquear com".tr())
            PaneCard(padded = false) {
                SettingsToggleRow(
                    title = "Impressão digital".tr(),
                    subtitle = if (!biometricAvailable) "Não disponível neste telemóvel".tr() else null,
                    checked = bioOn,
                    enabled = biometricAvailable,
                    onCheckedChange = { want ->
                        when {
                            want -> enableBiometric()
                            // Só impressão digital: desligá-la desliga o bloqueio → confirmar, como no interruptor geral.
                            !hasPinSet -> confirmBiometric("Desligar bloqueio".tr(), "Confirma a tua identidade para desligar".tr()) { onBiometricToggle(false) }
                            else -> onBiometricToggle(false)
                        }
                    },
                    showDivider = true,
                )
                val pinReady = pinEnabled && hasPinSet
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (pinReady) changeStep = 1 else pinStep = 1 }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("PIN".tr(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (pinReady) "4 dígitos · sempre disponível".tr() else "Sem PIN definido".tr(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsActionText(
                        if (pinReady) "Mudar PIN".tr() else "Definir PIN".tr(),
                        onClick = { if (pinReady) changeStep = 1 else pinStep = 1 },
                    )
                }
            }

            PaneSection("Bloquear depois de sair da app".tr())
            PaneCard(padded = false) {
                Box(Modifier.padding(8.dp)) {
                    SettingsSegmented(
                        options = listOf(
                            0L to "Agora".tr(),
                            60_000L to "%d min".tr().format(1),
                            300_000L to "%d min".tr().format(5),
                            900_000L to "%d min".tr().format(15),
                        ),
                        selected = lockDelayMs,
                        onSelect = onSetLockDelay,
                    )
                }
                // Antes do Android 13 esconder a miniatura exige FLAG_SECURE (bloqueia capturas).
                if (lockDelayMs <= 0L && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        "Com «Agora», a app fica oculta nos recentes e as capturas de ecrã ficam bloqueadas".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }

            PaneSection("Privacidade".tr())
            PaneCard(padded = false) {
                SettingsToggleRow(
                    title = "Ocultar nas apps recentes".tr(),
                    // Android 13+: só a miniatura; antes disso o FLAG_SECURE também bloqueia capturas.
                    subtitle = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                        "Pré-visualização em branco".tr()
                    else "Pré-visualização em branco, bloqueia capturas de ecrã".tr(),
                    checked = hideInRecents,
                    onCheckedChange = onSetHideInRecents,
                )
            }
        }

        // O botão Lua/Sol da gaveta só alterna claro/escuro: aqui pode voltar-se a "Sistema".
        PaneSection("Aparência".tr())
        PaneCard(padded = false) {
            Box(Modifier.padding(8.dp)) {
                SettingsSegmented(
                    options = listOf(
                        co.golink.tester.ui.theme.AppThemeMode.Mode.SYSTEM to "Sistema".tr(),
                        co.golink.tester.ui.theme.AppThemeMode.Mode.LIGHT to "Claro".tr(),
                        co.golink.tester.ui.theme.AppThemeMode.Mode.DARK to "Escuro".tr(),
                    ),
                    selected = co.golink.tester.ui.theme.AppThemeMode.mode,
                    onSelect = { co.golink.tester.ui.theme.AppThemeMode.set(context, it) },
                )
            }
        }

        PaneSection("Segurança".tr())
        PaneCard(padded = false) {
            SettingsListRow(
                icon = Icons.Outlined.Shield,
                title = "Atividade de segurança".tr(),
                subtitle = "Inícios de sessão e alterações".tr(),
                onClick = onOpenSecurityActivity,
            ) { SettingsChevron() }
        }

        PaneSection("Dados neste telemóvel".tr())
        PaneCard(padded = false) {
            SettingsListRow(
                icon = Icons.Outlined.Delete,
                title = "Limpar cache".tr(),
                subtitle = listOfNotNull(cacheBytes?.let { co.golink.tester.ui.common.formatBytes(it) }, "não apaga os teus ficheiros".tr()).joinToString(" · ")
                    .replaceFirstChar { it.uppercase() },
                showDivider = true,
                onClick = { confirmClearCache = true },
            )
            SettingsListRow(
                icon = Icons.AutoMirrored.Outlined.Article,
                title = "Registo de diagnóstico".tr(),
                subtitle = "Para enviar ao suporte".tr(),
                onClick = { onRefreshLog(); showLog = true },
            ) { SettingsChevron() }
        }
    }
}

@Composable
private fun LogDialog(
    user: User?,
    entries: List<LogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)) {
                // Cabeçalho
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Registo de diagnóstico".tr(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (entries.isEmpty()) "Sem entradas".tr() else trPlural("%d entrada", "%d entradas", entries.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Cartão do utilizador — só o essencial, sem dados sensíveis.
                if (user != null) {
                    Spacer(Modifier.height(12.dp))
                    LogUserCard(user, modifier = Modifier.padding(horizontal = 18.dp))
                }

                Spacer(Modifier.height(12.dp))
                MenuDivider()

                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Nada registado ainda".tr(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        var lastDate: String? = null
                        // Chaves pelo índice: entradas iguais no mesmo ms repetiam a chave (crash).
                        entries.forEachIndexed { i, entry ->
                            val date = entry.dateFormatted()
                            if (date != lastDate) {
                                lastDate = date
                                item(key = "date_${i}_$date") { DateSeparator(date) }
                            }
                            item(key = "log_$i") {
                                LogRow(entry)
                            }
                        }
                    }
                }

                MenuDivider()
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 18.dp)) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = entries.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f),
                    ) { Text("Limpar".tr(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SettingsAccent, contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                    ) { Text("Fechar".tr(), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// Identificação essencial e não sensível de quem está autenticado: nome,
// email mascarado e função/plano. Nunca mostramos o id interno nem o email
// completo.
@Composable
private fun LogUserCard(user: User, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    maskEmail(user.email),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val roleLabel = user.planName?.takeIf { it.isNotBlank() } ?: user.role.replaceFirstChar { it.uppercase() }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SettingsAccent.copy(alpha = 0.12f),
            ) {
                Text(
                    roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = SettingsAccent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

// Mascara o email: mantém a 1ª letra e o domínio (jo•••@golink.co).
private fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return "—"
    val local = email.substring(0, at)
    val domain = email.substring(at)
    val head = local.first()
    return "$head•••$domain"
}

// Cor por categoria de log, para leitura rápida da etiqueta.
private fun logTagColor(tag: String): Color = when (tag) {
    "Http" -> Color(0xFF5578EB)
    "Security" -> Color(0xFF16A34A)
    "AutoBackup" -> Color(0xFF9D66FE)
    "App" -> Color(0xFF64748B)
    "Nav" -> Color(0xFF0EA5E9)
    "Cache" -> Color(0xFFF59E0B)
    else -> Color(0xFF64748B)
}

@Composable
private fun DateSeparator(date: String) {
    Text(
        date,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun LogRow(entry: LogEntry) {
    val tagColor = logTagColor(entry.tag)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Etiqueta de categoria
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = tagColor.copy(alpha = 0.12f),
            modifier = Modifier.width(74.dp),
        ) {
            Text(
                entry.tag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tagColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp).fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                LogSanitizer.redact(entry.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                entry.timeFormatted(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}

// Avisa do pagamento em atraso. Antes do fim do prazo mostra os dias que
// faltam; depois, que a conta está limitada. Ver ScanPastDueSubscriptionsSchedule.
@Composable
internal fun PaymentOverdueBanner(
    isSuspended: Boolean,
    daysLeft: Int,
) {
    val container = if (isSuspended) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val onContainer = if (isSuspended) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp)) {
            Icon(Icons.Outlined.Warning, contentDescription = null, tint = onContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (isSuspended) "Conta limitada".tr() else "Pagamento em atraso".tr(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isSuspended) {
                        "Os uploads, partilhas e equipas estão pausados. Os teus ficheiros continuam acessíveis para download.".tr()
                    } else {
                        trPlural(
                            "Falta %d dia para regularizar. Depois disso, uploads e partilhas ficam pausados.",
                            "Faltam %d dias para regularizar. Depois disso, uploads e partilhas ficam pausados.",
                            daysLeft,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
        }
    }
}

/** Só para comparar frases-passe sem as guardar em claro no estado. */
private fun sha256Hex(text: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
