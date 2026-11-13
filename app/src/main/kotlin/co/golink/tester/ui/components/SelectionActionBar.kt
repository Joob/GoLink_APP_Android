package co.golink.tester.ui.components

import co.golink.tester.ui.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Barra de acções da selecção múltipla. No lixo (`trashMode`) só faz sentido
 * Mover (restaurar) — a eliminação permanente é feita pelo Flush; nos restantes
 * ecrãs mostra Descarregar / Mover / Eliminar.
 */
// Barra escura flutuante (mockup 2026-11), igual à da web.
private val DockBg = androidx.compose.ui.graphics.Color(0xFF111827)
private val DockFg = androidx.compose.ui.graphics.Color.White
private val DockDanger = androidx.compose.ui.graphics.Color(0xFFF87171)

@Composable
fun SelectionActionBar(
    onDownload: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    trashMode: Boolean = false,
    selectedCount: Int = 0,
    // Só leitura: apenas Descarregar.
    readOnly: Boolean = false,
    // "1 vídeo selecionado · 19.30 MB" (como na web); null = "N selecionados".
    summary: String? = null,
    // Partilhar (só com 1 item) e Restaurar (Lixo).
    onShare: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    // Favorito: só quando a seleção tem pastas próprias.
    onFavourite: (() -> Unit)? = null,
    // O resumo pode viver na barra de topo da seleção (BrowseScreen).
    showSummary: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Surface(
            color = DockBg,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = if (showSummary && selectedCount > 0) 8.dp else 0.dp)) {
                if (showSummary && selectedCount > 0) {
                    Text(
                        summary ?: if (selectedCount == 1) "1 ${"selecionado".tr()}"
                        else "$selectedCount ${"selecionados".tr()}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = DockFg.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (trashMode) {
                        // Lixo: Restaurar (para o sítio de origem) ou mover para outra pasta,
                        // e eliminar de vez.
                        onRestore?.let { SelectionAction(Icons.Outlined.Restore, "Restaurar".tr(), it) }
                        SelectionAction(Icons.Outlined.DriveFileMove, "Mover".tr(), onMove)
                        SelectionAction(Icons.Outlined.DeleteForever, "Eliminar".tr(), onDelete, danger = true)
                    } else if (readOnly) {
                        SelectionAction(Icons.Outlined.Download, "Descarregar".tr(), onDownload)
                    } else {
                        if (onShare != null) SelectionAction(Icons.Outlined.Link, "Partilhar".tr(), onShare, enabled = selectedCount == 1)
                        SelectionAction(Icons.Outlined.Download, "Descarregar".tr(), onDownload)
                        SelectionAction(Icons.Outlined.DriveFileMove, "Mover".tr(), onMove)
                        if (onFavourite != null) SelectionAction(Icons.Outlined.StarOutline, "Favorito".tr(), onFavourite)
                        SelectionAction(Icons.Outlined.DeleteOutline, "Eliminar".tr(), onDelete, danger = true)
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = (if (danger) DockDanger else DockFg).copy(alpha = if (enabled) 1f else 0.35f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
        )
    }
}
