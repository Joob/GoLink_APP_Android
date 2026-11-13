package co.golink.tester.ui.components.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.ShareInfo
import co.golink.tester.ui.components.EXPIRATION_OPTIONS
import co.golink.tester.ui.components.shareTimeLeft
import co.golink.tester.ui.i18n.tr
import kotlinx.coroutines.delay

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

private enum class ShareTab { Link, People }

/**
 * Partilhar (mockup 2026-11): separadores Link | Pessoas (Pessoas só em pastas,
 * quando [people] vem preenchido), formulário com permissão em cartões e
 * "Proteção" em linhas com switch; com a partilha criada mostra "Link pronto".
 */
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
    people: SharePeopleConfig? = null,
) {
    val item = state.item
    val isFolder = item is BrowseItem.Folder
    val context = LocalContext.current
    val share = state.share

    var tab by remember { mutableStateOf(ShareTab.Link) }
    // Com partilha: "Link pronto"; "Definições do link" abre o formulário.
    var editing by remember { mutableStateOf(false) }
    val showReady = share != null && !editing && tab == ShareTab.Link

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            if (showReady) {
                LinkReadyView(
                    share = share!!,
                    sendingEmail = state.sendingEmail,
                    keyPending = state.keyPending,
                    onCopy = {
                        val link = share.link ?: return@LinkReadyView
                        co.golink.tester.ui.common.copyLinkToClipboard(context, "share-link", link)
                        onCopy(link)
                    },
                    onShowEmailDialog = { onShowEmailDialog(true) },
                    onSettings = { editing = true },
                    onRevoke = onRevoke,
                )
            } else {
                ShareHeader(
                    name = item.name,
                    encrypted = share?.link?.contains("#k=") == true || (item as? BrowseItem.File)?.encrypted == true,
                    onBack = { if (share != null && editing) { editing = false; tab = ShareTab.Link } else onDismiss() },
                )
                if (people != null) {
                    Spacer(Modifier.height(14.dp))
                    SegmentedTabs(
                        selected = tab,
                        onSelect = { tab = it },
                    )
                }
                Spacer(Modifier.height(14.dp))
                when (tab) {
                    ShareTab.Link -> ShareForm(
                        share = share,
                        isFolder = isFolder,
                        isWorking = state.isWorking,
                        onCreate = onCreate,
                        onUpdate = { p, perm, m, d, v, prot -> onUpdate(p, perm, m, d, v, prot); editing = false },
                    )
                    ShareTab.People -> SharePeopleTab(config = people!!, onDone = onDismiss)
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

// ‹  Partilhar “X”  ·  Link encriptado · só quem tem o link o pode abrir
@Composable
private fun ShareHeader(name: String, encrypted: Boolean, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(onClick = onBack),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr(), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Partilhar “%s”".tr().format(name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (encrypted) "Link encriptado · só quem tem o link o pode abrir" else "Só quem tem o link o pode abrir").tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SegmentedTabs(selected: ShareTab, onSelect: (ShareTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(4.dp),
    ) {
        listOf(ShareTab.Link to "Link", ShareTab.People to "Pessoas").forEach { (t, label) ->
            val on = t == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    label.tr(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

// Cartão de permissão: "Só ver" / "Ver e transferir" / "Carregar e editar".
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.08f) else Color.Transparent)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Linha de "Proteção": ícone · nome · valor · switch; o conteúdo extra expande por baixo.
@Composable
private fun ProtectionRow(
    icon: ImageVector,
    label: String,
    value: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    expanded: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            value?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        AnimatedVisibility(visible = checked) {
            Box(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) { expanded() }
        }
    }
}

/**
 * Criar (share == null) ou editar a partilha. Regras como antes: visualizações
 * só em ficheiros e exclusivas do limite de downloads; password obrigatória ao
 * proteger uma partilha que ainda não tinha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareForm(
    share: ShareInfo?,
    isFolder: Boolean,
    isWorking: Boolean,
    onCreate: (password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?) -> Unit,
    onUpdate: (password: String?, permission: String?, expirationMinutes: Int?, downloadLimit: Int?, viewLimit: Int?, protected: Boolean) -> Unit,
) {
    val key = share?.token
    var permission by remember(key) { mutableStateOf(share?.permission?.takeIf { it.isNotBlank() } ?: "viewer") }
    var hasPassword by remember(key) { mutableStateOf(share?.protected == true) }
    var password by remember(key) { mutableStateOf("") }
    var hasExpiration by remember(key) { mutableStateOf((share?.expireInMinutes ?: 0) > 0) }
    var expirationMinutes by remember(key) { mutableStateOf(share?.expireInMinutes?.takeIf { it > 0 } ?: 1440) }
    var hasViewLimit by remember(key) { mutableStateOf(!isFolder && (share?.viewLimit ?: 0) > 0) }
    var viewLimit by remember(key) { mutableStateOf(share?.viewLimit?.takeIf { it > 0 } ?: 1) }
    var hasDownloadLimit by remember(key) { mutableStateOf((share?.downloadLimit ?: 0) > 0) }
    var downloadLimit by remember(key) { mutableStateOf(share?.downloadLimit?.takeIf { it > 0 }?.toString() ?: "10") }

    // Ficheiros "só ver" não transferem: o limite de downloads não se aplica.
    val downloadsApply = isFolder || (permission != "viewer" && !hasViewLimit)
    val effDownloadLimit = downloadLimit.toIntOrNull()?.takeIf { hasDownloadLimit && downloadsApply && it > 0 }
    val effViews = viewLimit.takeIf { hasViewLimit && !isFolder }
    val expirationLabel = EXPIRATION_OPTIONS.firstOrNull { it.first == expirationMinutes }?.second

    val valid = !isWorking &&
        !(hasPassword && share?.protected != true && password.isBlank()) &&
        (!hasDownloadLimit || !downloadsApply || effDownloadLimit != null)

    Column {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("Quem tem o link pode".tr())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionCard(
                    icon = Icons.Outlined.RemoveRedEye,
                    title = "Só ver".tr(),
                    subtitle = "Abre no browser, sem transferir".tr(),
                    selected = permission == "viewer",
                    modifier = Modifier.weight(1f),
                ) { permission = "viewer" }
                PermissionCard(
                    icon = Icons.Outlined.Download,
                    title = "Ver e transferir".tr(),
                    subtitle = "Pode guardar uma cópia".tr(),
                    selected = permission == "visitor",
                    modifier = Modifier.weight(1f),
                ) { permission = "visitor" }
            }
            if (isFolder) {
                Spacer(Modifier.height(8.dp))
                PermissionCard(
                    icon = Icons.Outlined.Edit,
                    title = "Carregar e editar".tr(),
                    subtitle = "Pode adicionar e alterar ficheiros".tr(),
                    selected = permission == "editor",
                    modifier = Modifier.fillMaxWidth(),
                ) { permission = "editor" }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("Proteção".tr())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtectionRow(
                    icon = Icons.Outlined.Lock,
                    label = "Password".tr(),
                    value = null,
                    checked = hasPassword,
                    onCheckedChange = { hasPassword = it },
                ) {
                    OutlinedTextField(
                        value = password,
                        // O servidor recusa passwords acima de 72 (bcrypt): 422.
                        onValueChange = { password = it.take(72) },
                        label = { Text((if (share?.protected == true) "Nova password" else "Password").tr()) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ProtectionRow(
                    icon = Icons.Outlined.Schedule,
                    label = "Expira".tr(),
                    value = if (hasExpiration) expirationLabel else "Nunca".tr(),
                    checked = hasExpiration,
                    onCheckedChange = { hasExpiration = it },
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        EXPIRATION_OPTIONS.forEach { (minutes, label) ->
                            OptionChip(label = label, selected = expirationMinutes == minutes) { expirationMinutes = minutes }
                        }
                    }
                }
                if (!isFolder) {
                    ProtectionRow(
                        icon = Icons.Outlined.RemoveRedEye,
                        label = "Limitar aberturas".tr(),
                        value = if (hasViewLimit) viewLimit.toString() else "Ilimitado".tr(),
                        checked = hasViewLimit,
                        onCheckedChange = { hasViewLimit = it; if (it) hasDownloadLimit = false },
                    ) {
                        NumberStepper(label = "Aberturas máximas".tr(), value = viewLimit, onChange = { viewLimit = it })
                    }
                }
                if (downloadsApply) {
                    ProtectionRow(
                        icon = Icons.Outlined.Download,
                        label = "Limitar downloads".tr(),
                        value = if (hasDownloadLimit) downloadLimit.ifBlank { "—" } else "Ilimitado".tr(),
                        checked = hasDownloadLimit,
                        onCheckedChange = { hasDownloadLimit = it },
                    ) {
                        OutlinedTextField(
                            value = downloadLimit,
                            onValueChange = { v -> downloadLimit = v.filter { it.isDigit() }.take(6) },
                            label = { Text("Número de downloads".tr()) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                formSummary(permission, hasPassword, expirationLabel.takeIf { hasExpiration }, effViews, effDownloadLimit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = {
                val pwd = password.takeIf { hasPassword && it.isNotBlank() }
                val minutes = expirationMinutes.takeIf { hasExpiration }
                if (share == null) {
                    onCreate(pwd, permission, minutes, effDownloadLimit, effViews)
                } else {
                    // O estado do switch, e não "escreveu password": sem isto gravar
                    // outra opção numa partilha protegida tirava-lhe a proteção.
                    onUpdate(pwd, permission, minutes, effDownloadLimit, effViews, hasPassword)
                }
            },
            enabled = valid,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 15.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isWorking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text((if (share == null) "Criar link de partilha" else "Guardar alterações").tr(), fontWeight = FontWeight.SemiBold)
        }
    }
}

// "Qualquer pessoa com o link pode só ver · nunca expira."
private fun formSummary(permission: String, password: Boolean, expiration: String?, views: Int?, downloads: Int?): String {
    val base = when (permission) {
        "visitor" -> "Qualquer pessoa com o link pode ver e transferir"
        "editor" -> "Qualquer pessoa com o link pode carregar e editar"
        else -> "Qualquer pessoa com o link pode só ver"
    }.tr()
    val parts = mutableListOf(base)
    if (password) parts += "com password".tr()
    parts += expiration?.let { "expira após %s".tr().format(it) } ?: "nunca expira".tr()
    views?.let { parts += "máx. %d aberturas".tr().format(it) }
    downloads?.let { parts += "máx. %d downloads".tr().format(it) }
    return parts.joinToString(" · ") + "."
}

// Ecrã "Link pronto": ✓, resumo, link + Copiar, Mensagem/E-mail/QR/Mais.
@Composable
private fun LinkReadyView(
    share: ShareInfo,
    sendingEmail: Boolean,
    keyPending: Boolean,
    onCopy: () -> Unit,
    onShowEmailDialog: () -> Unit,
    onSettings: () -> Unit,
    onRevoke: () -> Unit,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val danger = MaterialTheme.colorScheme.error
    var showQr by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    val link = share.link.orEmpty()
    val canSend = !keyPending && link.isNotBlank()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).clip(CircleShape).background(primary.copy(alpha = 0.12f)),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = primary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            (if (share.active) "Link pronto" else "Link inativo").tr(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            readySummary(share),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        // Link + Copiar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                if (keyPending) "A preparar a chave de encriptação…".tr()
                else link.ifBlank { "(sem URL)" }.removePrefix("https://").removePrefix("http://"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onCopy(); copied = true },
                enabled = canSend,
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                if (keyPending) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text((if (copied) "Copiado" else "Copiar").tr(), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // E2E: o link leva a chave (#k=).
        if (link.contains("#k=")) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "O link inclui a chave para abrir os ficheiros. Envia-o só a quem confias.".tr(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF92400E),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ReadyTile(Icons.AutoMirrored.Outlined.Chat, "Mensagem".tr(), enabled = canSend, modifier = Modifier.weight(1f)) {
                val sms = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")).apply {
                    putExtra("sms_body", link)
                }
                runCatching { context.startActivity(sms) }
            }
            ReadyTile(Icons.Outlined.MailOutline, "E-mail", enabled = canSend && !sendingEmail, modifier = Modifier.weight(1f)) {
                showQr = false
                onShowEmailDialog()
            }
            ReadyTile(Icons.Outlined.QrCode2, "Código QR".tr(), enabled = canSend, active = showQr, modifier = Modifier.weight(1f)) {
                showQr = !showQr
            }
            ReadyTile(Icons.Outlined.MoreHoriz, "Mais".tr(), enabled = canSend, modifier = Modifier.weight(1f)) {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, link)
                }
                runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
            }
        }

        AnimatedVisibility(visible = showQr && link.isNotBlank()) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                co.golink.tester.ui.components.QrCodeImage(url = link)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onSettings,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Text("Definições do link".tr(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
            OutlinedButton(
                onClick = onRevoke,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                border = BorderStroke(1.dp, danger.copy(alpha = 0.35f)),
                modifier = Modifier.weight(1f),
            ) {
                Text("Parar partilha".tr(), fontWeight = FontWeight.SemiBold, color = danger, maxLines = 1)
            }
        }
    }
}

// "Só ver · com password · expira em 7 d"
private fun readySummary(share: ShareInfo): String {
    val parts = mutableListOf<String>()
    share.permission?.takeIf { it.isNotBlank() }?.let { parts += prettyPermission(it) }
    if (share.protected) parts += "com password".tr()
    val timeLeft = shareTimeLeft(share.expiresAt)
    parts += when {
        timeLeft == null -> "nunca expira".tr()
        timeLeft.expired -> timeLeft.text
        else -> "expira em %s".tr().format(timeLeft.text)
    }
    if ((share.viewLimit ?: 0) > 0) parts += "${share.viewCount ?: 0}/${share.viewLimit} " + "aberturas".tr()
    if ((share.downloadLimit ?: 0) > 0) parts += "${share.downloadCount ?: 0}/${share.downloadLimit} downloads"
    return parts.joinToString(" · ")
}

@Composable
private fun ReadyTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    val primary = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(70.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = (if (active) primary else MaterialTheme.colorScheme.onSurface).copy(alpha = alpha), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, if (selected) primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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
            .background(MaterialTheme.colorScheme.surface)
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
private fun EmailRecipientsDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val emails = text.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
    // O servidor aceita no máximo 10 destinatários por envio.
    val tooMany = emails.size > co.golink.tester.data.share.ShareRepository.MAX_EMAILS
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
                    isError = tooMany,
                    supportingText = if (emails.size > 1 || tooMany) { {
                        Text("%d de %d no máximo".tr().format(emails.size, co.golink.tester.data.share.ShareRepository.MAX_EMAILS))
                    } } else null,
                )
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar".tr(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = emails.isNotEmpty() && !tooMany,
                        onClick = {
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
