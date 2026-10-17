package co.golink.tester.ui.screens.otp

import co.golink.tester.ui.i18n.tr
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.ui.common.AuthScaffold

@Composable
fun OtpScreen(
    onValidated: () -> Unit,
    onCancel: () -> Unit,
    viewModel: OtpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.validated) { if (state.validated) onValidated() }

    AuthScaffold(
        title = "Verificação".tr(),
        subtitle = "Introduz o código de 6 dígitos que enviámos para o teu email".tr(),
        onBack = onCancel,
    ) {
        OtpCodeInput(
            value = state.code,
            onValueChange = viewModel::onCode,
            isError = state.error != null,
            // Bloqueado enquanto valida (valida sozinho ao 6.º dígito).
            enabled = !state.expired && !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        when {
            state.error != null -> Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            state.expired -> Text(
                "O código expirou. Pede um novo.".tr(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            state.remainingSeconds > 0 -> Text(
                "${"O código expira em".tr()} ${formatCountdown(state.remainingSeconds)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            state.info != null -> Text(
                state.info!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Sem botão: o código valida-se sozinho quando fica completo.
        Spacer(Modifier.height(16.dp))
        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "A validar…".tr(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Não recebeu o código?".tr(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { viewModel.sendCode() },
                enabled = !state.sending && state.remainingSeconds <= 0,
            ) {
                Text(
                    when {
                        state.sending -> "A enviar…".tr()
                        state.remainingSeconds > 0 -> "Reenviar (${formatCountdown(state.remainingSeconds)})"
                        else -> "Reenviar"
                    }
                )
            }
        }
    }
}

private fun formatCountdown(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val minutes = s / 60
    val seconds = s % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Six single-digit boxes driven by a single (invisible) text field, so paste,
 * keyboard and IME autofill all work while each digit renders in its own square.
 */
@Composable
private fun OtpCodeInput(
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

    // Ao abrir e sempre que volta a ficar ativo (ex.: código errado limpo depois
    // da validação automática), volta a pôr o cursor/teclado no código.
    LaunchedEffect(enabled) { if (enabled) runCatching { focusRequester.requestFocus() } }
}
