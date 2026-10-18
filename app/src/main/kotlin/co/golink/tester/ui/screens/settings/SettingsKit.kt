package co.golink.tester.ui.screens.settings

import co.golink.tester.ui.i18n.tr
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Kit visual das Definições: o mesmo cabeçalho, cartões, linhas e botões em
 * todos os submenus, e uma cor por categoria nos ícones (antes era tudo verde).
 */
/** Verde dos botões principais e estados "ligado" (mockup). */
val SettingsAccent = Color(0xFF17784A)

/** Campo com etiqueta por cima (estilo do mockup), opcionalmente password. */
@Composable
fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        // Campo compacto (46dp) com contorno fino, como no mockup.
        var focused by remember { mutableStateOf(false) }
        val borderColor = when {
            isError -> MaterialTheme.colorScheme.error
            focused -> SettingsAccent
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(SettingsAccent),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(settingsCardColor())
                        .border(if (focused) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    inner()
                }
            },
        )
        if (supportingText != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Popup das Definições: cartão arredondado, ícone opcional, título, texto,
 * conteúdo livre e botões a toda a largura (principal verde/vermelho + Cancelar).
 */
@Composable
fun SettingsDialog(
    title: String,
    onDismiss: () -> Unit,
    message: String? = null,
    icon: ImageVector? = null,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissText: String = "Cancelar".tr(),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon != null) {
                    val tint = if (destructive) MaterialTheme.colorScheme.error else SettingsAccent
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!message.isNullOrBlank()) {
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                content?.invoke(this)
                Spacer(Modifier.height(2.dp))
                if (confirmText != null) {
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (destructive) MaterialTheme.colorScheme.error else SettingsAccent,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(confirmText, fontWeight = FontWeight.SemiBold) }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(dismissText, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

/** PIN de 4 dígitos em quadrados (o campo real fica por baixo, invisível). */
@Composable
fun SettingsPinInput(value: String, onValueChange: (String) -> Unit, isError: Boolean = false, length: Int = 4) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() }.take(length)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                repeat(length) { i ->
                    val filled = i < value.length
                    val active = i == value.length
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(settingsCardColor())
                            .border(
                                if (active) 1.5.dp else 1.dp,
                                when {
                                    isError -> MaterialTheme.colorScheme.error
                                    active -> SettingsAccent
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.onSurface))
                    }
                }
            }
        },
    )
}

/** Caixa de escolha compacta (abre um menu), com o mesmo aspeto do [SettingsField]. */
@Composable
fun SettingsSelectBox(text: String, placeholder: String? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(settingsCardColor())
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp),
    ) {
        Text(
            text.ifBlank { placeholder.orEmpty() },
            style = MaterialTheme.typography.bodyLarge,
            color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

/** Quadrado de seleção (ligado = verde com ✓). */
@Composable
fun SettingsCheckSquare(checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(shape)
            .background(if (checked) SettingsAccent.copy(alpha = alpha) else Color.Transparent)
            .border(1.5.dp, if (checked) SettingsAccent.copy(alpha = alpha) else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f * alpha), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

/** Linha de lista com ícone simples, título, sub-linha e um elemento à direita. */
@Composable
fun SettingsListRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.5f
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f * alpha), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subtitleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha) else subtitleColor,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                modifier = Modifier.padding(start = if (icon != null) 52.dp else 16.dp, end = 16.dp),
            )
        }
    }
}

/** Seta "›" das linhas de navegação. */
@Composable
fun SettingsChevron() {
    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
}

object SettingsColors {
    val Blue = Color(0xFF3B82F6)
    val Indigo = Color(0xFF6366F1)
    val Purple = Color(0xFF9D66FE)
    val Green = Color(0xFF00BC7E)
    val Teal = Color(0xFF14B8A6)
    val Orange = Color(0xFFF97316)
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFFD397A)
    val Pink = Color(0xFFEC4899)
    val Slate = Color(0xFF64748B)
}

/** Fundo dos cartões: branco no claro, um cinzento elevado no escuro. */
@Composable
fun settingsCardColor(): Color =
    // Pelo fundo do tema da app (e não do sistema): a app tem o seu próprio claro/escuro.
    if (MaterialTheme.colorScheme.background.luminanceIsDark()) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        Color.White
    }

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

@Composable
fun settingsCardBorderStroke(): BorderStroke = settingsCardBorder()

@Composable
private fun settingsCardBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

/** Quadrado arredondado com o ícone na cor da categoria. */
@Composable
fun SettingsIconTile(icon: ImageVector, color: Color, size: Int = 36, enabled: Boolean = true) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f * alpha)),
        contentAlignment = Alignment.Center,
    ) {
        // Redesign: ícones neutros (sem cor por categoria), como na web.
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f * alpha), modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** Título de secção em maiúsculas pequenas, por cima de um grupo/cartão. */
@Composable
fun SettingsSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Grupo de linhas do menu (cartão sem padding, as linhas tratam do seu). */
@Composable
fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) SettingsSectionTitle(title)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = settingsCardColor(),
            border = settingsCardBorder(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** Linha de navegação: ícone colorido, título, subtítulo e valor/badge à direita. */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            // Ícone simples, cinzento (mockup).
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f * alpha), modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!trailing.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f * alpha),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                modifier = Modifier.padding(start = 53.dp, end = 16.dp),
            )
        }
    }
}

/**
 * Esqueleto de um submenu: cabeçalho (ícone grande na cor da categoria,
 * título e frase) e o conteúdo em coluna com scroll e espaçamento regular.
 */
@Composable
fun SettingsPane(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsPaneHeader(icon, color, title, subtitle)
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SettingsPaneHeader(icon: ImageVector, color: Color, title: String, subtitle: String?) {
    // Redesign: o título já está na barra de topo; fica só a frase de contexto.
    if (subtitle.isNullOrBlank()) return
    Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

/** Cartão de conteúdo com título opcional e descrição. */
@Composable
fun SettingsCard(
    title: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = settingsCardColor(),
        border = settingsCardBorder(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        if (!description.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    trailing?.invoke(this)
                }
            } else if (!description.isNullOrBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

/** Botão principal (largura total, cantos redondos, spinner opcional). */
@Composable
fun SettingsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SettingsAccent,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Botão secundário/destrutivo em contorno. */
@Composable
fun SettingsOutlinedButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.45f else 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Aviso dentro de um cartão (info, aviso ou erro). */
@Composable
fun SettingsNotice(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
