package co.golink.tester.data.encryption

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.sun.jna.NativeLong
import java.security.SecureRandom

/**
 * Envelope E2E (libsodium via Lazysodium). Interopera com o PHP/JS:
 * o servidor sela a data key à chave pública (crypto_box_seal) e aqui abrimo-la.
 * base64 standard (= base64_encode do PHP / variante ORIGINAL do JS).
 */
object Envelope {
    private val ls: LazySodiumAndroid by lazy { LazySodiumAndroid(SodiumAndroid()) }
    private val rng = SecureRandom()

    const val KEY_BYTES = 32

    // ---- base64 ----
    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    // ---- data keys / recovery / salt ----
    fun generateDataKey(): ByteArray = ByteArray(KEY_BYTES).also(rng::nextBytes)
    fun generateRecoveryKey(): ByteArray = ByteArray(KEY_BYTES).also(rng::nextBytes)
    fun generateSalt(): ByteArray = ByteArray(PwHash.SALTBYTES).also(rng::nextBytes)

    // ---- par de chaves ----
    data class Keypair(val publicKey: ByteArray, val secretKey: ByteArray)

    fun generateKeypair(): Keypair {
        val pk = ByteArray(Box.PUBLICKEYBYTES)
        val sk = ByteArray(Box.SECRETKEYBYTES)
        check(ls.cryptoBoxKeypair(pk, sk)) { "keypair falhou" }
        return Keypair(pk, sk)
    }

    // ---- selar / abrir data key (crypto_box_seal) ----
    fun sealDataKey(dataKey: ByteArray, publicKey: ByteArray): ByteArray {
        val out = ByteArray(dataKey.size + Box.SEALBYTES)
        check(ls.cryptoBoxSeal(out, dataKey, dataKey.size.toLong(), publicKey)) { "seal falhou" }
        return out
    }

    fun openDataKey(sealed: ByteArray, publicKey: ByteArray, secretKey: ByteArray): ByteArray {
        val out = ByteArray(sealed.size - Box.SEALBYTES)
        check(ls.cryptoBoxSealOpen(out, sealed, sealed.size.toLong(), publicKey, secretKey)) { "open falhou (chave errada?)" }
        return out
    }

    // ---- derivação do password ----
    // kdfAlg vem do servidor (kdf_alg): 'pbkdf2-sha256' (novo default, igual à Web)
    // ou 'argon2id13' (legacy — só para abrir chaves antigas). A Web migrou para
    // PBKDF2 nativo (WebCrypto) porque o Argon2 do libsodium WASM provou-se NÃO
    // determinístico em browsers reais; o Android acompanha para interop.
    const val KDF_ALG_PBKDF2 = "pbkdf2-sha256"
    const val KDF_ALG_ARGON2 = "argon2id13"

    fun deriveKey(password: String, salt: ByteArray, ops: Long, mem: Long, kdfAlg: String = KDF_ALG_ARGON2): ByteArray {
        if (kdfAlg == KDF_ALG_PBKDF2) {
            // PBKDF2WithHmacSHA256 nativo (chars → UTF-8, igual ao TextEncoder da Web).
            val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, ops.toInt(), KEY_BYTES * 8)
            return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }
        val out = ByteArray(KEY_BYTES)
        val pw = password.toByteArray(Charsets.UTF_8)
        check(
            ls.cryptoPwHash(out, out.size, pw, pw.size, salt, ops, NativeLong(mem), PwHash.Alg.PWHASH_ALG_ARGON2ID13)
        ) { "pwhash falhou" }
        return out
    }

    // PBKDF2: ops = iterações (600k = OWASP, igual à Web), mem não se aplica (0).
    fun defaultKdfAlg(): String = KDF_ALG_PBKDF2
    fun defaultKdfOps(): Long = 600_000L
    fun defaultKdfMem(): Long = 0L

    /** Deriva a chave pública a partir da privada (X25519 = scalarmult_base). */
    fun publicFromSecret(secretKey: ByteArray): ByteArray {
        val pk = ByteArray(Box.PUBLICKEYBYTES)
        check(ls.cryptoScalarMultBase(pk, secretKey)) { "derivação da pública falhou" }
        return pk
    }

    // ---- proteção da privada (secretbox) ----
    data class Wrapped(val nonce: ByteArray, val ciphertext: ByteArray)

    fun wrapSecretKey(secretKey: ByteArray, wrappingKey: ByteArray): Wrapped {
        require(wrappingKey.size == KEY_BYTES) { "wrapping key tem de ter 32 bytes" }
        val nonce = ByteArray(SecretBox.NONCEBYTES).also(rng::nextBytes)
        val ct = ByteArray(secretKey.size + SecretBox.MACBYTES)
        check(ls.cryptoSecretBoxEasy(ct, secretKey, secretKey.size.toLong(), nonce, wrappingKey)) { "wrap falhou" }
        return Wrapped(nonce, ct)
    }

    fun unwrapSecretKey(ciphertext: ByteArray, nonce: ByteArray, wrappingKey: ByteArray): ByteArray {
        val out = ByteArray(ciphertext.size - SecretBox.MACBYTES)
        check(ls.cryptoSecretBoxOpenEasy(out, ciphertext, ciphertext.size.toLong(), nonce, wrappingKey)) { "unwrap falhou (segredo errado)" }
        return out
    }

    // ---- recovery key legível (igual ao formatRecovery/parseRecovery do JS) ----
    fun formatRecovery(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }.chunked(4).joinToString("-").uppercase()

    fun parseRecovery(s: String): ByteArray {
        val hex = s.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        return ByteArray(hex.length / 2) {
            ((Character.digit(hex[it * 2], 16) shl 4) + Character.digit(hex[it * 2 + 1], 16)).toByte()
        }
    }
}
