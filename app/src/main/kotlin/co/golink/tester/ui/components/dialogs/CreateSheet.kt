package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.i18n.tr

/**
 * Folha inferior dos popups de criar/editar (mockup do redesign): ícone +
 * título + "em 🔒 pasta" + ×, conteúdo com scroll, Cancelar | ação principal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    /** null: sem rodapé (folhas de escolha, fecham-se no ×). */
    confirmText: String?,
    onConfirm: () -> Unit,
    icon: @Composable () -> Unit,
    iconBackground: Color,
    subtitle: String? = null,
    subtitleLock: Boolean = true,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
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
        Column(Modifier.imePadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(iconBackground),
                ) { icon() }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val muted = MaterialTheme.colorScheme.onSurfaceVariant
                            val parts = subtitle.split("%LOCK%", limit = 2)
                            Text(parts[0], fontSize = 12.sp, color = muted, maxLines = 1)
                            if (parts.size == 2) {
                                if (subtitleLock) Icon(Icons.Outlined.Lock, contentDescription = null, tint = muted, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(parts[1], fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable(onClick = onDismiss),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Fechar".tr(), modifier = Modifier.size(18.dp))
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                content = content,
            )
            if (confirmText == null) Spacer(Modifier.height(24.dp))
            else Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Cancelar".tr(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1.6f).height(48.dp),
                ) { Text(confirmText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** "em 🔒 Backup Phone" (o cadeado entra onde está %LOCK%); prefixKey é a chave i18n. */
fun inLocation(location: String, prefixKey: String = "em"): String = "${prefixKey.tr()} %LOCK%$location"

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun HelperText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, fontSize = 12.sp, color = color)
}

/** Campo de nome: borda verde ao focar, sufixo ".ext" em chip e × para limpar. */
@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suffixExt: String? = null,
    clearable: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        trailingIcon = when {
            suffixExt != null -> { { ExtChip(suffixExt, Modifier.padding(end = 8.dp)) } }
            clearable && value.isNotEmpty() -> {
                {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onValueChange("") },
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Limpar".tr(), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ExtChip(ext: String, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        ".$ext",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = if (highlighted) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

data class FormatOption(val ext: String, val label: String)

/** Cartões de formato em linha (o escolhido fica verde). */
@Composable
fun FormatCards(options: List<FormatOption>, selected: String, onSelect: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(if (options.size > 3) 6.dp else 8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { o ->
            val active = o.ext == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                    .border(if (active) 1.5.dp else 1.dp, if (active) primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onSelect(o.ext) }
                    .padding(horizontal = 2.dp, vertical = 9.dp),
            ) {
                Text(
                    ".${o.ext}",
                    fontSize = if (options.size > 3) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (active) primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    o.label,
                    fontSize = 10.5.sp,
                    color = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** "● OpenDocument · melhor para LibreOffice" */
@Composable
fun FormatHint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        HelperText(text)
    }
}

@Composable
fun OpenEditorSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
    ) {
        Text("Abrir no editor depois de criar".tr(), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** Linha de escolha: ícone em quadrado + título/subtítulo + chevron (como o menu ⋯). */
@Composable
fun ChoiceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(primary.copy(alpha = 0.12f)),
        ) { Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Icon(
            androidx.compose.material.icons.Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Câmara (menu +): tirar foto ou gravar vídeo, no estilo das folhas de criar. */
@Composable
fun CameraModeSheet(location: String, onDismiss: () -> Unit, onPick: (video: Boolean) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    CreateSheetScaffold(
        title = "Câmara".tr(),
        subtitle = inLocation(location, "Guardado em"),
        icon = { Icon(androidx.compose.material.icons.Icons.Outlined.PhotoCamera, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp)) },
        iconBackground = primary.copy(alpha = 0.12f),
        onDismiss = onDismiss,
        confirmText = null,
        onConfirm = {},
    ) {
        ChoiceRow(androidx.compose.material.icons.Icons.Outlined.PhotoCamera, "Tirar foto".tr(), "Fotografa e carrega logo a imagem".tr()) { onDismiss(); onPick(false) }
        ChoiceRow(androidx.compose.material.icons.Icons.Outlined.Videocam, "Gravar vídeo".tr(), "Grava e carrega o vídeo quando terminares".tr()) { onDismiss(); onPick(true) }
    }
}
