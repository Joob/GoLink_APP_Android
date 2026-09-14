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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.encryption.NameMigrationStatusResponse
import co.golink.tester.network.UserEncryptionApi
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.BrandGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class E2ENameMigrationViewModel @Inject constructor(
    private val api: UserEncryptionApi,
    private val keys: E2EKeyManager,
) : ViewModel() {
    private val _status = MutableStateFlow<NameMigrationStatusResponse?>(null)
    val status: StateFlow<NameMigrationStatusResponse?> = _status.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            // Só faz sentido enquanto a chave está disponível (senão não migra).
            while (isActive && keys.isUnlocked) {
                val s = runCatching { api.nameMigrationStatus().body() }.getOrNull()
                _status.value = s
                // Mantém o polling enquanto a migração de ficheiros decorre — o
                // banner de nomes só aparece quando essa terminar.
                if (s != null && !s.in_progress && !s.waiting_for_file_migration) break
                delay(8000)
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
    }
}

/** Progresso da migração de NOMES (Fase 2). Some quando termina. */
@Composable
fun E2ENameMigrationBanner(viewModel: E2ENameMigrationViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.start() }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val s = status ?: return
    if (!s.in_progress) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(2.dp, BrandGreen, RoundedCornerShape(14.dp))
            .background(BrandGreen.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "A cifrar nomes".tr(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "A cifrar os nomes das tuas pastas e ficheiros em segundo plano.".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { (s.percent.coerceIn(0, 100)) / 100f },
                color = BrandGreen,
                trackColor = BrandGreen.copy(alpha = 0.15f),
                modifier = Modifier.weight(1f).height(8.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${s.percent}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${s.done} ${"de".tr()} ${s.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
