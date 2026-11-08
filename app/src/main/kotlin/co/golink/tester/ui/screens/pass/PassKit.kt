package co.golink.tester.ui.screens.pass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
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
import co.golink.tester.data.pass.PassItem

/** Cor do Pass (= #2f5be7 da web, `.pass-scope`). */
val PassBlue = Color(0xFF2F5BE7)
val PassRose = Color(0xFFE11D48)
val PassGreen = Color(0xFF059669)
val PassAmber = Color(0xFFD97706)

@Immutable
data class PassPalette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val soft: Color,
    val chip: Color,
    val accentSoft: Color,
)

val LocalPassPalette = staticCompositionLocalOf { lightPalette() }

private fun lightPalette() = PassPalette(
    dark = false, bg = Color(0xFFF2F4F8), card = Color.White, line = Color(0xFFE8EBF1), text = Color(0xFF0F172A),
    muted = Color(0xFF64748B), soft = Color(0xFF94A3B8), chip = Color.White, accentSoft = Color(0xFFE8EEFD),
)

private fun darkPalette() = PassPalette(
    dark = true, bg = Color(0xFF0E1116), card = Color(0xFF181C23), line = Color(0xFF262B34), text = Color(0xFFE5E7EB),
    muted = Color(0xFF9CA3AF), soft = Color(0xFF6B7280), chip = Color(0xFF181C23), accentSoft = Color(0xFF1D2A4D),
)

@Composable
fun passPalette(): PassPalette = if (MaterialTheme.colorScheme.background.luminance() < 0.4f) darkPalette() else lightPalette()

private val AVATAR_COLORS = listOf(
    0xFF1E40AF, 0xFFDB2777, 0xFFF59E0B, 0xFF111827, 0xFF7C3AED, 0xFF64748B,
    0xFFDC2626, 0xFF2563EB, 0xFF10B981, 0xFF0E7490, 0xFFC2410C, 0xFF4D7C0F,
).map { Color(it) }

/** Igual a avatarColor() da web (hash h*31 por carácter). */
fun avatarColor(name: String): Color {
    var h = 0L
    val s = name.ifEmpty { "?" }
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        h = (h * 31 + Character.toChars(cp)[0].code) and 0xFFFFFFFFL
        i += Character.charCount(cp)
    }
    return AVATAR_COLORS[(h % AVATAR_COLORS.size).toInt()]
}

fun avatarLetter(name: String): String {
    val clean = name.dropWhile { !it.isLetterOrDigit() }
    return clean.firstOrNull()?.uppercase() ?: "?"
}

@Composable
fun PassAvatar(item: PassItem, size: Dp = 40.dp) {
    val color = when {
        item.type == "card" -> Color(0xFF2563EB)
        item.type == "note" -> if (item.locked) Color(0xFF9CA3AF) else Color(0xFF10B981)
        else -> avatarColor(item.name)
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        when (item.type) {
            "card" -> Icon(Icons.Outlined.CreditCard, null, tint = Color.White, modifier = Modifier.size(size * 0.48f))
            "note" -> Icon(Icons.Outlined.Description, null, tint = Color.White, modifier = Modifier.size(size * 0.48f))
            else -> Text(avatarLetter(item.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        }
    }
}

@Composable
fun PassCard(modifier: Modifier = Modifier, padding: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPassPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .padding(padding),
        content = content,
    )
}

@Composable
fun PassDivider() = HorizontalDivider(color = LocalPassPalette.current.line, thickness = 1.dp)

@Composable
fun PassSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = LocalPassPalette.current.muted,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** Selo pequeno (2FA, breach, reused, weak, Strong…). */
@Composable
fun PassBadge(text: String, kind: String) {
    val p = LocalPassPalette.current
    val (bg, fg) = when (kind) {
        "breach" -> (if (p.dark) Color(0x33E11D48) else Color(0xFFFFE4E6)) to PassRose
        "weak" -> (if (p.dark) Color(0x33E11D48) else Color(0xFFFFE4E6)) to PassRose
        "reused" -> (if (p.dark) Color(0x33F59E0B) else Color(0xFFFEF3C7)) to Color(0xFFB45309)
        "strong", "good" -> (if (p.dark) Color(0x3310B981) else Color(0xFFD1FAE5)) to PassGreen
        else -> p.accentSoft to PassBlue
    }
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Chip de filtro (All 10 · Logins 7…). */
@Composable
fun PassChip(label: String, count: Int? = null, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPassPalette.current
    val bg = if (selected) (if (p.dark) Color.White else Color(0xFF0F172A)) else p.chip
    val fg = if (selected) (if (p.dark) Color(0xFF0F172A) else Color.White) else p.text
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, p.line, RoundedCornerShape(999.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(5.dp))
            Text(count.toString(), color = fg.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

@Composable
fun PassChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Controlo segmentado (Random | Passphrase | PIN, Login | Card | Secure note…). */
@Composable
fun PassSegmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPassPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (p.dark) Color(0xFF232833) else Color(0xFFE6E9F0))
            .padding(4.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) p.card else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) p.text else p.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun PassToggleRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = LocalPassPalette.current
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
            colors = SwitchDefaults.colors(checkedTrackColor = PassBlue, checkedThumbColor = Color.White),
        )
    }
}

/** Anel do TOTP (fica vermelho nos últimos 7 s). */
@Composable
fun TotpRing(remaining: Int, period: Int, size: Dp = 40.dp, showNumber: Boolean = true) {
    val p = LocalPassPalette.current
    val ending = remaining <= 7
    val color = if (ending) PassRose else PassBlue
    val fraction by animateFloatAsState(remaining / period.toFloat(), tween(900), label = "totp")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.1f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(p.line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (showNumber) Text(remaining.toString(), fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** 4 barras de força (score 0–4). */
@Composable
fun StrengthBars(score: Int, modifier: Modifier = Modifier) {
    val p = LocalPassPalette.current
    val color = strengthColor(score)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i ->
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (i < maxOf(1, score)) color else p.line))
        }
    }
}

fun strengthColor(score: Int): Color = when (score) {
    0, 1 -> PassRose
    2 -> PassAmber
    else -> PassGreen
}

/** Texto monoespaçado com cores por classe (números azuis, símbolos vermelhos). */
@Composable
fun ColoredPassword(text: String, fontSize: Int = 26) {
    val p = LocalPassPalette.current
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        text.forEach { c ->
            val color = when (co.golink.tester.data.pass.PassGenerator.charKind(c)) {
                co.golink.tester.data.pass.PassGenerator.CharKind.Digit -> PassBlue
                co.golink.tester.data.pass.PassGenerator.CharKind.Symbol -> PassRose
                else -> p.text
            }
            pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
            append(c)
            pop()
        }
    }
    Text(annotated, fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, lineHeight = (fontSize + 8).sp)
}

/** Barra inferior: Vault · 2FA · Generator · Health (selo = nº de problemas). */
@Composable
fun PassTabBar(tabs: List<Triple<String, ImageVector, String>>, selected: String, badges: Map<String, Int>, onSelect: (String) -> Unit) {
    val p = LocalPassPalette.current
    Column(Modifier.fillMaxWidth().background(p.card)) {
        PassDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp)) {
            tabs.forEach { (key, icon, label) ->
                val on = key == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(key) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) p.accentSoft else Color.Transparent).padding(horizontal = 18.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val n = badges[key] ?: 0
                        BadgedBox(badge = { if (n > 0) Badge(containerColor = PassRose) { Text(n.toString()) } }) {
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

/** Botão de ícone quadrado suave (copiar, mostrar, abrir). */
@Composable
fun PassIconButton(icon: ImageVector, description: String?, tint: Color = PassBlue, onClick: () -> Unit) {
    val p = LocalPassPalette.current
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.accentSoft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun PassDot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))
