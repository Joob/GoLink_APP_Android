package co.golink.tester.data.auth

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    // AES-GCM com chave da Keystore (EncryptedPrefs): migra das
    // EncryptedSharedPreferences antigas sem terminar a sessão; se a Keystore
    // falhar, nada crasha (no pior caso arranca sem sessão).
    private val prefs: SharedPreferences = co.golink.tester.data.settings.EncryptedPrefs.create(context, FILE_NAME)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<StoredToken> = _state.asStateFlow()

    val token: String? get() = state.value.token

    /**
     * Corre em todos os fins de sessão (logout, 401 de revogação, conta
     * suspensa, eliminação, ecrã de erro), na thread de quem limpa e antes de
     * a nova sessão poder entrar. Ver [SessionTerminator].
     */
    private var onCleared: ((previousToken: String) -> Unit)? = null
    private var missedClear: String? = null

    /** Um fim de sessão anterior ao registo (arranque) é entregue logo aqui. */
    fun setOnCleared(callback: (previousToken: String) -> Unit) {
        val missed = synchronized(this) {
            onCleared = callback
            missedClear.also { missedClear = null }
        }
        if (missed != null) runCatching { callback(missed) }
    }

    /** Último /api/user da sessão: permite abrir a app sem esperar pela rede. */
    val cachedUser: String? get() = prefs.getString(KEY_USER, null)

    /** Ignorado se a sessão mudou entretanto: um pedido atrasado não repõe dados de outra conta. */
    fun saveCachedUser(forToken: String, user: String) {
        if (token == forToken) prefs.edit().putString(KEY_USER, user).apply()
    }

    fun setPendingToken(token: String) = setToken(token, otpValidated = false)

    /**
     * Grava o token e o estado do OTP de uma só vez (um edit, uma emissão).
     * Com o passkey eram duas emissões e o gate podia passar por OtpRequired.
     */
    fun setToken(token: String, otpValidated: Boolean) {
        prefs.edit().putString(KEY_TOKEN, token).putBoolean(KEY_OTP_VALIDATED, otpValidated).remove(KEY_USER).apply()
        _state.value = StoredToken(token, otpValidated)
    }

    fun markOtpValidated() {
        prefs.edit().putBoolean(KEY_OTP_VALIDATED, true).apply()
        _state.value = _state.value.copy(otpValidated = true)
    }

    /** Send the session back to the OTP step, keeping the (pending) token. */
    fun markOtpPending() {
        prefs.edit().putBoolean(KEY_OTP_VALIDATED, false).apply()
        _state.value = _state.value.copy(otpValidated = false)
    }

    fun clear() {
        var callback: ((String) -> Unit)? = null
        val previous = synchronized(this) {
            val old = _state.value.token
            // Só a sessão: os tokens por revogar no servidor ficam (ver LogoutRevokeWorker).
            prefs.edit().remove(KEY_TOKEN).remove(KEY_OTP_VALIDATED).remove(KEY_USER).apply()
            _state.value = StoredToken(null, false)
            callback = onCleared
            if (old != null && callback == null) missedClear = old
            old
        }
        if (previous != null) callback?.let { cb -> runCatching { cb(previous) } }
    }

    // ---- Tokens a revogar no servidor quando houver rede --------------------

    fun addPendingRevoke(token: String) = synchronized(this) {
        val set = prefs.getStringSet(KEY_REVOKE, emptySet()).orEmpty().toMutableSet()
        set.add(token)
        prefs.edit().putStringSet(KEY_REVOKE, set).apply()
    }

    fun pendingRevokes(): Set<String> = prefs.getStringSet(KEY_REVOKE, emptySet()).orEmpty().toSet()

    fun removePendingRevoke(token: String) = synchronized(this) {
        val set = prefs.getStringSet(KEY_REVOKE, emptySet()).orEmpty().toMutableSet()
        if (set.remove(token)) prefs.edit().putStringSet(KEY_REVOKE, set).apply()
    }

    private fun read(): StoredToken {
        val token = prefs.getString(KEY_TOKEN, null)
        val validated = prefs.getBoolean(KEY_OTP_VALIDATED, false)
        return StoredToken(token, validated)
    }

    companion object {
        private const val FILE_NAME = "auth_prefs"
        private const val KEY_TOKEN = "token"
        private const val KEY_OTP_VALIDATED = "otp_validated"
        private const val KEY_USER = "cached_user"
        private const val KEY_REVOKE = "revoke_pending"
    }
}

data class StoredToken(val token: String?, val otpValidated: Boolean)
