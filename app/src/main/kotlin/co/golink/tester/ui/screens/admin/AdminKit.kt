package co.golink.tester.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.settings.SettingsAccent
import co.golink.tester.ui.screens.settings.SettingsIconTile
import co.golink.tester.ui.screens.settings.SettingsRowDivider
import co.golink.tester.ui.screens.settings.settingsCardBorder
import co.golink.tester.ui.screens.settings.settingsCardBorderStroke
import co.golink.tester.ui.screens.settings.settingsCardColor
import co.golink.tester.ui.screens.settings.settingsInsetColor
import co.golink.tester.ui.screens.settings.settingsIsDark
import co.golink.tester.ui.theme.BrandGreen
import coil.compose.AsyncImage

// Peças comuns das páginas de administração, ao estilo das classes ui-* da web
// (ui-page-header, ui-kpi, ui-pill, ui-section, ui-segmented, ui-empty) e por
// cima do SettingsKit: fundo cinza, cartões brancos sem contorno no claro.

/** Verde do admin da web (--theme). */
val AdminAccent = BrandGreen

private val CardShape = RoundedCornerShape(18.dp)

// ------------------------------------------------------------------ Estados

@Composable
fun AdminLoading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, color = AdminAccent)
    }
}

@Composable
fun AdminError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsIconTile(Icons.Outlined.ErrorOutline, AdminTone.Danger.solid, size = 44)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp)) { Text("Tentar de novo".tr()) }
    }
}

/** Estado vazio (ui-empty): mosaico com ícone, título e texto opcional. */
@Composable
fun AdminEmptyState(title: String, text: String? = null, icon: ImageVector = Icons.Outlined.Inbox) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsIconTile(icon, AdminAccent, size = 44)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (!text.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AdminEmpty(text: String) = AdminEmptyState(title = text)

// ---------------------------------------------------------------- Cabeçalho

/** Topo da página (ui-page-header): descrição e ações à direita. */
@Composable
fun AdminPageHeader(
    description: String?,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            description.orEmpty(),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (actions != null) {
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Botão compacto do cabeçalho/secções (ui-btn / ui-btn-primary). */
@Composable
fun AdminButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    primary: Boolean = true,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    val fg = when {
        primary -> Color.White
        destructive -> AdminTone.Danger.solid
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .then(
                if (primary) Modifier.background(if (destructive) AdminTone.Danger.solid else AdminAccent)
                else Modifier.background(settingsCardColor()).border(settingsCardBorderStroke(), shape),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = if (enabled) 1f else 0.5f), maxLines = 1)
    }
}

// ---------------------------------------------------------------------- KPIs

data class AdminStat(
    val label: String,
    /** null = ainda a carregar. */
    val value: String?,
    val icon: ImageVector,
    val tint: Color = AdminAccent,
    val hint: String? = null,
    val hintTone: AdminTone? = null,
    val onClick: (() -> Unit)? = null,
)

/** Cartão KPI (ui-kpi): etiqueta, valor grande, mosaico de ícone e dica. */
@Composable
fun AdminStatCard(stat: AdminStat, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = settingsCardColor(),
        border = settingsCardBorder(),
        modifier = modifier.then(if (stat.onClick != null) Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = stat.onClick) else Modifier),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stat.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SettingsIconTile(stat.icon, stat.tint, size = 30)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stat.value ?: "—",
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (stat.value == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
            )
            if (!stat.hint.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stat.hint,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = stat.hintTone?.text() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Grelha de KPIs a 2 colunas; um número ímpar deixa o último a toda a largura. */
@Composable
fun AdminStatGrid(stats: List<AdminStat>, modifier: Modifier = Modifier, columns: Int = 2) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { AdminStatCard(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

// -------------------------------------------------------------- Pills/tons

/** Tons das pills de estado da web (ui-pill is-success/warning/danger/info). */
enum class AdminTone(val solid: Color) {
    Success(BrandGreen),
    Warning(Color(0xFFD97706)),
    Danger(Color(0xFFE11D48)),
    Info(Color(0xFF2563EB)),
    Neutral(Color(0xFF6B7280)),
}

@Composable
fun AdminTone.background(): Color {
    val dark = settingsIsDark()
    return when (this) {
        AdminTone.Success -> solid.copy(alpha = if (dark) 0.18f else 0.12f)
        AdminTone.Warning -> if (dark) Color(0xFFF59E0B).copy(alpha = 0.18f) else Color(0xFFFEF3C7)
        AdminTone.Danger -> if (dark) Color(0xFFFB7185).copy(alpha = 0.18f) else Color(0xFFFFE4E6)
        AdminTone.Info -> if (dark) Color(0xFF60A5FA).copy(alpha = 0.18f) else Color(0xFFDBEAFE)
        AdminTone.Neutral -> if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    }
}

@Composable
fun AdminTone.text(): Color {
    val dark = settingsIsDark()
    return when (this) {
        AdminTone.Success -> if (dark) Color(0xFF6EE7B7) else Color(0xFF0B6B49)
        AdminTone.Warning -> if (dark) Color(0xFFFCD34D) else Color(0xFF92400E)
        AdminTone.Danger -> if (dark) Color(0xFFFDA4AF) else Color(0xFFBE123C)
        AdminTone.Info -> if (dark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
        AdminTone.Neutral -> if (dark) Color(0xFFD1D5DB) else Color(0xFF4B5563)
    }
}

/** Pill de estado (ui-pill): totalmente arredondada, com ponto. */
@Composable
fun AdminStatusPill(text: String, tone: AdminTone, dot: Boolean = true) {
    val fg = tone.text()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tone.background())
            .padding(start = if (dot) 7.dp else 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/** Contagem sem ponto (ui-count). */
@Composable
fun AdminCount(text: String, tone: AdminTone = AdminTone.Neutral) = AdminStatusPill(text, tone, dot = false)

/** Etiqueta antiga por cor: passa a usar a pill da web com o tom equivalente. */
@Composable
fun AdminBadge(text: String, color: Color) {
    val tone = when (color) {
        AdminColors.Danger, AdminTone.Danger.solid -> AdminTone.Danger
        AdminColors.Warning, AdminTone.Warning.solid -> AdminTone.Warning
        AdminColors.Info, AdminTone.Info.solid -> AdminTone.Info
        AdminColors.Neutral, AdminTone.Neutral.solid -> AdminTone.Neutral
        SettingsAccent, BrandGreen -> AdminTone.Success
        else -> null
    }
    if (tone != null) {
        AdminStatusPill(text, tone)
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = if (settingsIsDark()) 0.2f else 0.12f))
                .padding(start = 7.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(text, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        }
    }
}

/** Código/número em letra fixa (nº de fatura, referência). */
@Composable
fun AdminCodeChip(text: String) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(settingsInsetColor()).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ------------------------------------------------------------------ Secções

/** Secção em cartão (ui-section): mosaico de ícone, título, descrição e ações. */
@Composable
fun AdminSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = AdminAccent,
    description: String? = null,
    danger: Boolean = false,
    padded: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = CardShape,
        color = settingsCardColor(),
        border = if (danger) BorderStroke(1.dp, AdminTone.Danger.solid.copy(alpha = 0.35f)) else settingsCardBorder(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = if (padded) 12.dp else 6.dp),
            ) {
                if (icon != null) {
                    SettingsIconTile(icon, if (danger) AdminTone.Danger.solid else tint, size = 28)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (danger) AdminTone.Danger.text() else MaterialTheme.colorScheme.onSurface)
                    if (!description.isNullOrBlank()) {
                        Text(description, fontSize = 12.5.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Column(
                modifier = if (padded) Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp) else Modifier.fillMaxWidth().padding(bottom = 6.dp),
                content = content,
            )
        }
    }
}

// ------------------------------------------------------------------- Listas

/** Linha de uma lista dividida (a "tabela" da web em mobile). */
@Composable
fun AdminListItem(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            footer?.invoke(this)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/**
 * Lista dividida num só cartão, dentro de um LazyColumn: cada linha é um item
 * com os cantos do grupo (1.º em cima, último em baixo) e separador entre elas.
 */
fun <T> LazyListScope.adminListGroup(
    items: List<T>,
    key: ((T) -> Any)? = null,
    divider: Boolean = true,
    row: @Composable (T) -> Unit,
) {
    itemsIndexed(items, key = key?.let { k -> { _: Int, item: T -> k(item) } }) { index, item ->
        AdminGroupCell(index, items.size, divider) { row(item) }
    }
}

@Composable
fun AdminGroupCell(index: Int, count: Int, divider: Boolean = true, content: @Composable () -> Unit) {
    val r = 18.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp,
        topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp,
        bottomEnd = if (index == count - 1) r else 0.dp,
    )
    Surface(shape = shape, color = settingsCardColor(), modifier = Modifier.fillMaxWidth()) {
        Column {
            if (divider && index > 0) SettingsRowDivider()
            content()
        }
    }
}

/** Bloco de cabeçalho num LazyColumn com o espaço por baixo (as listas não usam spacedBy). */
fun LazyListScope.adminBlock(key: Any? = null, bottom: Int = 12, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.fillMaxWidth().padding(bottom = bottom.dp)) { content() } }
}

/** Margens de uma página de admin com listas. */
val AdminListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)

/** Linha de lista em cartão próprio (páginas ainda sem lista agrupada). */
@Composable
fun AdminListRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = settingsCardColor(),
        border = settingsCardBorder(),
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
    ) {
        val columnFooter: (@Composable ColumnScope.() -> Unit)? = if (footer != null) { { footer() } } else null
        AdminListItem(title, subtitle, leading = leading, trailing = trailing, footer = columnFooter)
    }
}

/** Botão "Carregar mais" no fim de listas paginadas. */
@Composable
fun AdminLoadMore(loading: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) else Text("Carregar mais".tr(), color = AdminAccent)
    }
}

/** Par etiqueta/valor dentro de um cartão de detalhe. */
@Composable
fun AdminInfoRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value?.ifBlank { null } ?: "—", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f))
    }
}

/** Barra de utilização (6dp): acento, âmbar a partir de 75 %, rosa a partir de 90 %. */
@Composable
fun AdminProgressBar(percent: Float, modifier: Modifier = Modifier, tint: Color? = null) {
    val p = percent.coerceIn(0f, 100f)
    // [tint]: cor fixa (ex.: progresso de tradução, onde 100 % é bom).
    val color = tint ?: when {
        p >= 90f -> AdminTone.Danger.solid
        p >= 75f -> Color(0xFFF59E0B)
        else -> AdminAccent
    }
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(if (settingsIsDark()) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)),
    ) {
        Box(Modifier.fillMaxWidth(p / 100f).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

// ----------------------------------------------------------------- Filtros

/** Separadores em pílula (ui-segmented), com contagem opcional. Desliza na horizontal. */
@Composable
fun <T> AdminSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    counts: Map<T, Int> = emptyMap(),
    enabled: Boolean = true,
) {
    val dark = settingsIsDark()
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.55f)
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(12.dp))
            .background(if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (key, label) ->
            val active = key == selected
            val shape = RoundedCornerShape(8.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(32.dp)
                    .then(if (active) Modifier.shadow(1.dp, shape) else Modifier)
                    .clip(shape)
                    .background(if (!active) Color.Transparent else if (dark) MaterialTheme.colorScheme.surfaceVariant else Color.White)
                    .clickable(enabled = enabled) { onSelect(key) }
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    label,
                    fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                counts[key]?.let { n ->
                    Spacer(Modifier.width(6.dp))
                    AdminCount(n.toString(), if (active) AdminTone.Success else AdminTone.Neutral)
                }
            }
        }
    }
}

/** Campo de pesquisa compacto (ui-input com lupa). */
@Composable
fun AdminSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(settingsCardColor())
            .border(settingsCardBorderStroke(), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp),
                cursorBrush = SolidColor(AdminAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Limpar".tr(),
                modifier = Modifier.size(18.dp).clickable { onValueChange("") },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Barra de filtros (ui-toolbar): pesquisa e, por baixo, o segmentado. */
@Composable
fun AdminToolbar(
    search: String?,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    filters: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        filters?.invoke()
        if (search != null) AdminSearchField(search, onSearch, placeholder)
    }
}

// ------------------------------------------------------------------ Avatar

/** Avatar redondo; sem foto mostra as iniciais com a tinta do estado. */
@Composable
fun AdminAvatar(url: String?, size: Int = 40, name: String? = null, tone: AdminTone = AdminTone.Success) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size.dp).clip(CircleShape))
    } else {
        val initials = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.take(2)?.joinToString("") { it.take(1).uppercase() }
        Box(
            modifier = Modifier.size(size.dp).clip(CircleShape).background(tone.background()),
            contentAlignment = Alignment.Center,
        ) {
            if (!initials.isNullOrEmpty()) {
                Text(initials, fontSize = (size * 0.36f).sp, fontWeight = FontWeight.SemiBold, color = tone.text())
            } else {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = tone.text(), modifier = Modifier.size((size * 0.55f).dp))
            }
        }
    }
}

// ------------------------------------------------------------------ Outros

/** ISO-8601 → dd/MM/yyyy HH:mm na hora local; devolve o original se não for ISO. */
fun formatAdminDate(iso: String?): String? = iso?.let {
    runCatching {
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.parse(it))
    }.getOrDefault(it)
}

object AdminColors {
    val Danger = Color(0xFFFD397A)
    val Warning = Color(0xFFE09B00)
    val Info = Color(0xFF9D66FE)
    val Neutral = Color(0xFF6B7280)
}
