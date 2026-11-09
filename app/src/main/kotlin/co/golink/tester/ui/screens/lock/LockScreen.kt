package co.golink.tester.ui.screens.lock

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import co.golink.tester.data.settings.AppLockBiometricKey
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bloqueio da app em folha a subir de baixo (por cima do conteúdo desfocado):
 * teclado redondo para o PIN ou ecrã de impressão digital, com "Usar PIN".
 * A folha fica sempre acima da barra de navegação (insets da janela do bloqueio).
 */
@Composable
fun LockScreen(
    viewModel: LockViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val error by viewModel.error.collectAsStateWithLifecycle()
    val lockoutSeconds by viewModel.lockoutSeconds.collectAsStateWithLifecycle()
    val verifying by viewModel.verifying.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    val dark = LocalDarkTheme.current

    BackHandler { /* consume back — app stays locked */ }

    // Dentro do Dialog o LocalContext não é a atividade: subir pelos ContextWrapper.
    val activity = remember(context) { context.findActivity() }
    val biometricAvailable = remember { viewModel.biometricUsable }
    val pinAvailable = viewModel.pinEnabled
    // Com biometria abre logo a impressão digital; "Usar PIN" passa ao teclado.
    var usePin by remember { mutableStateOf(!biometricAvailable) }
    var promptOpen by remember { mutableStateOf(false) }
    var bioNotice by remember { mutableStateOf<String?>(null) }
    // Sem PIN: o código do telemóvel serve de recurso (bloqueio da impressão digital).
    val allowDeviceCredential = !pinAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun triggerBiometric() {
        val fa = activity as? FragmentActivity ?: return
        if (promptOpen) return
        // Biometria com CryptoObject — uma impressão digital
        // acabada de inscrever invalida a chave e passa a pedir o PIN.
        var keyState = viewModel.biometricKeyState()
        if (keyState is AppLockBiometricKey.State.Invalidated && !pinAvailable && !allowDeviceCredential) {
            // Android < 11 sem PIN: não há outro meio; chave nova e biometria normal (como antes).
            keyState = viewModel.resetBiometricKey()
        }
        val deviceOnly = keyState is AppLockBiometricKey.State.Invalidated
        if (deviceOnly && pinAvailable) {
            usePin = true
            bioNotice = "A biometria do aparelho mudou. Usa o PIN para desbloquear.".tr()
            return
        }
        promptOpen = true
        bioNotice = null
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear GoLink".tr())
            .setSubtitle(
                if (deviceOnly) "A biometria do aparelho mudou. Confirma com o código do telemóvel.".tr()
                else "Desbloqueia os teus ficheiros encriptados".tr(),
            )
        if (deviceOnly) {
            info.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else if (allowDeviceCredential) {
            info.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            info.setNegativeButtonText(if (pinAvailable) "Usar PIN".tr() else "Cancelar".tr())
        }
        val crypto = (keyState as? AppLockBiometricKey.State.Ready)?.let { BiometricPrompt.CryptoObject(it.cipher) }
        val prompt = BiometricPrompt(
            fa,
            ContextCompat.getMainExecutor(fa),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // promptOpen fica true: a folha não volta a subir enquanto o bloqueio sai.
                    if (deviceOnly) {
                        viewModel.onDeviceCredentialSuccess()
                    } else if (!viewModel.onBiometricSuccess(result.cryptoObject?.cipher, usedCrypto = crypto != null)) {
                        promptOpen = false
                        if (pinAvailable) usePin = true
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptOpen = false
                    when (errorCode) {
                        // Cancelado pelo sistema (app saiu) ou pelo utilizador: volta a abrir no ON_RESUME / no ícone.
                        BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED -> Unit
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> if (pinAvailable) usePin = true
                        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            if (pinAvailable) usePin = true else bioNotice = errString.toString()
                        else -> if (pinAvailable) usePin = true else bioNotice = errString.toString()
                    }
                }
            },
        )
        if (crypto != null) prompt.authenticate(info.build(), crypto) else prompt.authenticate(info.build())
    }

    // Abre a impressão digital ao mostrar e sempre que a app volta (o sistema
    // cancela o pedido quando a app sai do ecrã).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (biometricAvailable && !usePin) triggerBiometric()
    }

    // PIN errado: abana os pontos e limpa.
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(error) {
        if (error != null) {
            scope.launch {
                for (x in listOf(12f, -10f, 7f, -4f, 2f, 0f)) shake.animateTo(x, tween(80, easing = EaseOutSoft))
            }
            delay(1100)
            pin = ""
            viewModel.consumeError()
        }
    }

    val inputBlocked = error != null || verifying || lockoutSeconds > 0
    fun onDigit(d: String) {
        if (pin.length < 4 && !inputBlocked) {
            pin += d
            if (pin.length == 4) viewModel.submitPin(pin)
        }
    }
    val notice = when {
        error != null -> error
        lockoutSeconds > 60 -> "Demasiadas tentativas. Tenta daqui a %d min.".tr().format((lockoutSeconds + 59) / 60)
        lockoutSeconds > 0 -> "Demasiadas tentativas. Aguarda %d s.".tr().format(lockoutSeconds)
        else -> bioNotice
    }

    // Fundo sempre nosso: captura da janela principal, reduzida e desfocada em
    // software; até chegar (ou se falhar) o fundo é opaco. Não depende do desfoque
    // do sistema (raio fraco em ecrãs densos, e há aparelhos que o anunciam mas
    // não o desenham ou o cortam em transições — deixava o conteúdo à vista).
    var backdrop by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        if (backdrop != null) return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        // Espera a camada opaca da janela principal sair (2.º frame do bloqueio) e redesenhar.
        repeat(3) { androidx.compose.runtime.withFrameNanos { } }
        delay(120)
        backdrop = LockBackdrop.capture(act)
    }
    val scrimTint = Color.Black.copy(alpha = if (dark) 0.55f else 0.35f)
    val opaque = MaterialTheme.colorScheme.background
    val rise = remember { Animatable(1f) }
    // Com a impressão digital do sistema aberta, a nossa folha sai (senão eram duas folhas).
    LaunchedEffect(promptOpen) {
        rise.animateTo(if (promptOpen) 1f else 0f, tween(if (promptOpen) MODE_OUT_MS else SHEET_RISE_MS, easing = EaseOutSoft))
    }

    Box(Modifier.fillMaxSize()) {
        val bd = backdrop
        if (bd != null) {
            androidx.compose.foundation.Image(
                bitmap = bd,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(opaque))
        }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (bd != null) scrimTint else Color.Transparent)
            // Bloqueia toques no conteúdo por trás.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Ecrãs baixos: teclas mais pequenas para a folha caber acima da barra de navegação.
        val keySize = when {
            maxHeight < 620.dp -> 58.dp
            maxHeight < 720.dp -> 64.dp
            else -> 72.dp
        }
        Surface(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .graphicsLayer {
                    translationY = size.height * rise.value
                    alpha = 1f - rise.value * 0.6f
                },
        ) {
            // A barra de navegação (gestos ou botões) fica livre por baixo da folha.
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom)))) {
                // Fechar: manda a app para segundo plano (continua bloqueada).
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { activity?.moveTaskToBack(true) },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar".tr(), modifier = Modifier.size(18.dp))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp),
                ) {
                    // Pega da folha
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                    )
                    Spacer(Modifier.height(18.dp))
                    // Logótipo
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    ) { Text("go", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                    Spacer(Modifier.height(16.dp))

                    AnimatedContent(
                        targetState = usePin,
                        transitionSpec = {
                            (fadeIn(tween(MODE_IN_MS, delayMillis = 90, easing = EaseOutSoft)) +
                                slideInVertically(tween(MODE_IN_MS, delayMillis = 90, easing = EaseOutSoft)) { it / 14 }) togetherWith
                                fadeOut(tween(MODE_OUT_MS)) using
                                SizeTransform(clip = false) { _, _ -> tween(MODE_IN_MS, easing = EaseOutSoft) }
                        },
                        label = "lock-mode",
                    ) { pinMode ->
                        if (pinMode) {
                            PinPane(
                                pin = pin,
                                error = notice,
                                shakeX = { shake.value },
                                keySize = keySize,
                                showBiometric = biometricAvailable,
                                onDigit = ::onDigit,
                                onDelete = { if (pin.isNotEmpty() && !inputBlocked) pin = pin.dropLast(1) },
                                onBiometric = ::triggerBiometric,
                            )
                        } else {
                            BiometricPane(
                                notice = bioNotice,
                                showUsePin = pinAvailable,
                                onFingerprint = ::triggerBiometric,
                                onUsePin = { usePin = true },
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

// Movimento calmo: a folha sobe devagar e assenta sem ressalto.
private const val SHEET_RISE_MS = 620
private const val MODE_IN_MS = 460
private const val MODE_OUT_MS = 220
private val EaseOutSoft = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
private fun Header(title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Desbloqueia os teus ficheiros encriptados".tr(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinPane(
    pin: String,
    error: String?,
    shakeX: () -> Float,
    keySize: Dp,
    showBiometric: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit,
) {
    val gap = if (keySize < 64.dp) 18.dp else 24.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Header("Introduz o PIN".tr())
        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(16.dp).offset { IntOffset(shakeX().dp.roundToPx(), 0) },
        ) {
            repeat(4) { i -> PinDot(filled = i < pin.length, error = error != null) }
        }
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(visible = error != null, enter = fadeIn(tween(240)), exit = fadeOut(tween(240))) {
                Text(error.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (keySize < 64.dp) 10.dp else 14.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { d -> DigitKey(d, keySize) { onDigit(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (showBiometric) {
                    GhostKey(keySize, onClick = onBiometric) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = "Biometria".tr(), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                } else {
                    Spacer(Modifier.size(keySize))
                }
                DigitKey("0", keySize) { onDigit("0") }
                GhostKey(keySize, onClick = onDelete) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Apagar".tr(), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Ponto do PIN: enche e cresce devagar; vermelho no erro. */
@Composable
private fun PinDot(filled: Boolean, error: Boolean) {
    val ring = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val target = when {
        error -> MaterialTheme.colorScheme.error
        filled -> MaterialTheme.colorScheme.primary
        else -> ring
    }
    val color by animateColorAsState(target, tween(280), label = "dot-color")
    val fill by animateFloatAsState(if (filled || error) 1f else 0f, tween(260, easing = EaseOutSoft), label = "dot-fill")
    val size by animateDpAsState(if (filled) 14.dp else 12.dp, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "dot-size")
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, color, CircleShape)
            .padding(1.5.dp)
            .graphicsLayer { scaleX = fill; scaleY = fill }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun BiometricPane(notice: String?, showUsePin: Boolean, onFingerprint: () -> Unit, onUsePin: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    // Halo a respirar devagar à volta do sensor.
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "fp-pulse")
    val halo by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "fp-halo",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Header("Desbloquear GoLink".tr())
        Spacer(Modifier.height(48.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
            Box(
                Modifier
                    .size(108.dp)
                    .graphicsLayer { scaleX = halo; scaleY = halo; alpha = 1.6f - halo }
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.10f)),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.14f))
                    .clickable(onClick = onFingerprint),
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = "Biometria".tr(), tint = primary, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        if (notice != null) {
            Text(notice, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        } else {
            Text("Toca no sensor de impressão digital".tr(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(48.dp))
        if (showUsePin) {
            Text(
                "Usar PIN".tr(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onUsePin)
                    .padding(horizontal = 26.dp, vertical = 13.dp),
            )
        } else {
            Spacer(Modifier.height(46.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Escala suave ao carregar (mola lenta, sem ressalto brusco). */
@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "key-press",
    )
    return scale
}

@Composable
private fun DigitKey(digit: String, size: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = MaterialTheme.colorScheme.surfaceVariant
    val bg by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f).compositeOver(base) else base,
        tween(if (pressed) 90 else 360),
        label = "key-bg",
    )
    val scale = pressScale(interaction)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Text(digit, fontSize = if (size < 64.dp) 24.sp else 28.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Biometria / apagar: sem fundo, só o ícone (mais leve que as teclas dos números). */
@Composable
private fun GhostKey(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        tween(if (pressed) 90 else 360),
        label = "ghost-bg",
    )
    val scale = pressScale(interaction)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) { content() }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
