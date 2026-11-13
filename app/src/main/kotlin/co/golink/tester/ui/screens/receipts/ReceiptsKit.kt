package co.golink.tester.ui.screens.receipts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.receipts.RcCategories
import co.golink.tester.ui.i18n.rt

/** Paleta dos Recibos (= .receipts-scope + receipts-ui.scss da web). */
@Immutable
data class RcPalette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val soft: Color,
    val accent: Color,
    val accentSoft: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val heroFrom: Color,
    val heroTo: Color,
    val track: Color,
)

private fun light() = RcPalette(
    dark = false, bg = Color(0xFFF2F4F5), card = Color.White, line = Color(0xFFEBEBEB), text = Color(0xFF111827),
    muted = Color(0xFF6B7280), soft = Color(0xFF9CA3AF), accent = Color(0xFF0D9488), accentSoft = Color(0xFFE6F7F5),
    good = Color(0xFF059669), warn = Color(0xFFB45309), bad = Color(0xFFBE123C),
    heroFrom = Color(0xFF0F766E), heroTo = Color(0xFF134E4A), track = Color(0xFFEDEFF1),
)

private fun dark() = RcPalette(
    dark = true, bg = Color(0xFF0E1113), card = Color(0xFF181B1F), line = Color(0xFF2A2B2F), text = Color(0xFFE5E7EB),
    muted = Color(0xFF9CA3AF), soft = Color(0xFF6B7280), accent = Color(0xFF2DD4BF), accentSoft = Color(0xFF12302D),
    good = Color(0xFF34D399), warn = Color(0xFFFBBF24), bad = Color(0xFFFB7185),
    heroFrom = Color(0xFF115E59), heroTo = Color(0xFF0B3B38), track = Color(0xFF26292E),
)

val LocalRc = staticCompositionLocalOf { light() }

@Composable
fun rcPalette(): RcPalette = if (MaterialTheme.colorScheme.background.luminance() < 0.4f) dark() else light()

/** Cor da categoria de despesa (CVD validada na web; escuro troca 4 delas). */
fun spendColor(key: String?, dark: Boolean): Color = when (key) {
    "groceries" -> Color(0xFF0D9488)
    "restaurants" -> Color(0xFFD97706)
    "transport" -> if (dark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    "health" -> if (dark) Color(0xFFF43F5E) else Color(0xFFE11D48)
    "home" -> if (dark) Color(0xFF8B5CF6) else Color(0xFF7C3AED)
    "education" -> Color(0xFF0891B2)
    "shopping" -> if (dark) Color(0xFFEC4899) else Color(0xFFDB2777)
    else -> if (dark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
}

/** Cor do bucket IRS (reaproveita a da categoria de despesa, como IRS_COLOR_VAR). */
fun irsColor(key: String?, dark: Boolean): Color = spendColor(RcCategories.IRS_COLOR_OF[key], dark)

fun catLabel(key: String?): String = if (key in RcCategories.SPEND_KEYS) rt("cat_short.$key") else rt("irs.none")
fun catLongLabel(key: String?): String = if (key in RcCategories.SPEND_KEYS) rt("cat.$key") else rt("irs.none")
fun irsLabel(key: String?): String = rt("irs.${key?.takeIf { it in RcCategories.IRS_ALL } ?: "none"}")
fun irsPt(key: String?): String = rt("irs_pt.${key?.takeIf { it in RcCategories.IRS_ALL } ?: "none"}")

@Composable
fun RcCard(modifier: Modifier = Modifier, padding: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalRc.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).padding(padding), content = content)
}

@Composable
fun RcDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, color = LocalRc.current.line, thickness = 1.dp)

@Composable
fun RcSectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val p = LocalRc.current
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = p.muted, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.muted)
    }
}

/** Selo pequeno com a cor de uma categoria (ex.: "Gerais" ao lado de Groceries). */
@Composable
fun RcTag(text: String, color: Color) {
    val p = LocalRc.current
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (p.dark) lerpWhite(color) else darken(color),
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = if (p.dark) 0.22f else 0.13f)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

fun darken(c: Color, f: Float = 0.8f) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)
fun lerpWhite(c: Color, f: Float = 0.35f) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, c.alpha)

/** Pill de estado: On track / Close / Over / Within / Not yet / No NIF. */
@Composable
fun RcPill(text: String, tone: String) {
    val p = LocalRc.current
    val color = when (tone) {
        "good" -> p.good
        "warn" -> p.warn
        "bad" -> p.bad
        else -> p.muted
    }
    Text(
        text,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = if (p.dark) 0.18f else 0.11f)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun RcDot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(RoundedCornerShape(2.dp)).background(color))

/** Chip de filtro (All 496 · To validate 13…). */
@Composable
fun RcChip(label: String, count: Int? = null, selected: Boolean, color: Color? = null, onClick: () -> Unit) {
    val p = LocalRc.current
    val bg = when {
        selected && color != null -> color.copy(alpha = if (p.dark) 0.25f else 0.14f)
        selected -> if (p.dark) Color.White else Color(0xFF111827)
        else -> p.card
    }
    val fg = when {
        selected && color != null -> if (p.dark) lerpWhite(color) else darken(color)
        selected -> if (p.dark) Color(0xFF111827) else Color.White
        else -> p.text
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(
                if (selected && color != null) Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                else if (selected) Modifier else Modifier.border(1.dp, p.line, RoundedCornerShape(999.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = fg.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

@Composable
fun RcChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Controlo segmentado (Day | Week | Month | Year · Overview | e-Fatura check). */
@Composable
fun RcSegmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalRc.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (p.dark) Color(0xFF22262B) else Color(0xFFE5E8EA)).padding(4.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) p.card else Color.Transparent).clickable { onSelect(key) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) p.text else p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun RcToggleRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = LocalRc.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, color = p.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = Color.White),
        )
    }
}

/** Barra de progresso com a marca do ritmo constante (a linha vertical do mockup). */
@Composable
fun RcProgress(fill: Float, color: Color, pace: Float? = null, height: Dp = 8.dp, modifier: Modifier = Modifier) {
    val p = LocalRc.current
    BoxWithConstraints(modifier.fillMaxWidth().height(height + 8.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(999.dp)).background(p.track))
        Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(999.dp)).background(color))
        if (pace != null && pace > 0f && pace < 1f) {
            Box(Modifier.offset(x = maxWidth * pace - 1.dp).width(2.dp).fillMaxHeight().background(p.text))
        }
    }
}

/** Stepper − valor + (orçamentos). */
@Composable
fun RcStepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, center: @Composable RowScope.() -> Unit = {
    Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LocalRc.current.text, modifier = Modifier.padding(horizontal = 14.dp))
}) {
    val p = LocalRc.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RcSquareButton(Icons.Outlined.Remove, "−", onMinus)
        center()
        RcSquareButton(Icons.Outlined.Add, "+", onPlus)
    }
}

@Composable
fun RcSquareButton(icon: ImageVector, description: String?, onClick: () -> Unit, size: Dp = 44.dp) {
    val p = LocalRc.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(if (p.dark) Color(0xFF26292E) else Color(0xFFEDEFF1)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = p.text, modifier = Modifier.size(20.dp)) }
}

/** Anel do orçamento (detalhe). */
@Composable
fun RcRing(fraction: Float, color: Color, size: Dp, stroke: Dp = 14.dp, content: @Composable () -> Unit) {
    val p = LocalRc.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val inset = s / 2
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(p.track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Avatar do comerciante: inicial + cor da categoria. */
@Composable
fun RcAvatar(name: String, cat: String?, size: Dp = 42.dp) {
    val p = LocalRc.current
    val c = spendColor(cat, p.dark)
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = if (p.dark) 0.22f else 0.12f)), contentAlignment = Alignment.Center) {
        Text(RcCategories.initialOf(name), color = if (p.dark) lerpWhite(c) else darken(c), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
    }
}

@Composable
fun RcTopBar(title: String, onMenu: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalRc.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onMenu != null) IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, rt("menu"), tint = p.text) } else Spacer(Modifier.size(12.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
fun RcBackBar(title: String?, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val p = LocalRc.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, rt("back"), tint = p.text) }
        Text(title.orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Seletor de ano (2026 ▾) do topo. */
@Composable
fun RcYearPill(year: Int, years: List<Int>, onPick: (Int) -> Unit) {
    val p = LocalRc.current
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, p.line, RoundedCornerShape(999.dp)).background(p.card).clickable { open = true }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$year ▾", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            years.forEach { y ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(y.toString(), fontWeight = if (y == year) FontWeight.Bold else FontWeight.Normal) }, onClick = { open = false; onPick(y) })
            }
        }
    }
}

/** Barra inferior: Spending · Receipts · IRS · Budgets (selo no IRS = por fazer). */
@Composable
fun RcTabBar(tabs: List<Triple<String, ImageVector, String>>, selected: String, badges: Map<String, Int>, onSelect: (String) -> Unit) {
    val p = LocalRc.current
    Column(Modifier.fillMaxWidth().background(p.card)) {
        RcDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp)) {
            tabs.forEach { (key, icon, label) ->
                val on = key == selected
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(key) }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) p.accentSoft else Color.Transparent).padding(horizontal = 18.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                        val n = badges[key] ?: 0
                        BadgedBox(badge = { if (n > 0) Badge(containerColor = Color(0xFFF59E0B), contentColor = Color.White) { Text(if (n > 99) "99+" else n.toString()) } }) {
                            Icon(icon, null, tint = if (on) p.text else p.muted, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) p.text else p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Linha "rótulo · valor" dos detalhes (Merchant NIF, Your NIF, VAT…). */
@Composable
fun RcInfoRow(label: String, value: String, valueColor: Color? = null, mono: Boolean = false) {
    val p = LocalRc.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = p.muted, modifier = Modifier.width(116.dp))
        Text(value, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = valueColor ?: p.text, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, modifier = Modifier.weight(1f))
    }
}

@Composable
fun RcCircle(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))
