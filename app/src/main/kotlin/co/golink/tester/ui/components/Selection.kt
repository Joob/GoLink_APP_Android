package co.golink.tester.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Topo em modo seleção: ✕, título (contagem), Selecionar tudo/Desmarcar e ⋮ opcional.
 * Partilhado por Notes e Inbox; cada um passa os textos e a cor da área.
 */
@Composable
fun SelectionTopBar(
    title: String,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    selectAllLabel: String,
    deselectAllLabel: String,
    closeLabel: String,
    accent: Color,
    accentBg: Color,
    onMore: (() -> Unit)? = null,
    moreLabel: String = "",
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = closeLabel) }
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Text(
            if (allSelected) deselectAllLabel else selectAllLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(end = 4.dp).clip(RoundedCornerShape(50)).background(accentBg).clickable(onClick = onToggleAll).padding(horizontal = 14.dp, vertical = 8.dp),
        )
        if (onMore != null) IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, contentDescription = moreLabel) }
    }
}

/** Doca escura da seleção (a mesma do Drive), no lugar da barra inferior. */
@Composable
fun SelectionDock(modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit) {
    Surface(
        color = Color(0xFF111827),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Círculo de seleção (cartões e linhas). */
@Composable
fun SelectCircle(checked: Boolean, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape)
            .background(if (checked) color else Color.Transparent)
            .border(1.5.dp, if (checked) color else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}
