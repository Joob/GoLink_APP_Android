package co.golink.tester.data.settings

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSecurityPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("app_security", Context.MODE_PRIVATE)

    // Keystore + Tink: caro a criar; aquecido em IO no arranque (warmUp).
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "app_security_pin",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Volatile private var pinSetCache: Boolean? = null

    /** Chamar fora da main thread: cria a chave/Tink e lê se há PIN. */
    fun warmUp() {
        runCatching { hasPinSet }
    }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric_enabled", false)
        set(value) = prefs.edit { putBoolean("biometric_enabled", value) }

    var pinEnabled: Boolean
        get() = prefs.getBoolean("pin_enabled", false)
        set(value) = prefs.edit { putBoolean("pin_enabled", value) }

    /** Tempo em segundo plano antes de voltar a pedir o desbloqueio (0 = logo). */
    var lockDelayMs: Long
        get() = prefs.getLong("lock_delay_ms", 0L)
        set(value) = prefs.edit { putLong("lock_delay_ms", value) }

    /** Miniatura em branco nos recentes enquanto o bloqueio estiver ligado. */
    var hideInRecents: Boolean
        get() = prefs.getBoolean("hide_in_recents", true)
        set(value) = prefs.edit { putBoolean("hide_in_recents", value) }

    /** Em cache depois da 1.ª leitura (não decifra a cada recomposição). */
    val hasPinSet: Boolean
        get() = pinSetCache ?: (encryptedPrefs.getString(KEY_V2, null) != null ||
            encryptedPrefs.getString(KEY_LEGACY, null) != null).also { pinSetCache = it }

    fun setPinHash(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        encryptedPrefs.edit()
            .putString(KEY_V2, b64(salt) + ":" + b64(pbkdf2(pin, salt)))
            .remove(KEY_LEGACY)
            .apply()
        pinSetCache = true
        resetFailures()
    }

    /** PBKDF2: chamar fora da main thread sempre que possível. */
    fun verifyPin(pin: String): Boolean {
        encryptedPrefs.getString(KEY_V2, null)?.let { stored ->
            val parts = stored.split(":")
            if (parts.size != 2) return false
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            return MessageDigest.isEqual(pbkdf2(pin, salt), Base64.decode(parts[1], Base64.NO_WRAP))
        }
        // PIN antigo (SHA-256 sem sal): ao acertar, passa a PBKDF2 com sal.
        val legacy = encryptedPrefs.getString(KEY_LEGACY, null) ?: return false
        val ok = MessageDigest.isEqual(sha256Hex(pin).toByteArray(), legacy.toByteArray())
        if (ok) setPinHash(pin)
        return ok
    }

    fun clearPin() {
        encryptedPrefs.edit().remove(KEY_V2).remove(KEY_LEGACY).apply()
        pinSetCache = false
        pinEnabled = false
        resetFailures()
    }

    // --- PIN errado no ecrã de bloqueio (sobrevive a reiniciar a app) ---

    val failedAttempts: Int get() = prefs.getInt("pin_failures", 0)

    /**
     * Tempo que falta da espera atual (0 = livre). Relógio mudado para trás:
     * nunca mais do que a espera máxima (e fica gravado assim).
     */
    fun lockoutRemainingMs(now: Long = System.currentTimeMillis()): Long {
        val left = prefs.getLong("pin_lockout_until", 0L) - now
        val max = lockoutFor(MAX_FAILURES)
        if (left > max) {
            prefs.edit { putLong("pin_lockout_until", now + max) }
            return max
        }
        return left.coerceAtLeast(0L)
    }

    /** Regista um PIN errado e devolve o novo total de falhas. */
    fun registerFailure(now: Long = System.currentTimeMillis()): Int {
        val n = failedAttempts + 1
        val wait = lockoutFor(n)
        prefs.edit {
            putInt("pin_failures", n)
            putLong("pin_lockout_until", if (wait > 0) now + wait else 0L)
        }
        return n
    }

    fun resetFailures() = prefs.edit {
        remove("pin_failures")
        remove("pin_lockout_until")
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_V2 = "pin_pbkdf2"
        private const val KEY_LEGACY = "pin_hash"
        private const val PBKDF2_ITERATIONS = 20_000

        /** Ao fim de tantas falhas a sessão é terminada. */
        const val MAX_FAILURES = 10

        /** 1–4 livres; depois 30 s, 1 min, 5 min, 15 min, 1 h. */
        fun lockoutFor(failures: Int): Long = when {
            failures < 5 -> 0L
            failures == 5 -> 30_000L
            failures == 6 -> 60_000L
            failures == 7 -> 5 * 60_000L
            failures == 8 -> 15 * 60_000L
            else -> 60 * 60_000L
        }
    }
}
