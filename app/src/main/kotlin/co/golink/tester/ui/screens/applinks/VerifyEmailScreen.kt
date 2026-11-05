package co.golink.tester.ui.screens.applinks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.golink.tester.ui.common.AuthScaffold
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class VerifyState { Loading, Done, Expired, Failed }

/**
 * Link "Confirmar email" aberto na app: faz o mesmo GET que o browser faria ao
 * URL assinado (sem reescrever o host nem seguir o redirect — 302 é sucesso).
 */
@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    @Named("base") private val client: OkHttpClient,
) : ViewModel() {
    private val _state = MutableStateFlow(VerifyState.Loading)
    val state: StateFlow<VerifyState> = _state
    private var started = false

    fun verify(url: String, force: Boolean = false) {
        if (started && !force) return
        started = true
        _state.value = VerifyState.Loading
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
                    client.newBuilder().followRedirects(false).followSslRedirects(false).build()
                        .newCall(request).execute().use { it.code }
                }.fold(
                    onSuccess = { code ->
                        when (code) {
                            in 200..399 -> VerifyState.Done
                            403, 404, 422 -> VerifyState.Expired
                            else -> VerifyState.Failed
                        }
                    },
                    onFailure = { VerifyState.Failed },
                )
            }
        }
    }
}

@Composable
fun VerifyEmailScreen(
    url: String,
    authenticated: Boolean,
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(url) { viewModel.verify(url) }

    AuthScaffold(
        title = "Confirmar email".tr(),
        subtitle = when (state) {
            VerifyState.Loading -> "A confirmar o teu email…".tr()
            VerifyState.Done -> "Email confirmado! A tua conta está pronta a usar.".tr()
            VerifyState.Expired -> "Este link expirou ou já não é válido. Inicia sessão para pedir um novo.".tr()
            VerifyState.Failed -> "Não foi possível confirmar agora. Verifica a ligação e tenta outra vez.".tr()
        },
        onBack = onBack,
    ) {
        Spacer(Modifier.height(8.dp))
        when (state) {
            VerifyState.Loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            VerifyState.Failed -> {
                PrimaryButton("Tentar outra vez".tr()) { viewModel.verify(url, force = true) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Voltar".tr()) }
            }
            else -> PrimaryButton(if (authenticated) "Continuar".tr() else "Iniciar sessão".tr()) {
                if (authenticated) onContinue() else onSignIn()
            }
        }
    }
}

@Composable
internal fun PrimaryButton(text: String, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}
