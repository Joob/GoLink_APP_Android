package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.golink.tester.ui.i18n.tr

/** Ação que precisa de password/passkey antes de seguir. */
private enum class TwoFactorAction { ENABLE, DISABLE, SHOW_CODES, REGENERATE }

/**
 * Verificação em dois passos (TOTP) nativa — o mesmo que a web em
 * /user/settings/password: ativar com QR/chave + código, desativar, ver e
 * gerar códigos de recuperação. Contas de login social não têm (como na web).
 */
@Composable
internal fun TwoFactorSection(
    enabled: Boolean,
    hasPasskeys: Boolean,
    flow: TwoFactorFlow,
    busy: Boolean,
    error: String?,
    onStart: (password: String?, passkeyToken: String?) -> Unit,
    onConfirm: (code: String) -> Unit,
    onDisable: (password: String?, passkeyToken: String?) -> Unit,
    onShowCodes: (password: String?, passkeyToken: String?, regenerate: Boolean) -> Unit,
    onClose: () -> Unit,
    onClearError: () -> Unit,
) {
    var pending by remember { mutableStateOf<TwoFactorAction?>(null) }

    // Chave e códigos no ecrã: sem capturas nem miniatura nos recentes.
    if (flow !is TwoFactorFlow.Idle) {
        DisposableEffect(Unit) {
            co.golink.tester.data.settings.SecureScreens.acquire()
            onDispose { co.golink.tester.data.settings.SecureScreens.release() }
        }
    }

    pending?.let { action ->
        StepUpDialog(
            action = action,
            hasPasskeys = hasPasskeys,
            busy = busy,
            error = error,
            onDismiss = { pending = null; onClearError() },
            onSubmit = { pwd, token ->
                when (action) {
                    TwoFactorAction.ENABLE -> onStart(pwd, token)
                    TwoFactorAction.DISABLE -> onDisable(pwd, token)
                    TwoFactorAction.SHOW_CODES -> onShowCodes(pwd, token, false)
                    TwoFactorAction.REGENERATE -> onShowCodes(pwd, token, true)
                }
            },
        )
    }
    // O pedido correu bem: o fluxo ou o estado da conta mudou → fecha o diálogo.
    androidx.compose.runtime.LaunchedEffect(flow, enabled) { pending = null }

    SettingsCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                tint = if (enabled) SettingsAccent else Color(0xFFB45309),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Verificação em dois passos".tr(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled) "Ligada".tr() else "Desligada".tr(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) SettingsAccent else Color(0xFFB45309),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Pede um código da app autenticadora além da password.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (flow) {
            is TwoFactorFlow.Setup -> SetupStep(flow, busy, error, onConfirm, onClose)
            is TwoFactorFlow.RecoveryCodes -> RecoveryCodesStep(flow, onClose)
            TwoFactorFlow.Idle -> if (enabled) {
                SettingsOutlinedButton("Ver códigos de recuperação".tr(), onClick = { pending = TwoFactorAction.SHOW_CODES })
                SettingsOutlinedButton("Gerar novos códigos".tr(), onClick = { pending = TwoFactorAction.REGENERATE })
                SettingsOutlinedButton("Desativar".tr(), onClick = { pending = TwoFactorAction.DISABLE }, destructive = true)
            } else {
                SettingsPrimaryButton("Ativar".tr(), onClick = { pending = TwoFactorAction.ENABLE })
            }
        }
    }
}

@Composable
private fun SetupStep(
    flow: TwoFactorFlow.Setup,
    busy: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    val canConfirm = code.length == 6 && !busy

    Text(
        "1. Lê o código QR com a app autenticadora (ou copia a chave).".tr(),
        style = MaterialTheme.typography.bodySmall,
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 200.dp)) { co.golink.tester.ui.components.QrCodeImage(flow.otpauthUrl) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            flow.secret.chunked(4).joinToString(" "),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        SettingsActionText("Copiar".tr(), onClick = {
            co.golink.tester.ui.common.copySecretToClipboard(context, "2FA", flow.secret)
        })
    }
    SettingsOutlinedButton(
        "Abrir na app autenticadora".tr(),
        icon = Icons.AutoMirrored.Outlined.OpenInNew,
        onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(flow.otpauthUrl))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                android.widget.Toast.makeText(context, "Nenhuma app autenticadora instalada".tr(), android.widget.Toast.LENGTH_SHORT).show()
            }
        },
    )
    Text("2. Escreve o código de 6 dígitos que a app mostra.".tr(), style = MaterialTheme.typography.bodySmall)
    SettingsField(
        "Código".tr(),
        code,
        { v -> code = v.filter(Char::isDigit).take(6) },
        keyboardType = KeyboardType.NumberPassword,
        isError = error != null,
        supportingText = error,
        imeAction = ImeAction.Done,
        onImeAction = { if (canConfirm) onConfirm(code) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsOutlinedButton("Cancelar".tr(), onClick = onCancel, modifier = Modifier.weight(1f))
        SettingsPrimaryButton("Confirmar".tr(), onClick = { onConfirm(code) }, enabled = canConfirm, loading = busy, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecoveryCodesStep(flow: TwoFactorFlow.RecoveryCodes, onDone: () -> Unit) {
    val context = LocalContext.current
    SettingsNotice(
        (if (flow.justEnabled) "Verificação em dois passos ativada. ".tr() else "") +
            "Guarda estes códigos num sítio seguro: cada um serve uma vez se perderes o telemóvel.".tr(),
        SettingsColors.Amber,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        flow.codes.forEach { c ->
            Text(c, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
    }
    SettingsOutlinedButton(
        "Copiar códigos".tr(),
        icon = Icons.Outlined.ContentCopy,
        onClick = { co.golink.tester.ui.common.copySecretToClipboard(context, "2FA", flow.codes.joinToString("\n")) },
    )
    SettingsPrimaryButton("Feito".tr(), onClick = onDone)
}

@Composable
private fun StepUpDialog(
    action: TwoFactorAction,
    hasPasskeys: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (password: String?, passkeyToken: String?) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passkeyToken by remember { mutableStateOf<String?>(null) }
    val canSubmit = !busy && (password.isNotBlank() || passkeyToken != null)
    val submit = {
        onSubmit(password.takeIf { passkeyToken == null }, passkeyToken)
        passkeyToken = null // uso único: se falhar, confirma-se de novo
    }

    SettingsDialog(
        title = when (action) {
            TwoFactorAction.ENABLE -> "Ativar verificação em dois passos".tr()
            TwoFactorAction.DISABLE -> "Desativar verificação em dois passos?".tr()
            TwoFactorAction.SHOW_CODES -> "Códigos de recuperação".tr()
            TwoFactorAction.REGENERATE -> "Gerar novos códigos?".tr()
        },
        message = when (action) {
            TwoFactorAction.DISABLE -> "A conta fica protegida só pela password.".tr()
            TwoFactorAction.REGENERATE -> "Os códigos atuais deixam de funcionar.".tr()
            else -> "Confirma que és tu.".tr()
        },
        icon = Icons.Outlined.Security,
        destructive = action == TwoFactorAction.DISABLE,
        confirmText = if (action == TwoFactorAction.DISABLE) "Desativar".tr() else "Continuar".tr(),
        confirmEnabled = canSubmit,
        onConfirm = submit,
        onDismiss = onDismiss,
    ) {
        PasskeyConfirmRow(purpose = "two_factor", hasPasskeys = hasPasskeys, confirmed = passkeyToken != null, onToken = { passkeyToken = it })
        if (passkeyToken == null) {
            SettingsField(
                "Password atual".tr(), password, { password = it }, isPassword = true,
                isError = error != null, supportingText = error,
                imeAction = ImeAction.Done, onImeAction = { if (canSubmit) submit() },
            )
        } else if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
