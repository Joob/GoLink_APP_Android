package co.golink.tester.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import co.golink.tester.ui.i18n.tr

data class SpeedDialAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val subtitle: String? = null,
    /** Título da secção (ex. "Carregar"); ações seguidas com o mesmo valor ficam juntas. */
    val section: String? = null,
    /** Ícone com fundo tingido (true) ou neutro (false). */
    val accent: Boolean = true,
    /** Botão direto na pilha do "+" (os restantes vão para "Mais opções"). */
    val quick: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Botão flutuante com menu em cartão (secções + subtítulos). Vai numa camada
 * por cima do Scaffold (num BoxScope que ocupa o ecrã todo), para o fundo
 * escurecido cobrir as barras de cima e de baixo.
 *
 * [bottomOffset]: altura a deixar livre em baixo.
 */
@Composable
fun BoxScope.SpeedDialFab(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    actions: List<SpeedDialAction>,
    bottomOffset: Dp = 80.dp,
) {
    // Só uma ação (ex.: raiz de Pastas de equipa): o "+" executa-a logo, sem menu.
    val single = actions.singleOrNull()
    // Aberto por fora (ex.: "Novo" na gaveta) com uma só ação: idem.
    if (single != null && open) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            onOpenChange(false)
            single.onClick()
        }
    }
    val quick = actions.filter { it.quick }
    val rest = actions.filterNot { it.quick }
    var showMore by remember { mutableStateOf(false) }
    if (!open && showMore) showMore = false
    BackHandler(enabled = open) { if (showMore && quick.isNotEmpty()) showMore = false else onOpenChange(false) }

    // Fundo escurecido: tocar fora fecha.
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onOpenChange(false) },
        )
    }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = bottomOffset + 16.dp),
    ) {
        // Pilha rápida e cartão sobrepostos (não empilhados): numa Column, o cartão
        // entrava já com o tamanho todo e empurrava a pilha (ainda a sair) para o
        // topo — via-se o "Mais opções" lá em cima a piscar.
        Box(contentAlignment = Alignment.BottomEnd) {
        if (quick.isNotEmpty()) {
            val items = buildList {
                if (rest.isNotEmpty()) add(
                    SpeedDialAction(Icons.Filled.MoreHoriz, "Mais opções".tr(), Color.Unspecified, accent = false) { showMore = true },
                )
                addAll(quick.reversed())
            }
            // A trocar para o cartão: a pilha sai logo, sem animação.
            QuickStack(items, visible = open && !showMore, instantExit = showMore) { action ->
                if (action.quick) {
                    onOpenChange(false)
                    action.onClick()
                } else action.onClick()
            }
        }
        AnimatedMenuCard(open && (quick.isEmpty() || showMore), if (quick.isEmpty()) actions else rest) { action ->
            onOpenChange(false)
            action.onClick()
        }
        }
        Spacer(Modifier.height(16.dp))

        val rotation by animateFloatAsState(
            targetValue = if (open) 45f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "fab-rotation",
        )
        FloatingActionButton(
            onClick = { if (single != null) single.onClick() else onOpenChange(!open) },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = if (open) "Fechar menu".tr() else "Adicionar".tr(),
                modifier = Modifier.size(28.dp).rotate(rotation),
            )
        }
    }
}

/** Cartão do menu com a animação de entrada/saída a partir do "+". */
@Composable
private fun AnimatedMenuCard(visible: Boolean, actions: List<SpeedDialAction>, onClick: (SpeedDialAction) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            initialScale = 0.2f,
            transformOrigin = TransformOrigin(1f, 1f),
        ) + fadeIn(tween(140)),
        exit = scaleOut(tween(140), targetScale = 0.2f, transformOrigin = TransformOrigin(1f, 1f)) +
            fadeOut(tween(120)),
    ) {
        MenuCard(actions, onClick)
    }
}

/** Pilha de botões rápidos (rótulo em pílula + botão quadrado), de cima para baixo. */
@Composable
private fun QuickStack(items: List<SpeedDialAction>, visible: Boolean, instantExit: Boolean = false, onClick: (SpeedDialAction) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, action ->
            // Escalonado a partir do "+": o mais próximo entra primeiro.
            val delay = (items.size - 1 - index) * 25
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(200, delay)) { it / 2 } +
                    fadeIn(tween(160, delay)) +
                    scaleIn(tween(200, delay), initialScale = 0.6f, transformOrigin = TransformOrigin(1f, 0.5f)),
                exit = if (instantExit) androidx.compose.animation.ExitTransition.None
                else fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.6f, transformOrigin = TransformOrigin(1f, 0.5f)),
            ) {
                QuickRow(action) { onClick(action) }
            }
        }
    }
}

@Composable
private fun QuickRow(action: SpeedDialAction, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "quick-scale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
        ) {
            Text(
                action.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        // Alinha os botões de 44dp ao centro do "+" de 56dp.
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (action.accent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 3.dp,
            modifier = Modifier.padding(end = 6.dp).size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = if (action.accent) action.tint else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun MenuCard(actions: List<SpeedDialAction>, onClick: (SpeedDialAction) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 10.dp)) {
            actions.forEachIndexed { index, action ->
                val prev = actions.getOrNull(index - 1)
                if (action.section != null && action.section != prev?.section) {
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    Text(
                        action.section.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                MenuRow(action) { onClick(action) }
            }
        }
    }
}

@Composable
private fun MenuRow(action: SpeedDialAction, onClick: () -> Unit) {
    // Toque em "bolha": realce em pílula + encolher ligeiro com ressalto.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "row-scale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = ripple(color = MaterialTheme.colorScheme.primary), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        val bg = if (action.accent) action.tint.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        val fg = if (action.accent) action.tint else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).background(bg, RoundedCornerShape(10.dp)),
        ) {
            Icon(action.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                action.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            action.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
