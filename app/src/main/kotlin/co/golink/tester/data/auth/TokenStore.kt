package co.golink.tester.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<StoredToken> = _state.asStateFlow()

    val token: String? get() = state.value.token

    fun setPendingToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).putBoolean(KEY_OTP_VALIDATED, false).apply()
        _state.value = StoredToken(token, otpValidated = false)
    }

    fun markOtpValidated() {
        prefs.edit().putBoolean(KEY_OTP_VALIDATED, true).apply()
        _state.value = _state.value.copy(otpValidated = true)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = StoredToken(null, false)
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
    }
}

data class StoredToken(val token: String?, val otpValidated: Boolean)
