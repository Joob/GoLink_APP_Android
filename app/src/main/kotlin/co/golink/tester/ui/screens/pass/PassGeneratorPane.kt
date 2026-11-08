package co.golink.tester.ui.screens.pass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.pass.PassGenerator
import co.golink.tester.data.pass.PassStrength
import co.golink.tester.ui.i18n.pt

private val SEPARATORS = listOf("-", ".", "_", " ", ",")

@Composable
fun PassGeneratorPane(vm: PassViewModel, onSaveAsLogin: (String) -> Unit) {
    val p = LocalPassPalette.current
    val prefs = vm.prefs
    var mode by remember { mutableStateOf(prefs.genMode) }
    var random by remember { mutableStateOf(prefs.genRandom) }
    var phrase by remember { mutableStateOf(prefs.genPhrase) }
    var pin by remember { mutableStateOf(prefs.genPin) }
    var value by remember { mutableStateOf("") }
    val recent by vm.recentGenerated.collectAsStateWithLifecycle()

    fun regenerate() {
        value = when (mode) {
            "passphrase" -> PassGenerator.passphrase(phrase)
            "pin" -> PassGenerator.pin(pin)
            else -> PassGenerator.random(random)
        }
    }
    LaunchedEffect(mode, random, phrase, pin) {
        prefs.genMode = mode; prefs.genRandom = random; prefs.genPhrase = phrase; prefs.genPin = pin
        regenerate()
    }
    val modeEnum = when (mode) { "passphrase" -> PassGenerator.Mode.Passphrase; "pin" -> PassGenerator.Mode.Pin; else -> PassGenerator.Mode.Random }
    val bits = PassGenerator.entropy(modeEnum, random, phrase, pin)
    val score = PassStrength.scoreFromEntropy(bits)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        PassSegmented(
            options = listOf("random" to pt("gen.random"), "passphrase" to pt("gen.passphrase"), "pin" to pt("gen.pin")),
            selected = mode,
            onSelect = { mode = it },
        )
        Spacer(Modifier.height(12.dp))
        PassCard(padding = 18.dp) {
            Box(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                ColoredPassword(value, fontSize = if (value.length > 24) 20 else 26)
            }
            Spacer(Modifier.height(10.dp))
            StrengthBars(score)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(pt("strength." + PassStrength.SCORE_LABELS[score]), color = strengthColor(score), fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(" · " + pt("crack." + PassStrength.crackTimeKey(bits)), color = p.muted, fontSize = 13.5.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(p.accentSoft).clickable { regenerate() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Refresh, pt("regenerate"), tint = PassBlue) }
                Button(
                    onClick = { vm.addGenerated(value); vm.copy(pt("f.password"), value) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PassBlue),
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(pt("copy"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
            Text(
                pt("save_new_login") + " →",
                color = PassBlue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).clickable { vm.addGenerated(value); onSaveAsLogin(value) }.padding(10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        // Tamanho / palavras
        val (label, current, limits) = when (mode) {
            "passphrase" -> Triple(pt("gen.words"), phrase.words, PassGenerator.PASSPHRASE_LIMITS)
            "pin" -> Triple(pt("gen.length"), pin.length, PassGenerator.PIN_LIMITS)
            else -> Triple(pt("gen.length"), random.length, PassGenerator.RANDOM_LIMITS)
        }
        PassCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.weight(1f))
                Text(
                    current.toString(),
                    color = PassBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.accentSoft).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Slider(
                value = current.toFloat(),
                onValueChange = { v ->
                    val n = v.toInt()
                    when (mode) {
                        "passphrase" -> if (n != phrase.words) phrase = phrase.copy(words = n)
                        "pin" -> if (n != pin.length) pin = pin.copy(length = n)
                        else -> if (n != random.length) random = random.copy(length = n)
                    }
                },
                valueRange = limits.first.toFloat()..limits.last.toFloat(),
                steps = limits.last - limits.first - 1,
                colors = SliderDefaults.colors(thumbColor = PassBlue, activeTrackColor = PassBlue, activeTickColor = PassBlue.copy(alpha = 0f), inactiveTickColor = p.line.copy(alpha = 0f)),
            )
            if (mode == "passphrase") {
                Text(pt("gen.separator"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                PassChipRow {
                    SEPARATORS.forEach { sep -> PassChip(if (sep == " ") "␣" else sep, null, phrase.separator == sep) { phrase = phrase.copy(separator = sep) } }
                }
            }
        }

        if (mode != "pin") {
            Spacer(Modifier.height(12.dp))
            PassCard {
                if (mode == "random") {
                    PassToggleRow(pt("gen.upper"), checked = random.upper) { random = random.copy(upper = it) }
                    PassDivider()
                    PassToggleRow(pt("gen.digits"), checked = random.digits) { random = random.copy(digits = it) }
                    PassDivider()
                    PassToggleRow(pt("gen.symbols"), checked = random.symbols) { random = random.copy(symbols = it) }
                    PassDivider()
                    PassToggleRow(pt("gen.lookalike"), checked = random.avoidLookalike) { random = random.copy(avoidLookalike = it) }
                } else {
                    PassToggleRow(pt("gen.capitalize"), checked = phrase.capitalize) { phrase = phrase.copy(capitalize = it) }
                    PassDivider()
                    PassToggleRow(pt("gen.include_number"), checked = phrase.number) { phrase = phrase.copy(number = it) }
                }
            }
        }

        PassSectionLabel(pt("recent_generated"))
        PassCard {
            if (recent.isEmpty()) {
                Text(pt("recent_empty"), color = p.muted, fontSize = 13.5.sp, modifier = Modifier.padding(16.dp))
            }
            recent.forEachIndexed { i, pw ->
                if (i > 0) PassDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pw, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    PassIconButton(Icons.Outlined.ContentCopy, pt("copy")) { vm.copy(pt("f.password"), pw) }
                }
            }
        }
    }
}
