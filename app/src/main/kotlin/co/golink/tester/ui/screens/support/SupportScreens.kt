package co.golink.tester.ui.screens.support

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.support.AttachmentData
import co.golink.tester.domain.support.DeletionRequestState
import co.golink.tester.domain.support.MessageEnvelope
import co.golink.tester.domain.support.SupportOptions
import co.golink.tester.domain.support.TicketData
import co.golink.tester.ui.i18n.tr
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Rose = Color(0xFFF43F5E)

private fun statusLabel(s: String) = when (s) {
    "open" -> "Aberto".tr()
    "pending" -> "À espera da tua resposta".tr()
    "closed" -> "Fechado".tr()
    else -> s
}

/** Estado do ponto de vista do suporte (open = à espera do suporte). */
internal fun staffStatusLabel(s: String) = when (s) {
    "open" -> "Aguarda suporte".tr()
    "pending" -> "Aguarda utilizador".tr()
    "closed" -> "Fechado".tr()
    else -> s
}

private val AVATAR_PALETTE = listOf(
    Color(0xFF00BC7E), Color(0xFF3B6CFF), Color(0xFF8B5CF6), Color(0xFFF59E0B),
    Color(0xFFEC4899), Color(0xFF0EA5E9), Color(0xFF10B981), Color(0xFFF97316),
)

/** Avatar com foto ou iniciais numa cor estável por nome. */
@Composable
internal fun SupportAvatar(name: String?, url: String?, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val label = name?.trim().orEmpty().ifEmpty { "?" }
    val parts = label.split(Regex("\\s+"))
    val initials = (parts.first().take(1) + if (parts.size > 1) parts.last().take(1) else "").uppercase()
    var hash = 0
    label.forEach { hash = (hash * 31 + it.code) }
    val color = AVATAR_PALETTE[Math.floorMod(hash, AVATAR_PALETTE.size)]
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(CircleShape).background(color),
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
        if (!url.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

internal fun priorityLabel(p: String) = when (p) {
    "low" -> "Baixa".tr()
    "normal" -> "Normal".tr()
    "high" -> "Alta".tr()
    else -> p
}

internal fun categoryLabel(c: String) = when (c) {
    "billing" -> "Faturação".tr()
    "technical" -> "Técnico".tr()
    "other" -> "Outro".tr()
    else -> c
}

internal fun formatDate(iso: String?, pattern: String = "dd/MM/yyyy HH:mm"): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
    }.getOrDefault(iso)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%d KB".format(bytes / 1024)
    else -> "$bytes B"
}

@Composable
private fun StatusChip(status: String, staff: Boolean = false) {
    val color = when (status) {
        "open" -> MaterialTheme.colorScheme.primary
        "pending" -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        if (staff) staffStatusLabel(status) else statusLabel(status),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ================================================================= Lista

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportListScreen(
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onNewTicket: () -> Unit,
    onOpenDashboard: () -> Unit = {},
    viewModel: SupportListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val admin = viewModel.isAdmin

    // Volta a carregar sempre que o ecrã reentra (ex.: ao voltar do detalhe).
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (admin) "Support Desk".tr() else "Suporte".tr()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    if (admin) {
                        IconButton(onClick = onOpenDashboard) {
                            Icon(Icons.Outlined.Insights, contentDescription = "Dashboard".tr())
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!admin) {
                ExtendedFloatingActionButton(
                    onClick = onNewTicket,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Novo ticket".tr()) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Staff: os mesmos separadores da web (open/pending/closed), com contagens.
            val counts = state.counts
            val filters: List<Pair<String?, String>> = if (admin) {
                SupportOptions.STATUSES.map { status ->
                    val n = when (status) {
                        "open" -> counts?.open
                        "pending" -> counts?.pending
                        else -> counts?.closed
                    }
                    status to (if (n != null) "${staffStatusLabel(status)} · $n" else staffStatusLabel(status))
                }
            } else {
                listOf("active" to "Ativos".tr(), "closed" to "Fechados".tr())
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                filters.forEach { (value, label) ->
                    FilterChip(selected = state.filter == value, onClick = { viewModel.setFilter(value) }, label = { Text(label) })
                }
            }
            if (admin) {
                // Filtros da web: atribuídos a mim / sem responsável / prioridade / categoria.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    FilterChip(
                        selected = state.assigned == "me",
                        onClick = { viewModel.setAssigned("me") },
                        label = { Text("Atribuídos a mim".tr() + (counts?.let { " · ${it.mine}" } ?: "")) },
                    )
                    FilterChip(
                        selected = state.assigned == "unassigned",
                        onClick = { viewModel.setAssigned("unassigned") },
                        label = { Text("Sem responsável".tr() + (counts?.let { " · ${it.unassigned}" } ?: "")) },
                    )
                    OptionFilterChip(
                        label = "Prioridade".tr(),
                        selected = state.priority,
                        options = SupportOptions.PRIORITIES,
                        optionLabel = ::priorityLabel,
                        onSelect = viewModel::setPriority,
                    )
                    OptionFilterChip(
                        label = "Categoria".tr(),
                        selected = state.category,
                        options = SupportOptions.CATEGORIES,
                        optionLabel = ::categoryLabel,
                        onSelect = viewModel::setCategory,
                    )
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Pesquisar assunto ou email".tr()) },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = viewModel::refresh) { Text("Procurar".tr()) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            if (!state.isLoading && state.tickets.isEmpty() && state.error == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                ) {
                    Icon(Icons.Outlined.SupportAgent, contentDescription = null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Sem tickets".tr(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                items(state.tickets, key = { it.id }) { ticket ->
                    TicketRow(ticket, admin) { onOpenTicket(ticket.id) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                } else if (state.hasMore) {
                    item {
                        TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("Carregar mais".tr())
                        }
                    }
                }
            }
        }
    }
}

/** Chip com menu: escolhe um valor ou "Todas" (null). */
@Composable
private fun OptionFilterChip(
    label: String,
    selected: String?,
    options: List<String>,
    optionLabel: (String) -> String,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(if (selected != null) "$label: ${optionLabel(selected)}" else label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Todas".tr()) }, onClick = { open = false; onSelect(null) })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun TicketRow(ticket: TicketData, admin: Boolean, onClick: () -> Unit) {
    val a = ticket.attributes
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (admin) {
            SupportAvatar(a.user_name ?: a.user_email, a.user_avatar, 36.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                a.subject,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (a.unread_count > 0) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(a.status, staff = admin)
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(
                        if (admin) (a.user_name ?: a.user_email) else null,
                        if (admin) (a.assigned_to_name ?: "Sem responsável".tr()) else null,
                        categoryLabel(a.category),
                        formatDate(a.last_message_at ?: a.created_at),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (a.unread_count > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Text("${a.unread_count}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ================================================================= Detalhe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportTicketScreen(
    onBack: () -> Unit,
    onNewTicket: () -> Unit = {},
    viewModel: SupportTicketViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val admin = viewModel.isAdmin
    val snackbarHost = remember { SnackbarHostState() }
    var reply by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDialog by remember { mutableStateOf<String?>(null) } // "delete" | "request-deletion" | "close"
    val listState = rememberLazyListState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.addAttachments(uris)
    }

    LaunchedEffect(state.toast) { state.toast?.let { snackbarHost.showSnackbar(it); viewModel.consumeToast() } }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    // Rascunho do copiloto: preenche (ou acrescenta ao já escrito) para o agente rever.
    LaunchedEffect(state.draft) {
        state.draft?.let { draft ->
            reply = if (reply.isBlank()) draft else reply.trimEnd() + "\n\n" + draft
            viewModel.consumeDraft()
        }
    }
    // Abre logo na última mensagem (sem animação) e, com mensagens novas, desce
    // animado. Inclui os separadores de dia: vai até ao último item da lista.
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            kotlinx.coroutines.yield()
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) {
                if (initialScrollDone) listState.animateScrollToItem(last) else listState.scrollToItem(last)
                initialScrollDone = true
            }
        }
    }

    confirmDialog?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title = {
                Text(
                    when (kind) {
                        "delete" -> "Apagar este ticket?".tr()
                        "request-deletion" -> "Pedir eliminação da conta?".tr()
                        else -> "Fechar este ticket?".tr()
                    }
                )
            },
            text = {
                Text(
                    when (kind) {
                        "delete" -> "A conversa e os anexos são apagados para sempre.".tr()
                        "request-deletion" -> "O utilizador vai ser convidado neste ticket a confirmar com a password. A eliminação começa 15 minutos depois da confirmação.".tr()
                        else -> "Podes reabrir respondendo ao ticket.".tr()
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (kind) {
                            "delete" -> viewModel.deleteTicket()
                            "request-deletion" -> viewModel.requestDeletion()
                            else -> viewModel.closeTicket()
                        }
                        confirmDialog = null
                    },
                    colors = if (kind == "close") ButtonDefaults.buttonColors()
                    else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Confirmar".tr()) }
            },
            dismissButton = { TextButton(onClick = { confirmDialog = null }) { Text("Cancelar".tr()) } },
        )
    }

    val ticket = state.ticket
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ticket?.attributes?.subject.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (admin && ticket != null) {
                            Text(
                                listOfNotNull(ticket.attributes.user_name, ticket.attributes.user_email).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    val perms = viewModel.permissions
                    val canRequestDeletion = admin && perms?.can_request_deletion == true
                    val canDelete = admin && perms?.can_delete == true
                    if (ticket != null && ((admin && (canRequestDeletion || canDelete)) || (!admin && ticket.attributes.status != "closed"))) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (admin) {
                                    if (canRequestDeletion) {
                                        DropdownMenuItem(
                                            text = { Text("Pedir eliminação da conta".tr()) },
                                            onClick = { menuOpen = false; confirmDialog = "request-deletion" },
                                        )
                                    }
                                    if (canDelete) {
                                        DropdownMenuItem(
                                            text = { Text("Apagar ticket".tr(), color = MaterialTheme.colorScheme.error) },
                                            onClick = { menuOpen = false; confirmDialog = "delete" },
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Fechar ticket".tr()) },
                                        onClick = { menuOpen = false; confirmDialog = "close" },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (ticket == null) return@Column

            if (admin) AdminControls(
                ticket = ticket,
                enabled = !state.isBusy,
                onUpdate = viewModel::update,
                agents = state.agents,
                canAssign = viewModel.permissions?.can_assign == true,
                myId = viewModel.myId,
                onAssign = viewModel::assign,
            )
            if (admin && (ticket.ai != null || viewModel.permissions?.can_use_ai == true)) AiAssistPanel(
                ticket = ticket,
                canUseAi = viewModel.permissions?.can_use_ai == true,
                state = state,
                onApplyTriage = viewModel::applyTriage,
                onSuggestReply = viewModel::suggestReply,
                onSummarize = viewModel::summarize,
            )
            if (!admin) Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(ticket.attributes.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${categoryLabel(ticket.attributes.category)} · ${priorityLabel(ticket.attributes.priority)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Quem está a tratar do pedido (visto pelo utilizador).
            if (!admin && ticket.attributes.assigned_to != null) {
                val handler = ticket.attributes.assigned_to_public_name ?: "Equipa de suporte".tr()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SupportAvatar(handler, ticket.attributes.assigned_to_avatar, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "%s está a tratar do teu pedido".tr().format(handler),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                state.messages.forEachIndexed { index, message ->
                    val day = formatDate(message.data.attributes.created_at, "yyyy-MM-dd")
                    val previousDay = state.messages.getOrNull(index - 1)?.let { formatDate(it.data.attributes.created_at, "yyyy-MM-dd") }
                    if (day.isNotEmpty() && day != previousDay) {
                        item(key = "day-$day") { DaySeparator(message.data.attributes.created_at) }
                    }
                    item(key = message.data.id) {
                    MessageBubble(
                        message = message,
                        viewerIsAdmin = admin,
                        isBusy = state.isBusy,
                        deletionError = state.deletionError,
                        onDownload = viewModel::download,
                        onConfirmDeletion = viewModel::confirmDeletion,
                        onCancelDeletion = viewModel::cancelDeletion,
                    )
                    }
                }
            }

            TicketDeadlineNotice(ticket.attributes, admin, onNewTicket)

            // Trancado (5 dias sem resposta / após o fecho): sem caixa de resposta.
            if (!ticket.attributes.is_locked) Composer(
                text = reply,
                onTextChange = { reply = it },
                attachments = state.attachments,
                isSending = state.isSending,
                onAttach = { picker.launch("*/*") },
                onRemoveAttachment = viewModel::removeAttachment,
                onSend = { viewModel.send(reply) { reply = "" } },
            )
        }
    }
}

/** Copiloto (Claude): triagem sugerida, rascunho de resposta e resumo — tudo para o agente rever. */
@Composable
private fun AiAssistPanel(
    ticket: TicketData,
    canUseAi: Boolean,
    state: SupportTicketUiState,
    onApplyTriage: () -> Unit,
    onSuggestReply: () -> Unit,
    onSummarize: () -> Unit,
) {
    val ai = ticket.ai
    val a = ticket.attributes
    val differs = ai != null && ((ai.category != null && ai.category != a.category) || (ai.priority != null && ai.priority != a.priority))
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Assistente IA".tr(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (ai != null) {
                ai.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    listOfNotNull(
                        "Sugestão".tr() + ":",
                        ai.category?.let(::categoryLabel),
                        ai.priority?.let(::priorityLabel),
                        ai.language?.uppercase(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (differs) {
                    TextButton(onClick = onApplyTriage, enabled = !state.isBusy, contentPadding = PaddingValues(0.dp)) {
                        Text("Aplicar sugestão".tr())
                    }
                }
            }
            if (canUseAi) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!a.is_locked) {
                        OutlinedButton(onClick = onSuggestReply, enabled = !state.isDrafting) {
                            if (state.isDrafting) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            else Text("Sugerir resposta".tr())
                        }
                    }
                    OutlinedButton(onClick = onSummarize, enabled = !state.isSummarizing) {
                        if (state.isSummarizing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        else Text("Resumir".tr())
                    }
                }
                state.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Revê sempre as sugestões da IA antes de enviar.".tr(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AdminControls(
    ticket: TicketData,
    enabled: Boolean,
    onUpdate: (String?, String?, String?) -> Unit,
    agents: List<co.golink.tester.domain.support.SupportAgentOption>,
    canAssign: Boolean,
    myId: String?,
    onAssign: (String?) -> Unit,
) {
    val a = ticket.attributes
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AssigneeChip(a.assigned_to, a.assigned_to_name, agents, canAssign, myId, enabled, onAssign)
        OptionChip(a.status, SupportOptions.STATUSES, ::staffStatusLabel, enabled) { onUpdate(it, null, null) }
        OptionChip(a.priority, SupportOptions.PRIORITIES, ::priorityLabel, enabled) { onUpdate(null, it, null) }
        OptionChip(a.category, SupportOptions.CATEGORIES, ::categoryLabel, enabled) { onUpdate(null, null, it) }
    }
}

/** Responsável: com can_assign escolhe qualquer membro; sem, só pega/larga. */
@Composable
private fun AssigneeChip(
    assignedTo: String?,
    assignedName: String?,
    agents: List<co.golink.tester.domain.support.SupportAgentOption>,
    canAssign: Boolean,
    myId: String?,
    enabled: Boolean,
    onAssign: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            enabled = enabled,
            leadingIcon = { SupportAvatar(assignedName ?: "?", null, 18.dp) },
            label = { Text(assignedName ?: "Sem responsável".tr()) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // Sem can_assign o backend só deixa pegar em tickets sem responsável.
            if (assignedTo != myId && (canAssign || assignedTo == null)) {
                DropdownMenuItem(text = { Text("Atribuir a mim".tr()) }, onClick = { open = false; onAssign(myId) })
            }
            if (assignedTo != null && (canAssign || assignedTo == myId)) {
                DropdownMenuItem(text = { Text("Largar / sem responsável".tr()) }, onClick = { open = false; onAssign(null) })
            }
            if (canAssign) {
                HorizontalDivider()
                agents.filter { it.id != assignedTo }.forEach { agent ->
                    DropdownMenuItem(
                        leadingIcon = { SupportAvatar(agent.name, agent.avatar, 24.dp) },
                        text = { Text(agent.name) },
                        onClick = { open = false; onAssign(agent.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionChip(
    value: String,
    options: List<String>,
    label: (String) -> String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, enabled = enabled, label = { Text(label(value)) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; if (option != value) onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageEnvelope,
    viewerIsAdmin: Boolean,
    isBusy: Boolean,
    deletionError: String?,
    onDownload: (String, String) -> Unit,
    onConfirmDeletion: (String, String) -> Unit,
    onCancelDeletion: (String) -> Unit,
) {
    val a = message.data.attributes
    val fromSupport = a.author_role == "admin"
    // Sem nome individual → "Equipa de suporte" na língua da app.
    val authorName = a.author_name ?: if (fromSupport) "Equipa de suporte".tr() else "—"
    // O lado de quem está a ver fica à direita.
    val mine = fromSupport == viewerIsAdmin
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            SupportAvatar(authorName, a.author_avatar, 32.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            // Nome público + cargo (nunca "Administrator"); o próprio vê "Tu".
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    if (mine) "Tu".tr() else authorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!mine && fromSupport && !a.author_title.isNullOrBlank()) {
                    Text(
                        " · " + a.author_title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            // Próprias mensagens num tom leve da cor primária (não sólido).
            color = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Tradução automática (Claude) — só chega ao staff; o original fica a um toque.
                var showOriginal by remember(message.data.id) { mutableStateOf(false) }
                val translated = a.translation?.takeIf { viewerIsAdmin && it.isNotBlank() }
                (if (translated != null && !showOriginal) translated else a.body)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (translated != null) {
                    Text(
                        if (showOriginal) "Ver tradução".tr()
                        else "Traduzido de %s · ver original".tr().format(a.language.orEmpty().uppercase()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp).clickable { showOriginal = !showOriginal },
                    )
                }
                a.deletion_request?.let { request ->
                    DeletionRequestCard(
                        request = request,
                        viewerIsAdmin = viewerIsAdmin,
                        isBusy = isBusy,
                        error = deletionError,
                        onConfirm = { password -> onConfirmDeletion(request.id, password) },
                        onCancel = { onCancelDeletion(request.id) },
                    )
                }
                message.data.relationships?.attachments?.forEach { att ->
                    AttachmentRow(att.data) { onDownload(att.data.attributes.download_url, att.data.attributes.filename) }
                }
            }
        }
            Text(
                formatDate(a.created_at, "HH:mm"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DaySeparator(iso: String?) {
    val date = runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() ?: return
    val today = java.time.LocalDate.now()
    val label = when (date) {
        today -> "Hoje".tr()
        today.minusDays(1) -> "Ontem".tr()
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun AttachmentRow(attachment: AttachmentData, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 6.dp)
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(attachment.attributes.filename, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                formatSize(attachment.attributes.filesize),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeletionRequestCard(
    request: DeletionRequestState,
    viewerIsAdmin: Boolean,
    isBusy: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember(request.id) { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(request.status) {
        while (request.status == "scheduled") {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .border(1.dp, Rose.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, contentDescription = null, tint = Rose, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pedido de eliminação de conta".tr(), color = Rose, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        val statusText = when (request.status) {
            "pending" -> "À espera de confirmação (válido até %s).".tr().format(formatDate(request.expires_at))
            "scheduled" -> {
                val left = request.scheduled_for?.let {
                    ((runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(now) - now) / 1000).coerceAtLeast(0)
                } ?: 0
                "Eliminação agendada para %s — faltam %s.".tr()
                    .format(formatDate(request.scheduled_for, "HH:mm"), "%d:%02d".format(left / 60, left % 60))
            }
            "cancelled" -> "Cancelado.".tr()
            "expired" -> "Expirou sem confirmação.".tr()
            "executed" -> "Eliminação iniciada.".tr()
            else -> request.status
        }
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!viewerIsAdmin && request.status == "pending") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password actual".tr()) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank() && !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Rose),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Confirmar eliminação".tr()) }
        }

        val canCancel = request.status == "scheduled" || (viewerIsAdmin && request.status == "pending")
        if (canCancel) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onCancel, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar eliminação".tr())
            }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<PickedFile>,
    isSending: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: (PickedFile) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (attachments.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    attachments.forEach { file ->
                        AssistChip(
                            onClick = { onRemoveAttachment(file) },
                            label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAttach, enabled = attachments.size < SUPPORT_MAX_FILES && !isSending) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Anexar ficheiros".tr())
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Escreve a tua resposta…".tr()) },
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSend, enabled = !isSending && (text.isNotBlank() || attachments.isNotEmpty())) {
                    if (isSending) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar".tr())
                }
            }
        }
    }
}

// ================================================================= Novo ticket

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSupportTicketScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: NewTicketViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("normal") }
    var category by remember { mutableStateOf("technical") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.addAttachments(uris)
    }

    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo ticket".tr()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it.take(160); viewModel.clearAssist() },
                label = { Text("Assunto".tr()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionChip(category, SupportOptions.CATEGORIES, ::categoryLabel, true) { category = it }
                OptionChip(priority, SupportOptions.PRIORITIES, ::priorityLabel, true) { priority = it }
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(10000); viewModel.clearAssist() },
                label = { Text("Descreve o problema".tr()) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                TextButton(onClick = { picker.launch("*/*") }, enabled = state.attachments.size < SUPPORT_MAX_FILES) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Anexar".tr())
                }
                state.attachments.forEach { file ->
                    AssistChip(
                        onClick = { viewModel.removeAttachment(file) },
                        label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    )
                }
            }
            Text(
                "Até %d ficheiros, 10 MB cada.".tr().format(SUPPORT_MAX_FILES),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            // Resposta rápida (Claude + centro de ajuda) antes de abrir o ticket.
            val assist = state.assist
            if (assist != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (assist.confident) {
                            Text("Resposta sugerida".tr(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(assist.answer, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("Resolveu o meu problema".tr()) }
                            Text(
                                "Resposta gerada por IA a partir do nosso centro de ajuda. A tua mensagem não foi guardada.".tr(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Não encontrámos resposta no nosso centro de ajuda. Abre o ticket e a nossa equipa ajuda-te.".tr(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else if (state.assistEnabled) {
                OutlinedButton(
                    onClick = { viewModel.askAssistant(subject, body) },
                    enabled = !state.isAsking && subject.trim().length >= 3 && body.isNotBlank(),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isAsking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Obter resposta rápida".tr())
                }
            }

            Button(
                onClick = { viewModel.create(subject, body, priority, category) },
                enabled = !state.isSending && subject.trim().length >= 3 && body.isNotBlank(),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.assist?.confident == true) "Abrir o ticket na mesma".tr() else "Enviar ticket".tr(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Prazos do ticket por cima da caixa de resposta: contagem até ao fecho
 * automático (48h sem resposta do utilizador), prazo para reabrir um ticket
 * fechado e aviso de ticket trancado (5 dias) com atalho para um novo.
 */
@Composable
private fun TicketDeadlineNotice(
    attrs: co.golink.tester.domain.support.TicketAttributes,
    admin: Boolean,
    onNewTicket: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = System.currentTimeMillis() }
    }
    fun left(iso: String?): String {
        val end = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
        val m = ((end - now).coerceAtLeast(0) / 60_000).toInt()
        val d = m / 1440; val h = (m % 1440) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m % 60}m"
            else -> "${maxOf(1, m)}m"
        }
    }

    val (title, text, warn) = when {
        attrs.is_locked -> Triple(
            "Ticket fechado definitivamente".tr(),
            if (admin) "O utilizador já não pode responder a este ticket.".tr()
            else "Esta conversa já não pode ser reaberta. Abre um novo ticket se ainda precisares de ajuda.".tr(),
            false,
        )
        attrs.status == "pending" && attrs.auto_close_at != null -> Triple(
            "Fecha em %s".tr().format(left(attrs.auto_close_at)),
            if (admin) "À espera do utilizador. Fecha automaticamente se não houver resposta.".tr()
            else "Este ticket fecha automaticamente se não responderes. Depois disso ainda podes responder para o reabrir, até 5 dias.".tr(),
            true,
        )
        attrs.status == "closed" && attrs.locks_at != null -> Triple(
            "Ticket fechado".tr(),
            if (admin) "O utilizador pode reabri-lo respondendo dentro de %s.".tr().format(left(attrs.locks_at))
            else "Responde dentro de %s para o reabrir. Depois disso terás de abrir um novo ticket.".tr().format(left(attrs.locks_at)),
            false,
        )
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (warn) Color(0xFFF59E0B).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (attrs.is_locked && !admin) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onNewTicket) { Text("Novo ticket".tr()) }
            }
        }
    }
}
