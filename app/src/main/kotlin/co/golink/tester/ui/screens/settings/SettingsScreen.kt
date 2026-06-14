package co.golink.tester.ui.screens.settings

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
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
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
import co.golink.tester.data.LogEntry
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.billing.Plan
import co.golink.tester.domain.settings.AccessToken
import co.golink.tester.domain.settings.SessionItem
import co.golink.tester.domain.settings.StorageUsage
import co.golink.tester.domain.settings.TransactionItem
import co.golink.tester.domain.user.User
import coil.compose.AsyncImage

private sealed interface SettingsRoute {
    data object Menu : SettingsRoute
    data object Profile : SettingsRoute
    data object Password : SettingsRoute
    data object Storage : SettingsRoute
    data object Sessions : SettingsRoute
    data object Billing : SettingsRoute
    data object AppSecurity : SettingsRoute
    data object News : SettingsRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    initialRoute: String? = null,
    onOpenAutoBackup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var route by remember {
        mutableStateOf<SettingsRoute>(
            when (initialRoute) {
                "billing" -> SettingsRoute.Billing
                "profile" -> SettingsRoute.Profile
                "storage" -> SettingsRoute.Storage
                else -> SettingsRoute.Menu
            }
        )
    }

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
    }

    val uriHandlerForCheckout = LocalUriHandler.current
    LaunchedEffect(state.checkoutUrl) {
        state.checkoutUrl?.let { url ->
            uriHandlerForCheckout.openUri(url)
            viewModel.consumeCheckoutUrl()
        }
    }

    BackHandler(enabled = route != SettingsRoute.Menu) {
        route = SettingsRoute.Menu
    }

    val title = when (route) {
        SettingsRoute.Menu -> "Definições"
        SettingsRoute.Profile -> "Perfil"
        SettingsRoute.Password -> "Password"
        SettingsRoute.Storage -> "Armazenamento"
        SettingsRoute.Sessions -> "Sessões"
        SettingsRoute.Billing -> "Faturação"
        SettingsRoute.AppSecurity -> "Segurança da app"
        SettingsRoute.News -> "Notícias"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (route == SettingsRoute.Menu) onBack() else route = SettingsRoute.Menu
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                SettingsRoute.Menu -> MenuPane(
                    user = user,
                    onNavigate = { route = it },
                    onOpenAutoBackup = onOpenAutoBackup,
                    onLogout = viewModel::logout,
                )
                SettingsRoute.Profile -> ProfilePane(
                    user = user,
                    isUpdating = state.isUpdatingProfile,
                    onUpdateField = viewModel::updateProfileField,
                )
                SettingsRoute.Password -> PasswordPane(
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
                SettingsRoute.Storage -> StoragePane(
                    storage = state.storage,
                    isLoading = state.isLoadingStorage,
                    onRefresh = viewModel::loadStorage,
                )
                SettingsRoute.Sessions -> SessionsPane(
                    sessions = state.sessions,
                    isLoading = state.isLoadingSessions,
                    onRevoke = viewModel::revokeSession,
                    onRevokeAll = viewModel::revokeAllSessions,
                    onRefresh = viewModel::loadSessions,
                )
                SettingsRoute.Billing -> BillingPane(
                    user = user,
                    transactions = state.transactions,
                    isLoading = state.isLoadingTransactions,
                    plans = state.plans,
                    isLoadingPlans = state.isLoadingPlans,
                    isStartingCheckout = state.isStartingCheckout,
                    onUpgrade = viewModel::startStripeCheckout,
                    onRefresh = viewModel::loadTransactions,
                )
                SettingsRoute.News -> NewsAdminPane()
                SettingsRoute.AppSecurity -> AppSecurityPane(
                    biometricEnabled = state.biometricEnabled,
                    pinEnabled = state.pinEnabled,
                    hasPinSet = viewModel.hasPinSet,
                    onBiometricToggle = viewModel::setBiometricEnabled,
                    onEnablePin = viewModel::enablePin,
                    onDisablePin = viewModel::disablePin,
                    onClearCache = viewModel::clearCache,
                    logEntries = state.logEntries,
                    onRefreshLog = viewModel::refreshLog,
                    onClearLog = viewModel::clearLog,
                )
            }
        }
    }
}

@Composable
private fun MenuPane(
    user: User?,
    onNavigate: (SettingsRoute) -> Unit,
    onOpenAutoBackup: () -> Unit,
    onLogout: () -> Unit,
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        co.golink.tester.ui.components.dialogs.ConfirmDialog(
            title = "Sair da Conta?",
            message = "Tens a certeza que queres sair da tua conta?",
            confirmText = "Sair",
            destructive = true,
            onDismiss = { showLogoutConfirm = false },
            onConfirm = onLogout,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        user?.let { u ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(16.dp),
            ) {
                if (u.avatar != null) {
                    AsyncImage(
                        model = u.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            u.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        u.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
                u.planName?.let { plan ->
                    Spacer(Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            plan,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        AutoBackupFeaturedRow(onClick = onOpenAutoBackup)

        Spacer(Modifier.height(16.dp))

        MenuGroup {
            MenuRow(Icons.Outlined.AccountCircle, "Perfil", "Nome e detalhes da conta") { onNavigate(SettingsRoute.Profile) }
            MenuDivider()
            MenuRow(Icons.Outlined.Lock, "Password", "Alterar a tua password") { onNavigate(SettingsRoute.Password) }
            MenuDivider()
            MenuRow(Icons.Outlined.CloudQueue, "Armazenamento", "Espaço utilizado e disponível") { onNavigate(SettingsRoute.Storage) }
            MenuDivider()
            MenuRow(Icons.Outlined.Devices, "Sessões", "Dispositivos e sessões activas") { onNavigate(SettingsRoute.Sessions) }
        }

        Spacer(Modifier.height(16.dp))

        MenuGroup {
            MenuRow(Icons.Outlined.Receipt, "Faturação", "Histórico de transações") { onNavigate(SettingsRoute.Billing) }
            MenuDivider()
            MenuRow(Icons.Outlined.ShieldMoon, "Segurança da app", "Biométrico e PIN") { onNavigate(SettingsRoute.AppSecurity) }
        }

        if (user?.role == "admin") {
            Spacer(Modifier.height(16.dp))
            MenuGroup {
                MenuRow(Icons.Outlined.Campaign, "Notícias", "Notícia importante na zona de files") { onNavigate(SettingsRoute.News) }
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { showLogoutConfirm = true },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Terminar sessão")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MenuGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f * alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
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
private fun AutoBackupFeaturedRow(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(co.golink.tester.ui.theme.BrandGreen, co.golink.tester.ui.theme.BrandGreenDark),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Backups Automáticos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.22f),
                    ) {
                        Text(
                            "NOVO",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Copia as tuas fotos e vídeos automaticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ProfilePane(
    user: User?,
    isUpdating: Boolean,
    onUpdateField: (name: String, value: String) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SectionLabel("Email")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = user.email,
                    onValueChange = {},
                    label = { Text("Email") },
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "O email não pode ser alterado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Definições da conta")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Primeiro nome") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Apelido") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
                TimezoneDropdown(
                    value = timezone,
                    onValueChange = { timezone = it },
                )
                Button(
                    onClick = {
                        if (firstName.isNotBlank()) onUpdateField("first_name", firstName.trim())
                        if (lastName.isNotBlank()) onUpdateField("last_name", lastName.trim())
                        if (timezone.isNotBlank()) onUpdateField("timezone", timezone.trim())
                    },
                    enabled = !isUpdating,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Guardar definições", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Informações de faturação")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Morada") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Cidade") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text("Código postal") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
                CountryDropdown(value = country, onValueChange = { country = it })
                OutlinedTextField(
                    value = state,
                    onValueChange = { state = it },
                    label = { Text("Estado/Região") },
                    placeholder = { Text("Se aplicável, ex.: Lisboa") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Telefone") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (address.isNotBlank()) onUpdateField("address", address.trim())
                        if (city.isNotBlank()) onUpdateField("city", city.trim())
                        if (postalCode.isNotBlank()) onUpdateField("postal_code", postalCode.trim())
                        if (state.isNotBlank()) onUpdateField("state", state.trim())
                        if (country.isNotBlank()) onUpdateField("country", country.trim())
                        if (phoneNumber.isNotBlank()) onUpdateField("phone_number", phoneNumber.trim())
                    },
                    enabled = !isUpdating,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Guardar informações", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
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
            label = { Text("Fuso horário (GMT)") },
            trailingIcon = {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            },
            shape = RoundedCornerShape(12.dp),
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
            label = { Text("País") },
            trailingIcon = {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            },
            shape = RoundedCornerShape(12.dp),
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
            title = { Text("Gerir 2FA") },
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
                }) { Text("Abrir no browser") }
            },
            dismissButton = {
                TextButton(onClick = { onRefreshUser(); show2faInfo = false }) { Text("Actualizar estado") }
            },
        )
    }
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = newPassword.isNotEmpty() && confirmation.isNotEmpty() && newPassword != confirmation
    val tooShort = newPassword.isNotEmpty() && newPassword.length < 6
    val canSubmit = !isLoading && current.isNotBlank() && newPassword.length >= 6 && newPassword == confirmation

    var showCreateTokenDialog by remember { mutableStateOf(false) }
    var newTokenName by remember { mutableStateOf("") }

    val clipboard = LocalClipboardManager.current

    if (showCreateTokenDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTokenDialog = false; newTokenName = "" },
            title = { Text("Novo token de acesso") },
            text = {
                OutlinedTextField(
                    value = newTokenName,
                    onValueChange = { newTokenName = it },
                    label = { Text("Nome do token") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { onCreateToken(newTokenName.trim()); showCreateTokenDialog = false; newTokenName = "" },
                    enabled = newTokenName.isNotBlank(),
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTokenDialog = false; newTokenName = "" }) { Text("Cancelar") }
            },
        )
    }

    val pink = Color(0xFFEC4899)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 2FA Card
        InfoCard(
            icon = Icons.Outlined.Security,
            iconTint = if (twoFactorEnabled) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
            iconBg = (if (twoFactorEnabled) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
            title = "Autenticação de Dois Fatores",
            description = if (twoFactorEnabled)
                "Activa nesta conta — toca em \"Gerir\" para abrir as definições no browser."
            else
                "Adicione uma camada extra de segurança. Configura no browser.",
            trailing = {
                Button(
                    onClick = { show2faInfo = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (twoFactorEnabled) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                    ),
                ) { Text(if (twoFactorEnabled) "Gerir" else "Activar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            },
        )

        // Personal Access Token Card
        InfoCard(
            icon = Icons.Outlined.VpnKey,
            iconTint = MaterialTheme.colorScheme.primary,
            iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            title = "Token de Acesso Pessoal",
            description = if (tokens.isEmpty()) "Você não tem tokens de acesso pessoal criados ainda."
            else "Tem ${tokens.size} token${if (tokens.size > 1) "s" else ""} de acesso pessoal.",
            trailing = {
                Button(
                    onClick = { showCreateTokenDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text("Criar Token", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            },
        )

        // Newly created token plaintext (shows once)
        newTokenPlaintext?.let { token ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Token criado — guarda-o agora, não será mostrado novamente.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            token,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { clipboard.setText(AnnotatedString(token)); onConsumeNewToken() }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Existing tokens list (if any)
        if (tokens.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    tokens.forEachIndexed { i, token ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(token.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                token.last_used_at?.let {
                                    Text("Último uso: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } ?: token.created_at?.let {
                                    Text("Criado: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onDeleteToken(token.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Apagar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (i < tokens.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        // CSRF reset card
        InfoCard(
            icon = Icons.Outlined.Refresh,
            iconTint = pink,
            iconBg = pink.copy(alpha = 0.12f),
            title = "Redefinir meu CSRF",
            description = "Redefinir seu token CSRF pode ajudar a resolver problemas de sessão em algumas situações.",
            trailing = {
                Button(
                    onClick = onResetCsrf,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = pink),
                ) { Text("Resetar meu CSRF", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            },
        )

        // Alterar password section
        Spacer(Modifier.height(8.dp))
        Text(
            "Alterar password",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )

        PasswordField(label = "Password actual", value = current, onChange = { current = it })
        PasswordField(label = "Nova password", value = newPassword, onChange = { newPassword = it }, isError = tooShort, errorText = if (tooShort) "Mínimo 6 caracteres" else null)
        PasswordField(label = "Confirmar nova password", value = confirmation, onChange = { confirmation = it }, isError = mismatch, errorText = if (mismatch) "Não coincide" else null)

        Text(
            "Por segurança, você será desconectado de outros dispositivos sempre que alterar a password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Button(
            onClick = { onSubmit(current, newPassword); current = ""; newPassword = ""; confirmation = "" },
            enabled = canSubmit,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Guardar nova password", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    description: String,
    trailing: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
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
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = isError,
            supportingText = errorText?.let { { Text(it) } },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StoragePane(
    storage: StorageUsage?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val rangeOptions = remember {
        listOf(
            "1 semana" to 7,
            "2 semanas" to 14,
            "3 semanas" to 21,
            "1 mês" to 30,
            "45 dias" to 45,
        )
    }
    var rangeIndex by remember { mutableStateOf(rangeOptions.lastIndex) }
    val (rangeLabel, rangeDays) = rangeOptions[rangeIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isLoading && storage == null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        if (storage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rangeOptions.forEachIndexed { i, (label, _) ->
                    val selected = i == rangeIndex
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.clickable { rangeIndex = i },
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val isLow = storage.percentage >= 90f
            val accentColor = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            StorageFeatureCard(
                icon = if (isLow) Icons.Outlined.Warning else Icons.Outlined.CloudQueue,
                iconColor = accentColor,
                title = "Armazenamento utilizado",
                subtitle = if (isLow) "Espaço quase esgotado — ${storage.used} de ${storage.capacity}" else "${storage.used} de ${storage.capacity}",
                progress = (storage.percentage / 100f).coerceIn(0f, 1f),
                trailing = {
                    Text(
                        "${storage.percentage.toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                },
            )

            storage.trafficUpload?.let { up ->
                StorageFeatureCard(
                    icon = Icons.Outlined.Upload,
                    iconColor = Color(0xFF2196F3),
                    title = "Upload",
                    subtitle = "Últimos $rangeLabel",
                    trailing = {
                        Text(
                            up,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    chartData = storage.trafficChartUpload?.mapNotNull { it.percentage }?.takeLast(rangeDays),
                )
            }

            storage.trafficDownload?.let { down ->
                StorageFeatureCard(
                    icon = Icons.Outlined.Download,
                    iconColor = Color(0xFF4CAF50),
                    title = "Download",
                    subtitle = "Últimos $rangeLabel",
                    trailing = {
                        Text(
                            down,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    chartData = storage.trafficChartDownload?.mapNotNull { it.percentage }?.takeLast(rangeDays),
                )
            }

            // File types section
            data class FileTypeEntry(val label: String, val usage: co.golink.tester.domain.settings.StorageTypeUsage, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

            val fileTypes = listOfNotNull(
                storage.videos?.let { FileTypeEntry("Vídeos", it, Icons.Outlined.Videocam, Color(0xFF7C4DFF)) },
                storage.images?.let { FileTypeEntry("Imagens", it, Icons.Outlined.Image, Color(0xFF43A047)) },
                storage.documents?.let { FileTypeEntry("Documentos", it, Icons.Outlined.Description, Color(0xFF1E88E5)) },
                storage.others?.let { FileTypeEntry("Outros", it, Icons.Outlined.FolderOpen, Color(0xFF9E9E9E)) },
                storage.audios?.let { FileTypeEntry("Áudios", it, Icons.Outlined.MusicNote, Color(0xFFFF8F00)) },
            ).filter { it.usage.used != null }

            if (fileTypes.isNotEmpty()) {
                var detailsExpanded by remember { mutableStateOf(true) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailsExpanded = !detailsExpanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(
                                "Detalhes por tipo",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (detailsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AnimatedVisibility(visible = detailsExpanded) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                fileTypes.forEachIndexed { i, entry ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(entry.color.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(entry.icon, contentDescription = null, tint = entry.color, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            entry.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            buildString {
                                                append(entry.usage.used ?: "")
                                                entry.usage.percentage?.let { pct ->
                                                    append(" (${if (pct < 1f) "~0" else "${pct.toInt()}"}%)")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (i < fileTypes.lastIndex) {
                                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onRefresh,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("A actualizar…")
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Atualizar")
            }
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun StorageFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    progress: Float? = null,
    chartData: List<Float>? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (progress != null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = iconColor,
                    trackColor = iconColor.copy(alpha = 0.15f),
                )
            }
            if (!chartData.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                Sparkline(
                    data = chartData,
                    color = iconColor,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                )
            }
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
            title = if (s.is_current) "Revogar sessão actual?" else "Revogar esta sessão?",
            message = if (s.is_current)
                "Vais ser desconectado deste dispositivo imediatamente."
            else "A sessão será terminada e o dispositivo terá de iniciar sessão novamente.",
            confirmText = "Revogar",
            destructive = true,
            onDismiss = { pendingRevoke = null },
            onConfirm = { onRevoke(s.id); pendingRevoke = null },
        )
    }
    if (showRevokeAll) {
        co.golink.tester.ui.components.dialogs.ConfirmDialog(
            title = "Terminar outras sessões?",
            message = "Todas as outras sessões serão revogadas. Continuarás autenticado neste dispositivo.",
            confirmText = "Terminar todas",
            destructive = true,
            onDismiss = { showRevokeAll = false },
            onConfirm = { onRevokeAll(); showRevokeAll = false },
        )
    }

    val webSessions = sessions.filter { (it.platform ?: "").lowercase() == "web" }
    val mobileSessions = sessions.filter { (it.platform ?: "").lowercase() != "web" }
    val hasOthers = sessions.any { !it.is_current }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isLoading && sessions.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        SessionsGroup(
            title = "Sessões Web",
            icon = Icons.Outlined.Devices,
            description = "Dispositivos com sessão iniciada via browser.",
            sessions = webSessions,
            emptyText = "Sem sessões web activas.",
            onRevoke = { pendingRevoke = it },
        )

        SessionsGroup(
            title = "Sessões Mobile",
            icon = Icons.Filled.Smartphone,
            description = "Dispositivos móveis com sessão iniciada.",
            sessions = mobileSessions,
            emptyText = "Sem sessões móveis activas.",
            onRevoke = { pendingRevoke = it },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("A actualizar…")
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Atualizar")
                }
            }
        }

        if (hasOthers) {
            Button(
                onClick = { showRevokeAll = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Terminar todas as outras sessões")
            }
        }
    }
}

@Composable
private fun SessionsGroup(
    title: String,
    icon: ImageVector,
    description: String,
    sessions: List<SessionItem>,
    emptyText: String,
    onRevoke: (SessionItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                if (sessions.isEmpty()) {
                    Text(
                        emptyText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sessions.forEachIndexed { i, s ->
                        SessionRow(s, onRevoke = { onRevoke(s) })
                        if (i < sessions.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: SessionItem, onRevoke: () -> Unit) {
    val accent = if (s.is_current) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
    val isMobile = (s.platform ?: "").lowercase() != "web"
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isMobile) Icons.Filled.Smartphone else Icons.Outlined.Devices,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.device ?: s.browser ?: "Dispositivo desconhecido",
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
                            "ACTUAL",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            s.login_at?.let {
                Text("Iniciada: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val ipLine = listOfNotNull(s.ip_address, s.location).joinToString(" · ")
            if (ipLine.isNotBlank()) {
                Text(ipLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.browser?.takeIf { it.isNotBlank() && it != s.device }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.last_activity_at?.takeIf { it != s.login_at }?.let {
                Text("Última actividade: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onRevoke,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        ) {
            Text("Revogar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
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
    var showPlansDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        InfoCard(icon = Icons.Outlined.Edit, iconContentDescription = "Plano") {
            Text("Plano", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                user?.planName ?: "Plano Gratuito",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Atualize a sua conta para obter mais.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showPlansDialog = true },
                enabled = !isStartingCheckout,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                if (isStartingCheckout) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Atualizar Conta", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        InfoCard(
            icon = Icons.Outlined.Receipt,
            iconContentDescription = "Transações",
            trailing = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        ) {
            Text("Transações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            when {
                isLoading && transactions.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                transactions.isEmpty() -> {
                    Text(
                        "Sem transações",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    transactions.forEachIndexed { i, t ->
                        TransactionRow(t)
                        if (i < transactions.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }
    }

    if (showPlansDialog) {
        PlansDialog(
            plans = plans,
            isLoading = isLoadingPlans,
            currentPlanName = user?.planName,
            onDismiss = { showPlansDialog = false },
            onSelect = { plan ->
                showPlansDialog = false
                onUpgrade(plan)
            },
        )
    }
}

@Composable
private fun PlansDialog(
    plans: List<Plan>,
    isLoading: Boolean,
    currentPlanName: String?,
    onDismiss: () -> Unit,
    onSelect: (Plan) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher plano", fontWeight = FontWeight.SemiBold) },
        text = {
            when {
                isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                plans.isEmpty() -> Text(
                    "Sem planos disponíveis no momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    plans.forEach { plan ->
                        val isCurrent = currentPlanName?.equals(plan.name, ignoreCase = true) == true
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable(enabled = !isCurrent && plan.stripePriceId != null) { onSelect(plan) },
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (plan.price != null) {
                                        Text(
                                            plan.price + (plan.interval?.let { " / $it" } ?: ""),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (!plan.description.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        plan.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isCurrent) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Plano atual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                } else if (plan.stripePriceId == null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Indisponível para checkout móvel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    iconContentDescription: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = iconContentDescription,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (trailing != null) {
                    Spacer(Modifier.weight(1f))
                    trailing()
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun TransactionRow(t: TransactionItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                t.note ?: t.type ?: "Transação",
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
                        "completed" -> "concluído" to MaterialTheme.colorScheme.primary
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
private fun AppSecurityPane(
    biometricEnabled: Boolean,
    pinEnabled: Boolean,
    hasPinSet: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    onEnablePin: (String) -> Unit,
    onDisablePin: (String) -> Boolean,
    onClearCache: () -> Unit,
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
            title = { Text("Definir PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Introduz um PIN de 4 dígitos para bloquear a aplicação.", style = MaterialTheme.typography.bodyMedium)
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
                    if (pinInput.length < 4) { pinError = "O PIN deve ter 4 dígitos"; return@TextButton }
                    pinFirst = pinInput; pinInput = ""; pinStep = 2
                }) { Text("Seguinte") }
            },
            dismissButton = {
                TextButton(onClick = { pinStep = 0; pinInput = ""; pinError = null }) { Text("Cancelar") }
            },
        )
    }
    if (pinStep == 2) {
        AlertDialog(
            onDismissRequest = { pinStep = 0; pinInput = ""; pinFirst = ""; pinError = null },
            title = { Text("Confirmar PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Repete o PIN para confirmar.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pinInput = it; pinError = null } },
                        label = { Text("Confirmar PIN") },
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
                    if (pinInput != pinFirst) { pinError = "Os PINs não coincidem"; return@TextButton }
                    onEnablePin(pinInput)
                    pinStep = 0; pinInput = ""; pinFirst = ""; pinError = null
                }) { Text("Activar") }
            },
            dismissButton = {
                TextButton(onClick = { pinStep = 0; pinInput = ""; pinFirst = ""; pinError = null }) { Text("Cancelar") }
            },
        )
    }

    // PIN disable dialog
    if (showPinDisable) {
        AlertDialog(
            onDismissRequest = { showPinDisable = false; pinDisableInput = ""; pinDisableError = null },
            title = { Text("Desactivar PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Introduz o PIN actual para desactivar o bloqueio.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = pinDisableInput,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pinDisableInput = it; pinDisableError = null } },
                        label = { Text("PIN actual") },
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
                    if (!onDisablePin(pinDisableInput)) { pinDisableError = "PIN incorrecto"; return@TextButton }
                    showPinDisable = false; pinDisableInput = ""; pinDisableError = null
                }) { Text("Desactivar") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDisable = false; pinDisableInput = ""; pinDisableError = null }) { Text("Cancelar") }
            },
        )
    }

    // Log dialog
    if (showLog) {
        LogDialog(entries = logEntries, onClear = onClearLog, onDismiss = { showLog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SectionLabel("Segurança")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                SecurityToggleRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = "Desbloqueio biométrico",
                    description = if (biometricAvailable) "Usa a impressão digital ou rosto para entrar" else "Não disponível neste dispositivo",
                    checked = biometricEnabled && biometricAvailable,
                    enabled = biometricAvailable,
                    onCheckedChange = { enabled ->
                        if (enabled) {
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
                                    .setTitle("Activar biométrico")
                                    .setSubtitle("Confirma a tua identidade para activar")
                                    .setNegativeButtonText("Cancelar")
                                    .build(),
                            )
                        } else {
                            onBiometricToggle(false)
                        }
                    },
                )
                MenuDivider()
                SecurityToggleRow(
                    icon = Icons.Outlined.Lock,
                    title = "Bloqueio por PIN",
                    description = "Requer PIN para abrir a aplicação",
                    checked = pinEnabled,
                    enabled = true,
                    onCheckedChange = { checked ->
                        if (checked) {
                            pinStep = 1
                        } else if (hasPinSet) {
                            showPinDisable = true
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Quando activo, o bloqueio é pedido sempre que a app fica em segundo plano por mais de 30 segundos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(24.dp))
        SectionLabel("Dados")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                SecurityActionRow(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Limpar cache local",
                    onClick = onClearCache,
                )
                MenuDivider()
                SecurityActionRow(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    title = "Mostrar log",
                    onClick = {
                        onRefreshLog()
                        showLog = true
                    },
                )
            }
        }
    }
}

@Composable
private fun SecurityActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SecurityToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.10f else 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun LogDialog(
    entries: List<LogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Limpar log", modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Sem entradas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
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
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            }
        }
    }
}

@Composable
private fun DateSeparator(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            entry.timeFormatted(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            entry.message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
