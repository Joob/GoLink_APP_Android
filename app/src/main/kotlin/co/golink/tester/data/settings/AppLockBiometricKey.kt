package co.golink.tester.data.settings

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chave do Keystore para a biometria do bloqueio da app (CryptoObject): é invalidada
 * quando muda a biometria do aparelho, por isso um dedo/rosto acabado de inscrever não
 * desbloqueia a app. Invalidada, fica marcada até o utilizador provar quem é com o PIN
 * (ou o código do telemóvel, sem PIN); depois [reset] deixa criar uma chave nova.
 */
@Singleton
class AppLockBiometricKey @Inject constructor(@ApplicationContext context: Context) {
    sealed interface State {
        data class Ready(val cipher: Cipher) : State
        /** A biometria do aparelho mudou: pedir o PIN / o código do telemóvel. */
        data object Invalidated : State
        /** Keystore indisponível neste aparelho: prompt sem CryptoObject (como antes). */
        data object Unsupported : State
    }

    private val prefs = context.getSharedPreferences("app_security", Context.MODE_PRIVATE)

    val invalidated: Boolean get() = prefs.getBoolean(KEY_INVALIDATED, false)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun secretKey(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // O código do telemóvel também serve (bloqueio só por biometria, sem PIN).
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
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

    fun state(): State {
        if (invalidated) return State.Invalidated
        return try {
            State.Ready(Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) })
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            prefs.edit { putBoolean(KEY_INVALIDATED, true) }
            State.Invalidated
        } catch (e: Exception) {
            State.Unsupported
        }
    }

    /** Depois do prompt: a operação só passa com a chave realmente desbloqueada. */
    fun verify(authenticated: Cipher?): Boolean =
        authenticated != null && runCatching { authenticated.doFinal(ByteArray(16)) }.isSuccess

    /** Identidade provada por outro meio (PIN / código do telemóvel) ou biometria religada. */
    fun reset() {
        deleteKey()
        prefs.edit { remove(KEY_INVALIDATED) }
    }

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private companion object {
        const val ALIAS = "golink_app_lock_bio"
        const val KEY_INVALIDATED = "bio_key_invalidated"
    }
}
