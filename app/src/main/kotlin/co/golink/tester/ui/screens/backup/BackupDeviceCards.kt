package co.golink.tester.ui.screens.backup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.backup.BackupDevice
import co.golink.tester.domain.backup.BackupSummary
import co.golink.tester.ui.common.formatBytes
import co.golink.tester.ui.common.formatDayTime
import co.golink.tester.ui.common.isWithinDays
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.BrandGreen
import co.golink.tester.ui.theme.Warning

/**
 * Cartões dos telemóveis (igual à web): nome, plataforma, último envio,
 * totais e Pausar/Retomar só desse aparelho. Este telemóvel aparece primeiro.
 */
@Composable
fun BackupDeviceCards(
    summary: BackupSummary,
    accountEnabled: Boolean,
    busy: Boolean,
    onSetPaused: (BackupDevice, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val devices = summary.devices.sortedByDescending { it.current }
    if (devices.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(devices, key = { it.id }) { device ->
            DeviceCard(
                device = device,
                accountEnabled = accountEnabled,
                busy = busy,
                onSetPaused = { onSetPaused(device, it) },
                modifier = if (devices.size == 1) Modifier.fillParentMaxWidth() else Modifier.width(300.dp),
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: BackupDevice,
    accountEnabled: Boolean,
    busy: Boolean,
    onSetPaused: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paused = !accountEnabled || device.paused
    val ios = device.platform == "ios"
    val dot = when {
        paused -> Warning
        isWithinDays(device.last_backup_at, 7) -> BrandGreen
        else -> MaterialTheme.colorScheme.outline
    }
    val status = when {
        paused -> "Em pausa".tr()
        device.last_backup_at == null -> "Ainda sem envios".tr()
        else -> "Último envio: %s".tr().format(formatDayTime(device.last_backup_at))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (device.current) 2.dp else 1.dp,
            if (device.current) BrandGreen else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (ios) MaterialTheme.colorScheme.surfaceVariant else BrandGreen.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp),
                        ),
                ) {
                    Icon(
                        if (ios) Icons.Outlined.PhoneIphone else Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint = if (ios) MaterialTheme.colorScheme.onSurfaceVariant else BrandGreen,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            device.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        PlatformTag(ios)
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(dot, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            status + if (device.current) " · " + "Este telemóvel".tr() else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    "%d pastas · %d itens · %s".tr().format(device.folders, device.items, formatBytes(device.bytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // Pausa da conta só se retoma no ecrã de Backup automático.
                if (accountEnabled) {
                    OutlinedButton(
                        onClick = { onSetPaused(!device.paused) },
                        enabled = !busy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).widthIn(min = 88.dp),
                    ) {
                        Icon(
                            if (device.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (device.paused) "Retomar".tr() else "Pausar".tr(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformTag(ios: Boolean) {
    Text(
        if (ios) "iOS" else "ANDROID",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = if (ios) MaterialTheme.colorScheme.surface else BrandGreen,
        modifier = Modifier
            .background(
                if (ios) MaterialTheme.colorScheme.onSurface else BrandGreen.copy(alpha = 0.14f),
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
