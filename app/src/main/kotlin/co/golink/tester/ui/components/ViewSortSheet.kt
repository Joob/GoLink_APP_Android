package co.golink.tester.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.browse.SortMode
import co.golink.tester.ui.screens.browse.ViewMode
import co.golink.tester.ui.screens.settings.SettingsPrimaryButton
import co.golink.tester.ui.screens.settings.SettingsSwitch

private enum class SortField { NAME, DATE, SIZE, TYPE }

private fun SortMode.field(): SortField = when {
    isAlpha -> SortField.NAME
    isSize -> SortField.SIZE
    isType -> SortField.TYPE
    else -> SortField.DATE
}

private fun modeFor(field: SortField, ascending: Boolean): SortMode = when (field) {
    SortField.NAME -> if (ascending) SortMode.ALPHA_ASC else SortMode.ALPHA_DESC
    SortField.DATE -> if (ascending) SortMode.DATE_ASC else SortMode.DATE_DESC
    SortField.SIZE -> if (ascending) SortMode.SIZE_ASC else SortMode.SIZE_DESC
    SortField.TYPE -> if (ascending) SortMode.TYPE_ASC else SortMode.TYPE_DESC
}

// Direção por defeito ao escolher cada campo (igual à web).
private fun defaultAscending(field: SortField) = field == SortField.NAME || field == SortField.TYPE

private fun directionLabel(field: SortField, ascending: Boolean): String = when (field) {
    SortField.NAME, SortField.TYPE -> if (ascending) "A → Z" else "Z → A"
    SortField.DATE -> if (ascending) "Mais antigos".tr() else "Mais recentes".tr()
    SortField.SIZE -> if (ascending) "Menores primeiro".tr() else "Maiores primeiro".tr()
}

/**
 * Folha "Ordenar e vista" (mockup 2026-11, igual à web): Lista/Grelha, campo e
 * direção, Pastas primeiro e Agrupar por mês (só com data). Concluído aplica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSortSheet(
    viewMode: ViewMode,
    sortMode: SortMode,
    foldersFirst: Boolean,
    groupByMonth: Boolean,
    onDismiss: () -> Unit,
    onApply: (ViewMode, SortMode, Boolean, Boolean) -> Unit,
) {
    var view by remember { mutableStateOf(viewMode) }
    var field by remember { mutableStateOf(sortMode.field()) }
    var ascending by remember { mutableStateOf(sortMode.isAscending) }
    var ff by remember { mutableStateOf(foldersFirst) }
    var gbm by remember { mutableStateOf(groupByMonth) }
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            SheetLabel("Vista".tr())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ViewCard(Icons.Outlined.ViewList, "Lista".tr(), view == ViewMode.LIST, Modifier.weight(1f)) { view = ViewMode.LIST }
                ViewCard(Icons.Outlined.GridView, "Grelha".tr(), view == ViewMode.GRID, Modifier.weight(1f)) { view = ViewMode.GRID }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SheetLabel("Ordenar por".tr(), Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, outline, RoundedCornerShape(50))
                        .clickable { ascending = !ascending }
                        .padding(horizontal = 12.dp),
                ) {
                    Icon(if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(directionLabel(field, ascending), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            listOf(
                Triple(SortField.NAME, "Nome".tr(), "A → Z"),
                Triple(SortField.DATE, "Data".tr(), "Mais recentes".tr()),
                Triple(SortField.SIZE, "Tamanho".tr(), "Maiores primeiro".tr()),
                Triple(SortField.TYPE, "Tipo".tr(), "Pastas, imagens…".tr()),
            ).forEach { (f, label, hint) ->
                val active = f == field
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) accent.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable {
                            if (f != field) {
                                field = f
                                ascending = defaultAscending(f)
                            }
                        }
                        .padding(horizontal = 10.dp),
                ) {
                    RadioDot(active)
                    Spacer(Modifier.width(12.dp))
                    Text(label, fontSize = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ToggleLine("Pastas primeiro".tr(), null, ff, true) { ff = it }
            val byDate = field == SortField.DATE
            ToggleLine("Agrupar por mês".tr(), if (byDate) null else "Só ao ordenar por data".tr(), gbm && byDate, byDate) { gbm = it }

            Spacer(Modifier.height(14.dp))
            SettingsPrimaryButton(
                text = "Concluído".tr(),
                onClick = { onApply(view, modeFor(field, ascending), ff, gbm) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ViewCard(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, if (active) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        val tint = if (active) accent else MaterialTheme.colorScheme.onSurface
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun RadioDot(active: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (active) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
    ) {
        if (active) Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
    }
}

@Composable
private fun ToggleLine(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
