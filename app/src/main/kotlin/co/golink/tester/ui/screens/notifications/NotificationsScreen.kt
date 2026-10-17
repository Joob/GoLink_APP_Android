package co.golink.tester.ui.screens.notifications

import co.golink.tester.ui.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.Badge
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.notifications.Notification

private val SYSTEM_CATEGORIES =
    setOf("storage-full", "billing-alert", "payment-alert", "insufficient-balance", "security-alert")

@Composable
private fun NotificationTab(label: String, unread: Int, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (unread > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text(if (unread > 99) "99+" else unread.toString()) }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenSecurityActivity: () -> Unit = {},
    onOpenSupportTicket: (id: String, admin: Boolean) -> Unit = { _, _ -> },
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHost.showSnackbar(it); viewModel.consumeToast() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notificações".tr()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr())
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::markAllRead, enabled = items.any { !it.isRead }) {
                        Icon(Icons.Filled.DoneAll, contentDescription = "Marcar todas lidas".tr())
                    }
                    IconButton(onClick = viewModel::flushAll, enabled = items.isNotEmpty()) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Apagar todas".tr())
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                else -> {
                    val systemItems = items.filter { it.category in SYSTEM_CATEGORIES }
                    val otherItems = items.filter { it.category !in SYSTEM_CATEGORIES }
                    val systemUnread = systemItems.count { !it.isRead }
                    val otherUnread = otherItems.count { !it.isRead }
                    // Duas tabs (como na web) em vez de uma lista longa. Abre na
                    // que tem novidades; Sistema tem prioridade.
                    var tab by rememberSaveable { mutableIntStateOf(if (systemUnread == 0 && otherUnread > 0) 1 else 0) }
                    val visible = if (tab == 0) systemItems else otherItems

                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = tab) {
                            NotificationTab("Sistema".tr(), systemUnread, tab == 0) { tab = 0 }
                            NotificationTab("Outros".tr(), otherUnread, tab == 1) { tab = 1 }
                        }
                        if (visible.isEmpty()) {
                            Text(
                                if (tab == 0) "Ainda não tem notificações de sistema".tr() else "Sem notificações".tr(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                            )
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(visible, key = { it.id }) { n ->
                                NotificationRow(
                                    notification = n,
                                    onTap = {
                                        if (!n.isRead) viewModel.markRead(n.id)
                                        if (n.category == "security-alert") onOpenSecurityActivity()
                                        openSupportTicket(n, onOpenSupportTicket)
                                    },
                                    onToggleRead = { viewModel.toggleRead(n) },
                                    onDelete = { viewModel.delete(n.id) },
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            }
                        }
                    }
                }
            }
            co.golink.tester.ui.components.E2EEncryptedFlash(
                trigger = Unit,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun NotificationRow(
    notification: Notification,
    onTap: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (notification.isRead) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.primary,
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                notification.title.ifBlank { notification.category ?: "Notificação".tr() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (notification.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    notification.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            notification.createdAt?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onToggleRead) {
            Icon(
                if (notification.isRead) Icons.Filled.MarkEmailUnread else Icons.Filled.Done,
                contentDescription = if (notification.isRead) "Marcar como não lida".tr() else "Marcar como lida".tr(),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Eliminar".tr(), modifier = Modifier.size(18.dp))
        }
    }
}

/** Notificações de suporte: o action web traz a rota (fila do staff ou do user) e o id do ticket. */
private fun openSupportTicket(
    n: co.golink.tester.domain.notifications.Notification,
    open: (String, Boolean) -> Unit,
) {
    val id = n.actionId ?: return
    when (n.actionRoute) {
        "AdminSupportTicketDetail" -> open(id, true)
        "SupportTicketDetail" -> open(id, false)
    }
}
