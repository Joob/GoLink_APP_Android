package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.admin.AdminError
import co.golink.tester.ui.screens.admin.AdminLoading
import co.golink.tester.ui.screens.admin.AdminPageHeader
import co.golink.tester.ui.screens.admin.AdminStatusPill
import co.golink.tester.ui.screens.admin.AdminTone
import co.golink.tester.ui.screens.admin.SwitchRow
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
        /** Valores gravados no servidor (para Descartar e para saber se há alterações). */
        val savedMessage: String = "",
        val savedAllowed: Boolean = false,
        val isLoading: Boolean = true,
        val error: String? = null,
        val isSaving: Boolean = false,
        val feedback: String? = null,
    ) {
        val changed: Boolean get() = message.trim() != savedMessage || allowed != savedAllowed
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.refresh().onSuccess { news ->
                val msg = news?.message.orEmpty()
                val on = news?.allowed == true
                _state.value = _state.value.copy(message = msg, allowed = on, savedMessage = msg, savedAllowed = on, isLoading = false)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = "Não foi possível carregar".tr())
            }
        }
    }

    fun setMessage(value: String) {
        _state.value = _state.value.copy(message = value)
    }

    fun setAllowed(value: Boolean) {
        _state.value = _state.value.copy(allowed = value)
    }

    fun discard() {
        _state.value = _state.value.copy(message = _state.value.savedMessage, allowed = _state.value.savedAllowed)
    }

    fun save() {
        val s = _state.value
        _state.value = s.copy(isSaving = true, feedback = null)
        viewModelScope.launch {
            repository.save(s.message.trim(), s.allowed)
                .onSuccess {
                    _state.value = _state.value.copy(isSaving = false, savedMessage = s.message.trim(), savedAllowed = s.allowed, feedback = "Notícia guardada".tr())
                }
                .onFailure { _state.value = _state.value.copy(isSaving = false, feedback = "Erro ao guardar: ${it.message}") }
        }
    }

    fun consumeFeedback() {
        _state.value = _state.value.copy(feedback = null)
    }
}

@Composable
fun NewsAdminPane(
    viewModel: NewsAdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.feedback) { state.feedback?.let { snackbar.showSnackbar(it); viewModel.consumeFeedback() } }

    Box(Modifier.fillMaxSize()) {
        if (state.isLoading) {
            AdminLoading()
        } else if (state.error != null) {
            AdminError(state.error!!, viewModel::load)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AdminPageHeader("Aviso mostrado a todos na zona de ficheiros, na app e no site.".tr())
                SettingsCard(
                    title = "Notícia importante".tr(),
                    description = "Aparece num retângulo laranja no topo dos files, na app e no site.".tr(),
                    icon = Icons.Outlined.Campaign,
                    iconTint = SettingsColors.Amber,
                    trailing = {
                        if (state.savedAllowed && state.savedMessage.isNotBlank()) AdminStatusPill("Visível".tr(), AdminTone.Success)
                        else AdminStatusPill("Escondida".tr(), AdminTone.Neutral)
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SwitchRow(
                            "Permitir notícia".tr(),
                            state.allowed,
                            description = "Mostrar o banner aos utilizadores".tr(),
                            onChange = viewModel::setAllowed,
                        )
                        Column {
                            SettingsFieldLabel("Mensagem".tr())
                            OutlinedTextField(
                                value = state.message,
                                onValueChange = viewModel::setMessage,
                                placeholder = { Text("Escreve aqui a notícia importante...".tr()) },
                                minLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            if (state.changed) {
                                TextButton(onClick = viewModel::discard, enabled = !state.isSaving) { Text("Descartar".tr()) }
                                Spacer(Modifier.width(4.dp))
                            }
                            SettingsPrimaryButton("Guardar".tr(), onClick = viewModel::save, enabled = state.changed, loading = state.isSaving)
                        }
                    }
                }
                if (state.message.isNotBlank()) {
                    SettingsCard(title = "Pré-visualização".tr(), icon = Icons.Outlined.Visibility, iconTint = SettingsColors.Slate) {
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
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
