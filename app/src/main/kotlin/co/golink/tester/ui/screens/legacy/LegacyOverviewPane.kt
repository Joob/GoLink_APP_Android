package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.legacy.LegacyState
import co.golink.tester.domain.legacy.LegacyVaultDto
import co.golink.tester.ui.i18n.lt

/** Overview: hero com o estado, mosaicos e o que acontece se parares. */
@Composable
fun LegacyOverviewPane(
    legacy: LegacyState,
    busy: Boolean,
    vm: LegacyViewModel,
    onOpen: (String) -> Unit,
) {
    val vault = legacy.vault ?: return
    var confirmRevoke by remember { mutableStateOf(false) }
    var pausing by remember { mutableStateOf(false) }
    var pauseUntil by remember { mutableStateOf<String?>(null) }
    val t = vault.timeline

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hero(vault, busy, vm, onRevoke = { confirmRevoke = true }, onPause = { pausing = true }, onOpen = onOpen)

        // As partes antigas continuam válidas: não pode passar despercebido. O rekey corre
        // no load (settle); "tentar de novo" volta a recarregar e a reconciliar.
        if (vault.rekey_required) {
            LegacyCard(border = amberBorder()) {
                CardText(lt("rekey_pending"), Modifier.padding(top = 0.dp))
                TextButton(onClick = { vm.load() }, enabled = !busy) { Text(lt("retry")) }
            }
        }
        if (legacy.keysToConfirm.isNotEmpty() && legacy.editable) {
            LegacyCard(border = amberBorder(), onClick = { onOpen("guardians") }) {
                CardTitle(lt("key_pending_title"))
                CardText(lt("key_pending_text", "count" to legacy.keysToConfirm.size))
            }
        }

        val sealedMessages = legacy.messages.count { it.dto.state == "sealed" }
        val stats = listOf(
            Stat("items", Icons.Outlined.Lock, Color(0xFF7C3AED), legacy.vaultItems.size.toString(), lt("items"), lt("stat_items_hint")),
            Stat("messages", Icons.Outlined.MailOutline, Color(0xFFE11D48), legacy.messages.size.toString(), lt("messages"), lt("stat_sealed", "count" to sealedMessages)),
            Stat("guardians", Icons.Outlined.People, Color(0xFF2563EB), legacy.guardians.size.toString(), lt("guardians"), lt("stat_must_agree", "count" to vault.threshold)),
            Stat("timing", Icons.Outlined.Schedule, Color(0xFFD97706), lt("days_short", "days" to t.opens_day), lt("stat_opens"), lt("stat_opens_hint", "days" to vault.release_after_days, "wait" to vault.wait_days)),
        )
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                row.forEach { s ->
                    LegacyCard(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = { onOpen(s.pane) }) {
                        TintIcon(s.icon, s.color, size = 30)
                        Text(s.value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 10.dp))
                        Text(s.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        SectionLabel(lt("what_happens"), Modifier.padding(top = 4.dp))
        LegacyCard {
            val steps = listOf(
                lt("step_quiet_title", "days" to vault.release_after_days) to lt("step_quiet_text"),
                lt("step_wait_title", "days" to vault.wait_days) to lt("step_wait_text"),
                lt("step_open_title") to lt("step_open_text", "count" to vault.threshold, "total" to legacy.guardians.size.coerceAtLeast(vault.threshold)),
            )
            steps.forEachIndexed { i, (title, text) ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(26.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        ) { Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                        if (i < steps.size - 1) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outline))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.padding(bottom = if (i < steps.size - 1) 14.dp else 0.dp)) {
                        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                        Text(text, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Revogar e pausar pedem step-up (password ou passkey).
    if (confirmRevoke) {
        LegacyStepUpDialog(LegacyStepUpRequest.Disarm(revoke = true), vm, onDismiss = { confirmRevoke = false })
    }
    if (pausing) {
        PauseDialog(onDismiss = { pausing = false }, onPause = { day -> pausing = false; pauseUntil = day })
    }
    pauseUntil?.let { day ->
        LegacyStepUpDialog(LegacyStepUpRequest.Pause(day), vm, onDismiss = { pauseUntil = null })
    }
}

private data class Stat(val pane: String, val icon: ImageVector, val color: Color, val value: String, val label: String, val hint: String)

/** Cartão de topo: muda de cor e de ações conforme o estado do cofre. */
@Composable
private fun Hero(vault: LegacyVaultDto, busy: Boolean, vm: LegacyViewModel, onRevoke: () -> Unit, onPause: () -> Unit, onOpen: (String) -> Unit) {
    val t = vault.timeline
    val alarmed = vault.status in setOf("notified", "released")
    val paused = vault.paused_until != null
    val brush: Brush = when {
        vault.status == "opened" -> LegacyDangerBrush
        alarmed -> LegacyAlarmBrush
        paused -> LegacyIdleBrush
        else -> LegacyHeroBrush
    }
    HeroBox(brush) {
        when {
            vault.status == "opened" -> {
                HeroTitle(lt("opened_title"))
                HeroText(lt("opened_text", "date" to shortDate(vault.opened_at)))
                Spacer(Modifier.height(14.dp))
                HeroPrimary(lt("revoke"), Color(0xFFB91C1C), enabled = !busy, onClick = onRevoke)
            }
            alarmed -> {
                HeroTitle(lt("alarm_${vault.status}_title"))
                HeroText(lt("alarm_${vault.status}_text", "date" to shortDate(vault.releases_at)))
                Spacer(Modifier.height(14.dp))
                HeroPrimary(lt("checkin_short"), Color(0xFFB45309), icon = Icons.Outlined.Check, enabled = !busy, onClick = vm::checkin)
            }
            vault.status == "armed" -> {
                val daysLeft = (t.opens_day - t.days_since).coerceAtLeast(0)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingProgress(if (t.opens_day > 0) daysLeft.toFloat() / t.opens_day else 0f, daysLeft.toString(), lt("days_left"))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        HeroTitle(lt(if (paused) "status_paused" else "status_active"))
                        val last = if (t.days_since == 0) lt("last_today") else lt("last_days", "days" to t.days_since)
                        HeroText(if (paused) lt("paused_until", "date" to pickedDate(vault.paused_until)) else lt("hero_text", "last" to last, "days" to vault.release_after_days))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (paused) {
                        HeroPrimary(lt("resume"), Color(0xFF475569), enabled = !busy, onClick = vm::resume, modifier = Modifier.weight(1f))
                    } else {
                        HeroPrimary(lt("checkin_short"), Color(0xFF6D28D9), icon = Icons.Outlined.Check, enabled = !busy, onClick = vm::checkin, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = onPause,
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) { Text(lt("pause"), fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            else -> {
                // Rascunho ou desarmado: o que falta para armar.
                val r = vault.ready
                val ready = r.guardians && r.recipients && r.items && !vault.rekey_required
                HeroTitle(lt("arm_title"))
                HeroText(lt("arm_text"))
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple("guardians", "guardians", r.guardians),
                    Triple("recipients", "recipients", r.recipients),
                    Triple("items", "items", r.items),
                ).forEach { (key, pane, done) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(pane) }.padding(vertical = 5.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (done) Color.White else Color.Transparent)
                                .border(2.dp, Color.White.copy(alpha = if (done) 1f else 0.6f), CircleShape),
                        ) {
                            if (done) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color(0xFF6D28D9), modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(lt("ready_$key", "count" to vault.threshold), fontSize = 13.sp, color = Color.White)
                    }
                }
                Spacer(Modifier.height(10.dp))
                HeroPrimary(lt("arm"), Color(0xFF6D28D9), enabled = ready && !busy, onClick = vm::arm)
            }
        }
    }
}

@Composable
private fun HeroBox(brush: Brush, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun HeroTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
}

@Composable
private fun HeroText(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
}

/** Botão branco com texto na cor do hero. */
@Composable
private fun HeroPrimary(text: String, fg: Color, enabled: Boolean, onClick: () -> Unit, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = fg,
            disabledContainerColor = Color.White.copy(alpha = 0.5f),
            disabledContentColor = fg.copy(alpha = 0.6f),
        ),
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
