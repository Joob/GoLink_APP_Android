package co.golink.tester.ui.screens.lock

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bloqueio da app em folha a subir de baixo (por cima do conteúdo desfocado):
 * teclado redondo para o PIN ou ecrã de impressão digital, com "Usar PIN".
 */
@Composable
fun LockScreen(
    viewModel: LockViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val error by viewModel.error.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    val dark = LocalDarkTheme.current

    BackHandler { /* consume back — app stays locked */ }

    val biometricAvailable = remember {
        viewModel.biometricEnabled &&
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val pinAvailable = viewModel.pinEnabled
    // Com biometria abre logo a impressão digital; "Usar PIN" passa ao teclado.
    var usePin by remember { mutableStateOf(!biometricAvailable) }

    fun triggerBiometric() {
        val activity = context as? FragmentActivity ?: return
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && pinAvailable) usePin = true
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear GoLink".tr())
                .setSubtitle("Desbloqueia os teus ficheiros encriptados".tr())
                .setNegativeButtonText(if (pinAvailable) "Usar PIN".tr() else "Cancelar".tr())
                .build(),
        )
    }

    LaunchedEffect(Unit) {
        if (biometricAvailable) triggerBiometric()
    }

    // PIN errado: abana os pontos e limpa.
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(error) {
        if (error != null) {
            scope.launch {
                for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(45))
            }
            delay(900)
            pin = ""
            viewModel.consumeError()
        }
    }

    fun onDigit(d: String) {
        if (pin.length < 4 && error == null) {
            pin += d
            if (pin.length == 4) viewModel.verifyPin(pin)
        }
    }

    // Fundo: desfoque no Android 12+ (AppNavHost); antes disso, opaco para não se ver nada.
    val scrim = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Color.Black.copy(alpha = if (dark) 0.55f else 0.35f)
    else MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim)
            // Bloqueia toques no conteúdo por trás.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        ) {
            Box(Modifier.navigationBarsPadding()) {
                // Fechar: manda a app para segundo plano (continua bloqueada).
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 14.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { (context as? Activity)?.moveTaskToBack(true) },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar".tr(), modifier = Modifier.size(18.dp))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 18.dp),
                ) {
                    // Logótipo
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    ) { Text("go", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    Spacer(Modifier.height(6.dp))
                    Text("GoLink", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))

                    AnimatedContent(
                        targetState = usePin,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "lock-mode",
                    ) { pinMode ->
                        if (pinMode) {
                            PinPane(
                                pin = pin,
                                error = error,
                                shakeX = shake.value,
                                showBiometric = biometricAvailable,
                                onDigit = ::onDigit,
                                onDelete = { if (pin.isNotEmpty() && error == null) pin = pin.dropLast(1) },
                                onBiometric = ::triggerBiometric,
                            )
                        } else {
                            BiometricPane(
                                showUsePin = pinAvailable,
                                onFingerprint = ::triggerBiometric,
                                onUsePin = { usePin = true },
                            )
                        }
                    }

                    // Indicador da folha
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .width(96.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Normal)
        Spacer(Modifier.height(6.dp))
        Text(
            "Desbloqueia os teus ficheiros encriptados".tr(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinPane(
    pin: String,
    error: String?,
    shakeX: Float,
    showBiometric: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Header("Introduz o PIN".tr())
        Spacer(Modifier.height(22.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.offset { IntOffset(shakeX.dp.roundToPx(), 0) },
        ) {
            val ring = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            repeat(4) { i ->
                val filled = i < pin.length
                val c = when {
                    error != null -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> ring
                }
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (filled || error != null) c else Color.Transparent)
                        .border(1.5.dp, c, CircleShape),
                )
            }
        }
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { d -> DigitKey(d) { onDigit(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (showBiometric) {
                    DarkKey(onClick = onBiometric) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = "Biometria".tr(), modifier = Modifier.size(24.dp))
                    }
                } else {
                    Spacer(Modifier.size(KeySize))
                }
                DigitKey("0") { onDigit("0") }
                DarkKey(onClick = onDelete) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Apagar".tr(), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun BiometricPane(showUsePin: Boolean, onFingerprint: () -> Unit, onUsePin: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Header("Desbloquear GoLink".tr())
        Spacer(Modifier.height(72.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f))
                .border(2.dp, primary.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onFingerprint),
        ) {
            Icon(Icons.Outlined.Fingerprint, contentDescription = "Biometria".tr(), tint = primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Toca no sensor de impressão digital".tr(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(80.dp))
        if (showUsePin) {
            Text(
                "Usar PIN".tr(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onUsePin)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        } else {
            Spacer(Modifier.height(44.dp))
        }
    }
}

private val KeySize = 64.dp

@Composable
private fun DigitKey(digit: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(KeySize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        Text(digit, fontSize = 24.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DarkKey(onClick: () -> Unit, content: @Composable () -> Unit) {
    val dark = LocalDarkTheme.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(KeySize)
            .clip(CircleShape)
            .background(if (dark) Color(0xFFE5E7EB) else Color(0xFF3B403E))
            .clickable(onClick = onClick),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (dark) Color(0xFF111111) else Color.White,
        ) { content() }
    }
}
