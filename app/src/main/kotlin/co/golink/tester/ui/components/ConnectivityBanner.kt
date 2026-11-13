package co.golink.tester.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.network.ConnectivityMonitor
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay

@HiltViewModel
class ConnectivityViewModel @Inject constructor(
    private val monitor: ConnectivityMonitor,
) : ViewModel() {
    val online = monitor.online
    val retryAt = monitor.retryAt
    fun retryNow() = monitor.retryNow()
}

private enum class BannerKind { Offline, Retrying, Restored }

/**
 * Aviso de ligação no topo da app:
 *  - sem rede → "Estás offline. Verifica a tua ligação."
 *  - com rede mas um envio à espera → "A tentar de novo em Ns…" + "Ligar agora"
 *  - a rede voltou → "Ligação restabelecida." durante uns segundos
 */
@Composable
fun ConnectivityBanner(modifier: Modifier = Modifier, viewModel: ConnectivityViewModel = hiltViewModel()) {
    val online by viewModel.online.collectAsStateWithLifecycle()
    val retryAt by viewModel.retryAt.collectAsStateWithLifecycle()
    var wasOffline by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(online) {
        if (!online) {
            wasOffline = true
            restored = false
        } else if (wasOffline) {
            wasOffline = false
            restored = true
            delay(3000)
            restored = false
        }
    }
    LaunchedEffect(retryAt) {
        while (retryAt != null) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val seconds = retryAt?.let { ((it - now + 999) / 1000).coerceAtLeast(0) }
    val kind = when {
        !online -> BannerKind.Offline
        seconds != null -> BannerKind.Retrying
        restored -> BannerKind.Restored
        else -> null
    }

    AnimatedVisibility(
        visible = kind != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.statusBarsPadding(),
    ) {
        val k = kind ?: BannerKind.Restored
        val dark = MaterialTheme.colorScheme.background.let { 0.299f * it.red + 0.587f * it.green + 0.114f * it.blue < 0.5f }
        val (bg, fg, icon) = when (k) {
            BannerKind.Offline -> Triple(
                if (dark) Color(0xFF3A2A00) else Color(0xFFFFF4D6),
                if (dark) Color(0xFFF5B83D) else Color(0xFF8A5A00),
                Icons.Outlined.WarningAmber,
            )
            BannerKind.Retrying -> Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurface,
                Icons.Outlined.Info,
            )
            BannerKind.Restored -> Triple(
                if (dark) Color(0xFF0E3B2B) else Color(0xFFE3F7EF),
                if (dark) Color(0xFF4ADE9F) else Color(0xFF017A53),
                Icons.Outlined.CheckCircle,
            )
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bg,
            border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    when (k) {
                        BannerKind.Offline -> "Estás offline. Verifica a tua ligação.".tr()
                        BannerKind.Retrying -> "Ligação perdida. A tentar de novo em %ds…".tr().format(seconds ?: 0)
                        BannerKind.Restored -> "Ligação restabelecida.".tr()
                    },
                    color = fg,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                if (k == BannerKind.Retrying) {
                    TextButton(onClick = viewModel::retryNow) {
                        Text("Ligar agora".tr(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
