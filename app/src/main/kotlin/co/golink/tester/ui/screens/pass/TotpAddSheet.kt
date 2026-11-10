package co.golink.tester.ui.screens.pass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.pass.Totp
import co.golink.tester.ui.components.dialogs.CreateSheetScaffold
import co.golink.tester.ui.i18n.pt

/**
 * "Inserir chave" do 2FA como o "Add account" dos autenticadores: Emissor, Conta,
 * Chave secreta e Avançado (algoritmo/dígitos/período). Espelho do modo totp do
 * PassItemForm.vue. [onDone] recebe a chave pronta a guardar (base32 ou otpauth://).
 */
@Composable
fun TotpAddSheet(onDismiss: () -> Unit, onScan: () -> Unit, onDone: (String) -> Unit) {
    val p = LocalPassPalette.current
    var issuer by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    var algorithm by rememberSaveable { mutableStateOf("SHA1") }
    var digits by rememberSaveable { mutableStateOf(6) }
    var period by rememberSaveable { mutableStateOf("30") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var showSecret by rememberSaveable { mutableStateOf(true) }
    var tried by rememberSaveable { mutableStateOf(false) }

    // Colar um otpauth:// na chave preenche o resto.
    fun onSecret(v: String) {
        val t = v.trim()
        if (t.startsWith("otpauth://", ignoreCase = true)) {
            val cfg = Totp.parse(t)
            if (cfg != null) {
                secret = cfg.secret
                if (cfg.issuer.isNotEmpty()) issuer = cfg.issuer
                if (cfg.account.isNotEmpty()) account = cfg.account
                algorithm = cfg.algorithm
                digits = cfg.digits
                period = cfg.period.toString()
                advanced = cfg.algorithm != "SHA1" || cfg.digits != 6 || cfg.period != 30
                return
            }
        }
        secret = v
    }

    val raw = if (secret.isBlank()) "" else Totp.build(secret, issuer.trim(), account.trim(), algorithm, digits, period.toIntOrNull() ?: -1)
    val cfg = Totp.parse(raw)
    val secretError = when {
        secret.isBlank() -> if (tried) pt("totp_form.err_secret") else null
        cfg == null -> pt("totp_invalid")
        else -> null
    }
    val nameError = if (tried && issuer.isBlank() && account.isBlank()) pt("totp_form.err_name") else null

    CreateSheetScaffold(
        title = pt("totp_form.title"),
        onDismiss = onDismiss,
        confirmText = pt("confirm"),
        onConfirm = {
            tried = true
            if (cfg != null && (issuer.isNotBlank() || account.isNotBlank())) onDone(raw)
        },
        icon = { Icon(Icons.Outlined.Key, null, tint = PassBlue) },
        iconBackground = p.accentSoft,
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.accentSoft).clickable(onClick = onScan).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.QrCodeScanner, null, tint = PassBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(pt("scan_qr"), color = PassBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        SheetField(pt("totp_form.issuer"), issuer, { issuer = it }, pt("totp_form.ph_issuer"), error = nameError)
        SheetField(pt("totp_form.account"), account, { account = it }, pt("totp_form.ph_account"), keyboard = KeyboardType.Email)
        SheetLabel("${pt("totp_form.secret")} *")
        OutlinedTextField(
            value = secret,
            onValueChange = ::onSecret,
            singleLine = true,
            isError = secretError != null,
            placeholder = { Text(pt("totp_form.ph_secret"), color = p.soft) },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { showSecret = !showSecret }) { Icon(if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, tint = p.muted) }
            },
            shape = RoundedCornerShape(14.dp),
            colors = sheetFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (secretError != null) Text(secretError, color = PassRose, fontSize = 12.5.sp, modifier = Modifier.padding(start = 2.dp))
        else if (cfg != null) {
            val now = rememberTotp(cfg, rememberNowMs())
            if (now != null) Row(verticalAlignment = Alignment.CenterVertically) {
                TotpRing(now.remaining, now.period, 18.dp, showNumber = false)
                Spacer(Modifier.width(6.dp))
                Text("${pt("totp_preview")} ", fontSize = 12.5.sp, color = p.muted)
                Text(Totp.format(now.code), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = p.text)
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { advanced = !advanced }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pt("totp_form.advanced"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = p.muted, modifier = Modifier.weight(1f))
            Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = p.muted)
        }
        if (advanced) {
            SheetLabel(pt("totp_form.algorithm"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SHA1", "SHA256", "SHA512").forEach { a -> PassChip(a, null, algorithm == a) { algorithm = a } }
            }
            SheetLabel(pt("totp_form.digits"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 7, 8).forEach { d -> PassChip(d.toString(), null, digits == d) { digits = d } }
            }
            SheetField(pt("totp_form.period"), period, { v -> period = v.filter(Char::isDigit).take(3) }, null, keyboard = KeyboardType.Number)
        }

        Column(
            Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (p.dark) Color(0x14FFFFFF) else Color(0xFFF4F5F7)).padding(12.dp),
        ) {
            Text(pt("totp_form.where_title"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = p.text)
            Text(pt("totp_form.where_body"), fontSize = 12.5.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PassBlue,
    unfocusedBorderColor = LocalPassPalette.current.line,
)

@Composable
private fun SheetLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalPassPalette.current.muted, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
}

@Composable
private fun SheetField(label: String, value: String, onChange: (String) -> Unit, placeholder: String?, keyboard: KeyboardType = KeyboardType.Text, error: String? = null) {
    SheetLabel(label)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        isError = error != null,
        placeholder = placeholder?.let { { Text(it, color = LocalPassPalette.current.soft) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
        shape = RoundedCornerShape(14.dp),
        colors = sheetFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) Text(error, color = PassRose, fontSize = 12.5.sp, modifier = Modifier.padding(start = 2.dp))
}
