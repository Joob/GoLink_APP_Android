package co.golink.tester.data.settings

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSecurityPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("app_security", Context.MODE_PRIVATE)

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "app_security_pin",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric_enabled", false)
        set(value) = prefs.edit { putBoolean("biometric_enabled", value) }

    var pinEnabled: Boolean
        get() = prefs.getBoolean("pin_enabled", false)
        set(value) = prefs.edit { putBoolean("pin_enabled", value) }

    val hasPinSet: Boolean
        get() = encryptedPrefs.getString("pin_hash", null) != null

    fun setPinHash(pin: String) {
        encryptedPrefs.edit().putString("pin_hash", sha256(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = encryptedPrefs.getString("pin_hash", null) ?: return false
        return sha256(pin) == stored
    }

    fun clearPin() {
        encryptedPrefs.edit().remove("pin_hash").apply()
        pinEnabled = false
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
