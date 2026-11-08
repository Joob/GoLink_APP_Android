package co.golink.tester.data.pass

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Desbloqueio do Pass com biometria: a K_pass fica cifrada (AES-GCM) com uma chave do
 * Android Keystore que só funciona depois de uma autenticação biométrica forte e é
 * invalidada quando muda a biometria do aparelho (aí volta a pedir a passphrase).
 */
@Singleton
class PassBiometricStore @Inject constructor(@ApplicationContext context: Context) {
    companion object {
        private const val ALIAS = "golink_pass_bio"
        private const val PREFS = "pass_bio"
        private const val IV = "iv"
        private const val CT = "ct"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A biometria foi invalidada (novo dedo/rosto): mostrar o aviso uma vez. */
    @Volatile var invalidated = false
        private set

    val enabled: Boolean get() = prefs.contains(CT) && prefs.contains(IV)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun secretKey(create: Boolean): SecretKey? {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }

    /** Cipher para ligar a biometria (passa no BiometricPrompt.CryptoObject). */
    fun encryptCipher(): Cipher? = try {
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey(create = true)) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        deleteKey()
        runCatching { Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey(create = true)) } }.getOrNull()
    } catch (e: Exception) {
        null
    }

    /** Cipher para abrir a K; null se a chave foi invalidada (apaga e marca [invalidated]). */
    fun decryptCipher(): Cipher? {
        val iv = prefs.getString(IV, null) ?: return null
        return try {
            val key = secretKey(create = false) ?: run { clear(); return null }
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            invalidated = true
            clear()
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Depois do prompt: guarda a K cifrada. */
    fun store(authenticated: Cipher, passKey: ByteArray) {
        val ct = authenticated.doFinal(passKey)
        prefs.edit()
            .putString(IV, Base64.encodeToString(authenticated.iv, Base64.NO_WRAP))
            .putString(CT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
        invalidated = false
    }

    /** Depois do prompt: devolve a K. */
    fun open(authenticated: Cipher): ByteArray? {
        val ct = prefs.getString(CT, null) ?: return null
        return runCatching { authenticated.doFinal(Base64.decode(ct, Base64.NO_WRAP)) }.getOrNull()
    }

    fun consumeInvalidated(): Boolean = invalidated.also { invalidated = false }

    fun clear() {
        prefs.edit().clear().apply()
        deleteKey()
    }

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }
}
