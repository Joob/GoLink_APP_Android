package co.golink.tester.ui.components.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
)

@Composable
fun ShareDialog(
    state: ShareDialogState,
    onDismiss: () -> Unit,
    onCreate: (password: String?, permission: String?, expirationDays: Int?) -> Unit,
    onUpdate: (password: String?, permission: String?, expirationDays: Int?) -> Unit = { _, _, _ -> },
    onCopy: (String) -> Unit,
    onShowQr: () -> Unit,
    onSendEmail: (List<String>) -> Unit,
    onShowEmailDialog: (Boolean) -> Unit,
    onRevoke: () -> Unit,
) {
    val item = state.item
    val isFolder = item is BrowseItem.Folder
    val clipboard = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Partilhar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
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
                            onCopy = {
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

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Fechar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (state.emailDialogVisible) {
        EmailRecipientsDialog(
            onDismiss = { onShowEmailDialog(false) },
            onConfirm = { onSendEmail(it) },
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
    ) {
        Text(
            "Permissão",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PermissionChip(
                icon = Icons.Outlined.RemoveRedEye,
                label = "Apenas ver",
                selected = permission == "visitor",
                modifier = Modifier.weight(1f),
                onClick = { onPermissionChange("visitor") },
            )
            PermissionChip(
                icon = Icons.Outlined.Edit,
                label = "Pode editar",
                selected = permission == "editor",
                modifier = Modifier.weight(1f),
                onClick = { onPermissionChange("editor") },
            )
        }
    }
}

@Composable
private fun CreateShareForm(
    isFolder: Boolean,
    isWorking: Boolean,
    onCreate: (password: String?, permission: String?, expirationDays: Int?) -> Unit,
) {
    var protectWithPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("visitor") }
    var hasExpiration by remember { mutableStateOf(false) }
    var expirationDays by remember { mutableStateOf("7") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchRow(
            icon = Icons.Filled.Lock,
            label = "Proteger com password",
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

        if (isFolder) {
            PermissionSection(permission = permission, onPermissionChange = { permission = it })
        }

        SwitchRow(
            icon = Icons.Outlined.Schedule,
            label = "Expira após (dias)",
            checked = hasExpiration,
            onCheckedChange = { hasExpiration = it },
        )
        AnimatedVisibility(visible = hasExpiration) {
            OutlinedTextField(
                value = expirationDays,
                onValueChange = { v -> expirationDays = v.filter { it.isDigit() }.take(4) },
                label = { Text("Número de dias") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                onCreate(
                    password.takeIf { protectWithPassword && it.isNotBlank() },
                    if (isFolder) permission else null,
                    expirationDays.toIntOrNull()?.takeIf { hasExpiration && it > 0 },
                )
            },
            enabled = !isWorking && (!protectWithPassword || password.isNotBlank()) &&
                (!hasExpiration || expirationDays.toIntOrNull()?.let { it > 0 } == true),
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
            Text("Criar link de partilha", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ExistingShareView(
    share: ShareInfo,
    isFolder: Boolean,
    sendingEmail: Boolean,
    isWorking: Boolean,
    onCopy: () -> Unit,
    onShowEmailDialog: () -> Unit,
    onUpdate: (password: String?, permission: String?, expirationDays: Int?) -> Unit,
    onRevoke: () -> Unit,
) {
    var activePanel by remember { mutableStateOf(SharePanel.None) }
    val editMode = activePanel == SharePanel.Edit
    val showQr = activePanel == SharePanel.Qr
    var password by remember { mutableStateOf("") }
    var hasPassword by remember(share.protected) { mutableStateOf(share.protected) }
    var permission by remember(share.permission) { mutableStateOf(share.permission ?: "visitor") }
    var hasExpiration by remember(share.expireIn) { mutableStateOf((share.expireIn ?: 0) > 0) }
    var expirationDays by remember(share.expireIn) { mutableStateOf(share.expireIn?.takeIf { it > 0 }?.toString() ?: "7") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Link box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(14.dp),
        ) {
            Text(
                "Link público",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    share.link ?: "(sem URL)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable(onClick = onCopy),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copiar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (share.protected || (share.expireIn != null && share.expireIn > 0) || !share.permission.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (share.protected) TagChip("Com password")
                    if (share.expireIn != null && share.expireIn > 0) TagChip("Expira em ${share.expireIn}d")
                    if (!share.permission.isNullOrBlank()) TagChip(prettyPermission(share.permission))
                }
            }
        }

        // Action buttons row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareActionButton(
                icon = Icons.Filled.QrCode2,
                label = "QR code",
                modifier = Modifier.weight(1f),
                active = showQr,
                onClick = { activePanel = if (showQr) SharePanel.None else SharePanel.Qr },
            )
            ShareActionButton(
                icon = Icons.Filled.Email,
                label = "Email",
                modifier = Modifier.weight(1f),
                enabled = !sendingEmail,
                onClick = {
                    activePanel = SharePanel.None
                    onShowEmailDialog()
                },
            )
            ShareActionButton(
                icon = Icons.Outlined.Tune,
                label = "Editar",
                modifier = Modifier.weight(1f),
                active = editMode,
                onClick = {
                    activePanel = if (editMode) SharePanel.None else SharePanel.Edit
                },
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
                    "Editar partilha",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                SwitchRow(
                    icon = Icons.Filled.Lock,
                    label = "Proteger com password",
                    checked = hasPassword,
                    onCheckedChange = { hasPassword = it },
                )
                AnimatedVisibility(visible = hasPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Nova password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isFolder) {
                    PermissionSection(permission = permission, onPermissionChange = { permission = it })
                }
                SwitchRow(
                    icon = Icons.Outlined.Schedule,
                    label = "Expira após (dias)",
                    checked = hasExpiration,
                    onCheckedChange = { hasExpiration = it },
                )
                AnimatedVisibility(visible = hasExpiration) {
                    OutlinedTextField(
                        value = expirationDays,
                        onValueChange = { v -> expirationDays = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Número de dias") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = {
                        onUpdate(
                            password.takeIf { hasPassword && it.isNotBlank() },
                            if (isFolder) permission else null,
                            expirationDays.toIntOrNull()?.takeIf { hasExpiration && it > 0 },
                        )
                        activePanel = SharePanel.None
                    },
                    enabled = !isWorking,
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
                    Text("Guardar alterações", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Revoke
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.06f))
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable(onClick = onRevoke)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Revogar partilha",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }
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
                    Text("Enviar por email", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Indica os emails separados por vírgula",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Destinatários") },
                    placeholder = { Text("a@b.com, c@d.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Enviar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun prettyPermission(p: String): String = when (p) {
    "editor" -> "Pode editar"
    "visitor" -> "Apenas ver"
    else -> p
}
