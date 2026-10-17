package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Download
import co.golink.tester.ui.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import co.golink.tester.ui.components.shareTimeLeft
import co.golink.tester.ui.components.EXPIRATION_OPTIONS
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.ShareInfo

private enum class SharePanel { None, Qr, Edit }

data class ShareDialogState(
    val item: BrowseItem,
    val share: ShareInfo?,
    val qrSvg: String?,
    val sendingEmail: Boolean,
    val loadingQr: Boolean,
    val isWorking: Boolean,
    val emailDialogVisible: Boolean,
    // E2E: chave (#k=) ainda a ser preparada em fundo → bloquear copiar/QR/email.
    val keyPending: Boolean = false,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    state: ShareDialogState,
    onDismiss: () -> Unit,
    onCreate: (password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?) -> Unit,
    onUpdate: (password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?, protected: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onCopy: (String) -> Unit,
    onShowQr: () -> Unit,
    onSendEmail: (List<String>) -> Unit,
    onShowEmailDialog: (Boolean) -> Unit,
    onRevoke: () -> Unit,
) {
    val item = state.item
    val isFolder = item is BrowseItem.Folder
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        run {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                // Cabeçalho: Partilhar "Pasta 1" · estado do link
                Text(
                    "Partilhar “%s”".tr().format(item.name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.share != null) "Link ativo · qualquer pessoa com o link".tr() else "Cria um link para partilhar".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.share == null) {
                        CreateShareForm(
                            isFolder = isFolder,
                            isWorking = state.isWorking,
                            onCreate = onCreate,
                        )
                    } else {
                        ExistingShareView(
                            share = state.share,
                            isFolder = isFolder,
                            sendingEmail = state.sendingEmail,
                            isWorking = state.isWorking,
                            keyPending = state.keyPending,
                            onCopy = {
                                if (state.keyPending) return@ExistingShareView
                                val link = state.share.link ?: return@ExistingShareView
                                clipboard.setText(AnnotatedString(link))
                                onCopy(link)
                            },
                            onShowEmailDialog = { onShowEmailDialog(true) },
                            onUpdate = onUpdate,
                            onRevoke = onRevoke,
                        )
                    }
                }

            }
        }
    }

    if (state.emailDialogVisible) {
        EmailRecipientsDialog(
            onDismiss = { onShowEmailDialog(false) },
            onConfirm = { emails ->
                // E2E: o email do servidor levaria o link SEM a chave (#k=) — o
                // servidor não a conhece. Para partilhas cifradas (o link já traz
                // #k=) abre-se um rascunho no cliente de email do utilizador com o
                // link completo; a chave nunca sai do dispositivo. Igual à web.
                val link = state.share?.link
                if (link != null && link.contains("#k=")) {
                    val subject = "Partilharam algo contigo".tr()
                    val body = "Abre este link para ver (a chave de desencriptação vai incluída no link):".tr() + "\n\n" + link
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:")
                        putExtra(android.content.Intent.EXTRA_EMAIL, emails.toTypedArray())
                        putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                        putExtra(android.content.Intent.EXTRA_TEXT, body)
                    }
                    runCatching { context.startActivity(intent) }
                    onShowEmailDialog(false)
                } else {
                    onSendEmail(emails)
                }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun PermissionChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = fg,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun PermissionSection(
    permission: String,
    onPermissionChange: (String) -> Unit,
    // Ficheiros: só Ver / Transferir (editar é só em pastas).
    isFolder: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
    ) {
        Text(
            "Permissão".tr(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when (permission) {
                "viewer" -> "Só pré-visualizar".tr()
                "editor" -> "Carregar e alterar".tr()
                else -> "Ver e descarregar".tr()
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // viewer = só pré-visualizar (sem transferir), como na web.
            PermissionChip(
                icon = Icons.Outlined.RemoveRedEye,
                label = "Ver".tr(),
                selected = permission == "viewer",
                modifier = Modifier.weight(1f),
                onClick = { onPermissionChange("viewer") },
            )
            PermissionChip(
                icon = Icons.Outlined.Download,
                label = "Transferir".tr(),
                selected = permission == "visitor",
                modifier = Modifier.weight(1f),
                onClick = { onPermissionChange("visitor") },
            )
            if (isFolder) PermissionChip(
                icon = Icons.Outlined.Edit,
                label = "Editar".tr(),
                selected = permission == "editor",
                modifier = Modifier.weight(1f),
                onClick = { onPermissionChange("editor") },
            )
        }
    }
}

/**
 * Validade + limite de visualizações + limite de downloads, iguais nos
 * formulários de criar e de editar. Visualizações só em ficheiros e exclusivas
 * do limite de downloads (o link só pode prometer uma regra ao visitante).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareLimitsFields(
    isFolder: Boolean,
    hasExpiration: Boolean,
    onHasExpiration: (Boolean) -> Unit,
    expirationMinutes: Int,
    onExpirationMinutes: (Int) -> Unit,
    hasViewLimit: Boolean,
    onHasViewLimit: (Boolean) -> Unit,
    viewLimit: Int,
    onViewLimit: (Int) -> Unit,
    hasDownloadLimit: Boolean,
    onHasDownloadLimit: (Boolean) -> Unit,
    downloadLimit: String,
    onDownloadLimit: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchRow(
            icon = Icons.Outlined.Schedule,
            label = "Expira após".tr(),
            checked = hasExpiration,
            onCheckedChange = onHasExpiration,
        )
        AnimatedVisibility(visible = hasExpiration) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EXPIRATION_OPTIONS.forEach { (minutes, label) ->
                    OptionChip(label = label, selected = expirationMinutes == minutes) { onExpirationMinutes(minutes) }
                }
            }
        }

        if (!isFolder) {
            SwitchRow(
                icon = Icons.Outlined.RemoveRedEye,
                label = "Restringir nº de visualizações".tr(),
                checked = hasViewLimit,
                onCheckedChange = onHasViewLimit,
            )
            AnimatedVisibility(visible = hasViewLimit) {
                NumberStepper(
                    label = "Visualizações máximas".tr(),
                    value = viewLimit,
                    onChange = onViewLimit,
                )
            }
        }

        if (!hasViewLimit || isFolder) {
            SwitchRow(
                icon = Icons.Outlined.Schedule,
                label = "Limite de downloads".tr(),
                checked = hasDownloadLimit,
                onCheckedChange = onHasDownloadLimit,
            )
            AnimatedVisibility(visible = hasDownloadLimit) {
                OutlinedTextField(
                    value = downloadLimit,
                    onValueChange = { v -> onDownloadLimit(v.filter { it.isDigit() }.take(6)) },
                    label = { Text("Número de downloads".tr()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.5.dp, if (selected) primary else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NumberStepper(label: String, value: Int, onChange: (Int) -> Unit, min: Int = 1, max: Int = 100) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        StepButton("−", enabled = value > min) { onChange((value - 1).coerceAtLeast(min)) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center,
        )
        StepButton("+", enabled = value < max) { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun CreateShareForm(
    isFolder: Boolean,
    isWorking: Boolean,
    onCreate: (password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?) -> Unit,
) {
    var protectWithPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("visitor") }
    var hasExpiration by remember { mutableStateOf(false) }
    var expirationMinutes by remember { mutableStateOf(1440) }
    var hasDownloadLimit by remember { mutableStateOf(false) }
    var downloadLimit by remember { mutableStateOf("10") }
    var hasViewLimit by remember { mutableStateOf(false) }
    var viewLimit by remember { mutableStateOf(1) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchRow(
            icon = Icons.Filled.Lock,
            label = "Proteger com password".tr(),
            checked = protectWithPassword,
            onCheckedChange = { protectWithPassword = it },
        )
        AnimatedVisibility(visible = protectWithPassword) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PermissionSection(permission = permission, onPermissionChange = { permission = it }, isFolder = isFolder)

        ShareLimitsFields(
            isFolder = isFolder,
            hasExpiration = hasExpiration,
            onHasExpiration = { hasExpiration = it },
            expirationMinutes = expirationMinutes,
            onExpirationMinutes = { expirationMinutes = it },
            hasViewLimit = hasViewLimit,
            onHasViewLimit = { hasViewLimit = it; if (it) hasDownloadLimit = false },
            viewLimit = viewLimit,
            onViewLimit = { viewLimit = it },
            hasDownloadLimit = hasDownloadLimit,
            onHasDownloadLimit = { hasDownloadLimit = it },
            downloadLimit = downloadLimit,
            onDownloadLimit = { downloadLimit = it },
        )

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                val views = viewLimit.takeIf { hasViewLimit && !isFolder }
                onCreate(
                    password.takeIf { protectWithPassword && it.isNotBlank() },
                    permission,
                    expirationMinutes.takeIf { hasExpiration },
                    downloadLimit.toIntOrNull()?.takeIf { hasDownloadLimit && views == null && it > 0 },
                    views,
                )
            },
            enabled = !isWorking && (!protectWithPassword || password.isNotBlank()) &&
                (hasViewLimit || !hasDownloadLimit || downloadLimit.toIntOrNull()?.let { it > 0 } == true),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Criar link de partilha".tr(), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ExistingShareView(
    share: ShareInfo,
    isFolder: Boolean,
    sendingEmail: Boolean,
    isWorking: Boolean,
    keyPending: Boolean = false,
    onCopy: () -> Unit,
    onShowEmailDialog: () -> Unit,
    onUpdate: (password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?, protected: Boolean) -> Unit,
    onRevoke: () -> Unit,
) {
    var activePanel by remember { mutableStateOf(SharePanel.None) }
    val editMode = activePanel == SharePanel.Edit
    val showQr = activePanel == SharePanel.Qr
    var password by remember { mutableStateOf("") }
    var hasPassword by remember(share.protected) { mutableStateOf(share.protected) }
    var permission by remember(share.permission) { mutableStateOf(share.permission ?: "visitor") }
    // Pré-preenchido com o que está gravado na partilha.
    var hasExpiration by remember(share.expireInMinutes) { mutableStateOf((share.expireInMinutes ?: 0) > 0) }
    var expirationMinutes by remember(share.expireInMinutes) { mutableStateOf(share.expireInMinutes?.takeIf { it > 0 } ?: 1440) }
    var hasDownloadLimit by remember(share.downloadLimit) { mutableStateOf((share.downloadLimit ?: 0) > 0) }
    var downloadLimit by remember(share.downloadLimit) { mutableStateOf(share.downloadLimit?.takeIf { it > 0 }?.toString() ?: "10") }
    var hasViewLimit by remember(share.viewLimit) { mutableStateOf((share.viewLimit ?: 0) > 0) }
    var viewLimit by remember(share.viewLimit) { mutableStateOf(share.viewLimit?.takeIf { it > 0 } ?: 1) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Link: endereço, etiquetas e "Copiar link" em destaque.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(14.dp),
        ) {
            Text(
                (share.link ?: "(sem URL)").removePrefix("https://").removePrefix("http://"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val timeLeft = shareTimeLeft(share.expiresAt)
            if (share.protected || timeLeft != null ||
                (share.downloadLimit != null && share.downloadLimit > 0) ||
                (share.viewLimit != null && share.viewLimit > 0) || !share.permission.isNullOrBlank()
            ) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if (share.protected) TagChip("Com senha".tr())
                    if (!share.permission.isNullOrBlank()) TagChip(prettyPermission(share.permission))
                    timeLeft?.let { TagChip(if (it.expired) it.text else "Expira em".tr() + " " + it.text) }
                    if (share.downloadLimit != null && share.downloadLimit > 0) {
                        TagChip("${share.downloadCount ?: 0}/${share.downloadLimit} downloads")
                    }
                    if (share.viewLimit != null && share.viewLimit > 0) {
                        TagChip("${share.viewCount ?: 0}/${share.viewLimit} " + "visualizações".tr())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onCopy,
                enabled = !keyPending,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 13.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (keyPending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("A preparar a chave de encriptação…".tr(), fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copiar link".tr(), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // E2E: o link leva a chave (#k=).
        if (share.link?.contains("#k=") == true) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "O link inclui a chave para abrir os ficheiros. Envia-o só a quem confias.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF92400E),
                )
            }
        }

        // Enviar… · Código QR · E-mail · Definições
        val shareContext = androidx.compose.ui.platform.LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareActionButton(
                icon = Icons.Filled.Send,
                label = "Enviar…".tr(),
                modifier = Modifier.weight(1f),
                enabled = !keyPending && !share.link.isNullOrBlank(),
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, share.link)
                    }
                    runCatching { shareContext.startActivity(android.content.Intent.createChooser(send, null)) }
                },
            )
            ShareActionButton(
                icon = Icons.Filled.QrCode2,
                label = "Código QR".tr(),
                modifier = Modifier.weight(1f),
                active = showQr,
                enabled = !keyPending,
                onClick = { activePanel = if (showQr) SharePanel.None else SharePanel.Qr },
            )
            ShareActionButton(
                icon = Icons.Filled.Email,
                label = "E-mail",
                modifier = Modifier.weight(1f),
                enabled = !sendingEmail && !keyPending,
                onClick = {
                    activePanel = SharePanel.None
                    onShowEmailDialog()
                },
            )
            ShareActionButton(
                icon = Icons.Outlined.Tune,
                label = "Definições".tr(),
                modifier = Modifier.weight(1f),
                active = editMode,
                onClick = { activePanel = if (editMode) SharePanel.None else SharePanel.Edit },
            )
        }

        // QR code display
        AnimatedVisibility(visible = showQr && !share.link.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                co.golink.tester.ui.components.QrCodeImage(url = share.link ?: "")
            }
        }

        // Edit panel
        AnimatedVisibility(visible = editMode) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text(
                    "Editar partilha".tr(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                SwitchRow(
                    icon = Icons.Filled.Lock,
                    label = "Proteger com password".tr(),
                    checked = hasPassword,
                    onCheckedChange = { hasPassword = it },
                )
                AnimatedVisibility(visible = hasPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Nova password".tr()) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PermissionSection(permission = permission, onPermissionChange = { permission = it }, isFolder = isFolder)
                ShareLimitsFields(
                    isFolder = isFolder,
                    hasExpiration = hasExpiration,
                    onHasExpiration = { hasExpiration = it },
                    expirationMinutes = expirationMinutes,
                    onExpirationMinutes = { expirationMinutes = it },
                    hasViewLimit = hasViewLimit,
                    onHasViewLimit = { hasViewLimit = it; if (it) hasDownloadLimit = false },
                    viewLimit = viewLimit,
                    onViewLimit = { viewLimit = it },
                    hasDownloadLimit = hasDownloadLimit,
                    onHasDownloadLimit = { hasDownloadLimit = it },
                    downloadLimit = downloadLimit,
                    onDownloadLimit = { downloadLimit = it },
                )
                Button(
                    onClick = {
                        val views = viewLimit.takeIf { hasViewLimit && !isFolder }
                        onUpdate(
                            password.takeIf { hasPassword && it.isNotBlank() },
                            permission,
                            expirationMinutes.takeIf { hasExpiration },
                            downloadLimit.toIntOrNull()?.takeIf { hasDownloadLimit && views == null && it > 0 },
                            views,
                            // O estado do switch, e não "escreveu password": sem
                            // isto gravar outra opção numa partilha protegida
                            // tirava-lhe a proteção.
                            hasPassword,
                        )
                        activePanel = SharePanel.None
                    },
                    // Proteger uma partilha que nunca teve password exige uma.
                    enabled = !isWorking && !(hasPassword && !share.protected && password.isBlank()),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Guardar alterações".tr(), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Desativar link (texto vermelho, como no mockup)
        Text(
            "Desativar link".tr(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onRevoke)
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun ShareActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    val bg = if (active)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f * alpha)
    val border = if (active)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    else
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f * alpha)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary.copy(alpha = alpha), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TagChip(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmailRecipientsDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Enviar por email".tr(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Indica os emails separados por vírgula".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Destinatários".tr()) },
                    placeholder = { Text("a@b.com, c@d.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar".tr(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = text.isNotBlank(),
                        onClick = {
                            val emails = text.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
                            onConfirm(emails)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enviar".tr(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun prettyPermission(p: String): String = when (p) {
    "editor" -> "Pode editar".tr()
    "visitor" -> "Ver e transferir".tr()
    "viewer" -> "Só ver".tr()
    else -> p
}
