package co.golink.tester.ui.screens.pass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.pass.PassHealth
import co.golink.tester.data.pass.PassState
import co.golink.tester.ui.i18n.pt

private val ORDER = listOf("breach", "reused", "weak", "twofa")

@Composable
fun PassHealthPane(vm: PassViewModel, state: PassState, onEdit: (String) -> Unit, onOpen: (String) -> Unit) {
    val p = LocalPassPalette.current
    val context = LocalContext.current
    val health = state.health
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (state.health == null && !state.healthRunning) vm.recheckHealth() }
    // Abre na primeira categoria com problemas (como a web).
    LaunchedEffect(health) { if (kind == null && health != null) kind = ORDER.firstOrNull { health.lists[it].orEmpty().isNotEmpty() } ?: "breach" }
    val selected = kind ?: "breach"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        PassCard(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(health?.score, Modifier.size(104.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                health == null -> pt("checking")
                                health.issues > 0 -> pt("needs_attention")
                                else -> pt("all_good")
                            },
                            fontSize = 19.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.Refresh, pt("recheck"),
                            tint = if (state.healthRunning) p.soft else p.muted,
                            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = !state.healthRunning) { vm.recheckHealth(fresh = true) },
                        )
                    }
                    if (health != null) Text(pt("issues_in", "issues" to health.issues, "logins" to health.flagged), fontSize = 15.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                    Text(pt("health_sub"), fontSize = 12.sp, color = p.soft, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
                    if (health != null && health.unchecked > 0) Text(pt("breach_unchecked", "n" to health.unchecked), fontSize = 12.sp, color = PassAmber, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // 4 mosaicos
        listOf(ORDER.take(2), ORDER.drop(2)).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { k ->
                    val n = health?.lists?.get(k)?.size ?: 0
                    // Sem resultado → "–"; breach a 0 com verificações falhadas → "?" (não afirmar que está limpo).
                    val label = when {
                        health == null -> "–"
                        k == "breach" && n == 0 && health.unchecked > 0 -> "?"
                        else -> n.toString()
                    }
                    HealthTile(k, n, label, k == selected, Modifier.weight(1f)) { kind = k }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        val list = health?.lists?.get(selected).orEmpty().mapNotNull { state.item(it) }
        PassSectionLabel("${pt("h.$selected")}  ·  ${list.size}")
        PassCard {
            if (list.isEmpty()) Text(if (health == null) pt("checking") else pt("h.none"), color = p.muted, fontSize = 13.5.sp, modifier = Modifier.padding(18.dp))
            list.forEachIndexed { i, item ->
                if (i > 0) PassDivider()
                Row(Modifier.fillMaxWidth().clickable { onOpen(item.id) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PassAvatar(item, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(pt("h.${selected}_row"), PassHealth.hostOf(item.login?.url)).filter { it.isNotEmpty() }.joinToString(" · "),
                            fontSize = 12.5.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                            SmallLink(pt("mark_fixed"), PassGreen) { vm.markFixed(item.id, selected) }
                            if (!item.login?.url.isNullOrBlank()) SmallLink(if (selected == "twofa") pt("turn_on") else pt("open"), p.muted) { openPassUrl(context, item.login?.url) }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selected == "twofa") pt("add_key") else pt("change_now"),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(PassBlue).clickable { onEdit(item.id) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallLink(text: String, color: Color, onClick: () -> Unit) {
    Text(text, color = color, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick))
}

@Composable
private fun ScoreRing(score: Int?, modifier: Modifier) {
    val p = LocalPassPalette.current
    val value = score ?: 0
    val color = if (value < 80) PassRose else PassGreen
    val fraction by animateFloatAsState(if (score == null) 0f else maxOf(0.04f, value / 100f), label = "score")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.1f
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(p.line, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (score != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value.toString(), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color)
                Text(pt("h.of_100"), fontSize = 11.sp, color = p.soft)
            }
        }
    }
}

@Composable
private fun HealthTile(kind: String, count: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPassPalette.current
    val (icon: ImageVector, tint: Color, bg: Color) = when (kind) {
        "reused" -> Triple(Icons.Outlined.Repeat, Color(0xFFB45309), if (p.dark) Color(0x33F59E0B) else Color(0xFFFEF3C7))
        "twofa" -> Triple(Icons.Outlined.Shield, PassBlue, p.accentSoft)
        else -> Triple(Icons.Outlined.WarningAmber, PassRose, if (p.dark) Color(0x33E11D48) else Color(0xFFFFE4E6))
    }
    val numberColor = when {
        label == "?" -> PassAmber
        kind == "twofa" -> PassBlue
        count == 0 -> p.soft
        kind == "reused" -> Color(0xFFD97706)
        else -> PassRose
    }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .then(if (selected) Modifier.border(1.5.dp, PassBlue, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = numberColor)
        }
        Spacer(Modifier.height(10.dp))
        Text(pt("h.$kind"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
        Text(pt("h.${kind}_text"), fontSize = 12.5.sp, color = p.muted, maxLines = 2)
    }
}
