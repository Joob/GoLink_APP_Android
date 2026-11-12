package co.golink.tester.ui.screens.pass

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.pass.PassBiometricStore
import co.golink.tester.data.pass.PassClipboard
import co.golink.tester.data.pass.PassConflictException
import co.golink.tester.data.pass.PassCrypto
import co.golink.tester.data.pass.PassDraft
import co.golink.tester.data.pass.PassHttpException
import co.golink.tester.data.pass.PassImportException
import co.golink.tester.data.pass.PassImporters
import co.golink.tester.data.pass.PassItem
import co.golink.tester.data.pass.PassLimitException
import co.golink.tester.data.pass.PassPrefs
import co.golink.tester.data.pass.PassRepository
import co.golink.tester.data.pass.PassSettings
import co.golink.tester.data.pass.PassState
import co.golink.tester.ui.i18n.pt
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Ficheiro de importação lido: rascunhos prontos, ou export cifrado à espera da password. */
sealed interface PassImportFile {
    data class Ready(val drafts: List<PassDraft>) : PassImportFile
    data class NeedsPassword(val json: JsonObject) : PassImportFile
}

/** 422 de quota do cofre (pelo `code`) → mensagem própria; null se não for. */
fun passQuotaMessage(e: Throwable?): String? = when ((e as? PassHttpException)?.takeIf { it.status == 422 }?.code) {
    "pass_quota" -> pt("quota_full")
    "pass_item_too_large" -> pt("item_too_large")
    else -> null
}

@HiltViewModel
class PassViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: PassRepository,
    private val clipboard: PassClipboard,
    val prefs: PassPrefs,
    val bio: PassBiometricStore,
    private val lockManager: co.golink.tester.data.settings.AppLockManager,
) : ViewModel() {
    val state: StateFlow<PassState> = repo.state

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _bioEnabled = MutableStateFlow(bio.enabled)
    val bioEnabled: StateFlow<Boolean> = _bioEnabled.asStateFlow()
    private val _blockScreens = MutableStateFlow(prefs.blockScreens)
    val blockScreens: StateFlow<Boolean> = _blockScreens.asStateFlow()

    /** Passwords geradas nesta sessão (só em memória). */
    val recentGenerated = MutableStateFlow<List<String>>(emptyList())

    fun toast(text: String) { _toast.value = text }
    fun consumeToast() { _toast.value = null }

    fun load() = viewModelScope.launch { repo.load() }
    fun retry() = viewModelScope.launch { repo.load(force = true) }
    suspend fun sync() = repo.sync()
    fun touch() = repo.touch()
    fun checkIdle() = repo.checkIdle()
    fun lock() = repo.lock()
    fun hasKey() = repo.hasKey

    /** Câmara / seletor de imagens: voltar deles não pede o PIN da app. */
    fun expectExternal() = lockManager.expectExternalReturn()

    private fun fail(e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        toast(if (e is PassConflictException) pt("conflict") else passQuotaMessage(e) ?: pt("error"))
    }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { fail(e) }
    }

    // ---- Desbloquear ---------------------------------------------------------

    suspend fun unlockWithSecret(secret: String): Boolean = runCatching { repo.unlockWithSecret(secret) }.getOrDefault(false)

    /** Depois do BiometricPrompt: abre a K e o cofre. */
    fun unlockWithBiometric(cipher: Cipher, onFail: () -> Unit) = viewModelScope.launch {
        val key = bio.open(cipher)
        val ok = key != null && runCatching { repo.unlockWithKey(key) }.getOrDefault(false)
        if (!ok) {
            // A K guardada já não abre o cofre (cofre recriado): desliga e pede a passphrase.
            if (key != null) { bio.clear(); _bioEnabled.value = false }
            onFail()
        }
    }

    fun enableBiometric(cipher: Cipher) {
        val key = repo.currentKey() ?: return
        runCatching { bio.store(cipher, key) }.onSuccess { _bioEnabled.value = true }.onFailure { toast(pt("error")) }
        key.fill(0)
    }

    fun disableBiometric() { bio.clear(); _bioEnabled.value = false }

    fun setBlockScreens(on: Boolean) { prefs.blockScreens = on; _blockScreens.value = on }

    // Reprompt (Ask again for passwords)
    fun needsReprompt(): Boolean = state.value.settings.reprompt && !repo.repromptValid()
    fun markReprompted() = repo.markReprompted()
    suspend fun verifySecret(secret: String): Boolean = runCatching { repo.verifySecret(secret) }.getOrDefault(false)

    // ---- Clipboard -----------------------------------------------------------

    fun copy(label: String, value: String, sensitive: Boolean = true) {
        val clear = state.value.settings.clipboardClear
        if (!clipboard.copy(label, value, sensitive, clear)) { toast(pt("copy_failed")); return }
        toast(if (sensitive && clear > 0) pt("copied_clear", "what" to label, "s" to clear) else pt("copied", "what" to label))
    }

    // ---- Itens ---------------------------------------------------------------

    suspend fun save(id: String?, payload: JsonObject, baseRevision: Int?): PassItem? = try {
        _busy.value = true
        repo.save(id, payload, baseRevision).also { toast(pt("saved")) }
    } catch (e: Exception) {
        fail(e); null
    } finally {
        _busy.value = false
    }

    fun toggleFavourite(id: String) = run { repo.toggleFavourite(id) }
    fun markFixed(id: String, kind: String) = run { repo.markFixed(id, kind) }
    fun trash(id: String) = run { repo.trash(id); toast(pt("moved_to_trash")) }
    fun restore(id: String) = run { repo.restore(id); toast(pt("restored")) }
    fun deleteForever(id: String) = run { repo.deleteForever(id) }
    fun emptyTrash() = run { repo.emptyTrash() }
    fun recheckHealth(fresh: Boolean = false) = run { repo.runHealth(fresh) }

    suspend fun addFolder(name: String) = try { repo.addFolder(name) } catch (e: Exception) { fail(e); null }
    fun renameFolder(id: String, name: String) = run { repo.renameFolder(id, name) }
    fun deleteFolder(id: String) = run { repo.deleteFolder(id) }
    fun saveSettings(s: PassSettings) = run { repo.saveSettings(s) }

    // ---- Importar / exportar -------------------------------------------------

    /** Lê o ficheiro escolhido; null + toast se não der. */
    suspend fun readImport(uri: Uri, source: String): PassImportFile? = withContext(Dispatchers.IO) {
        try {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
            if (size > 20L * 1024 * 1024) { toast(pt("import_too_big")); return@withContext null }
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@withContext null
            val trimmed = text.trim()
            val result = if (trimmed.startsWith("{")) {
                val json = Json.parseToJsonElement(trimmed) as JsonObject
                if ((json["format"] as? JsonPrimitive)?.contentOrNull == "golink-pass") return@withContext PassImportFile.NeedsPassword(json)
                PassImportFile.Ready(PassImporters.importBitwardenJson(json))
            } else {
                PassImportFile.Ready(PassImporters.importCsv(text, source))
            }
            if (result is PassImportFile.Ready && result.drafts.isEmpty()) { toast(pt("import_empty")); null } else result
        } catch (e: PassImporters.EncryptedBitwarden) {
            toast(pt("import_bw_encrypted")); null
        } catch (e: Exception) {
            toast(pt("import_failed")); null
        }
    }

    suspend fun decryptImport(json: JsonObject, password: String): List<PassDraft>? = withContext(Dispatchers.Default) {
        runCatching { PassCrypto.decryptExport(json, password) }.getOrNull()
    }

    suspend fun import(drafts: List<PassDraft>): Boolean {
        _busy.value = true
        return try {
            val n = repo.import(drafts)
            toast(pt("imported", "n" to n)); true
        } catch (e: PassLimitException) {
            toast(if (e.imported > 0) pt("import_partial", "done" to e.imported, "left" to drafts.size - e.imported) else pt("import_limit")); false
        } catch (e: PassImportException) {
            toast(passQuotaMessage(e.cause) ?: pt("import_partial", "done" to e.imported, "left" to drafts.size - e.imported)); false
        } catch (e: Exception) {
            fail(e); false
        } finally {
            _busy.value = false
        }
    }

    /**
     * Password do export à espera do seletor SAF. Fica no ViewModel (sobrevive à rotação/tema),
     * nunca em disco nem no SavedState; se o processo morrer, o export aborta.
     */
    private var pendingExportPassword: String? = null

    fun prepareExport(password: String) { pendingExportPassword = password }

    /** Export cifrado para o ficheiro escolhido (SAF CreateDocument). uri null = cancelado. */
    fun exportEncrypted(uri: Uri?) = viewModelScope.launch {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri == null) return@launch
        if (password == null || password.length < 8) {
            // Nunca exportar com password vazia/curta (ex.: processo recriado no seletor): apagar o ficheiro vazio.
            withContext(Dispatchers.IO) { runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) } }
            toast(pt("error"))
            return@launch
        }
        _busy.value = true
        try {
            val text = withContext(Dispatchers.Default) { PassCrypto.encryptExport(repo.portable(), password) }
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } }
            toast(pt("saved"))
        } catch (e: Exception) {
            fail(e)
        } finally {
            _busy.value = false
        }
    }

    fun addGenerated(pw: String) {
        recentGenerated.value = (listOf(pw) + recentGenerated.value.filter { it != pw }).take(5)
    }
}
