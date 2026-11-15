package co.golink.tester.ui.screens.pass.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import co.golink.tester.MainActivity
import co.golink.tester.data.pass.PassBiometricStore
import co.golink.tester.data.pass.PassItem
import co.golink.tester.data.pass.PassPrefs
import co.golink.tester.data.pass.PassRepository
import co.golink.tester.data.pass.autofill.PassAutofillMatch
import co.golink.tester.data.pass.autofill.PassAutofillParser
import co.golink.tester.data.pass.autofill.PassAutofillResponses
import co.golink.tester.data.pass.autofill.PassAutofillTarget
import co.golink.tester.data.settings.AppLockManager
import co.golink.tester.ui.i18n.pt
import co.golink.tester.ui.screens.pass.LocalPassPalette
import co.golink.tester.ui.screens.pass.PassAvatar
import co.golink.tester.ui.screens.pass.PassBlue
import co.golink.tester.ui.screens.pass.PassCard
import co.golink.tester.ui.screens.pass.PassRose
import co.golink.tester.ui.screens.pass.hostLabel
import co.golink.tester.ui.screens.pass.passBiometricConfirm
import co.golink.tester.ui.screens.pass.passBiometricPrompt
import co.golink.tester.ui.screens.pass.passQuotaMessage
import co.golink.tester.ui.screens.pass.passPalette
import co.golink.tester.ui.theme.VueFileManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ecrãs do preenchimento automático, abertos pelo sistema por cima da outra app:
 *  - unlock: desbloquear o Pass e devolver as sugestões;
 *  - pick: "Outros logins…" (com "lembrar para esta app");
 *  - confirm: reprompt antes de entregar um login;
 *  - save: guardar/atualizar o login que o utilizador acabou de escrever.
 */
@AndroidEntryPoint
class PassAutofillActivity : FragmentActivity() {
    companion object {
        const val EXTRA_MODE = "pass_af_mode"
        const val EXTRA_PACKAGE = "pass_af_pkg"
        const val EXTRA_DOMAIN = "pass_af_domain"
        const val EXTRA_USER_IDS = "pass_af_user_ids"
        const val EXTRA_PASS_IDS = "pass_af_pass_ids"
        const val EXTRA_ITEM = "pass_af_item"
        const val EXTRA_USERNAME = "pass_af_username"
        const val EXTRA_PASSWORD = "pass_af_password"
        const val MODE_UNLOCK = "unlock"
        const val MODE_PICK = "pick"
        const val MODE_CONFIRM = "confirm"
        const val MODE_SAVE = "save"
        const val MODE_NONE = "none"
    }

    @Inject lateinit var repo: PassRepository
    @Inject lateinit var bio: PassBiometricStore
    @Inject lateinit var prefs: PassPrefs
    @Inject lateinit var appLock: AppLockManager

    private lateinit var mode: String
    private lateinit var target: PassAutofillTarget

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_NONE
        if (mode == MODE_NONE) {
            // Toque longo num chip do teclado: abrir a app.
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        if (prefs.blockScreens) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        target = PassAutofillTarget(
            packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty(),
            webDomain = intent.getStringExtra(EXTRA_DOMAIN),
            usernameIds = ids(EXTRA_USER_IDS),
            passwordIds = ids(EXTRA_PASS_IDS),
            usernameValue = intent.getStringExtra(EXTRA_USERNAME),
            passwordValue = intent.getStringExtra(EXTRA_PASSWORD),
        )
        setResult(Activity.RESULT_CANCELED)
        setContent {
            VueFileManagerTheme {
                CompositionLocalProvider(LocalPassPalette provides passPalette()) { Root() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun ids(key: String): List<AutofillId> =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableArrayListExtra(key, AutofillId::class.java)
        else intent.getParcelableArrayListExtra<Parcelable>(key)?.filterIsInstance<AutofillId>()).orEmpty()

    private fun finishWith(result: Parcelable) {
        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result))
        finish()
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Domínio (só de browsers conhecidos) ou nome da app + pacote (o nome pode ser imitado). */
    private val targetName: String get() = target.webDomain?.removePrefix("www.")
        ?: appLabel(target.packageName).let { if (it == target.packageName) it else "$it · ${target.packageName}" }

    /** "Outros logins…" à espera do reprompt (item, lembrar para esta app). */
    private var pendingPick: Pair<PassItem, Boolean>? = null

    private suspend fun completePick(item: PassItem, rememberApp: Boolean) {
        if (rememberApp) runCatching { repo.rememberApp(item.id, target.packageName) }
        finishWith(PassAutofillResponses.filled(this, target, repo.state.value.item(item.id) ?: item))
    }

    private fun needsReprompt() = repo.state.value.settings.reprompt && !repo.repromptValid()

    /** Login já guardado para o que se vai gravar (mesmo utilizador neste site/app). */
    private fun existingFor(): PassItem? {
        val hits = PassAutofillMatch.match(repo.state.value.items, target.packageName, target.webDomain)
            .filter { it.strength != PassAutofillMatch.Strength.Heuristic }.map { it.item }
        val user = target.usernameValue.orEmpty()
        return if (user.isNotEmpty()) hits.firstOrNull { it.login?.username.equals(user, ignoreCase = true) }
        else hits.singleOrNull()
    }

    private enum class Phase { Loading, Lock, Confirm, Pick, Save }

    /** O que fazer depois de o cofre estar aberto. null = já terminou. */
    private fun afterOpen(justUnlocked: Boolean): Phase? {
        val state = repo.state.value
        when (mode) {
            MODE_UNLOCK -> {
                finishWith(PassAutofillResponses.fill(this, target, state, needsReprompt(), null))
                return null
            }
            MODE_CONFIRM -> {
                val item = state.item(intent.getStringExtra(EXTRA_ITEM)) ?: run { finish(); return null }
                if (!justUnlocked && needsReprompt()) return Phase.Confirm
                finishWith(PassAutofillResponses.filled(this, target, item))
                return null
            }
            MODE_PICK -> return Phase.Pick
            MODE_SAVE -> {
                // Nada de novo (mesma password já guardada): sair calado.
                val same = PassAutofillMatch.match(state.items, target.packageName, target.webDomain).any {
                    it.item.login?.password == target.passwordValue &&
                        (target.usernameValue.isNullOrEmpty() || it.item.login?.username.equals(target.usernameValue, ignoreCase = true))
                }
                if (same || target.passwordValue.isNullOrEmpty()) { finish(); return null }
                return Phase.Save
            }
        }
        finish()
        return null
    }

    @Composable
    private fun Root() {
        val p = LocalPassPalette.current
        var phase by remember { mutableStateOf(Phase.Loading) }
        LaunchedEffect(Unit) {
            // lock_after já passado (o ticker não corre com o processo congelado): trancar já.
            repo.checkIdle()
            if (!repo.isOpen && !repo.state.value.locked) runCatching { repo.load() }
            phase = when {
                !repo.isOpen -> Phase.Lock
                // Cofre aberto mas a app bloqueada (PIN/biometria): confirmar antes de entregar.
                appLock.lockedForAutofill() -> Phase.Confirm
                else -> afterOpen(false) ?: return@LaunchedEffect
            }
        }
        Box(Modifier.fillMaxSize().background(p.bg).safeDrawingPadding().imePadding()) {
            when (phase) {
                Phase.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = PassBlue)
                Phase.Lock, Phase.Confirm -> LockView(confirm = phase == Phase.Confirm) {
                    appLock.grantAutofill()
                    repo.markReprompted()
                    repo.touch()
                    val pick = pendingPick
                    if (pick != null) {
                        pendingPick = null
                        lifecycleScope.launch { completePick(pick.first, pick.second) }
                    } else {
                        afterOpen(true)?.let { phase = it }
                    }
                }
                Phase.Pick -> PickView(onNeedsConfirm = { item, rememberApp ->
                    pendingPick = item to rememberApp
                    phase = Phase.Confirm
                })
                Phase.Save -> SaveView()
            }
        }
    }

    /** Desbloquear (ou confirmar) com biometria ou passphrase E2E. */
    @Composable
    private fun LockView(confirm: Boolean, onOk: () -> Unit) {
        val p = LocalPassPalette.current
        val scope = rememberCoroutineScope()
        var secret by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val bioOn = remember { bio.enabled }

        val tryBiometric: () -> Unit = {
            if (confirm) {
                passBiometricConfirm(bio, pt("reprompt_title"), onOk = onOk, onFail = {
                    if (bio.consumeInvalidated()) error = pt("biometric_changed")
                })
            } else {
                val cipher = bio.decryptCipher()
                if (cipher == null) {
                    if (bio.consumeInvalidated()) error = pt("biometric_changed")
                } else {
                    passBiometricPrompt(cipher, pt("biometric_prompt"), onOk = { c ->
                        busy = true
                        scope.launch {
                            val key = bio.open(c)
                            val ok = key != null && runCatching { repo.unlockWithKey(key) }.getOrDefault(false)
                            busy = false
                            if (ok) onOk() else {
                                if (key != null) bio.clear()
                                error = pt("unlock_wrong")
                            }
                        }
                    })
                }
            }
        }
        LaunchedEffect(Unit) { if (bioOn) tryBiometric() }

        val submit: () -> Unit = {
            if (secret.isNotBlank() && !busy) {
                busy = true
                error = null
                scope.launch {
                    val ok = runCatching { if (confirm) repo.verifySecret(secret) else repo.unlockWithSecret(secret) && repo.isOpen }.getOrDefault(false)
                    busy = false
                    if (ok) { secret = ""; onOk() } else error = if (!confirm && repo.state.value.failed) pt("error") else pt("unlock_wrong")
                }
            }
        }

        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            PassCard(Modifier.widthIn(max = 460.dp), padding = 24.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).background(p.accentSoft, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Lock, null, tint = PassBlue, modifier = Modifier.size(28.dp))
                    }
                    Text(if (confirm) pt("reprompt_title") else pt("af.unlock"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                    Text(targetName, fontSize = 13.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(16.dp))
                    if (bioOn) {
                        Button(
                            onClick = tryBiometric,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PassBlue),
                        ) {
                            Icon(Icons.Outlined.Fingerprint, null)
                            Spacer(Modifier.size(8.dp))
                            Text(pt("use_biometric"), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it; error = null },
                        placeholder = { Text(pt("unlock_placeholder")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        isError = error != null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) Text(error!!, color = PassRose, fontSize = 12.5.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = submit,
                        enabled = secret.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (bioOn) p.text else PassBlue),
                    ) { Text(if (busy) pt("unlocking") else pt("unlock"), fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { finish() }) { Text(pt("cancel"), color = p.muted) }
                }
            }
        }
    }

    /** "Outros logins…": qualquer login do cofre; os que correspondem vêm primeiro. */
    @Composable
    private fun PickView(onNeedsConfirm: (PassItem, Boolean) -> Unit) {
        val p = LocalPassPalette.current
        val scope = rememberCoroutineScope()
        val state by repo.state.collectAsState()
        var query by remember { mutableStateOf("") }
        // Browser sem domínio (ou conhecido mas sem assinatura verificada) não é "app": não
        // ligar o pacote do browser a um login nem oferecer "lembrar" (PassTrustedBrowsers.KNOWN).
        val isApp = target.webDomain == null && !PassAutofillParser.isTrustedBrowser(target.packageName)
        var remember by remember { mutableStateOf(isApp) }
        var busy by remember { mutableStateOf(false) }
        val hits = remember(state.items) { PassAutofillMatch.match(state.items, target.packageName, target.webDomain) }
        // Ordenação: também os heurísticos (não verificados); "lembrar" só fica de fora nos verificados.
        val matched = remember(hits) { hits.map { it.item.id }.toSet() }
        val verified = remember(hits) { hits.filter { it.strength != PassAutofillMatch.Strength.Heuristic }.map { it.item.id }.toSet() }
        val list = remember(state.items, query, matched) {
            val q = query.trim().lowercase()
            state.items.filter { it.type == "login" && !it.isTrashed && !it.locked }
                .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.login?.username.orEmpty().lowercase().contains(q) || it.login?.url.orEmpty().lowercase().contains(q) }
                .sortedWith(compareBy<PassItem> { it.id !in matched }.thenByDescending { it.favourite }.thenBy { it.name.lowercase() })
        }
        val choose: (PassItem) -> Unit = { item ->
            if (!busy) {
                val rememberApp = isApp && remember && item.id !in verified
                // Reprompt também aqui: sem ele, "Outros logins…" entregava valores sem confirmar.
                if (needsReprompt()) onNeedsConfirm(item, rememberApp)
                else {
                    busy = true
                    scope.launch { completePick(item, rememberApp) }
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text(pt("af.pick_title"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.text)
                Text(pt("af.pick_sub", "target" to targetName), fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(pt("search")) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (isApp) {
                    Row(Modifier.fillMaxWidth().clickable { remember = !remember }.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = remember, onCheckedChange = { remember = it })
                        Text(pt("af.remember"), fontSize = 14.sp, color = p.text)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                if (list.isEmpty()) item {
                    Text(pt("af.no_match"), color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
                items(list, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).background(p.card, RoundedCornerShape(14.dp)).clickable { choose(item) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PassAvatar(item, 38.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(item.name.ifEmpty { "—" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = listOf(item.login?.username.orEmpty(), hostLabel(item.login?.url)).filter { it.isNotEmpty() }.joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)) { Text(pt("cancel"), color = p.muted) }
        }
    }

    /** "Guardar no Pass?" / "Atualizar a password?" depois de um login noutra app. */
    @Composable
    private fun SaveView() {
        val p = LocalPassPalette.current
        val scope = rememberCoroutineScope()
        val existing = remember { existingFor() }
        var name by remember { mutableStateOf(existing?.name ?: if (target.webDomain != null) hostLabel(target.webDomain) else appLabel(target.packageName)) }
        var username by remember { mutableStateOf(target.usernameValue.orEmpty()) }
        var show by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        val password = target.passwordValue.orEmpty()

        val save: (Boolean) -> Unit = { update ->
            if (!busy) {
                busy = true
                scope.launch {
                    val err = runCatching {
                        repo.saveFromAutofill(
                            existingId = if (update) existing?.id else null,
                            name = name.trim().ifEmpty { targetName },
                            url = target.webDomain?.let { "https://" + it.removePrefix("www.") }.orEmpty(),
                            username = username.trim(),
                            password = password,
                            packageName = target.packageName.takeIf { target.webDomain == null && !PassAutofillParser.isTrustedBrowser(it) },
                        )
                    }.exceptionOrNull()
                    val ok = err == null
                    busy = false
                    Toast.makeText(this@PassAutofillActivity, if (ok) pt("saved") else passQuotaMessage(err) ?: pt("error"), Toast.LENGTH_SHORT).show()
                    if (ok) finish()
                }
            }
        }

        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            PassCard(Modifier.widthIn(max = 480.dp), padding = 22.dp) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(p.accentSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Lock, null, tint = PassBlue)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(if (existing != null) pt("af.update_title") else pt("af.save_title"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.text)
                            Text(targetName, fontSize = 13.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        if (existing != null) pt("af.update_text", "name" to existing.name.ifEmpty { targetName }) else pt("af.save_text"),
                        fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 12.dp),
                    )
                    if (existing == null) {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it }, label = { Text(pt("f.name")) }, singleLine = true,
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        )
                    }
                    OutlinedTextField(
                        value = username, onValueChange = { username = it }, label = { Text(pt("f.username")) }, singleLine = true,
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = {}, readOnly = true, label = { Text(pt("f.password")) }, singleLine = true,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) }
                        },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { save(existing != null) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PassBlue),
                    ) { Text(if (existing != null) pt("af.update") else pt("save"), fontWeight = FontWeight.SemiBold) }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (existing != null) {
                            OutlinedButton(onClick = { save(false) }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Text(pt("af.save_new"), color = p.text)
                            }
                        }
                        TextButton(onClick = { finish() }, modifier = Modifier.weight(1f)) { Text(pt("af.not_now"), color = p.muted) }
                    }
                }
            }
        }
    }
}
