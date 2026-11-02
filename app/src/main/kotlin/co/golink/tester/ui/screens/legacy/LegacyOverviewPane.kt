package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.legacy.LegacyState
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.lt

/** Overview.vue */
@Composable
fun LegacyOverviewPane(
    legacy: LegacyState,
    busy: Boolean,
    vm: LegacyViewModel,
    onOpen: (String) -> Unit,
) {
    val vault = legacy.vault ?: return
    val counting = vault.status == "armed"
    val alarmed = vault.status in setOf("notified", "released")
    val canArm = vault.status in setOf("draft", "disarmed")
    val r = vault.ready
    val ready = r.guardians && r.recipients && r.items && !vault.rekey_required
    var confirmRevoke by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LegacyHeader(lt("overview"), lt("overview_text")) {
            if (counting || alarmed) StillHereButton(enabled = !busy, onClick = vm::checkin)
        }

        // Aberto: o dono ainda pode revogar.
        if (vault.status == "opened") {
            LegacyCard(border = redBorder()) {
                CardTitle(lt("opened_title"))
                CardText(lt("opened_text", "date" to shortDate(vault.opened_at)))
                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = { confirmRevoke = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(lt("revoke")) }
            }
        } else if (alarmed) {
            // Contagem decrescente terminou: o check-in cancela.
            LegacyCard(border = amberBorder()) {
                CardTitle(lt("alarm_${vault.status}_title"))
                CardText(lt("alarm_${vault.status}_text", "date" to shortDate(vault.releases_at)))
            }
        }

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

        if (canArm) {
            LegacyCard {
                CardTitle(lt("arm_title"))
                CardText(lt("arm_text"))
                Spacer(Modifier.size(8.dp))
                listOf(
                    Triple("guardians", "guardians", r.guardians),
                    Triple("recipients", "recipients", r.recipients),
                    Triple("items", "items", r.items),
                ).forEach { (key, pane, done) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpen(pane) }
                            .padding(vertical = 6.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (done) Color(0xFF10B981) else Color.Transparent)
                                .border(2.dp, if (done) Color(0xFF10B981) else MaterialTheme.colorScheme.outline, CircleShape),
                        ) {
                            if (done) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(lt("ready_$key", "count" to vault.threshold), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.size(8.dp))
                Button(onClick = vm::arm, enabled = ready && !busy, shape = RoundedCornerShape(12.dp)) { Text(lt("arm")) }
            }
        } else if (vault.paused_until != null) {
            LegacyCard {
                Text(lt("paused_until", "date" to pickedDate(vault.paused_until)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = vm::resume, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(lt("resume")) }
            }
        }

        LegacyTimeline(vault.timeline)

        val stats = listOf(
            Stat("items", Icons.Outlined.Lock, legacy.vaultItems.size.toString(), lt("items")),
            Stat("messages", Icons.Outlined.MailOutline, legacy.messages.size.toString(), lt("messages")),
            Stat(
                "guardians",
                Icons.Outlined.Shield,
                if (legacy.guardians.isNotEmpty()) "${vault.threshold}/${legacy.guardians.size}" else "0",
                lt("guardians"),
            ),
            Stat("recipients", Icons.Outlined.People, legacy.recipients.size.toString(), lt("recipients")),
        )
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { stat ->
                    LegacyCard(modifier = Modifier.weight(1f), onClick = { onOpen(stat.pane) }) {
                        Icon(stat.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(stat.value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 10.dp))
                        Text(stat.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        LegacyCard {
            SectionHead(lt("guardians")) { onOpen("guardians") }
            if (legacy.guardians.isEmpty()) CardText(lt("no_guardians"))
            legacy.guardians.forEach { g ->
                PersonRow(personName(g.meta, g.dto.email), g.dto.email) {
                    LegacyChip(
                        lt("guardian_${g.dto.status}"),
                        when (g.dto.status) {
                            "accepted" -> ChipTone.Ok
                            "invited" -> ChipTone.Warn
                            "declined" -> ChipTone.Danger
                            else -> ChipTone.Neutral
                        },
                    )
                }
            }
        }

        LegacyCard {
            SectionHead(lt("recipients")) { onOpen("recipients") }
            if (legacy.recipients.isEmpty()) CardText(lt("no_recipients"))
            legacy.recipients.forEach { p ->
                val granted = legacy.items.count { it.dto.state == "sealed" && p.dto.id in it.dto.recipient_ids }
                PersonRow(personName(p.meta, p.dto.email), p.dto.email) {
                    Text(lt("items_count", "count" to granted), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmRevoke) {
        ConfirmDialog(
            title = lt("revoke_title"),
            message = lt("revoke_text"),
            confirmText = lt("revoke"),
            destructive = true,
            onDismiss = { confirmRevoke = false },
            onConfirm = { confirmRevoke = false; vm.disarm() },
        )
    }
}

private data class Stat(val pane: String, val icon: ImageVector, val value: String, val label: String)

@Composable
fun SectionHead(title: String, onManage: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (onManage != null) TextButton(onClick = onManage) { Text(lt("manage"), fontSize = 12.sp) }
    }
}

@Composable
fun PersonRow(name: String, seed: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        LegacyAvatar(name, seed)
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
