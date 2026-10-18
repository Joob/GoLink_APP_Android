package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.outlined.CurrencyBitcoin
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.outlined.Language
import co.golink.tester.data.LogEntry
import co.golink.tester.data.LogSanitizer
import co.golink.tester.ui.i18n.tr
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.billing.Plan
import co.golink.tester.domain.settings.AccessToken
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
    data object Encryption : SettingsRoute
    data object Storage : SettingsRoute
    data object SecurityActivity : SettingsRoute
    data object Sessions : SettingsRoute
    data object Billing : SettingsRoute
    data object AppSecurity : SettingsRoute
    data object Dashboard : SettingsRoute
    data object Analytics : SettingsRoute
    data object Users : SettingsRoute
    data object InviteRegisters : SettingsRoute
    data object News : SettingsRoute
    data object DeleteAccount : SettingsRoute
}

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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
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
            "dashboard" -> SettingsRoute.Dashboard
            "analytics" -> SettingsRoute.Analytics
            "users" -> SettingsRoute.Users
            "invites" -> SettingsRoute.InviteRegisters
            "news" -> SettingsRoute.News
            else -> SettingsRoute.Menu
        }
    }
    var route by remember { mutableStateOf<SettingsRoute>(entryRoute) }

    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHost.showSnackbar(it); viewModel.consumeToast() }
    }

    LaunchedEffect(route) {
        if (route == SettingsRoute.Billing && state.transactions.isEmpty() && !state.isLoadingTransactions) {
            viewModel.loadTransactions()
        }
        if (route == SettingsRoute.Billing) {
            viewModel.loadPlans()
        }
        if (route == SettingsRoute.SecurityActivity && state.securityEvents.isEmpty() && !state.isLoadingSecurityEvents) {
            viewModel.loadSecurityEvents()
        }
    }

    val uriHandlerForCheckout = LocalUriHandler.current
    LaunchedEffect(state.checkoutUrl) {
        state.checkoutUrl?.let { url ->
            uriHandlerForCheckout.openUri(url)
            viewModel.consumeCheckoutUrl()
        }
    }

    state.pendingCheckoutPlan?.let { plan ->
        PaymentMethodDialog(
            plan = plan,
            onDismiss = viewModel::dismissPaymentMethods,
            onStripe = { viewModel.startStripeCheckout(plan) },
            onCrypto = { viewModel.startCryptoCheckout(plan) },
        )
    }

    BackHandler(enabled = route != SettingsRoute.Menu && route != entryRoute) {
        route = SettingsRoute.Menu
    }

    val title = when (route) {
        SettingsRoute.Menu -> "Definições".tr()
        SettingsRoute.Profile -> "Perfil".tr()
        SettingsRoute.Password -> "Password e acesso".tr()
        SettingsRoute.Encryption -> "Encriptação".tr()
        SettingsRoute.Storage -> "Armazenamento".tr()
        SettingsRoute.Sessions -> "Sessões ativas".tr()
        SettingsRoute.SecurityActivity -> "Atividade de segurança".tr()
        SettingsRoute.Billing -> "Plano e donativos".tr()
        SettingsRoute.AppSecurity -> "Bloqueio da app".tr()
        SettingsRoute.Dashboard -> "Painel".tr()
        SettingsRoute.Analytics -> "Análises".tr()
        SettingsRoute.Users -> "Utilizadores".tr()
        SettingsRoute.InviteRegisters -> "Registos de Convite".tr()
        SettingsRoute.News -> "Notícias".tr()
        SettingsRoute.DeleteAccount -> "Apagar conta".tr()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                    onUpdateField = viewModel::updateProfileField,
                    onUpdateAvatar = viewModel::updateAvatar,
                )
                SettingsRoute.Password -> PasswordPane(
                    currentEmail = user?.email.orEmpty(),
                    emailChange = state,
                    onRequestEmailChange = viewModel::requestEmailChange,
                    onConfirmEmailChange = viewModel::confirmEmailChange,
                    onCancelEmailChange = viewModel::cancelEmailChange,
                    isLoading = state.isUpdatingPassword,
                    onSubmit = viewModel::updatePassword,
                    tokens = state.tokens,
                    isLoadingTokens = state.isLoadingTokens,
                    newTokenPlaintext = state.newTokenPlaintext,
                    onCreateToken = viewModel::createToken,
                    onDeleteToken = viewModel::deleteToken,
                    onConsumeNewToken = viewModel::consumeNewToken,
                    twoFactorEnabled = state.twoFactorEnabled,
                    backendUrl = backendUrl,
                    onRefreshUser = viewModel::refreshUser,
                    onResetCsrf = viewModel::resetCsrf,
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
                    isLoading = state.isLoadingStorage,
                    onRefresh = viewModel::loadStorage,
                    onFreeSpace = onOpenTrash,
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
                    transactions = state.transactions,
                    isLoading = state.isLoadingTransactions,
                    plans = state.plans,
                    isLoadingPlans = state.isLoadingPlans,
                    isStartingCheckout = state.isStartingCheckout,
                    onUpgrade = viewModel::selectPlan,
                    onRefresh = viewModel::loadTransactions,
                )
                SettingsRoute.News -> NewsAdminPane()
                SettingsRoute.DeleteAccount -> DeleteAccountPane(userEmail = user?.email)
                SettingsRoute.Dashboard -> DashboardPane()
                SettingsRoute.Analytics -> AnalyticsPane()
                SettingsRoute.Users -> AdminUsersPane()
                SettingsRoute.InviteRegisters -> InviteRegistersPane()
                SettingsRoute.AppSecurity -> AppSecurityPane(
                    user = user,
                    biometricEnabled = state.biometricEnabled,
                    pinEnabled = state.pinEnabled,
                    hasPinSet = viewModel.hasPinSet,
                    onBiometricToggle = viewModel::setBiometricEnabled,
                    onEnablePin = viewModel::enablePin,
                    onDisablePin = viewModel::disablePin,
                    onClearCache = viewModel::clearCache,
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
    backup: co.golink.tester.data.backup.AutoBackupState,
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
            // Cartão de perfil (mockup): avatar, nome, e-mail, › para o Perfil e espaço usado.
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = settingsCardColor(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
            Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(SettingsRoute.Profile) }
                    .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 14.dp),
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
                                .background(MaterialTheme.colorScheme.onSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                u.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "?" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface,
                            )
                        }
                    }
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
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            // Espaço usado, com a cor do nível (verde / âmbar / vermelho).
            storage?.let { st ->
                val barColor = when (st.level) {
                    co.golink.tester.domain.settings.StorageLevel.DANGER -> SettingsColors.Red
                    co.golink.tester.domain.settings.StorageLevel.WARNING -> SettingsColors.Amber
                    else -> SettingsColors.Green
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(SettingsRoute.Storage) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%s de %s".tr().format(st.used, st.capacity),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${st.percentage.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (st.percentage / 100f).coerceIn(0f, 1f) },
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                }
            }
            }
            }
        }

        Spacer(Modifier.height(12.dp))

        AutoBackupFeaturedRow(backup = backup, onClick = onOpenAutoBackup)

        Spacer(Modifier.height(20.dp))

        SettingsGroup(title = "Conta".tr()) {
            SettingsNavRow(Icons.Outlined.Person, SettingsColors.Blue, "Perfil".tr(), "Nome, e-mail e fotografia".tr()) { onNavigate(SettingsRoute.Profile) }
            SettingsNavRow(Icons.Outlined.Lock, SettingsColors.Indigo, "Password", "Password de acesso e tokens".tr()) { onNavigate(SettingsRoute.Password) }
            SettingsNavRow(Icons.Outlined.VpnKey, SettingsColors.Green, "Encriptação".tr(), "Frase-passe e impressão digital da chave".tr()) { onNavigate(SettingsRoute.Encryption) }
            SettingsNavRow(
                Icons.Outlined.CloudQueue, SettingsColors.Teal, "Armazenamento".tr(), "Espaço utilizado e disponível".tr(),
                showDivider = false,
            ) { onNavigate(SettingsRoute.Storage) }
        }

        Spacer(Modifier.height(20.dp))

        SettingsGroup(title = "Segurança".tr()) {
            SettingsNavRow(
                Icons.Outlined.Devices, SettingsColors.Orange, "Sessões ativas".tr(), "Dispositivos com sessão iniciada".tr(),
                trailing = activeSessions.takeIf { it > 0 }?.toString(),
            ) { onNavigate(SettingsRoute.Sessions) }
            SettingsNavRow(Icons.Outlined.Shield, SettingsColors.Red, "Atividade de segurança".tr(), "Inícios de sessão e alterações".tr()) { onNavigate(SettingsRoute.SecurityActivity) }
            SettingsNavRow(Icons.Outlined.Fingerprint, SettingsColors.Purple, "Bloqueio da app".tr(), "Impressão digital e PIN".tr(), showDivider = false) { onNavigate(SettingsRoute.AppSecurity) }
        }

        Spacer(Modifier.height(20.dp))

        SettingsGroup(title = "Plano e ajuda".tr()) {
            val hasSupport = user?.role != "admin"
            SettingsNavRow(Icons.Outlined.CreditCard, SettingsColors.Amber, "Plano e pagamentos".tr(), "Donativos e histórico".tr(), showDivider = hasSupport) { onNavigate(SettingsRoute.Billing) }
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutConfirm = true }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, tint = SettingsColors.Red, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(16.dp))
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
private fun AutoBackupFeaturedRow(backup: co.golink.tester.data.backup.AutoBackupState, onClick: () -> Unit) {
    val on = backup.enabled
    val what = when {
        backup.includeImages && backup.includeVideos -> "fotos e vídeos deste telemóvel".tr()
        backup.includeVideos -> "vídeos deste telemóvel".tr()
        else -> "fotos deste telemóvel".tr()
    }
    // Cartão claro (mockup): mosaico verde, estado e indicador ✓.
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = settingsCardColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SettingsColors.Green.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = SettingsColors.Green, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Backup automático".tr(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (on) "Ativo · %s".tr().format(what) else "Desativado · toca para configurar".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) SettingsColors.Green else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (on) SettingsColors.Green else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) = SettingsSectionTitle(text)

/** Título de secção dos painéis (cinzento, fora do cartão) com ação opcional à direita. */
@Composable
private fun PaneSection(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp)) {
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
private fun PaneCard(padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = settingsCardColor(),
        border = settingsCardBorderStroke(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = if (padded) Modifier.padding(16.dp) else Modifier,
            verticalArrangement = if (padded) Arrangement.spacedBy(12.dp) else Arrangement.Top,
            content = content,
        )
    }
}

@Composable
private fun PaneColumn(content: @Composable ColumnScope.() -> Unit) {
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
    onUpdateField: (name: String, value: String) -> Unit,
    onUpdateAvatar: (ByteArray) -> Unit,
) {
    if (user == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var firstName by remember(user.id) { mutableStateOf(user.firstName.orEmpty()) }
    var lastName by remember(user.id) { mutableStateOf(user.lastName.orEmpty()) }
    var phoneNumber by remember(user.id) { mutableStateOf(user.phoneNumber.orEmpty()) }
    var address by remember(user.id) { mutableStateOf(user.address.orEmpty()) }
    var city by remember(user.id) { mutableStateOf(user.city.orEmpty()) }
    var postalCode by remember(user.id) { mutableStateOf(user.postalCode.orEmpty()) }
    var country by remember(user.id) { mutableStateOf(user.country.orEmpty()) }
    var state by remember(user.id) { mutableStateOf(user.state.orEmpty()) }
    var timezone by remember(user.id) { mutableStateOf(user.timezone.orEmpty()) }
    var billingOpen by remember { mutableStateOf(false) }

    // Só os campos alterados vão ao servidor; sem alterações o botão fica inativo.
    val changes = listOf(
        "first_name" to (firstName to user.firstName.orEmpty()),
        "last_name" to (lastName to user.lastName.orEmpty()),
        "timezone" to (timezone to user.timezone.orEmpty()),
        "address" to (address to user.address.orEmpty()),
        "city" to (city to user.city.orEmpty()),
        "postal_code" to (postalCode to user.postalCode.orEmpty()),
        "state" to (state to user.state.orEmpty()),
        "country" to (country to user.country.orEmpty()),
        "phone_number" to (phoneNumber to user.phoneNumber.orEmpty()),
    ).filter { (_, v) -> v.first.trim() != v.second.trim() && v.first.isNotBlank() }
    val dirty = changes.isNotEmpty()

    // Fotografia: escolher → recortar → enviar (igual ao menu).
    var pickedImage by remember { mutableStateOf<android.net.Uri?>(null) }
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

    PaneColumn {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Box(
                modifier = Modifier.size(76.dp).clip(CircleShape).clickable(onClick = pickAvatar),
                contentAlignment = Alignment.Center,
            ) {
                if (user.avatar != null) {
                    AsyncImage(model = user.avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface), contentAlignment = Alignment.Center) {
                        Text(
                            user.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "?" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = pickAvatar,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) { Text("Mudar fotografia".tr(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface) }
        }

        // O e-mail muda-se em "Password e acesso" (código de verificação).
        PaneSection("Os teus dados".tr())
        PaneCard {
            SettingsField("Nome".tr(), firstName, { firstName = it })
            SettingsField("Apelido".tr(), lastName, { lastName = it }, placeholder = "Opcional".tr())
            Column {
                Text("Fuso horário".tr(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                TimezoneDropdown(value = timezone, onValueChange = { timezone = it })
            }
        }

        // Faturação: opcional, recolhida por omissão.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { billingOpen = !billingOpen }
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dados de faturação".tr(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Opcional · só para recibos de donativos".tr(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
            Icon(
                if (billingOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = billingOpen) {
            PaneCard {
                SettingsField("Morada".tr(), address, { address = it })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsField("Cidade".tr(), city, { city = it }, modifier = Modifier.weight(1f))
                    SettingsField("Código postal".tr(), postalCode, { postalCode = it }, modifier = Modifier.weight(1f))
                }
                Column {
                    Text("País".tr(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    CountryDropdown(value = country, onValueChange = { country = it })
                }
                SettingsField("Estado/Região".tr(), state, { state = it }, placeholder = "Se aplicável, ex.: Lisboa".tr())
                SettingsField("Telefone".tr(), phoneNumber, { phoneNumber = it }, keyboardType = KeyboardType.Phone)
            }
        }

        SettingsPrimaryButton(
            text = if (dirty) "Guardar alterações".tr() else "Sem alterações".tr(),
            onClick = { changes.forEach { (field, v) -> onUpdateField(field, v.first.trim()) } },
            enabled = dirty,
            loading = isUpdating,
        )
    }
}

private val TIMEZONES = listOf(
    "-12.0" to "(GMT -12:00) Eniwetok, Kwajalein",
    "-11.0" to "(GMT -11:00) Midway Island, Samoa",
    "-10.0" to "(GMT -10:00) Hawaii",
    "-9.0" to "(GMT -9:00) Alaska",
    "-8.0" to "(GMT -8:00) Pacific Time (US & Canada)",
    "-7.0" to "(GMT -7:00) Mountain Time (US & Canada)",
    "-6.0" to "(GMT -6:00) Central Time (US & Canada), Mexico City",
    "-5.0" to "(GMT -5:00) Eastern Time (US & Canada), Bogota, Lima",
    "-4.0" to "(GMT -4:00) Atlantic Time (Canada), Caracas, La Paz",
    "-3.5" to "(GMT -3:30) Newfoundland",
    "-3.0" to "(GMT -3:00) Brazil, Buenos Aires, Georgetown",
    "-2.0" to "(GMT -2:00) Mid-Atlantic",
    "-1.0" to "(GMT -1:00) Azores, Cape Verde Islands",
    "0.0" to "(GMT) Western Europe Time, London, Lisbon, Casablanca",
    "1.0" to "(GMT +1:00) Brussels, Copenhagen, Madrid, Paris",
    "2.0" to "(GMT +2:00) Kaliningrad, South Africa",
    "3.0" to "(GMT +3:00) Baghdad, Riyadh, Moscow, St. Petersburg",
    "3.5" to "(GMT +3:30) Tehran",
    "4.0" to "(GMT +4:00) Abu Dhabi, Muscat, Baku, Tbilisi",
    "4.5" to "(GMT +4:30) Kabul",
    "5.0" to "(GMT +5:00) Ekaterinburg, Islamabad, Karachi, Tashkent",
    "5.5" to "(GMT +5:30) Bombay, Calcutta, Madras, New Delhi",
    "5.75" to "(GMT +5:45) Kathmandu",
    "6.0" to "(GMT +6:00) Almaty, Dhaka, Colombo",
    "7.0" to "(GMT +7:00) Bangkok, Hanoi, Jakarta",
    "8.0" to "(GMT +8:00) Beijing, Perth, Singapore, Hong Kong",
    "9.0" to "(GMT +9:00) Tokyo, Seoul, Osaka, Sapporo, Yakutsk",
    "9.5" to "(GMT +9:30) Adelaide, Darwin",
    "10.0" to "(GMT +10:00) Eastern Australia, Guam, Vladivostok",
    "11.0" to "(GMT +11:00) Magadan, Solomon Islands, New Caledonia",
    "12.0" to "(GMT +12:00) Auckland, Wellington, Fiji, Kamchatka",
)

private val COUNTRIES = listOf(
    "PT" to "Portugal", "ES" to "Spain", "FR" to "France", "DE" to "Germany",
    "IT" to "Italy", "GB" to "United Kingdom", "IE" to "Ireland", "NL" to "Netherlands",
    "BE" to "Belgium", "LU" to "Luxembourg", "CH" to "Switzerland", "AT" to "Austria",
    "DK" to "Denmark", "SE" to "Sweden", "NO" to "Norway", "FI" to "Finland",
    "PL" to "Poland", "CZ" to "Czech Republic", "SK" to "Slovakia", "HU" to "Hungary",
    "RO" to "Romania", "BG" to "Bulgaria", "GR" to "Greece", "HR" to "Croatia",
    "SI" to "Slovenia", "EE" to "Estonia", "LV" to "Latvia", "LT" to "Lithuania",
    "US" to "United States", "CA" to "Canada", "MX" to "Mexico", "BR" to "Brazil",
    "AR" to "Argentina", "CL" to "Chile", "CO" to "Colombia", "PE" to "Peru",
    "AU" to "Australia", "NZ" to "New Zealand", "JP" to "Japan", "KR" to "South Korea",
    "CN" to "China", "HK" to "Hong Kong", "TW" to "Taiwan", "SG" to "Singapore",
    "IN" to "India", "ID" to "Indonesia", "TH" to "Thailand", "VN" to "Vietnam",
    "PH" to "Philippines", "MY" to "Malaysia", "AE" to "United Arab Emirates",
    "SA" to "Saudi Arabia", "IL" to "Israel", "TR" to "Turkey", "EG" to "Egypt",
    "ZA" to "South Africa", "NG" to "Nigeria", "KE" to "Kenya", "MA" to "Morocco",
    "AO" to "Angola", "MZ" to "Mozambique", "CV" to "Cape Verde",
    "RU" to "Russia", "UA" to "Ukraine",
)

@Composable
private fun TimezoneDropdown(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = TIMEZONES.firstOrNull { it.first == value }?.second ?: value.ifBlank { "" }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                focusedBorderColor = SettingsAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
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
private fun CountryDropdown(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = COUNTRIES.firstOrNull { it.first.equals(value, ignoreCase = true) }?.second ?: value
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                focusedBorderColor = SettingsAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            COUNTRIES.forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onValueChange(v); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun PasswordPane(
    currentEmail: String,
    emailChange: SettingsUiState,
    onRequestEmailChange: (email: String, password: String) -> Unit,
    onConfirmEmailChange: (code: String) -> Unit,
    onCancelEmailChange: () -> Unit,
    isLoading: Boolean,
    onSubmit: (current: String, new: String) -> Unit,
    tokens: List<AccessToken>,
    isLoadingTokens: Boolean,
    newTokenPlaintext: String?,
    onCreateToken: (String) -> Unit,
    onDeleteToken: (Long) -> Unit,
    onConsumeNewToken: () -> Unit,
    twoFactorEnabled: Boolean,
    backendUrl: String,
    onRefreshUser: () -> Unit,
    onResetCsrf: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var show2faInfo by remember { mutableStateOf(false) }
    if (show2faInfo) {
        AlertDialog(
            onDismissRequest = { show2faInfo = false },
            title = { Text("Gerir 2FA".tr()) },
            text = {
                Text(
                    if (twoFactorEnabled)
                        "A autenticação de dois fatores está activa nesta conta.\n\nPara desactivar ou ver os códigos de recuperação, abre as definições da conta no browser."
                    else
                        "Por questões de segurança, a configuração inicial do 2FA (leitura do código QR e códigos de recuperação) é feita no browser. Após activar, esta sessão móvel reflectirá o estado actualizado.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = {
                    val url = backendUrl.trimEnd('/') + "/user/settings/password"
                    uriHandler.openUri(url)
                    show2faInfo = false
                }) { Text("Abrir no browser".tr()) }
            },
            dismissButton = {
                TextButton(onClick = { onRefreshUser(); show2faInfo = false }) { Text("Actualizar estado".tr()) }
            },
        )
    }
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = newPassword.isNotEmpty() && confirmation.isNotEmpty() && newPassword != confirmation
    // 8 = mesma política do registo e do servidor (Fortify\Rules\Password).
    val tooShort = newPassword.isNotEmpty() && newPassword.length < MIN_PASSWORD_LENGTH
    val canSubmit = !isLoading && current.isNotBlank() &&
        newPassword.length >= MIN_PASSWORD_LENGTH && newPassword == confirmation

    var showCreateTokenDialog by remember { mutableStateOf(false) }
    var newTokenName by remember { mutableStateOf("") }

    val clipboard = LocalClipboardManager.current

    if (showCreateTokenDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTokenDialog = false; newTokenName = "" },
            title = { Text("Novo token de acesso".tr()) },
            text = {
                OutlinedTextField(
                    value = newTokenName,
                    onValueChange = { newTokenName = it },
                    label = { Text("Nome do token".tr()) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { onCreateToken(newTokenName.trim()); showCreateTokenDialog = false; newTokenName = "" },
                    enabled = newTokenName.isNotBlank(),
                ) { Text("Criar".tr()) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTokenDialog = false; newTokenName = "" }) { Text("Cancelar".tr()) }
            },
        )
    }

    // Força simples (4 traços): comprimento, maiúsculas+minúsculas, números, símbolos.
    val strength = listOf(
        newPassword.length >= MIN_PASSWORD_LENGTH,
        newPassword.any { it.isUpperCase() } && newPassword.any { it.isLowerCase() },
        newPassword.any { it.isDigit() },
        newPassword.any { !it.isLetterOrDigit() },
    ).count { it }

    PaneColumn {
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
            SettingsField("Password atual".tr(), current, { current = it }, isPassword = true)
            Column {
                SettingsField("Nova password".tr(), newPassword, { newPassword = it }, isPassword = true, isError = tooShort)
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
                Spacer(Modifier.height(4.dp))
                Text(
                    "Mínimo 8 caracteres".tr(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tooShort) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsField(
                "Confirmar nova password".tr(), confirmation, { confirmation = it }, isPassword = true,
                isError = mismatch, supportingText = if (mismatch) "Não coincide".tr() else null,
            )
            Text(
                "Por segurança, terminamos a sessão nos outros dispositivos.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsPrimaryButton(
                text = "Guardar nova password".tr(),
                onClick = { onSubmit(current, newPassword); current = ""; newPassword = ""; confirmation = "" },
                enabled = canSubmit,
                loading = isLoading,
            )
        }

        EmailChangeSection(
            currentEmail = currentEmail,
            state = emailChange,
            onRequest = onRequestEmailChange,
            onConfirm = onConfirmEmailChange,
            onCancel = onCancelEmailChange,
        )

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
                        IconButton(onClick = { clipboard.setText(AnnotatedString(token)); onConsumeNewToken() }) {
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
                SettingsListRow(
                    icon = Icons.Outlined.VpnKey,
                    title = token.name,
                    subtitle = token.last_used_at?.let { "Último uso: %s".tr().format(it) }
                        ?: token.created_at?.let { "Criado: %s".tr().format(it) },
                    showDivider = i < tokens.lastIndex,
                ) {
                    Text(
                        "Revogar".tr(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onDeleteToken(token.id) }.padding(6.dp),
                    )
                }
            }
        }

        PaneSection("Resolução de problemas".tr())
        PaneCard {
            Column {
                Text("Repor token de sessão (CSRF)".tr(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Usa só se tiveres erros ao iniciar sessão ou ao guardar.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onResetCsrf,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Repor token".tr(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) }
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
    state: SettingsUiState,
    onRequest: (email: String, password: String) -> Unit,
    onConfirm: (code: String) -> Unit,
    onCancel: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
            editing = false; newEmail = ""; password = ""; code = ""
        }
    }

    PaneSection("E-mail da conta".tr())

    if (!editing && !inCodeStep) {
        PaneCard(padded = false) {
            SettingsListRow(icon = Icons.Outlined.Email, title = currentEmail) {
                Text(
                    "Mudar".tr(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SettingsAccent,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { editing = true }.padding(6.dp),
                )
            }
        }
        return
    }

    PaneCard {
    if (!inCodeStep) {
        SettingsField("Novo endereço de email".tr(), newEmail, { newEmail = it }, keyboardType = KeyboardType.Email)
        SettingsField("Password atual".tr(), password, { password = it }, isPassword = true)
    } else {
        Text(
            "Enviámos um código de 6 dígitos para %s.".tr().format(state.emailChangeTarget),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
            singleLine = true,
            label = { Text("Código de confirmação".tr()) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (secondsLeft > 0) "O código expira em %ds".tr().format(secondsLeft)
            else "O código expirou. Pede um novo.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    state.emailChangeError?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    val canSubmit = !state.isChangingEmail && if (inCodeStep) code.length == 6 && secondsLeft > 0
        else android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail.trim()).matches() && password.isNotBlank()

    SettingsPrimaryButton(
        text = if (inCodeStep) "Confirmar alteração".tr() else "Enviar código".tr(),
        onClick = { if (inCodeStep) onConfirm(code) else onRequest(newEmail, password) },
        enabled = canSubmit,
        loading = state.isChangingEmail,
    )
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        if (inCodeStep) {
            TextButton(
                onClick = { onRequest(state.emailChangeTarget, password) },
                enabled = !state.isChangingEmail,
            ) { Text("Reenviar código".tr()) }
        }
        TextButton(onClick = { onCancel(); editing = false; newEmail = ""; password = ""; code = "" }) {
            Text("Cancelar".tr())
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
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onFreeSpace: () -> Unit,
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
                types.forEach { t -> StorageLegendRow(t.color, t.label, t.usage?.used.orEmpty()) }
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
private fun StorageLegendRow(color: Color, label: String, value: String, muted: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
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
        co.golink.tester.ui.components.dialogs.ConfirmDialog(
            title = if (s.is_current) "Revogar sessão actual?".tr() else "Revogar esta sessão?".tr(),
            message = if (s.is_current)
                "Vais ser desconectado deste dispositivo imediatamente.".tr()
            else "A sessão será terminada e o dispositivo terá de iniciar sessão novamente.".tr(),
            confirmText = "Revogar",
            destructive = true,
            onDismiss = { pendingRevoke = null },
            onConfirm = { onRevoke(s.id); pendingRevoke = null },
        )
    }
    if (showRevokeAll) {
        co.golink.tester.ui.components.dialogs.ConfirmDialog(
            title = "Terminar outras sessões?".tr(),
            message = "Todas as outras sessões serão revogadas. Continuarás autenticado neste dispositivo.".tr(),
            confirmText = "Terminar todas".tr(),
            destructive = true,
            onDismiss = { showRevokeAll = false },
            onConfirm = { onRevokeAll(); showRevokeAll = false },
        )
    }

    // The session currently viewing this list IS this Android app on a phone,
    // so always treat it as mobile even if the backend still has it labelled
    // "web" (e.g. it was created before the app sent a mobile User-Agent).
    val webSessions = sessions.filter { !it.isMobileSession() }
    val mobileSessions = sessions.filter { it.isMobileSession() }
    val hasOthers = sessions.any { !it.is_current }

    PaneColumn {
        Text(
            "Dispositivos com sessão iniciada na tua conta.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (isLoading && sessions.isEmpty()) {
            PaneCard { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        SessionsGroup(
            title = "Neste e noutros telemóveis".tr(),
            sessions = mobileSessions,
            emptyText = "Sem sessões móveis ativas.".tr(),
            onRevoke = { pendingRevoke = it },
            action = if (isLoading) "A atualizar…".tr() else "Atualizar".tr(),
            onAction = if (isLoading) null else onRefresh,
        )
        SessionsGroup(
            title = "Browsers".tr(),
            sessions = webSessions,
            emptyText = "Sem sessões web ativas.".tr(),
            onRevoke = { pendingRevoke = it },
        )
        if (hasOthers) {
            OutlinedButton(
                onClick = { showRevokeAll = true },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(vertical = 13.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Text("Terminar todas as outras sessões".tr(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * A session is shown as mobile when the backend labels it non-"web", OR when
 * it is the current session — because this list is being viewed from the
 * Android app, so the active session is necessarily this phone.
 */
private fun SessionItem.isMobileSession(): Boolean =
    is_current || (platform ?: "").lowercase() != "web"

/** Eventos que indicam possível compromisso — destacados a vermelho. */
private val SECURITY_ALERT_EVENTS = setOf(
    "account.new_device",
    "auth.login_failed",
    "auth.otp_locked",
    "account.password_changed",
    "account.e2e_secret_rotated",
    "account.email_changed",
    "account.email_reverted",
    "account.deletion_scheduled",
)

private fun securityEventLabel(event: String): String = when (event) {
    "auth.login" -> "Início de sessão".tr()
    "auth.login_failed" -> "Tentativa de início de sessão falhada".tr()
    "auth.otp_failed" -> "Código de segurança incorrecto".tr()
    "auth.otp_locked" -> "Bloqueio temporário após demasiadas tentativas".tr()
    "account.new_device" -> "Início de sessão de um dispositivo novo".tr()
    "account.password_changed" -> "Password alterada".tr()
    "account.e2e_secret_rotated" -> "Segredo de encriptação alterado".tr()
    "account.email_changed" -> "Email alterado".tr()
    "account.email_reverted" -> "Email reposto".tr()
    "account.deletion_scheduled" -> "Eliminação da conta agendada".tr()
    "account.deletion_cancelled" -> "Eliminação da conta cancelada".tr()
    else -> "Evento de segurança".tr()
}

/** ISO-8601 do servidor → data local legível; se falhar mostra o original. */
private fun formatSecurityEventDate(value: String?): String {
    if (value.isNullOrBlank()) return ""

    return runCatching {
        val instant = java.time.Instant.parse(value)
        java.time.format.DateTimeFormatter
            .ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM)
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault(value)
}

@Composable
private fun SecurityActivityPane(
    events: List<SecurityEventData>,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
) {
    PaneColumn {
        Text(
            "Eventos de segurança recentes na tua conta. Se vires algo que não reconheces, altera a password e revê as sessões activas.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        PaneCard(padded = false) {
            when {
                isLoading && events.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                events.isEmpty() -> Text(
                    "Ainda não há atividade de segurança registada.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            events.forEachIndexed { i, entry ->
                val attrs = entry.attributes
                val isAlert = attrs.event in SECURITY_ALERT_EVENTS
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp).size(20.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            securityEventLabel(attrs.event),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        val meta = listOfNotNull(formatSecurityEventDate(attrs.created_at).ifBlank { null }, attrs.ip).joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        attrs.user_agent?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (i < events.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), modifier = Modifier.padding(start = 52.dp, end = 16.dp))
                }
            }
        }
        if (hasMore) {
            TextButton(onClick = onLoadMore, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                Text("Carregar mais".tr(), color = SettingsAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SessionsGroup(
    title: String,
    sessions: List<SessionItem>,
    emptyText: String,
    onRevoke: (SessionItem) -> Unit,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    PaneSection(title, action = action, onAction = onAction)
    PaneCard(padded = false) {
        if (sessions.isEmpty()) {
            Text(emptyText, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            sessions.forEachIndexed { i, s ->
                SessionRow(s, onRevoke = { onRevoke(s) })
                if (i < sessions.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), modifier = Modifier.padding(start = 52.dp, end = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: SessionItem, onRevoke: () -> Unit) {
    val isMobile = s.isMobileSession()
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
    ) {
        Icon(
            if (isMobile) Icons.Filled.Smartphone else Icons.Outlined.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.device ?: s.browser ?: "Dispositivo desconhecido".tr(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (s.is_current) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF16A34A).copy(alpha = 0.15f),
                    ) {
                        Text(
                            "Este telemóvel".tr(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = SettingsAccent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            s.login_at?.let {
                Text("Iniciada: %s".tr().format(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val ipLine = listOfNotNull(s.ip_address, s.location).joinToString(" · ")
            if (ipLine.isNotBlank()) {
                Text(ipLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.browser?.takeIf { it.isNotBlank() && it != s.device }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.last_activity_at?.takeIf { it != s.login_at }?.let {
                Text("Última atividade: %s".tr().format(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Revogar".tr(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onRevoke).padding(6.dp),
        )
    }
}

@Composable
private fun BillingPane(
    user: User?,
    transactions: List<TransactionItem>,
    isLoading: Boolean,
    plans: List<Plan>,
    isLoadingPlans: Boolean,
    isStartingCheckout: Boolean,
    onUpgrade: (Plan) -> Unit,
    onRefresh: () -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    var showYearly by remember { mutableStateOf(false) }
    val hasYearly = plans.any { it.interval == "year" }
    val visiblePlans = if (hasYearly) plans.filter { it.interval == if (showYearly) "year" else "month" } else plans
    var selectedId by remember(visiblePlans) {
        mutableStateOf(visiblePlans.firstOrNull { !it.name.equals(user?.planName, ignoreCase = true) }?.id ?: visiblePlans.firstOrNull()?.id)
    }
    val selected = visiblePlans.firstOrNull { it.id == selectedId }

    PaneColumn {
        if (user?.isPastDue == true || user?.isPaymentSuspended == true) {
            PaymentOverdueBanner(isSuspended = user.isPaymentSuspended, daysLeft = user.daysLeftToPay)
        }
        // Plano atual + atalho para o histórico de pagamentos.
        PaneCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Plano atual".tr(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(user?.planName ?: "Gratuito".tr(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (showHistory) "Fechar".tr() else "Histórico".tr(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SettingsAccent,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showHistory = !showHistory; if (showHistory && transactions.isEmpty()) onRefresh() }.padding(6.dp),
                )
            }
            AnimatedVisibility(visible = showHistory) {
                Column {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    when {
                        isLoading && transactions.isEmpty() -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        transactions.isEmpty() -> Text("Sem transações".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                        else -> transactions.forEachIndexed { k, t ->
                            TransactionRow(t)
                            if (k < transactions.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            Text("Apoiar o GoLink".tr(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "O GoLink é gratuito e sem fins lucrativos. Os donativos mantêm o serviço para todos.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            isLoadingPlans -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            plans.isEmpty() -> Text("Sem planos disponíveis.".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                if (hasYearly) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(3.dp),
                    ) {
                        listOf(false to "Mensal".tr(), true to "Anual".tr()).forEach { (yearly, label) ->
                            val on = showYearly == yearly
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) settingsCardColor() else Color.Transparent)
                                    .clickable { showYearly = yearly }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
                visiblePlans.forEach { plan ->
                    PlanOptionRow(
                        plan = plan,
                        selected = plan.id == selectedId,
                        isCurrent = user?.planName?.equals(plan.name, ignoreCase = true) == true,
                        onClick = { selectedId = plan.id },
                    )
                }
                PaneCard {
                    Text(
                        "Todos incluem: encriptação ponta-a-ponta AES-256, SSL/TLS, dados privados e descentralizados, sem metadados e suporte.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                selected?.let { plan ->
                    val isCurrent = user?.planName?.equals(plan.name, ignoreCase = true) == true
                    SettingsPrimaryButton(
                        text = if (isCurrent) "Plano atual".tr()
                        else listOfNotNull("Apoiar com %s".tr().format(plan.name), plan.price).joinToString(" · "),
                        onClick = { onUpgrade(plan) },
                        enabled = !isCurrent,
                        loading = isStartingCheckout,
                    )
                }
            }
        }
    }
}

// Opção de plano (rádio): nome, "100 GB · 8 membros" e preço/intervalo.
@Composable
private fun PlanOptionRow(plan: Plan, selected: Boolean, isCurrent: Boolean, onClick: () -> Unit) {
    val storage = plan.features["max_storage_amount"]?.let { if (it >= 1000) "${it / 1000} TB" else "$it GB" }
    val members = plan.features["max_team_members"]?.let { if (it == -1L) "membros ilimitados".tr() else "%d membros".tr().format(it) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SettingsAccent.copy(alpha = 0.06f) else settingsCardColor(),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) SettingsAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) SettingsAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(SettingsAccent))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plan.name + if (isCurrent) " · " + "atual".tr() else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                listOfNotNull(storage, members).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            plan.price?.let { price ->
                Text(
                    price + (plan.interval?.let { "/" + planIntervalLabel(it) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun planIntervalLabel(interval: String): String = when (interval) {
    "month" -> "mês".tr()
    "year" -> "ano".tr()
    else -> interval
}

@Composable
private fun TransactionRow(t: TransactionItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                t.note ?: t.type ?: "Transação".tr(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            t.createdAt?.takeIf { it.isNotBlank() }?.let { date ->
                Spacer(Modifier.height(2.dp))
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        t.price?.let { price ->
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                t.status?.let { status ->
                    val (statusLabel, statusColor) = when (status) {
                        "completed" -> "concluído".tr() to MaterialTheme.colorScheme.primary
                        "cancelled" -> "cancelado" to MaterialTheme.colorScheme.error
                        "error" -> "erro" to MaterialTheme.colorScheme.error
                        "pending" -> "pendente" to MaterialTheme.colorScheme.onSurfaceVariant
                        else -> status to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }
        }
    }
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
    var currentError by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var changeToken by remember { mutableStateOf<String?>(null) }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    val minLen = 12
    val tooShort = newPass.isNotEmpty() && newPass.length < minLen
    val sameAsCurrent = newPass.isNotEmpty() && newPass == currentPass
    val mismatch = confirmPass.isNotEmpty() && newPass != confirmPass
    val canSubmit = newPass.length >= minLen && !sameAsCurrent && newPass == confirmPass && !isChanging && changeToken != null
    fun resetFlow() {
        step = "current"; currentPass = ""; currentError = null; changeToken = null; newPass = ""; confirmPass = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SettingsPaneHeader(Icons.Outlined.VpnKey, SettingsColors.Green, "Encriptação".tr(), "Os ficheiros são cifrados no dispositivo. Gere aqui a chave e a passphrase.".tr())
        Spacer(Modifier.height(12.dp))
        // Impressão da chave pública (deteta key-substitution).
        SectionLabel("Impressão da chave de encriptação".tr())
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = settingsCardColor(),
            border = settingsCardBorderStroke(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Confirma que é igual nos teus outros dispositivos — um código diferente pode indicar troca de chave.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (e2eFingerprint != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e2eFingerprint,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { clipboard.setText(AnnotatedString(e2eFingerprint)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar".tr(), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    Text(
                        "Desbloqueia a encriptação para veres a impressão da chave.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Mudar passphrase: re-embrulha a chave privada (em memória) com o novo
        // segredo. NÃO re-cifra ficheiros — continuam acessíveis com a nova passphrase.
        SectionLabel("Alterar passphrase de encriptação".tr())
        Text(
            "Muda a passphrase que desbloqueia a tua encriptação. Os teus ficheiros NÃO são re-cifrados — continuam acessíveis com a nova passphrase.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (!e2eUnlocked) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Desbloqueia a encriptação primeiro para poderes alterar a passphrase.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Text(
                when (step) {
                    "current" -> "Passo 1 de 3 · Passphrase atual".tr()
                    "code" -> "Passo 2 de 3 · Código enviado para o teu email".tr()
                    else -> "Passo 3 de 3 · Nova passphrase".tr()
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            when (step) {
                "current" -> {
                    PasswordField(
                        label = "Passphrase atual".tr(),
                        value = currentPass,
                        onChange = { currentPass = it; currentError = null },
                        isError = currentError != null,
                        errorText = currentError,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            verifying = true
                            scope.launch {
                                val ok = runCatching { onVerifyCurrent(currentPass) }.getOrDefault(false)
                                verifying = false
                                if (ok) step = "code" else currentError = "A passphrase atual está incorreta.".tr()
                            }
                        },
                        enabled = currentPass.isNotEmpty() && !verifying,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SettingsAccent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (verifying) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Continuar".tr(), fontWeight = FontWeight.SemiBold)
                    }
                }
                "code" -> co.golink.tester.ui.components.SecretCodeStep(
                    requestCode = requestCode,
                    confirmCode = confirmCode,
                    onConfirmed = { token -> changeToken = token; step = "new" },
                )
                else -> {
                    PasswordField(
                        label = "Nova passphrase".tr(),
                        value = newPass,
                        onChange = { newPass = it },
                        isError = tooShort || sameAsCurrent,
                        errorText = when {
                            tooShort -> "Mínimo %d caracteres".tr().format(minLen)
                            sameAsCurrent -> "A nova passphrase tem de ser diferente da atual.".tr()
                            else -> null
                        },
                    )
                    PasswordField(
                        label = "Confirmar nova passphrase".tr(),
                        value = confirmPass,
                        onChange = { confirmPass = it },
                        isError = mismatch,
                        errorText = if (mismatch) "Não coincide".tr() else null,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onChangePassphrase(newPass, changeToken.orEmpty()) { ok, expired ->
                                when {
                                    ok -> resetFlow()
                                    expired -> { changeToken = null; step = "code" }
                                }
                            }
                        },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SettingsAccent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isChanging) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Outlined.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Alterar passphrase".tr(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (step != "current") {
                TextButton(onClick = ::resetFlow, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancelar".tr())
                }
            }
        }

        Spacer(Modifier.height(16.dp))
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
    onDisablePin: (String) -> Boolean,
    onClearCache: () -> Unit,
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

    // PIN disable dialog
    var showPinDisable by remember { mutableStateOf(false) }
    var pinDisableInput by remember { mutableStateOf("") }
    var pinDisableError by remember { mutableStateOf<String?>(null) }

    // Log dialog
    var showLog by remember { mutableStateOf(false) }

    // PIN setup dialogs
    if (pinStep == 1) {
        AlertDialog(
            onDismissRequest = { pinStep = 0; pinInput = ""; pinError = null },
            title = { Text("Definir PIN".tr()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Introduz um PIN de 4 dígitos para bloquear a aplicação.".tr(), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pinInput = it; pinError = null } },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = pinError != null,
                        supportingText = pinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length < 4) { pinError = "O PIN deve ter 4 dígitos".tr(); return@TextButton }
                    pinFirst = pinInput; pinInput = ""; pinStep = 2
                }) { Text("Seguinte".tr()) }
            },
            dismissButton = {
                TextButton(onClick = { pinStep = 0; pinInput = ""; pinError = null }) { Text("Cancelar".tr()) }
            },
        )
    }
    if (pinStep == 2) {
        AlertDialog(
            onDismissRequest = { pinStep = 0; pinInput = ""; pinFirst = ""; pinError = null },
            title = { Text("Confirmar PIN".tr()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Repete o PIN para confirmar.".tr(), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pinInput = it; pinError = null } },
                        label = { Text("Confirmar PIN".tr()) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = pinError != null,
                        supportingText = pinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput != pinFirst) { pinError = "Os PINs não coincidem".tr(); return@TextButton }
                    onEnablePin(pinInput)
                    pinStep = 0; pinInput = ""; pinFirst = ""; pinError = null
                }) { Text("Activar".tr()) }
            },
            dismissButton = {
                TextButton(onClick = { pinStep = 0; pinInput = ""; pinFirst = ""; pinError = null }) { Text("Cancelar".tr()) }
            },
        )
    }

    // PIN disable dialog
    if (showPinDisable) {
        AlertDialog(
            onDismissRequest = { showPinDisable = false; pinDisableInput = ""; pinDisableError = null },
            title = { Text("Desactivar PIN".tr()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Introduz o PIN actual para desactivar o bloqueio.".tr(), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = pinDisableInput,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pinDisableInput = it; pinDisableError = null } },
                        label = { Text("PIN actual".tr()) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = pinDisableError != null,
                        supportingText = pinDisableError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!onDisablePin(pinDisableInput)) { pinDisableError = "PIN incorrecto".tr(); return@TextButton }
                    showPinDisable = false; pinDisableInput = ""; pinDisableError = null
                }) { Text("Desactivar") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDisable = false; pinDisableInput = ""; pinDisableError = null }) { Text("Cancelar".tr()) }
            },
        )
    }

    // Log dialog
    if (showLog) {
        LogDialog(user = user, entries = logEntries, onClear = onClearLog, onDismiss = { showLog = false })
    }

    val enableBiometric = {
        val executor = ContextCompat.getMainExecutor(context)
        BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onBiometricToggle(true)
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Activar biométrico".tr())
                .setSubtitle("Confirma a tua identidade para activar".tr())
                .setNegativeButtonText("Cancelar".tr())
                .build(),
        )
    }
    val bioOn = biometricEnabled && biometricAvailable

    PaneColumn {
        Text(
            "Protege a app neste telemóvel, mesmo que alguém o tenha desbloqueado.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        PaneCard(padded = false) {
            SettingsListRow(
                icon = Icons.Outlined.Lock,
                title = "Pedir PIN ao abrir".tr(),
                subtitle = if (pinEnabled) "Ligado".tr() else "Desligado".tr(),
                subtitleColor = if (pinEnabled) SettingsAccent else Color.Unspecified,
                showDivider = true,
                onClick = { if (!pinEnabled) pinStep = 1 else if (hasPinSet) showPinDisable = true },
            ) {
                SettingsCheckSquare(checked = pinEnabled) { if (!pinEnabled) pinStep = 1 else if (hasPinSet) showPinDisable = true }
            }
            SettingsListRow(
                icon = Icons.Outlined.Fingerprint,
                title = "Impressão digital".tr(),
                subtitle = when {
                    !biometricAvailable -> "Não disponível neste telemóvel".tr()
                    bioOn -> "Ligada".tr()
                    else -> "Desligada".tr()
                },
                subtitleColor = if (bioOn) SettingsAccent else Color.Unspecified,
                enabled = biometricAvailable,
                onClick = { if (bioOn) onBiometricToggle(false) else enableBiometric() },
            ) {
                if (biometricAvailable) SettingsCheckSquare(checked = bioOn) { if (bioOn) onBiometricToggle(false) else enableBiometric() }
            }
        }
        Text(
            "O bloqueio é pedido sempre que a app fica em segundo plano mais de 30 segundos.".tr(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
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
                subtitle = "Não apaga os teus ficheiros".tr(),
                showDivider = true,
                onClick = onClearCache,
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
                        Text("Registo de atividade".tr(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (entries.isEmpty()) "Sem entradas".tr() else "${entries.size} entrada${if (entries.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Limpar log".tr(), modifier = Modifier.size(20.dp))
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
                        entries.forEach { entry ->
                            val date = entry.dateFormatted()
                            if (date != lastDate) {
                                lastDate = date
                                item(key = "date_$date") { DateSeparator(date) }
                            }
                            item(key = "${entry.timestamp}_${entry.message.hashCode()}") {
                                LogRow(entry)
                            }
                        }
                    }
                }

                MenuDivider()
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(end = 10.dp),
                ) { Text("Fechar".tr()) }
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.name.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
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

// Espelha o seletor de método da web (SubscribeAccountPopup.vue): cartão via
// Stripe ou cripto via NOWPayments. Ambos abrem uma página externa.
@Composable
private fun PaymentMethodDialog(
    plan: Plan,
    onDismiss: () -> Unit,
    onStripe: () -> Unit,
    onCrypto: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Método de pagamento".tr()) },
        text = {
            Column {
                Text(
                    plan.name + (plan.price?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                if (plan.stripePriceId != null) {
                    PaymentMethodRow(
                        icon = Icons.Outlined.CreditCard,
                        title = "Cartão".tr(),
                        description = "Renovação automática todos os meses.".tr(),
                        onClick = onStripe,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                PaymentMethodRow(
                    icon = Icons.Outlined.CurrencyBitcoin,
                    title = "Criptomoeda".tr(),
                    description = "Bitcoin, Ethereum, Dogecoin e mais. Cada período é pago manualmente.".tr(),
                    onClick = onCrypto,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar".tr()) }
        },
    )
}

@Composable
private fun PaymentMethodRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Avisa do pagamento em atraso. Antes do fim do prazo mostra os dias que
// faltam; depois, que a conta está limitada. Ver ScanPastDueSubscriptionsSchedule.
@Composable
private fun PaymentOverdueBanner(
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
                        "Faltam {days} dia(s) para regularizar. Depois disso, uploads e partilhas ficam pausados."
                            .tr()
                            .replace("{days}", daysLeft.toString())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
        }
    }
}
