package co.golink.tester.ui.components.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import co.golink.tester.domain.settings.StorageLevel
import co.golink.tester.domain.settings.StorageUsage
import co.golink.tester.domain.user.User
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.AppThemeMode
import co.golink.tester.ui.theme.LocalDarkTheme

/** Largura a partir da qual a gaveta fica fixa à esquerda (tablets / paisagem larga). */
private const val PERMANENT_DRAWER_MIN_WIDTH_DP = 840

private val DrawerWidth = 280.dp

/** Áreas do toggle no topo da gaveta (igual à sidebar da web). */
enum class DrawerArea { Drive, Admin, Support }

data class DrawerItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    /** Número discreto à direita (ex.: nº de favoritos). */
    val count: Int? = null,
    /** Bolha com a cor da marca (ex.: tickets por ler). */
    val badge: Int? = null,
    /** Ponto verde (ex.: backup automático ativo). */
    val statusDot: Boolean = false,
    /** Sub-itens indentados (ex.: as pastas favoritas, como na sidebar da web). */
    val children: List<DrawerItem> = emptyList(),
    val onClick: () -> Unit,
)

data class DrawerSection(val title: String?, val items: List<DrawerItem>)

@Composable
fun isPermanentDrawer(): Boolean = LocalConfiguration.current.screenWidthDp >= PERMANENT_DRAWER_MIN_WIDTH_DP

/**
 * Telemóvel: gaveta modal. Ecrã largo: gaveta permanente à esquerda (como a
 * sidebar da web). O conteúdo da gaveta é o mesmo nos dois casos.
 */
@Composable
fun AdaptiveNavigationDrawer(
    permanent: Boolean,
    drawerState: DrawerState,
    drawerContent: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val container = MaterialTheme.colorScheme.surfaceVariant
    if (permanent) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(DrawerWidth),
                    drawerContainerColor = container,
                    content = drawerContent,
                )
            },
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                Box(Modifier.weight(1f)) { content() }
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(DrawerWidth),
                    drawerContainerColor = container,
                    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    content = drawerContent,
                )
            },
            content = content,
        )
    }
}

/**
 * Conteúdo da gaveta (redesign 2026-10, igual à sidebar da web): cabeçalho com
 * logo e sino, toggle Drive | Admin, ações rápidas, secções, cartão de
 * armazenamento e rodapé com perfil e atalhos.
 */
@Composable
fun ColumnScope.AppDrawerContent(
    user: User?,
    area: DrawerArea,
    onAreaChange: (DrawerArea) -> Unit,
    secondaryArea: DrawerArea?,
    sections: List<DrawerSection>,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
    storage: StorageUsage?,
    onUpgrade: () -> Unit,
    appVersion: String,
    onProfile: () -> Unit,
    onAvatarClick: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onNewUpload: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    lockedContent: (@Composable () -> Unit)? = null,
) {
    DrawerHeader(
        title = when (area) {
            DrawerArea.Drive -> "Drive"
            DrawerArea.Admin -> "Admin"
            DrawerArea.Support -> "Suporte".tr()
        },
        unread = unreadNotifications,
        onOpenNotifications = onOpenNotifications,
    )

    if (secondaryArea != null) {
        AreaToggle(area = area, secondary = secondaryArea, onChange = onAreaChange)
    }

    if (area == DrawerArea.Drive && lockedContent == null) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            onNewUpload?.let { NewUploadButton(it) }
            onSearch?.let { SearchButton(it) }
        }
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(bottom = 8.dp),
    ) {
        if (lockedContent != null && area == DrawerArea.Drive) {
            lockedContent()
        } else {
            sections.forEachIndexed { si, section ->
                // Admin/Suporte: secções separadas por uma linha (como na web).
                if (area != DrawerArea.Drive && si > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp),
                    )
                }
                if (section.title != null) SectionLabel(section.title, compactTop = area != DrawerArea.Drive && si > 0)
                else Spacer(Modifier.height(10.dp))
                section.items.forEach { item ->
                    DrawerRow(item = item, dot = false)
                item.children.forEach { child ->
                    Box(Modifier.padding(start = 22.dp)) { DrawerRow(item = child, dot = false) }
                }
                }
            }
        }
    }

    if (area == DrawerArea.Drive && storage != null && lockedContent == null) {
        StorageCard(storage = storage, onUpgrade = onUpgrade)
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    DrawerFooter(
        user = user,
        appVersion = appVersion,
        onProfile = onProfile,
        onAvatarClick = onAvatarClick,
        onHelp = onHelp,
        onSettings = onSettings,
        onLogout = onLogout,
    )
}

@Composable
private fun DrawerHeader(title: String, unread: Int, onOpenNotifications: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
    ) {
        val dark = LocalDarkTheme.current
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (dark) Color.White else Color(0xFF111827)),
        ) {
            Text(
                "go",
                color = if (dark) Color(0xFF111827) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenNotifications) {
            BadgedBox(badge = {
                if (unread > 0) Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text(if (unread > 99) "99+" else unread.toString())
                }
            }) {
                Icon(
                    if (unread > 0) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                    contentDescription = "Notificações".tr(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AreaToggle(area: DrawerArea, secondary: DrawerArea, onChange: (DrawerArea) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(4.dp),
    ) {
        listOf(DrawerArea.Drive, secondary).forEach { option ->
            val active = option == area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onChange(option) },
            ) {
                Text(
                    when (option) {
                        DrawerArea.Drive -> "Drive"
                        DrawerArea.Admin -> "Admin"
                        DrawerArea.Support -> "Suporte".tr()
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NewUploadButton(onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Novo / Carregar".tr(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text("Pesquisar ficheiros".tr(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun SectionLabel(text: String, compactTop: Boolean = false) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = if (compactTop) 12.dp else 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun DrawerRow(item: DrawerItem, dot: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val fg = if (item.selected) primary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (item.selected) primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = item.onClick)
            .height(40.dp)
            .padding(horizontal = 10.dp),
    ) {
        if (dot) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (item.selected) primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                )
            }
        } else {
            Icon(
                item.icon,
                contentDescription = null,
                tint = if (item.selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            item.label,
            fontSize = 14.sp,
            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when {
            item.badge != null && item.badge > 0 -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(primary)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(if (item.badge > 99) "99+" else item.badge.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            item.count != null -> Text(item.count.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.statusDot -> Box(Modifier.size(7.dp).clip(CircleShape).background(primary))
        }
    }
}

@Composable
private fun storageColor(level: StorageLevel): Color {
    val dark = LocalDarkTheme.current
    return when (level) {
        StorageLevel.OK -> MaterialTheme.colorScheme.primary
        StorageLevel.WARNING -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
        StorageLevel.DANGER -> if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
    }
}

@Composable
private fun StorageCard(storage: StorageUsage, onUpgrade: () -> Unit) {
    val accent = storageColor(storage.level)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Armazenamento".tr(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text("${storage.percentage.toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (storage.percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "%1\$s de %2\$s utilizados".tr().format(storage.used, storage.capacity),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (storage.isLow) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Obter mais espaço".tr() + " →",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.clickable(onClick = onUpgrade),
            )
        }
    }
}

@Composable
private fun DrawerFooter(
    user: User?,
    appVersion: String,
    onProfile: () -> Unit,
    onAvatarClick: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 12.dp),
    ) {
        // Avatar: tocar troca a imagem (como antes); nome → perfil.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .clickable(onClick = onAvatarClick),
        ) {
            val avatar = user?.avatar
            if (!avatar.isNullOrBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = "Alterar imagem".tr(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    user?.name?.firstOrNull()?.uppercase() ?: "U",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onProfile),
        ) {
            Text(
                user?.name ?: "—",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Perfil".tr() + " · v" + appVersion,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FooterIcon(Icons.Outlined.HelpOutline, "Suporte".tr(), onHelp)
        FooterIcon(
            if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            if (dark) "Tema claro".tr() else "Tema escuro".tr(),
        ) {
            AppThemeMode.set(context, if (dark) AppThemeMode.Mode.LIGHT else AppThemeMode.Mode.DARK)
        }

        // ⚙: definições e idioma.
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            FooterIcon(Icons.Outlined.Settings, "Definições".tr()) { menuOpen = true }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                DropdownMenuItem(
                    text = { Text("Definições".tr(), fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { menuOpen = false; onSettings() },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(
                    "IDIOMA".tr(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
                )
                val current = I18n.current()
                I18n.languages.forEach { lang ->
                    val selected = lang.code == current.code
                    DropdownMenuItem(
                        text = { Text(lang.label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                        leadingIcon = { Text(lang.flag, fontSize = 16.sp) },
                        trailingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                        } else null,
                        onClick = { menuOpen = false; I18n.set(context, lang.code) },
                    )
                }
            }
        }
        FooterIcon(Icons.AutoMirrored.Outlined.Logout, "Sair da Conta".tr(), onLogout)
    }
}

@Composable
private fun FooterIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
