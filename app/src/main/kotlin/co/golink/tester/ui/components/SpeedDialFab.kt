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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.golink.tester.ui.i18n.tr
import kotlinx.coroutines.delay

data class SpeedDialAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val onClick: () -> Unit,
)

/**
 * Botão "+" flutuante com menu em bolhas. Vai numa camada por cima do Scaffold
 * (num BoxScope que ocupa o ecrã todo), para o fundo escurecido cobrir as
 * barras de cima e de baixo e ficar por baixo das bolhas.
 *
 * [bottomOffset]: altura a deixar livre em baixo (a NavigationBar), para o
 * botão ficar por cima do separador da direita ("Ficheiros").
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
            .padding(end = 16.dp, bottom = bottomOffset + 16.dp),
    ) {
        // Bolhas: a mais próxima do botão (última da lista) aparece primeiro.
        actions.forEachIndexed { index, action ->
            val order = actions.lastIndex - index
            Bubble(action = action, visible = open, order = order, count = actions.size) {
                onOpenChange(false)
                action.onClick()
            }
            Spacer(Modifier.height(10.dp))
        }

        val rotation by animateFloatAsState(
            targetValue = if (open) 45f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "fab-rotation",
        )
        FloatingActionButton(
            onClick = { onOpenChange(!open) },
            shape = CircleShape,
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

/** Uma bolha: rótulo em pílula + botão redondo, com entrada em mola escalonada. */
@Composable
private fun Bubble(action: SpeedDialAction, visible: Boolean, order: Int, count: Int, onClick: () -> Unit) {
    // Atraso escalonado: ao abrir sobem do botão para cima; ao fechar, o inverso.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        delay(if (visible) order * 35L else (count - 1 - order) * 25L)
        shown = visible
    }

    AnimatedVisibility(
        visible = shown,
        enter = scaleIn(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            initialScale = 0.3f,
            transformOrigin = TransformOrigin(1f, 1f),
        ) + fadeIn(tween(140)) + slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        ) { it / 2 },
        exit = scaleOut(tween(120), targetScale = 0.3f, transformOrigin = TransformOrigin(1f, 1f)) +
            fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 2 },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
            ) {
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(action.tint.copy(alpha = 0.12f), CircleShape),
                    ) {
                        Icon(action.icon, contentDescription = null, tint = action.tint, modifier = Modifier.size(20.dp))
                    }
                }
            }
            // Alinha o centro das bolhas com o centro do botão principal (56dp).
            Spacer(Modifier.width(6.dp))
        }
    }
}
