package co.golink.tester.ui.components.drawer

import android.content.Context
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SupportAgent
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

private val RailWidth = 76.dp
private val PanelWidth = 248.dp

/** Barra + linha divisória + painel. */
private val DrawerFullWidth = RailWidth + 1.dp + PanelWidth

/** Áreas da barra de ícones (igual à sidebar da web). */
enum class DrawerArea { Drive, Calendar, Admin, Support }

fun DrawerArea.label(): String = when (this) {
    DrawerArea.Drive -> "Drive"
    DrawerArea.Calendar -> "Calendário".tr()
    DrawerArea.Admin -> "Admin"
    DrawerArea.Support -> "Suporte".tr()
}

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

/**
 * Painel da área encolhido (só fica a barra de ícones), como o « da web. Só
 * se aplica à gaveta fixa; mesmo padrão do [AppThemeMode] (snapshot state + prefs).
 */
object DrawerPanel {
    var collapsed by mutableStateOf(false)
        private set

    fun init(context: Context) {
        collapsed = prefs(context).getBoolean(KEY, false)
    }

    fun toggle(context: Context) {
        collapsed = !collapsed
        prefs(context).edit().putBoolean(KEY, collapsed).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("app_drawer", Context.MODE_PRIVATE)

    private const val KEY = "panel_collapsed"
}

@Composable
fun isPermanentDrawer(): Boolean = LocalConfiguration.current.screenWidthDp >= PERMANENT_DRAWER_MIN_WIDTH_DP

/**
 * Telemóvel: gaveta modal (barra + painel, sempre aberta). Ecrã largo: gaveta
 * permanente à esquerda, com o painel encolhível. O conteúdo é o mesmo.
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
        // Sem animar: animar a largura fazia relayout da grelha/calendário ao lado a cada frame.
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(if (DrawerPanel.collapsed) RailWidth else DrawerFullWidth),
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
                // Telemóveis estreitos: deixa sempre uma faixa de scrim para fechar.
                val maxWidth = LocalConfiguration.current.screenWidthDp.dp - 56.dp
                ModalDrawerSheet(
                    modifier = Modifier.width(minOf(DrawerFullWidth, maxWidth)),
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
 * Conteúdo da gaveta (redesign 2026-10-29, igual à sidebar da web): barra de
 * ícones (logo, sino, Drive, Calendário, Admin/Suporte, ajuda, tema, avatar) e
 * painel da área com título, ações rápidas, secções e armazenamento. A área
 * Calendário desenha o seu próprio menu em [areaContent].
 */
@Composable
fun ColumnScope.AppDrawerContent(
    user: User?,
    area: DrawerArea,
    onAreaChange: (DrawerArea) -> Unit,
    areas: List<DrawerArea>,
    sections: List<DrawerSection>,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
    /** Logo da barra: início da área (como o homeRoute da web). */
    onHome: () -> Unit,
    storage: StorageUsage?,
    onUpgrade: () -> Unit,
    canUpgrade: Boolean = false,
    appVersion: String,
    onProfile: () -> Unit,
    /** Trocar a imagem de perfil; null = sem a opção no menu do avatar. */
    onAvatarClick: (() -> Unit)?,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onNewUpload: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    lockedContent: (@Composable () -> Unit)? = null,
    onNewEvent: (() -> Unit)? = null,
    areaContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    val canCollapse = isPermanentDrawer()
    val collapsed = canCollapse && DrawerPanel.collapsed

    Row(Modifier.fillMaxSize()) {
        DrawerRail(
            user = user,
            area = area,
            areas = areas,
            onAreaChange = onAreaChange,
            unread = unreadNotifications,
            onOpenNotifications = onOpenNotifications,
            onHome = onHome,
            onExpand = if (collapsed) ({ DrawerPanel.toggle(context) }) else null,
            appVersion = appVersion,
            onProfile = onProfile,
            onAvatarClick = onAvatarClick,
            onHelp = onHelp,
            onSettings = onSettings,
            onLogout = onLogout,
        )
        if (!collapsed) {
            Row(Modifier.fillMaxHeight()) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                PanelHeader(
                    title = area.label(),
                    onCollapse = if (canCollapse) ({ DrawerPanel.toggle(context) }) else null,
                )

                if (area == DrawerArea.Calendar && onNewEvent != null) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) { NewUploadButton(onNewEvent, label = "Novo evento".tr()) }
                }

                if (area == DrawerArea.Drive && lockedContent == null) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    if (lockedContent != null && (area == DrawerArea.Drive || area == DrawerArea.Calendar)) {
                        lockedContent()
                    } else if (areaContent != null) {
                        areaContent()
                    } else {
                        sections.forEachIndexed { si, section ->
                            // Admin/Suporte: secções separadas por uma linha (como na web).
                            if (area != DrawerArea.Drive && area != DrawerArea.Calendar && si > 0) {
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
                    StorageCard(storage = storage, canUpgrade = canUpgrade, onUpgrade = onUpgrade)
                }
            }
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, onCollapse: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 16.dp, end = 8.dp),
    ) {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onCollapse != null) RailIcon(Icons.Filled.KeyboardDoubleArrowLeft, "Recolher menu".tr(), onCollapse)
    }
}

@Composable
private fun NewUploadButton(onClick: () -> Unit, label: String = "Novo / Carregar".tr()) {
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
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
private fun StorageCard(storage: StorageUsage, canUpgrade: Boolean, onUpgrade: () -> Unit) {
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
        // Como na web: plano fixo mostra sempre o upgrade; com pouco espaço o apelo é mais forte.
        if (storage.isLow || canUpgrade) {
            Spacer(Modifier.height(6.dp))
            Text(
                (if (storage.isLow) "Obter mais espaço".tr() else "Melhorar plano".tr()) + " →",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.clickable(onClick = onUpgrade),
            )
        }
    }
}
@Composable
private fun DrawerRail(
    user: User?,
    area: DrawerArea,
    areas: List<DrawerArea>,
    onAreaChange: (DrawerArea) -> Unit,
    unread: Int,
    onOpenNotifications: () -> Unit,
    onHome: () -> Unit,
    onExpand: (() -> Unit)?,
    appVersion: String,
    onProfile: () -> Unit,
    onAvatarClick: (() -> Unit)?,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val secondary = areas.firstOrNull { it == DrawerArea.Admin || it == DrawerArea.Support }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(RailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.025f))
            .padding(vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onHome),
        ) {
            Text("go", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        // Só o meio faz scroll: em ecrãs baixos (landscape, split-screen) o
        // rodapé com o avatar continua acessível.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
        Spacer(Modifier.height(8.dp))
        // Inbox (como na web): notificações de tudo, calendário incluído.
        RailItem(
            if (unread > 0) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
            "Inbox".tr(),
            selected = false,
            badge = unread,
            onClick = onOpenNotifications,
        )
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .width(32.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        if (DrawerArea.Drive in areas) {
            RailItem(Icons.Outlined.Storage, DrawerArea.Drive.label(), area == DrawerArea.Drive) { onAreaChange(DrawerArea.Drive) }
        }
        if (DrawerArea.Calendar in areas) {
            RailItem(Icons.Outlined.CalendarToday, DrawerArea.Calendar.label(), area == DrawerArea.Calendar) {
                onAreaChange(DrawerArea.Calendar)
            }
        }
        if (onExpand != null) {
            Spacer(Modifier.height(4.dp))
            RailIcon(Icons.Filled.KeyboardDoubleArrowRight, "Expandir menu".tr(), onExpand)
        }
        Spacer(Modifier.height(8.dp))
        }

        if (secondary != null) {
            RailItem(
                if (secondary == DrawerArea.Admin) Icons.Outlined.AdminPanelSettings else Icons.Outlined.SupportAgent,
                secondary.label(),
                area == secondary,
            ) { onAreaChange(secondary) }
        }
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .width(32.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        RailIcon(Icons.Outlined.HelpOutline, "Ajuda".tr(), onHelp)
        RailIcon(
            if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            if (dark) "Tema claro".tr() else "Tema escuro".tr(),
        ) {
            AppThemeMode.set(context, if (dark) AppThemeMode.Mode.LIGHT else AppThemeMode.Mode.DARK)
        }
        Spacer(Modifier.height(6.dp))
        UserMenu(
            user = user,
            appVersion = appVersion,
            onProfile = onProfile,
            onAvatarClick = onAvatarClick,
            onSettings = onSettings,
            onLogout = onLogout,
        )
    }
}

/** Item de área da barra: ícone numa "pill" e legenda por baixo (como na web). */
@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val fg = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(48.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) primary.copy(alpha = 0.15f) else Color.Transparent),
            ) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            }
            if (badge > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-4).dp)
                        .widthIn(min = 18.dp)
                        .height(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(primary)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Avatar no fundo da barra: abre o menu com perfil, definições, idioma e sair. */
@Composable
private fun UserMenu(
    user: User?,
    appVersion: String,
    onProfile: () -> Unit,
    onAvatarClick: (() -> Unit)?,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .clickable { menuOpen = true },
        ) {
            val avatar = user?.avatar
            if (!avatar.isNullOrBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = "Perfil".tr(),
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
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 240.dp)) {
                Text(
                    user?.name ?: "—",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(user?.email, "v$appVersion").joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            DropdownMenuItem(
                text = { Text("Perfil".tr(), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = { menuOpen = false; onProfile() },
            )
            if (onAvatarClick != null) DropdownMenuItem(
                text = { Text("Alterar imagem".tr(), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = { menuOpen = false; onAvatarClick() },
            )
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            val danger = if (LocalDarkTheme.current) Color(0xFFF87171) else Color(0xFFDC2626)
            DropdownMenuItem(
                text = { Text("Sair da Conta".tr(), fontSize = 14.sp, color = danger) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = danger, modifier = Modifier.size(18.dp)) },
                onClick = { menuOpen = false; onLogout() },
            )
        }
    }
}

@Composable
private fun RailIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
