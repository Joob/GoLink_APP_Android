package co.golink.tester.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.security.SecurityEvent
import co.golink.tester.ui.i18n.tr

/** Eventos que indicam potencial compromisso da conta — destacados a vermelho. */
private val ALERTS = setOf(
    "account.new_device",
    "account.password_changed",
    "account.e2e_secret_rotated",
    "auth.login_failed",
    "auth.login_failed_repeated",
    "auth.otp_locked",
    "auth.otp_failed_repeated",
)

private fun eventLabel(event: String): String = when (event) {
    "auth.login" -> "Início de sessão".tr()
    "auth.login_failed" -> "Tentativa de início de sessão falhada".tr()
    "auth.login_failed_repeated" -> "Várias tentativas de início de sessão falhadas".tr()
    "auth.otp_failed" -> "Código de segurança incorreto".tr()
    "auth.otp_failed_repeated" -> "Vários códigos de segurança incorretos".tr()
    "auth.otp_locked" -> "Conta temporariamente bloqueada".tr()
    "account.password_changed" -> "Palavra-passe alterada".tr()
    "account.e2e_secret_rotated" -> "Segredo de encriptação alterado".tr()
    "account.new_device" -> "Novo dispositivo ou localização".tr()
    "share.created" -> "Link de partilha criado".tr()
    "share.updated" -> "Link de partilha alterado".tr()
    "team.invitation_accepted" -> "Convite de equipa aceite".tr()
    else -> "Evento de segurança".tr()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityActivityScreen(
    onBack: () -> Unit,
    viewModel: SecurityActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHost.showSnackbar(it); viewModel.consumeToast() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Atividade de segurança".tr()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr())
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.events.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                state.events.isEmpty() -> Text(
                    "Ainda não há atividade registada".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.events, key = { it.id }) { event ->
                        SecurityEventRow(event)
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityEventRow(event: SecurityEvent) {
    val isAlert = event.event in ALERTS
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isAlert) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                eventLabel(event.event),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isAlert) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            event.ip?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "IP: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            event.userAgent?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            event.createdAt?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
