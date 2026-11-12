package co.golink.tester.ui.screens.pass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import co.golink.tester.data.pass.PassState
import co.golink.tester.data.pass.PassStrength
import co.golink.tester.data.pass.Totp
import co.golink.tester.data.pass.TotpConfig
import co.golink.tester.data.pass.TotpNow
import co.golink.tester.ui.common.relativeAgo
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.pt
import kotlinx.coroutines.delay

/** Relógio partilhado de 1 s (alinhado ao segundo) para os códigos TOTP; pára com a app em fundo (abaixo de STARTED). */
@Composable
fun rememberNowMs(): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1000 - (value % 1000))
            }
        }
    }
    return now
}

@Composable
fun rememberTotp(cfg: TotpConfig?, nowMs: Long): TotpNow? {
    val step = cfg?.let { nowMs / 1000 / it.period }
    val codes = remember(cfg, step) { cfg?.let { runCatching { Totp.now(it, nowMs) }.getOrNull() } }
    return codes?.copy(remaining = cfg!!.period - ((nowMs / 1000) % cfg.period).toInt())
}

@Composable
fun PassItemDetail(vm: PassViewModel, state: PassState, id: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val p = LocalPassPalette.current
    val item = state.item(id) ?: return
    val context = LocalContext.current
    val guard = LocalPassGuard.current
    var shown by remember(id) { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var showOld by remember { mutableStateOf(false) }
    val reveal: (String) -> Unit = { key -> if (key in shown) shown = shown - key else guard { shown = shown + key } }

    Column(Modifier.fillMaxSize()) {
        PassBackBar("", onBack) {
            if (!item.isTrashed && !item.locked) {
                IconButton(onClick = { vm.toggleFavourite(item.id) }) {
                    Icon(if (item.favourite) Icons.Filled.Star else Icons.Outlined.StarOutline, pt("favourite"), tint = if (item.favourite) Color(0xFFF59E0B) else p.text)
                }
                IconButton(onClick = { confirm = "trash" }) { Icon(Icons.Outlined.DeleteOutline, pt("move_to_trash"), tint = p.text) }
            }
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PassAvatar(item, 72.dp)
                Text(displayName(item), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(state.folder(item.folder)?.name, item.changedAt?.let { pt("edited", "when" to relativeAgo(it)) } ?: item.updatedAt?.let { pt("edited", "when" to relativeAgo(it)) })
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(18.dp))

            if (item.locked) {
                PassCard(padding = 18.dp) { Text(pt("item_unreadable"), color = p.muted, fontSize = 14.sp) }
                return@Column
            }

            val fields = mutableListOf<@Composable () -> Unit>()
            item.login?.let { l ->
                if (l.username.isNotEmpty()) fields += { FieldRow(pt("f.username"), l.username) { PassIconButton(Icons.Outlined.ContentCopy, pt("copy")) { vm.copy(pt("f.username"), l.username, sensitive = false) } } }
                if (l.password.isNotEmpty()) fields += {
                    val score = remember(l.password) { PassStrength.of(l.password).score }
                    SecretRow(
                        label = pt("f.password"),
                        value = l.password,
                        shown = "password" in shown,
                        badge = { PassBadge(pt("strength." + PassStrength.SCORE_LABELS[score]), if (score >= 3) "strong" else "weak") },
                        onReveal = { reveal("password") },
                        onCopy = { guard { vm.copy(pt("f.password"), l.password) } },
                    )
                }
                if (l.totp.isNotEmpty()) fields += { TotpRow(item.totp, vm) }
                if (l.url.isNotEmpty()) fields += {
                    FieldRow(pt("f.website"), hostLabel(l.url), valueColor = PassBlue, onValueClick = { openPassUrl(context, l.url) }) {
                        PassIconButton(Icons.AutoMirrored.Outlined.OpenInNew, pt("open"), tint = p.muted) { openPassUrl(context, l.url) }
                    }
                }
            }
            item.card?.let { c ->
                if (c.holder.isNotEmpty()) fields += { FieldRow(pt("f.holder"), c.holder) { PassIconButton(Icons.Outlined.ContentCopy, pt("copy")) { vm.copy(pt("f.holder"), c.holder, sensitive = false) } } }
                if (c.number.isNotEmpty()) fields += {
                    SecretRow(pt("f.number"), c.number, "number" in shown, masked = maskCard(c.number), onReveal = { reveal("number") }, onCopy = { guard { vm.copy(pt("f.number"), c.number.filter { it.isDigit() }) } })
                }
                if (c.expiry.isNotEmpty()) fields += { FieldRow(pt("f.expiry"), c.expiry) { PassIconButton(Icons.Outlined.ContentCopy, pt("copy")) { vm.copy(pt("f.expiry"), c.expiry, sensitive = false) } } }
                if (c.cvv.isNotEmpty()) fields += { SecretRow(pt("f.cvv"), c.cvv, "cvv" in shown, onReveal = { reveal("cvv") }, onCopy = { guard { vm.copy(pt("f.cvv"), c.cvv) } }) }
                if (c.pin.isNotEmpty()) fields += { SecretRow(pt("f.pin"), c.pin, "pin" in shown, onReveal = { reveal("pin") }, onCopy = { guard { vm.copy(pt("f.pin"), c.pin) } }) }
            }
            if (item.type == "note" && item.notes.isNotEmpty()) fields += {
                FieldRow(pt("f.note"), item.notes, multiline = true) { PassIconButton(Icons.Outlined.ContentCopy, pt("copy")) { vm.copy(pt("f.note"), item.notes) } }
            }
            if (fields.isNotEmpty()) {
                PassCard {
                    fields.forEachIndexed { i, f -> if (i > 0) PassDivider(); f() }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Pasta · Partilhado · Notas
            PassCard(padding = 0.dp) {
                MetaRow(pt("folder"), state.folder(item.folder)?.name ?: pt("no_folder"))
                PassDivider()
                MetaRow(pt("shared_with"), pt("only_you"))
                if (item.type != "note" && item.notes.isNotEmpty()) {
                    PassDivider()
                    MetaRow(pt("f.notes"), item.notes)
                }
            }

            // Histórico
            val events = item.events
            val old = item.passwordHistory
            if (events.isNotEmpty() || old.isNotEmpty()) {
                PassSectionLabel(pt("history"))
                PassCard(padding = 14.dp) {
                    events.takeLast(6).reversed().forEach { e ->
                        Text(pt("ev.${e.kind}", "when" to relativeAgo(e.at)), fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(vertical = 3.dp))
                    }
                    if (old.isNotEmpty()) {
                        TextButton(onClick = { if (showOld) showOld = false else guard { showOld = true } }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text(if (showOld) pt("hide_old") else pt("old_passwords", "n" to old.size), color = PassBlue, fontSize = 13.sp)
                        }
                        if (showOld) old.forEach { o ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(o.password, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = p.text, modifier = Modifier.weight(1f))
                                Text(relativeAgo(o.until), fontSize = 12.sp, color = p.soft)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            if (item.isTrashed) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { confirm = "forever" }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(pt("delete_forever"), color = PassRose) }
                    Button(onClick = { vm.restore(item.id) }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = PassBlue)) { Text(pt("restore"), fontWeight = FontWeight.SemiBold) }
                }
            } else {
                // "Send once securely" fica escondido (como na web): Edit ocupa a largura toda.
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = PassBlue)) {
                    Text(pt("edit"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }

    when (confirm) {
        "trash" -> ConfirmDialog(
            title = pt("trash_title"),
            message = pt("trash_text", "name" to item.name, "days" to state.trashDays),
            confirmText = pt("move_to_trash"),
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = { confirm = null; vm.trash(item.id); onBack() },
        )
        "forever" -> ConfirmDialog(
            title = pt("delete_forever_title"),
            message = pt("delete_forever_text", "name" to item.name),
            confirmText = pt("delete_forever"),
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = { confirm = null; vm.deleteForever(item.id); onBack() },
        )
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    valueColor: Color? = null,
    multiline: Boolean = false,
    onValueClick: (() -> Unit)? = null,
    action: @Composable () -> Unit,
) {
    val p = LocalPassPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.muted)
            Text(
                value,
                fontSize = 17.sp,
                color = valueColor ?: p.text,
                maxLines = if (multiline) 40 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).then(if (onValueClick != null) Modifier.clickable(onClick = onValueClick) else Modifier),
            )
        }
        Spacer(Modifier.width(10.dp))
        action()
    }
}

@Composable
private fun SecretRow(
    label: String,
    value: String,
    shown: Boolean,
    masked: String? = null,
    badge: (@Composable () -> Unit)? = null,
    onReveal: () -> Unit,
    onCopy: () -> Unit,
) {
    val p = LocalPassPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.muted)
                badge?.invoke()
            }
            Text(
                if (shown) value else masked ?: "•".repeat(minOf(14, value.length)),
                fontSize = if (shown) 17.sp else 18.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = if (shown) 0.sp else 2.sp,
                color = p.text,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        PassIconButton(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (shown) pt("hide") else pt("show"), tint = p.text, onClick = onReveal)
        Spacer(Modifier.width(8.dp))
        PassIconButton(Icons.Outlined.ContentCopy, pt("copy"), onClick = onCopy)
    }
}

@Composable
private fun TotpRow(cfg: TotpConfig?, vm: PassViewModel) {
    val p = LocalPassPalette.current
    // Um só relógio/código para a linha toda (texto e anel).
    val now = rememberTotp(cfg, rememberNowMs())
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pt("f.totp"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.muted)
            if (cfg == null) {
                Text(pt("totp_invalid"), fontSize = 12.5.sp, color = PassRose, modifier = Modifier.padding(top = 2.dp))
                return@Column
            }
            Text(
                now?.let { Totp.format(it.code) }.orEmpty(),
                fontSize = 28.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if ((now?.remaining ?: 30) <= 7) PassRose else PassBlue,
                letterSpacing = 1.sp,
            )
        }
        if (now != null) {
            TotpRing(now.remaining, now.period, 36.dp, showNumber = false)
            Spacer(Modifier.width(10.dp))
            PassIconButton(Icons.Outlined.ContentCopy, pt("copy")) { vm.copy(pt("f.totp"), now.code) }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    val p = LocalPassPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(label, fontSize = 15.sp, color = p.muted, modifier = Modifier.width(110.dp))
        Text(value, fontSize = 15.sp, color = p.text, modifier = Modifier.weight(1f))
    }
}
