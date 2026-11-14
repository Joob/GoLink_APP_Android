package co.golink.tester.data.settings

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.core.content.edit
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

    // Keystore (AES-GCM, ver EncryptedPrefs): caro a criar; aquecido em IO no arranque
    // (warmUp). Migra uma vez das EncryptedSharedPreferences antigas.
    private val encryptedPrefs by lazy { EncryptedPrefs.create(context, "app_security_pin") }

    @Volatile private var pinSetCache: Boolean? = null

    @Volatile private var bioSecureCache: Boolean? = null

    /** Chamar fora da main thread: cria a chave/Tink e lê se há PIN. */
    fun warmUp() {
        runCatching {
            hasPinSet
            migrateToSecure()
            bioSecure
        }
    }

    /**
     * Falhas/espera do PIN e o espelho da biometria passaram para o ficheiro cifrado: em
     * claro, quem tivesse acesso aos ficheiros da app (root/forense) desligava o bloqueio
     * ou zerava o contador. Migra uma vez a partir de "app_security".
     */
    private fun migrateToSecure() {
        if (prefs.contains("pin_failures") || prefs.contains("pin_lockout_until")) {
            encryptedPrefs.edit()
                .putInt(K_FAILURES, maxOf(prefs.getInt("pin_failures", 0), encryptedPrefs.getInt(K_FAILURES, 0)))
                .putLong(K_LOCKOUT_WALL, maxOf(prefs.getLong("pin_lockout_until", 0L), encryptedPrefs.getLong(K_LOCKOUT_WALL, 0L)))
                .commit()
            prefs.edit { remove("pin_failures"); remove("pin_lockout_until") }
        }
        if (prefs.getBoolean("biometric_enabled", false) && !encryptedPrefs.getBoolean(K_BIO, false)) {
            encryptedPrefs.edit().putBoolean(K_BIO, true).commit()
            bioSecureCache = true
        }
    }

    private val bioSecure: Boolean
        get() = bioSecureCache ?: encryptedPrefs.getBoolean(K_BIO, false).also { bioSecureCache = it }

    /** Ligada em qualquer das cópias: apagar só a de texto simples não desliga a biometria. */
    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric_enabled", false) || bioSecure
        set(value) {
            prefs.edit { putBoolean("biometric_enabled", value) }
            encryptedPrefs.edit().putBoolean(K_BIO, value).apply()
            bioSecureCache = value
        }

    /** Com um PIN gravado (ficheiro cifrado) o bloqueio conta como ligado, seja qual for a flag em claro. */
    var pinEnabled: Boolean
        get() = prefs.getBoolean("pin_enabled", false) || hasPinSet
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

    val failedAttempts: Int get() = encryptedPrefs.getInt(K_FAILURES, 0)

    /**
     * Tempo que falta da espera atual (0 = livre). No mesmo arranque do aparelho conta pelo
     * relógio monótono (adiantar a hora não salta a espera); depois de reiniciar, pelo relógio
     * de parede, e um relógio mudado para trás nunca dá mais do que a espera máxima.
     */
    fun lockoutRemainingMs(
        now: Long = System.currentTimeMillis(),
        elapsed: Long = SystemClock.elapsedRealtime(),
        boot: Int = bootCount(),
    ): Long {
        val max = lockoutFor(MAX_FAILURES)
        val sameBoot = boot >= 0 && encryptedPrefs.getInt(K_LOCKOUT_BOOT, -1) == boot
        val left = if (sameBoot) encryptedPrefs.getLong(K_LOCKOUT_ELAPSED, 0L) - elapsed
        else encryptedPrefs.getLong(K_LOCKOUT_WALL, 0L) - now
        if (left > max) {
            encryptedPrefs.edit().putLong(K_LOCKOUT_WALL, now + max).putLong(K_LOCKOUT_ELAPSED, elapsed + max).apply()
            return max
        }
        return left.coerceAtLeast(0L)
    }

    /** Regista um PIN errado e devolve o novo total de falhas. */
    fun registerFailure(
        now: Long = System.currentTimeMillis(),
        elapsed: Long = SystemClock.elapsedRealtime(),
        boot: Int = bootCount(),
    ): Int {
        val n = failedAttempts + 1
        val wait = lockoutFor(n)
        encryptedPrefs.edit()
            .putInt(K_FAILURES, n)
            .putLong(K_LOCKOUT_WALL, if (wait > 0) now + wait else 0L)
            .putLong(K_LOCKOUT_ELAPSED, if (wait > 0) elapsed + wait else 0L)
            .putInt(K_LOCKOUT_BOOT, boot)
            .commit()
        return n
    }

    fun resetFailures() {
        encryptedPrefs.edit().remove(K_FAILURES).remove(K_LOCKOUT_WALL).remove(K_LOCKOUT_ELAPSED).remove(K_LOCKOUT_BOOT).apply()
        prefs.edit { remove("pin_failures"); remove("pin_lockout_until") }
    }

    /** Contador de arranques do aparelho (-1 se indisponível): o relógio monótono recomeça em cada um. */
    private fun bootCount(): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

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
        private const val K_FAILURES = "pin_failures"
        private const val K_LOCKOUT_WALL = "pin_lockout_until"
        private const val K_LOCKOUT_ELAPSED = "pin_lockout_elapsed_until"
        private const val K_LOCKOUT_BOOT = "pin_lockout_boot"
        private const val K_BIO = "biometric_enabled"

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
