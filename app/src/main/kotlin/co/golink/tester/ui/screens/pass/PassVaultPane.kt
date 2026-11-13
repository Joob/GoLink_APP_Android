package co.golink.tester.ui.screens.pass

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.pass.PassItem
import co.golink.tester.data.pass.PassState
import co.golink.tester.ui.i18n.pt
import java.text.Collator

// ---- Utilitários partilhados (iguais a passUi.js) ----

fun maskCard(number: String?): String {
    val d = number.orEmpty().filter { it.isDigit() }
    return if (d.isEmpty()) "" else "•••• ${d.takeLast(4)}"
}

fun cardBrand(number: String?): String {
    val n = number.orEmpty().filter { it.isDigit() }
    return when {
        n.startsWith("4") -> "Visa"
        Regex("^(5[1-5]|2[2-7])").containsMatchIn(n) -> "Mastercard"
        Regex("^3[47]").containsMatchIn(n) -> "Amex"
        Regex("^6(011|5)").containsMatchIn(n) -> "Discover"
        else -> ""
    }
}

fun displayName(item: PassItem): String =
    if (item.type == "card") listOf(item.name.ifEmpty { cardBrand(item.card?.number).ifEmpty { "Card" } }, maskCard(item.card?.number)).filter { it.isNotEmpty() }.joinToString(" ")
    else item.name.ifEmpty { "—" }

fun hostLabel(url: String?): String = url.orEmpty().replace(Regex("^[a-z]+://", RegexOption.IGNORE_CASE), "").removePrefix("www.").substringBefore('/')

fun itemSubtitle(item: PassItem): String = when (item.type) {
    "login" -> listOf(item.login?.username.orEmpty(), hostLabel(item.login?.url)).filter { it.isNotEmpty() }.joinToString(" · ")
    "card" -> listOf(pt("card"), item.card?.expiry?.takeIf { it.isNotEmpty() }?.let { pt("expires", "date" to it) }.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
    else -> pt("secure_note")
}

/** Abre só http(s) (igual a openUrl da web). */
fun openPassUrl(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    val href = if (Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(url)) url else "https://$url"
    if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(href)) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(href)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Valor que o botão de copiar da lista copia (password / número / nota). */
fun quickCopy(item: PassItem): Pair<String, String>? = when (item.type) {
    "login" -> item.login?.password?.takeIf { it.isNotEmpty() }?.let { pt("f.password") to it }
        ?: item.login?.username?.takeIf { it.isNotEmpty() }?.let { pt("f.username") to it }
    "card" -> item.card?.number?.takeIf { it.isNotEmpty() }?.let { pt("f.number") to it.filter { c -> c.isDigit() } }
    else -> item.notes.takeIf { it.isNotEmpty() }?.let { pt("f.note") to it }
}

private data class VaultRow(val item: PassItem, val name: String, val sub: String, val hay: String)

@Composable
fun PassVaultPane(
    vm: PassViewModel,
    state: PassState,
    filter: String,
    onFilter: (String) -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    val p = LocalPassPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val collator = remember { Collator.getInstance().apply { strength = Collator.PRIMARY } }
    val rows = remember(state.items, state.folders, filter) {
        val folderName = state.folders.associate { it.id to it.name }
        var list = state.active
        if (filter in setOf("login", "card", "note")) list = list.filter { it.type == filter }
        if (filter == "favourite") list = list.filter { it.favourite }
        list.map { i ->
            VaultRow(
                i, displayName(i), itemSubtitle(i),
                listOfNotNull(i.name, i.login?.username, i.login?.url, i.notes, i.card?.holder, folderName[i.folder]).joinToString("\n").lowercase(),
            )
        }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }
    val q = query.trim().lowercase()
    val visible = if (q.isEmpty()) rows else rows.filter { it.hay.contains(q) }
    val favourites = if (q.isEmpty() && filter == "all") visible.filter { it.item.favourite } else emptyList()
    val others = if (favourites.isEmpty()) visible else visible.filter { !it.item.favourite }
    val counts = mapOf(
        "all" to state.active.size,
        "login" to state.active.count { it.type == "login" },
        "card" to state.active.count { it.type == "card" },
        "note" to state.active.count { it.type == "note" },
    )
    val health = state.health
    val guard = LocalPassGuard.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item {
                // Pesquisa
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = p.muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(pt("search_n", "n" to state.active.size), color = p.soft, fontSize = 16.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = p.text, fontSize = 16.sp),
                            cursorBrush = SolidColor(PassBlue),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) Icon(Icons.Outlined.Close, pt("clear"), tint = p.muted, modifier = Modifier.size(20.dp).clickable { query = "" })
                }
                Spacer(Modifier.height(12.dp))
                PassChipRow {
                    PassChip(pt("all_short"), counts["all"], filter == "all") { onFilter("all") }
                    PassChip(pt("logins"), counts["login"], filter == "login") { onFilter("login") }
                    PassChip(pt("cards"), counts["card"], filter == "card") { onFilter("card") }
                    PassChip(pt("f.notes"), counts["note"], filter == "note") { onFilter("note") }
                    if (state.active.any { it.favourite }) PassChip(pt("favourites"), state.active.count { it.favourite }, filter == "favourite") { onFilter("favourite") }
                }
                if (health != null && health.flagged > 0 && q.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    AttentionBanner(health.flagged, health.score, onOpenHealth)
                }
            }
            if (visible.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (q.isNotEmpty()) pt("no_results") else if (state.active.isEmpty()) pt("empty_title") else pt("nothing_here"), color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (state.active.isEmpty() && q.isEmpty()) Text(pt("empty_text"), color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            if (favourites.isNotEmpty()) {
                item { PassSectionLabel("${pt("favourites")}  ·  ${favourites.size}") }
                item { RowGroup(favourites, health, vm, guard, onOpen) }
            }
            if (others.isNotEmpty()) {
                item { PassSectionLabel("${if (favourites.isNotEmpty()) pt("all") else if (q.isNotEmpty()) pt("searching", "q" to query.trim()) else pt("all")}  ·  ${others.size}") }
                item { RowGroup(others, health, vm, guard, onOpen) }
            }
        }
        FloatingActionButton(
            onClick = onNew,
            containerColor = PassBlue,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp).size(60.dp),
        ) { Icon(Icons.Outlined.Add, pt("new_item"), modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun AttentionBanner(flagged: Int, score: Int, onClick: () -> Unit) {
    val p = LocalPassPalette.current
    val bg = if (p.dark) Color(0x2EF59E0B) else Color(0xFFFFF4E8)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (p.dark) Color(0x33F59E0B) else Color(0xFFFFE7CC)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Color(0xFFEA580C), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pt("attention_banner", "n" to flagged), color = Color(0xFFC2410C), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(pt("attention_sub", "score" to score), color = Color(0xFFC2410C).copy(alpha = 0.85f), fontSize = 12.5.sp)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color(0xFFC2410C))
    }
}

@Composable
private fun RowGroup(rows: List<VaultRow>, health: co.golink.tester.data.pass.PassHealthResult?, vm: PassViewModel, guard: (() -> Unit) -> Unit, onOpen: (String) -> Unit) {
    PassCard {
        rows.forEachIndexed { i, r ->
            if (i > 0) PassDivider()
            PassItemRow(r.item, r.name, r.sub, health, onClick = { onOpen(r.item.id) }, onCopy = quickCopy(r.item)?.let { (label, value) ->
                { guard { vm.copy(label, value, sensitive = r.item.type != "note") } }
            })
        }
    }
}

@Composable
fun PassItemRow(item: PassItem, name: String, sub: String, health: co.golink.tester.data.pass.PassHealthResult?, onClick: () -> Unit, onCopy: (() -> Unit)?) {
    val p = LocalPassPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PassAvatar(item, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.totp != null) PassBadge(pt("badge.2fa"), "2fa")
                health?.badgeOf(item.id)?.let { PassBadge(pt("badge.$it"), it) }
            }
            if (sub.isNotEmpty()) Text(sub, color = p.muted, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onCopy != null) {
            Spacer(Modifier.width(8.dp))
            PassIconButton(Icons.Outlined.ContentCopy, pt("copy"), onClick = onCopy)
        }
    }
}
