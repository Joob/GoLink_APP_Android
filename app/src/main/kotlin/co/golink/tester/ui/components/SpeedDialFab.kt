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
    BackHandler(enabled = open) { onOpenChange(false) }

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
        AnimatedVisibility(
            visible = open,
            enter = scaleIn(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                initialScale = 0.2f,
                transformOrigin = TransformOrigin(1f, 1f),
            ) + fadeIn(tween(140)),
            exit = scaleOut(tween(140), targetScale = 0.2f, transformOrigin = TransformOrigin(1f, 1f)) +
                fadeOut(tween(120)),
        ) {
            MenuCard(actions) { action ->
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
            onClick = { onOpenChange(!open) },
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
