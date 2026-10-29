package co.golink.tester.ui.screens.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.calendar.CalInfo
import co.golink.tester.domain.calendar.CalInstance
import co.golink.tester.domain.calendar.CalendarDates
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.drawer.AdaptiveNavigationDrawer
import co.golink.tester.ui.components.drawer.AppDrawerContent
import co.golink.tester.ui.components.drawer.DrawerArea
import co.golink.tester.ui.components.drawer.isPermanentDrawer
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.browse.BrowseShellViewModel
import co.golink.tester.ui.screens.encryption.E2EGateViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    openEventId: String?,
    openDate: String?,
    onOpenDrive: () -> Unit,
    onOpenArea: (DrawerArea) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenBilling: () -> Unit,
    onOpenHelp: (supportStaff: Boolean) -> Unit,
    onOpenFile: (String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
    shell: BrowseShellViewModel = hiltViewModel(),
    gateVm: E2EGateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val holidaysVisible by viewModel.holidaysVisible.collectAsStateWithLifecycle()
    val holidayCountry by viewModel.holidayCountry.collectAsStateWithLifecycle()
    val holidayChoice by viewModel.holidayCountryChoice.collectAsStateWithLifecycle()
    var holidaysSheet by remember { mutableStateOf(false) }
    val user by shell.user.collectAsStateWithLifecycle()
    val unread by shell.unreadNotifications.collectAsStateWithLifecycle()
    val storage by shell.storage.collectAsStateWithLifecycle()
    val e2eUnlocked by gateVm.unlocked.collectAsStateWithLifecycle()
    val e2eConfigured by gateVm.configuredFlow.collectAsStateWithLifecycle()
    val e2eLocked = e2eConfigured && !e2eUnlocked

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    // Do sino (?event=&date=): abre o evento uma vez.
    var deepLinkDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openEventId, e2eUnlocked) {
        if (deepLinkDone || openEventId.isNullOrBlank() || !e2eUnlocked) return@LaunchedEffect
        deepLinkDone = true
        viewModel.openEventById(openEventId, openDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    }

    // Anexo: viewer (pela pasta-mãe) ou link de partilha no browser.
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    LaunchedEffect(state.openFileId, state.openUrl) {
        state.openFileId?.let(onOpenFile)
        state.openUrl?.let { runCatching { uriHandler.openUri(it) } }
        if (state.openFileId != null || state.openUrl != null) viewModel.consumeOpenFile()
    }

    LaunchedEffect(state.toast) {
        val msg = state.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.consumeToast()
    }

    BackHandler(enabled = drawerState.isOpen || searchOpen) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else { searchOpen = false; viewModel.closeSearch() }
    }

    val secondaryArea = when {
        user?.role == "admin" -> DrawerArea.Admin
        user?.supportStaff != null -> DrawerArea.Support
        else -> null
    }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val go: (() -> Unit) -> () -> Unit = { action -> { closeDrawer(); action() } }

    AdaptiveNavigationDrawer(
        permanent = isPermanentDrawer(),
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                user = user,
                area = DrawerArea.Calendar,
                onAreaChange = { area ->
                    when (area) {
                        DrawerArea.Calendar -> Unit
                        DrawerArea.Drive -> go(onOpenDrive)()
                        else -> go { onOpenArea(area) }()
                    }
                },
                areas = listOfNotNull(DrawerArea.Drive, DrawerArea.Calendar, secondaryArea),
                sections = emptyList(),
                unreadNotifications = unread,
                onOpenNotifications = go(onOpenNotifications),
                storage = storage,
                onUpgrade = go(onOpenBilling),
                appVersion = co.golink.tester.BuildConfig.VERSION_NAME,
                onProfile = go(onOpenProfile),
                onAvatarClick = go(onOpenProfile),
                onHelp = go { onOpenHelp(user?.supportStaff != null) },
                onSettings = go(onOpenSettings),
                onLogout = go { confirmLogout = true },
                lockedContent = if (e2eLocked) {
                    { LockedDrawerNote(onUnlock = { closeDrawer(); gateVm.reopen() }) }
                } else null,
                onNewEvent = if (!e2eLocked && calendars.any { it.canEdit }) go { viewModel.newEvent() } else null,
                areaContent = {
                    CalendarDrawerMenu(
                        cursor = state.cursor,
                        calendars = calendars,
                        onSelectDay = { viewModel.select(it); closeDrawer() },
                        onToggle = viewModel::toggleCalendar,
                        onSettings = { id -> closeDrawer(); viewModel.openSettings(id) },
                        holidaysVisible = holidaysVisible,
                        holidayCountry = holidayCountry ?: holidayChoice,
                        onToggleHolidays = viewModel::toggleHolidays,
                        onHolidaySettings = { closeDrawer(); holidaysSheet = true },
                    )
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (!e2eLocked && !searchOpen && calendars.any { it.canEdit }) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.newEvent() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Evento".tr(), fontWeight = FontWeight.SemiBold) },
                    )
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                if (searchOpen) {
                    SearchBar(
                        query = state.searchQuery,
                        onQuery = viewModel::search,
                        onClose = { searchOpen = false; viewModel.closeSearch() },
                    )
                } else {
                    TopBar(
                        cursor = state.cursor,
                        view = state.view,
                        showMenu = !isPermanentDrawer(),
                        onMenu = { scope.launch { drawerState.open() } },
                        onPrev = { viewModel.step(-1) },
                        onNext = { viewModel.step(1) },
                        onToday = viewModel::today,
                        onSearch = { searchOpen = true },
                    )
                    Segmented(
                        options = listOf(CalView.Month to "Mês".tr(), CalView.Agenda to "Agenda".tr(), CalView.Day to "Dia".tr()),
                        selected = state.view,
                        onSelect = viewModel::setView,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (state.loading || state.searching) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                else Spacer(Modifier.height(2.dp))

                val bottom = PaddingValues(bottom = 96.dp)
                when {
                    e2eLocked -> LockedState(onUnlock = gateVm::reopen)
                    searchOpen -> SearchResults(state, calendars, onOpen = { ev ->
                        searchOpen = false
                        viewModel.closeSearch()
                        viewModel.openEventById(ev.id)
                    })
                    state.error != null && !state.loaded -> ErrorState(onRetry = { viewModel.reload(force = true) })
                    else -> when (state.view) {
                        CalView.Month -> MonthView(state.cursor, instances, calendars, viewModel::select, viewModel::openInstance, bottom)
                        CalView.Agenda -> AgendaView(state.cursor, state.agendaDays, instances, calendars, state.loading, viewModel::openInstance, viewModel::loadMoreAgenda, bottom)
                        CalView.Day -> DayView(state.cursor, instances, calendars, viewModel::select, viewModel::openInstance, onCreateAt = { at -> if (calendars.any { it.canEdit }) viewModel.newEvent(at) }, contentPadding = bottom)
                    }
                }
            }
        }
    }

    state.open?.let { inst ->
        EventDetailSheet(
            instance = inst,
            calendars = calendars,
            onDismiss = { viewModel.openInstance(null) },
            onEdit = { viewModel.editEvent(inst.event) },
            onDelete = { viewModel.deleteEvent(inst.event) },
            onRsvp = { status -> viewModel.rsvp(inst, status) },
            onOpenAttachment = { a -> viewModel.openAttachment(a, user?.id) },
        )
    }

    state.editor?.let { req ->
        EventEditor(
            event = req.event,
            start = req.start,
            day = state.cursor,
            calendars = calendars,
            ownEmail = user?.email,
            saving = state.saving,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::save,
            onAttach = { viewModel.openPicker(true) },
            pendingAttachments = state.pendingAttachments,
            onPendingConsumed = viewModel::consumePendingAttachments,
        )
        if (state.picker) {
            DrivePickerDialog(
                exclude = emptySet(),
                load = viewModel::listDriveFolder,
                onDismiss = { viewModel.openPicker(false) },
                onPick = viewModel::attach,
            )
        }
    }

    state.settings?.let { id ->
        val cal = calendars.firstOrNull { it.id == id }
        if (id == "new" || cal != null) {
            CalendarSettingsSheet(
                calendar = cal,
                myUserId = user?.id,
                busy = state.calBusy,
                onDismiss = { viewModel.openSettings(null) },
                onCreate = viewModel::createCalendar,
                onUpdate = { name, color -> viewModel.updateCalendar(id, name, color) },
                onShare = { email, perm -> viewModel.shareCalendar(id, email, perm) },
                onRemoveMember = { uid -> viewModel.removeMember(id, uid) },
                onDelete = { viewModel.deleteCalendar(id) },
            )
        }
    }

    if (holidaysSheet) {
        HolidaysSheet(
            selected = holidayChoice,
            busy = state.calBusy,
            onDismiss = { holidaysSheet = false },
            onPick = { code -> viewModel.setHolidayCountry(code); holidaysSheet = false },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Sair da Conta?".tr(),
            message = "Tens a certeza que queres sair da tua conta?".tr(),
            confirmText = "Sair".tr(),
            destructive = true,
            onDismiss = { confirmLogout = false },
            onConfirm = { confirmLogout = false; shell.logout() },
        )
    }
}

@Composable
private fun TopBar(
    cursor: LocalDate,
    view: CalView,
    showMenu: Boolean,
    onMenu: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp),
    ) {
        if (showMenu) IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = null) }
        else Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
            if (view == CalView.Day) {
                Text(
                    "${cursor.dayOfMonth} ${shortMonth(cursor)}", fontSize = 21.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                )
                Text(
                    " " + cursor.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, co.golink.tester.ui.common.appLocale()).substringBefore('-'),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            } else {
                Text(fmtMonthName(cursor), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(
                    " ${cursor.year}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Pesquisar eventos".tr(), modifier = Modifier.size(20.dp)) }
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = null) }
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = null) }
        // Botão "hoje" com o número do dia, como no mockup.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .clickable(onClick = onToday),
        ) {
            Text(LocalDate.now().dayOfMonth.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun shortMonth(d: LocalDate): String =
    d.format(java.time.format.DateTimeFormatter.ofPattern("MMM", co.golink.tester.ui.common.appLocale())).replace(".", "") + "."

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Pesquisar eventos".tr()) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchResults(state: CalendarUiState, calendars: List<CalInfo>, onOpen: (co.golink.tester.domain.calendar.CalEvent) -> Unit) {
    val results = state.searchResults
    if (results != null && results.isEmpty() && !state.searching) {
        Text("Sem resultados".tr(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(results.orEmpty(), key = { it.id }) { ev ->
            val c = eventColor(ev, calendars)
            val day = ev.start.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(ev) }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(eventTitle(ev), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${fmtLongDay(day)} · ${fmtSpan(ev, ev.start, ev.end)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockedState(onUnlock: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Calendário bloqueado".tr(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Desbloqueia a encriptação ponta a ponta para veres os teus eventos.".tr(),
            fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onUnlock) { Text("Desbloquear".tr(), fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun LockedDrawerNote(onUnlock: () -> Unit) {
    Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
        Text("Desbloqueia a encriptação ponta a ponta para veres os teus eventos.".tr(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onUnlock) { Text("Desbloquear".tr()) }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        Text("Não foi possível concluir. Tenta de novo.".tr(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text("Tentar de novo".tr()) }
    }
}

// ---- Menu da gaveta (SidebarCalendarMenu.vue) ----------------------------------

@Composable
private fun ColumnScope.CalendarDrawerMenu(
    cursor: LocalDate,
    calendars: List<CalInfo>,
    onSelectDay: (LocalDate) -> Unit,
    onToggle: (CalInfo) -> Unit,
    onSettings: (String) -> Unit,
    holidaysVisible: Boolean,
    holidayCountry: String?,
    onToggleHolidays: () -> Unit,
    onHolidaySettings: () -> Unit,
) {
    MiniMonth(cursor = cursor, onSelect = onSelectDay)

    // Como na web: meus = do próprio, com ≤1 membro e sem aniversários;
    // partilhados = de outro dono ou com mais membros; outros = aniversários.
    val mine = calendars.filter { it.isOwner && it.members.size <= 1 && !it.isBirthdays }
    val shared = calendars.filter { !it.isOwner || it.members.size > 1 }.filterNot { it.isOwner && it.isBirthdays && it.members.size <= 1 }
    val others = calendars.filter { it.isOwner && it.isBirthdays && it.members.size <= 1 }

    DrawerLabel("Os meus calendários".tr())
    mine.forEach { CalendarToggleRow(it, onToggle, onSettings) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSettings("new") }.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text("Novo calendário".tr(), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
    if (shared.isNotEmpty()) {
        DrawerLabel("Partilhados".tr())
        shared.forEach { CalendarToggleRow(it, onToggle, onSettings, members = it.members.size) }
    }
    DrawerLabel("Outros".tr())
    // Feriados (calculados no aparelho, só leitura), como na web.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggleHolidays).padding(end = 8.dp),
    ) {
        Checkbox(
            checked = holidaysVisible,
            onCheckedChange = { onToggleHolidays() },
            colors = CheckboxDefaults.colors(checkedColor = HolidayColor, uncheckedColor = HolidayColor),
        )
        Text(
            holidayCountry?.let { "Feriados em %s".tr().format(countryName(it)) } ?: "Feriados".tr(),
            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onHolidaySettings, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Settings, contentDescription = "País dos feriados".tr(), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    others.forEach { CalendarToggleRow(it, onToggle, onSettings) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp)) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text("Eventos encriptados ponta a ponta".tr(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text.uppercase(co.golink.tester.ui.common.appLocale()), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun CalendarToggleRow(cal: CalInfo, onToggle: (CalInfo) -> Unit, onSettings: (String) -> Unit, members: Int? = null) {
    val color = calendarColor(cal.color)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onToggle(cal) }.padding(end = 8.dp),
    ) {
        Checkbox(
            checked = cal.visible,
            onCheckedChange = { onToggle(cal) },
            colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color),
        )
        Text(cal.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (members != null && members > 1) {
            Text(
                (if (members == 1) "%d membro" else "%d membros").tr().format(members),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onSettings(cal.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Settings, contentDescription = "Definições do calendário".tr(), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniMonth(cursor: LocalDate, onSelect: (LocalDate) -> Unit) {
    var month by remember(cursor.year, cursor.monthValue) { mutableStateOf(cursor.withDayOfMonth(1)) }
    val today = LocalDate.now()
    val primary = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${fmtMonthName(month)} ${month.year}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ChevronLeft, null, Modifier.size(18.dp)) }
            IconButton(onClick = { month = month.plusMonths(1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp)) }
        }
        Row(Modifier.fillMaxWidth()) {
            weekdayLetters().forEach {
                Text(it, fontSize = 10.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        CalendarDates.monthGrid(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).height(30.dp).clickable { onSelect(d) }) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(24.dp).clip(CircleShape).then(
                                when {
                                    d == today -> Modifier.background(primary)
                                    d == cursor -> Modifier.background(primary.copy(alpha = 0.15f))
                                    else -> Modifier
                                },
                            ),
                        ) {
                            Text(
                                d.dayOfMonth.toString(), fontSize = 11.sp,
                                color = when {
                                    d == today -> Color.White
                                    d.monthValue != month.monthValue -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
