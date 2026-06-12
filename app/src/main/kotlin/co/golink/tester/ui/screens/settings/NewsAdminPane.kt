package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.news.NewsRepository
import co.golink.tester.ui.components.NewsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NewsAdminViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {

    data class UiState(
        val message: String = "",
        val allowed: Boolean = false,
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val feedback: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refresh().onSuccess { news ->
                _state.value = _state.value.copy(
                    message = news?.message.orEmpty(),
                    allowed = news?.allowed == true,
                    isLoading = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, feedback = "Não foi possível carregar")
            }
        }
    }

    fun setMessage(value: String) {
        _state.value = _state.value.copy(message = value, feedback = null)
    }

    fun setAllowed(value: Boolean) {
        _state.value = _state.value.copy(allowed = value, feedback = null)
    }

    fun save() {
        val s = _state.value
        _state.value = s.copy(isSaving = true, feedback = null)
        viewModelScope.launch {
            repository.save(s.message.trim(), s.allowed)
                .onSuccess { _state.value = _state.value.copy(isSaving = false, feedback = "Notícia guardada") }
                .onFailure { _state.value = _state.value.copy(isSaving = false, feedback = "Erro ao guardar: ${it.message}") }
        }
    }
}

@Composable
fun NewsAdminPane(
    viewModel: NewsAdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "Notícia importante",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Aparece num retângulo laranja no topo dos files, na app e no site.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.message,
            onValueChange = viewModel::setMessage,
            label = { Text("Mensagem") },
            placeholder = { Text("Escreve aqui a notícia importante...") },
            minLines = 4,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Permitir notícia", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Mostrar o banner aos utilizadores",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.allowed,
                onCheckedChange = viewModel::setAllowed,
                enabled = !state.isLoading,
            )
        }

        if (state.message.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Pré-visualização",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NewsOrange)
                    .padding(16.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = viewModel::save,
            enabled = !state.isLoading && !state.isSaving,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.height(18.dp),
                )
            } else {
                Text("Guardar")
            }
        }

        state.feedback?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
