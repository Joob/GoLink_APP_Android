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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenSecurityActivity: () -> Unit = {},
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
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Secção Sistema — sempre visível
                        item(key = "system-header") { SectionHeader("Sistema".tr()) }
                        if (systemItems.isEmpty()) {
                            item(key = "system-empty") {
                                Text(
                                    "Ainda não tem notificações de sistema".tr(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        } else {
                            items(systemItems, key = { it.id }) { n ->
                                NotificationRow(
                                    notification = n,
                                    onTap = {
                                        if (!n.isRead) viewModel.markRead(n.id)
                                        if (n.category == "security-alert") onOpenSecurityActivity()
                                    },
                                    onDelete = { viewModel.delete(n.id) },
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            }
                        }
                        if (otherItems.isNotEmpty()) {
                            item(key = "others-header") { SectionHeader("Outros".tr()) }
                        }
                        items(otherItems, key = { it.id }) { n ->
                            NotificationRow(
                                notification = n,
                                onTap = {
                                    if (!n.isRead) viewModel.markRead(n.id)
                                    if (n.category == "security-alert") onOpenSecurityActivity()
                                },
                                onDelete = { viewModel.delete(n.id) },
                            )
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Apagar", modifier = Modifier.size(18.dp))
        }
    }
}
