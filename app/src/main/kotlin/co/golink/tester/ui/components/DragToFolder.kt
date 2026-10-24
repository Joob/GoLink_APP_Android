package co.golink.tester.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.ui.i18n.tr
import kotlin.math.roundToInt

/** Destino de largada: uma pasta da lista ou um elemento do caminho (toId null = raiz do Drive). */
data class DropTarget(val key: String, val toId: String?, val name: String)

/**
 * Arrastar itens para uma pasta: toque longo + arrastar levanta o item (ou a
 * seleção toda, se o item estiver selecionado); largar em cima de uma pasta
 * ou do caminho move-os. Toque longo sem arrastar mantém o comportamento
 * antigo (modo de seleção). Coordenadas em root.
 */
class DragToFolderState {
    var dragging by mutableStateOf<List<BrowseItem>>(emptyList())
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var targetKey by mutableStateOf<String?>(null)
        private set

    val isDragging: Boolean get() = dragging.isNotEmpty()

    // Fora do estado observável: muda a cada frame de scroll.
    private val targets = mutableMapOf<String, Pair<DropTarget, Rect>>()

    val target: DropTarget? get() = targetKey?.let { targets[it]?.first }

    fun register(target: DropTarget, bounds: Rect) { targets[target.key] = target to bounds }
    fun unregister(key: String) { targets.remove(key); if (targetKey == key) targetKey = null }

    fun start(items: List<BrowseItem>, at: Offset) { dragging = items; move(at) }

    fun move(at: Offset) {
        pointer = at
        refreshTarget()
    }

    /** Recalcula o destino por baixo do dedo (também depois de scroll automático). */
    fun refreshTarget() {
        if (!isDragging) return
        val ids = dragging.map { it.id }.toSet()
        // Pastas a ser arrastadas não são destino delas próprias.
        targetKey = targets.values.firstOrNull { (t, r) -> t.toId !in ids && r.contains(pointer) }?.first?.key
    }

    /** Devolve (itens, destino) se largou em cima de um destino. */
    fun end(): Pair<List<BrowseItem>, DropTarget>? {
        val result = target?.let { dragging.takeIf { d -> d.isNotEmpty() }?.to(it) }
        cancel()
        return result
    }

    fun cancel() { dragging = emptyList(); targetKey = null }
}

/**
 * Cliques do item com arrastar: [onClick] normal, toque longo sem mover →
 * [onLongClick], toque longo + mover → arrasta [dragItems] (o item ou a
 * seleção). Consome o "up" depois do toque longo para o clickable não
 * disparar [onClick]. O item fica "preso" na lista enquanto é arrastado,
 * para o gesto não morrer quando sai do ecrã com o scroll automático.
 */
fun Modifier.dragToFolderClicks(
    item: BrowseItem,
    state: DragToFolderState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    dragItems: () -> List<BrowseItem>,
    onDrop: (List<BrowseItem>, DropTarget) -> Unit,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val pinnable = LocalPinnableContainer.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentItems by rememberUpdatedState(dragItems)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val currentDrop by rememberUpdatedState(onDrop)
    this
        .onGloballyPositioned { coords = it }
        .clickable(onClick = onClick)
        .pointerInput(item.id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val toRoot = { p: Offset -> coords?.takeIf { it.isAttached }?.localToRoot(p) ?: p }
                var dragging = false
                var pin: androidx.compose.ui.layout.PinnableContainer.PinnedHandle? = null
                var travelled = Offset.Zero
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == press.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        travelled += change.positionChange()
                        if (!dragging && travelled.getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            pin = pinnable?.pin()
                            state.start(currentItems(), toRoot(change.position))
                        }
                        if (dragging) state.move(toRoot(change.position))
                        change.consume()
                    }
                    if (dragging) state.end()?.let { (items, t) -> currentDrop(items, t) } else currentLongClick()
                } finally {
                    pin?.release()
                    if (dragging && state.isDragging) state.cancel()
                }
            }
        }
}

/** Regista um destino de largada enquanto está visível; realça-o quando está por baixo do dedo. */
fun Modifier.dropTarget(target: DropTarget?, state: DragToFolderState, radius: Dp = 12.dp): Modifier = composed {
    if (target == null) return@composed this
    DisposableEffect(target.key) { onDispose { state.unregister(target.key) } }
    val active = state.targetKey == target.key
    this
        .onGloballyPositioned { if (it.isAttached) state.register(target, it.boundsInRoot()) }
        .then(
            if (active) Modifier
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(radius))
            else Modifier
        )
}

/** Pastas da lista como destino (ficheiros não são). */
fun Modifier.folderDropTarget(item: BrowseItem, state: DragToFolderState): Modifier =
    dropTarget((item as? BrowseItem.Folder)?.let { DropTarget(it.id, it.id, it.name) }, state)

/** "Fantasma" dos itens a seguir o dedo; [hostCoords] é o contentor onde é desenhado. */
@Composable
fun DragToFolderGhost(state: DragToFolderState, hostCoords: LayoutCoordinates?) {
    val items = state.dragging
    val first = items.firstOrNull() ?: return
    val origin = hostCoords?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
    val p = state.pointer - origin
    val target = state.target
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .offset { IntOffset((p.x - 40.dp.toPx()).roundToInt(), (p.y - 64.dp.toPx()).roundToInt()) }
            .widthIn(max = 240.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Leading(first, false)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (items.size > 1) "%d itens".tr().format(items.size) else first.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    target?.let { "→ " + it.name } ?: "Larga numa pasta para mover".tr(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (target != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
