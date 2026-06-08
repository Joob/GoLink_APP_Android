package co.golink.tester.ui.screens.lock

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    viewModel: LockViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val error by viewModel.error.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }

    BackHandler { /* consume back — app stays locked */ }

    val biometricAvailable = remember {
        viewModel.biometricEnabled &&
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun triggerBiometric() {
        val executor = ContextCompat.getMainExecutor(context)
        BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricSuccess()
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear aplicação")
                .setSubtitle("Confirma a tua identidade")
                .apply {
                    if (viewModel.pinEnabled) setNegativeButtonText("Usar PIN")
                    else setNegativeButtonText("Cancelar")
                }
                .build(),
        )
    }

    LaunchedEffect(Unit) {
        if (biometricAvailable) triggerBiometric()
    }

    LaunchedEffect(error) {
        if (error != null) {
            delay(1200)
            pin = ""
            viewModel.consumeError()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier,
        ) {
            Text(
                "Introduz o PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (error != null) MaterialTheme.colorScheme.error
                                else if (i < pin.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }

            AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Numpad
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(if (biometricAvailable) "bio" else "", "0", "del"),
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            NumpadKey(
                                key = key,
                                onDigit = { digit ->
                                    if (pin.length < 4 && error == null) {
                                        pin += digit
                                        if (pin.length == 4) viewModel.verifyPin(pin)
                                    }
                                },
                                onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                onBiometric = ::triggerBiometric,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun NumpadKey(
    key: String,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit,
) {
    val size = 76.dp
    when (key) {
        "" -> Spacer(Modifier.size(size))
        "del" -> IconButton(onClick = onDelete, modifier = Modifier.size(size)) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Apagar", modifier = Modifier.size(26.dp))
        }
        "bio" -> IconButton(onClick = onBiometric, modifier = Modifier.size(size)) {
            Icon(
                Icons.Outlined.Fingerprint,
                contentDescription = "Biometria",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        else -> OutlinedButton(
            onClick = { onDigit(key) },
            modifier = Modifier.size(size),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}
