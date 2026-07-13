package co.golink.tester.ui.screens.encryption

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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.encryption.MigrationStatusResponse
import co.golink.tester.network.UserEncryptionApi
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val Orange = Color(0xFFF97316)

@HiltViewModel
class E2EMigrationViewModel @Inject constructor(
    private val api: UserEncryptionApi,
) : ViewModel() {
    private val _status = MutableStateFlow<MigrationStatusResponse?>(null)
    val status: StateFlow<MigrationStatusResponse?> = _status.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            while (isActive) {
                val s = runCatching { api.migrationStatus().body() }.getOrNull()
                _status.value = s
                if (s != null && !s.in_progress) break // terminou -> pára o polling
                delay(8000)
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
    }
}

@Composable
fun E2EMigrationBanner(viewModel: E2EMigrationViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.start() }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val s = status ?: return
    if (!s.in_progress) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(2.dp, Orange, RoundedCornerShape(14.dp))
            .background(Orange.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Orange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Encriptação em curso".tr(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Estamos a encriptar os teus ficheiros. Podes continuar a usar a app.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { (s.percent.coerceIn(0, 100)) / 100f },
                color = Orange,
                trackColor = Orange.copy(alpha = 0.15f),
                modifier = Modifier.weight(1f).height(8.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${s.percent}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
