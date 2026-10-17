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
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                // Quantos itens estão selecionados.
                if (selectedCount > 0) {
                    Text(
                        summary ?: if (selectedCount == 1) "1 ${"selecionado".tr()}"
                        else "$selectedCount ${"selecionados".tr()}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (trashMode) {
                        // Lixo: Restaurar (para o sítio de origem) ou mover para outra pasta,
                        // e eliminar de vez.
                        onRestore?.let { SelectionAction(Icons.Outlined.Restore, "Restaurar".tr(), it) }
                        SelectionAction(Icons.Outlined.DriveFileMove, "Mover".tr(), onMove)
                        SelectionAction(Icons.Outlined.DeleteForever, "Eliminar".tr(), onDelete)
                    } else if (readOnly) {
                        SelectionAction(Icons.Outlined.Download, "Descarregar".tr(), onDownload)
                    } else {
                        SelectionAction(Icons.Outlined.Download, "Descarregar".tr(), onDownload)
                        if (onShare != null && selectedCount == 1) SelectionAction(Icons.Outlined.Link, "Partilhar".tr(), onShare)
                        SelectionAction(Icons.Outlined.DriveFileMove, "Mover".tr(), onMove)
                        SelectionAction(Icons.Outlined.DeleteOutline, "Eliminar".tr(), onDelete)
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
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
