package co.golink.tester.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.data.user.UserRepository
import co.golink.tester.network.SettingsApi
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val Rose = Color(0xFFF43F5E)

@HiltViewModel
class DeletionScheduledViewModel @Inject constructor(
    userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val api: SettingsApi,
) : ViewModel() {
    val user = userRepository.me

    private val _isCancelling = MutableStateFlow(false)
    val isCancelling = _isCancelling.asStateFlow()

    fun cancel() {
        if (_isCancelling.value) return
        _isCancelling.value = true
        viewModelScope.launch {
            runCatching { api.cancelScheduledDeletion() }
            sessionManager.refreshUser()
            _isCancelling.value = false
        }
    }
}

/**
 * Eliminação de conta confirmada via ticket de suporte e ainda cancelável.
 * A conta continua utilizável até à hora agendada.
 */
@Composable
fun DeletionScheduledBanner(viewModel: DeletionScheduledViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val isCancelling by viewModel.isCancelling.collectAsStateWithLifecycle()
    val scheduledFor = user?.deletionScheduledFor ?: return
    val target = runCatching { Instant.parse(scheduledFor) }.getOrNull() ?: return

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(target) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val secondsLeft = ((target.toEpochMilli() - now) / 1000).coerceAtLeast(0)
    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(target)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(2.dp, Rose, RoundedCornerShape(14.dp))
            .background(Rose.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, contentDescription = null, tint = Rose, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "A tua conta será eliminada às %s (faltam %s)".tr()
                    .format(time, "%d:%02d".format(secondsLeft / 60, secondsLeft % 60)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Descarrega o que quiseres guardar. Ainda podes cancelar.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = viewModel::cancel, enabled = !isCancelling) {
            Text("Cancelar eliminação".tr(), color = Rose, fontWeight = FontWeight.SemiBold)
        }
    }
}
