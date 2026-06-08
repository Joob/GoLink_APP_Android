package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.golink.tester.domain.browse.NavFolder
import co.golink.tester.domain.browse.NavigationSection

@Composable
fun MoveDestinationDialog(
    sections: List<NavigationSection>,
    excludeId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (folderId: String?) -> Unit,
    onCreateFolder: ((parentId: String?) -> Unit)? = null,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateOf(emptySet<String>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 20.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Mover para",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Escolhe a pasta de destino",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onCreateFolder != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable { onCreateFolder(selectedId) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Criar pasta aqui",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))

                Column(modifier = Modifier.heightIn(min = 200.dp, max = 380.dp)) {
                    RootRow(selected = selectedId == null) { selectedId = null }
                    LazyColumn {
                        sections.forEach { section ->
                            items(section.folders, key = { it.id }) { folder ->
                                FolderTree(
                                    folder = folder,
                                    depth = 0,
                                    excludeId = excludeId,
                                    selectedId = selectedId,
                                    expanded = expanded.value,
                                    onSelect = { selectedId = it },
                                    onToggle = { id ->
                                        expanded.value = if (id in expanded.value)
                                            expanded.value - id else expanded.value + id
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selectedId); onDismiss() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("Mover", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowBase(
    indentStart: Int,
    leading: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    excluded: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    else Color.Transparent
    val textColor = when {
        excluded -> MaterialTheme.colorScheme.outline
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (16 + indentStart).dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(enabled = !excluded, onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 12.dp),
    ) {
        leading()
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RootRow(selected: Boolean, onClick: () -> Unit) {
    RowBase(
        indentStart = 0,
        leading = {
            Icon(
                Icons.Outlined.Home,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        label = "Os meus ficheiros (raiz)",
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun FolderTree(
    folder: NavFolder,
    depth: Int,
    excludeId: String?,
    selectedId: String?,
    expanded: Set<String>,
    onSelect: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    val isExcluded = folder.id == excludeId
    val isSelected = selectedId == folder.id
    val isOpen = folder.id in expanded
    val hasChildren = folder.folders.isNotEmpty()

    RowBase(
        indentStart = depth * 18,
        leading = {
            if (hasChildren) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggle(folder.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                if (isOpen && hasChildren) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                contentDescription = null,
                tint = when {
                    isExcluded -> MaterialTheme.colorScheme.outline
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        },
        label = folder.name,
        selected = isSelected,
        excluded = isExcluded,
        onClick = { onSelect(folder.id) },
    )
    if (isOpen && hasChildren) {
        folder.folders.forEach { child ->
            FolderTree(child, depth + 1, excludeId, selectedId, expanded, onSelect, onToggle)
        }
    }
}
