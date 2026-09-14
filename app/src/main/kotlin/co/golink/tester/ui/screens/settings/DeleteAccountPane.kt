package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.DeletionGuard
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.data.user.AccountDeletionRepository
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Eliminação de conta self-service — o mesmo fluxo de 3 passos da web:
 * confirmar email → código de 6 dígitos → progresso do job de eliminação.
 */

enum class DeleteAccountStep { Email, Code, Deleting }

data class DeleteAccountUiState(
    val step: DeleteAccountStep = DeleteAccountStep.Email,
    val isLoading: Boolean = false,
    val error: String? = null,
    val resendCooldown: Int = 0,
    val progressPercentage: Int = 0,
    val progressStep: String? = null,
    val progressDetails: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val repository: AccountDeletionRepository,
    private val sessionManager: SessionManager,
    private val deletionGuard: DeletionGuard,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var cooldownJob: Job? = null

    fun sendCode(emailConfirmation: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            repository.sendCode(emailConfirmation.trim())
                .onSuccess {
                    _state.update { it.copy(isLoading = false, step = DeleteAccountStep.Code) }
                    startCooldown()
                }
                .onFailure { t ->
                    _state.update { it.copy(isLoading = false, error = t.message ?: "Erro ao enviar o código".tr()) }
                }
        }
    }

    fun resendCode(emailConfirmation: String) {
        if (_state.value.resendCooldown > 0 || _state.value.isLoading) return
        sendCode(emailConfirmation)
    }

    fun confirm(code: String) {
        if (_state.value.isLoading || code.length != 6) return
        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            repository.confirm(code)
                .onSuccess {
                    // O backend suspendeu a conta e agendou o job: a partir daqui
                    // só o polling do progresso é autorizado — ligar a guarda para
                    // os pollers de background (notificações/backup) não limparem
                    // o token ao apanharem 403 account_suspended.
                    deletionGuard.active = true
                    _state.update {
                        it.copy(isLoading = false, step = DeleteAccountStep.Deleting, progressPercentage = 5)
                    }
                    startPolling()
                }
                .onFailure { t ->
                    _state.update { it.copy(isLoading = false, error = t.message ?: "Código inválido ou expirado".tr()) }
                }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return

        pollJob = viewModelScope.launch {
            while (isActive) {
                repository.progress()
                    .onSuccess { progress ->
                        _state.update {
                            it.copy(
                                progressPercentage = progress.percentage.coerceIn(0, 100),
                                progressStep = progress.current_step,
                                progressDetails = progress.details,
                            )
                        }

                        if (progress.completed || progress.percentage >= 100) {
                            finish()
                            return@launch
                        }
                    }
                    .onFailure { t ->
                        // 401/403 = job terminou e revogou o token — conta apagada.
                        val code = (t as? AccountDeletionRepository.HttpException)?.statusCode
                        if (code == 401 || code == 403) {
                            finish()
                            return@launch
                        }
                        // Erros transitórios (rede): continuar a tentar.
                    }

                delay(1_500)
            }
        }
    }

    private suspend fun finish() {
        _state.update {
            it.copy(progressPercentage = 100, completed = true, progressStep = "Conta eliminada com sucesso!".tr())
        }

        delay(2_000)

        deletionGuard.active = false
        // Token limpo → SessionManager passa a Unauthenticated → RootGate troca
        // para o ecrã de login automaticamente.
        sessionManager.forceLogout()
    }

    private fun startCooldown(seconds: Int = 60) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0 && isActive) {
                _state.update { it.copy(resendCooldown = remaining) }
                delay(1_000)
                remaining--
            }
            _state.update { it.copy(resendCooldown = 0) }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        // Se o utilizador sair do ecrã a meio, a eliminação continua no servidor;
        // desligar a guarda devolve ao interceptor o logout automático no próximo
        // 403 account_suspended — nunca fica preso numa app "meio morta".
        deletionGuard.active = false
        super.onCleared()
    }
}

@Composable
internal fun DeleteAccountPane(
    userEmail: String?,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var emailInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }

    // Auto-submissão aos 6 dígitos, como no ecrã de OTP.
    LaunchedEffect(codeInput) {
        if (codeInput.length == 6 && state.step == DeleteAccountStep.Code && !state.isLoading) {
            viewModel.confirm(codeInput)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state.step) {
            DeleteAccountStep.Email -> {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Apagar conta permanentemente".tr(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Esta ação é irreversível. Todos os ficheiros, pastas e dados encriptados serão eliminados a 100%.".tr(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "Para continuar, escreve o email da conta:".tr(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                userEmail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it; viewModel.consumeError() },
                    label = { Text("Email".tr()) },
                    singleLine = true,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.sendCode(emailInput) },
                    enabled = !state.isLoading && emailInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enviar código de verificação".tr())
                    }
                }
            }

            DeleteAccountStep.Code -> {
                Text(
                    "Código de verificação".tr(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enviámos um código de 6 dígitos para o teu email. O código expira em 30 minutos.".tr(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                DeletionCodeInput(
                    value = codeInput,
                    onValueChange = { codeInput = it; viewModel.consumeError() },
                    isError = state.error != null,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(
                    onClick = { viewModel.resendCode(emailInput) },
                    enabled = state.resendCooldown == 0 && !state.isLoading,
                ) {
                    Text(
                        if (state.resendCooldown > 0) {
                            "Reenviar em".tr() + " ${state.resendCooldown}s"
                        } else {
                            "Reenviar código".tr()
                        },
                    )
                }
            }

            DeleteAccountStep.Deleting -> {
                Spacer(Modifier.height(24.dp))
                Text(
                    if (state.completed) "Conta eliminada".tr() else "A eliminar a conta…".tr(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "${state.progressPercentage}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.progressPercentage / 100f },
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Spacer(Modifier.height(16.dp))
                state.progressStep?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                state.progressDetails?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Cópia do OtpCodeInput (privado no OtpScreen) — 6 células de dígitos. */
@Composable
private fun DeletionCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    length: Int = 6,
) {
    val focusRequester = remember { FocusRequester() }

    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(length)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(length) { index ->
                    val char = value.getOrNull(index)?.toString() ?: ""
                    val isActive = enabled && index == value.length.coerceAtMost(length - 1) && value.length < length
                    val borderColor = when {
                        isError -> MaterialTheme.colorScheme.error
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 56.dp)
                            .border(
                                width = if (isError || isActive) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char,
                            textAlign = TextAlign.Center,
                            style = LocalTextStyle.current.merge(MaterialTheme.typography.headlineSmall),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
    )

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}
