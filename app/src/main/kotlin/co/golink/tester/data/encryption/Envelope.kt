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

    // ---- nomes cifrados (Fase 2) — mesmo primitivo, sobre os bytes UTF-8 ----
    fun sealName(name: String, publicKey: ByteArray): ByteArray =
        sealDataKey(name.toByteArray(Charsets.UTF_8), publicKey)

    fun openName(sealed: ByteArray, publicKey: ByteArray, secretKey: ByteArray): String =
        String(openDataKey(sealed, publicKey, secretKey), Charsets.UTF_8)

    // ---- derivação do password ----
    // kdfAlg vem do servidor (kdf_alg): 'pbkdf2-sha256' (novo default, igual à Web)
    // ou 'argon2id13' (legacy — só para abrir chaves antigas). A Web migrou para
    // PBKDF2 nativo (WebCrypto) porque o Argon2 do libsodium WASM provou-se NÃO
    // determinístico em browsers reais; o Android acompanha para interop.
    const val KDF_ALG_PBKDF2 = "pbkdf2-sha256"
    const val KDF_ALG_ARGON2 = "argon2id13"

    /**
     * Round-trip do secretbox com uma chave conhecida. Se isto falhar, o problema
     * é o libsodium nativo deste dispositivo (ABI/lib), não a passphrase — e sem
     * o teste essa avaria era indistinguível de "segredo errado".
     */
    fun selfTestSecretBox(): Boolean = runCatching {
        val key = ByteArray(KEY_BYTES) { it.toByte() }
        val msg = ByteArray(KEY_BYTES) { (it * 7 + 1).toByte() }
        val w = wrapSecretKey(msg, key)
        unwrapSecretKey(w.ciphertext, w.nonce, key).contentEquals(msg)
    }.getOrDefault(false)

    fun deriveKey(password: String, salt: ByteArray, ops: Long, mem: Long, kdfAlg: String = KDF_ALG_ARGON2): ByteArray {
        val pw = password.toByteArray(Charsets.UTF_8)
        if (kdfAlg == KDF_ALG_PBKDF2) {
            // PBKDF2-HMAC-SHA256 sobre os bytes da password como chave HMAC, IGUAL
            // ao WebCrypto da Web (deriveBits com key = TextEncoder().encode(pw)).
            // NÃO usar PBEKeySpec/SecretKeyFactory: o provider BC do Android converte
            // os chars pelos 8 bits baixos (Latin-1) → passphrases não-ASCII davam
            // chave diferente da Web.
            return pbkdf2HmacSha256(pw, salt, ops.toInt(), KEY_BYTES)
        }
        val out = ByteArray(KEY_BYTES)
        check(
            ls.cryptoPwHash(out, out.size, pw, pw.size, salt, ops, NativeLong(mem), PwHash.Alg.PWHASH_ALG_ARGON2ID13)
        ) { "pwhash falhou" }
        return out
    }

    /**
     * PBKDF2-HMAC-SHA256 puro (RFC 8018) via javax.crypto.Mac. A password entra como
     * chave HMAC em bytes crus (UTF-8), por isso é bit-a-bit idêntico ao WebCrypto da
     * Web — sem a ambiguidade de char→byte do PBEKeySpec/provider.
     */
    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(password, "HmacSHA256"))
        val hLen = mac.macLength
        val blocks = (dkLen + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)
        val block = ByteArray(salt.size + 4)
        System.arraycopy(salt, 0, block, 0, salt.size)
        for (i in 1..blocks) {
            block[salt.size] = (i ushr 24).toByte()
            block[salt.size + 1] = (i ushr 16).toByte()
            block[salt.size + 2] = (i ushr 8).toByte()
            block[salt.size + 3] = i.toByte()
            var u = mac.doFinal(block)
            val t = u.copyOf(hLen)
            for (c in 2..iterations) {
                u = mac.doFinal(u)
                for (k in 0 until hLen) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (i - 1) * hLen, hLen)
        }
        return out.copyOf(dkLen)
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

    // ---- partilha de pasta E2E: data key embrulhada com a chave da partilha ----
    // Interop com a web (openKeyFromShare): o texto em claro dentro do secretbox
    // é o b64 da data key (bytes UTF-8), NÃO os bytes crus; o pacote é nonce||ct.
    // ---- nomes cifrados com a chave da PARTILHA (simétrico, mesmo codec dos
    // ficheiros) — o visitante do link abre-os com a chave que vem no #k=.
    fun sealNameForShare(name: String, shareKey: ByteArray): String =
        b64(EncryptedFileCodec.encrypt(name.toByteArray(Charsets.UTF_8), shareKey))

    fun openNameForShare(b64Name: String, shareKey: ByteArray): String =
        String(EncryptedFileCodec.decryptFull(unb64(b64Name), shareKey), Charsets.UTF_8)

    fun wrapKeyForShare(dataKey: ByteArray, shareKey: ByteArray): String {
        val inner = b64(dataKey).toByteArray(Charsets.UTF_8)
        val w = wrapSecretKey(inner, shareKey)
        val packed = ByteArray(w.nonce.size + w.ciphertext.size)
        System.arraycopy(w.nonce, 0, packed, 0, w.nonce.size)
        System.arraycopy(w.ciphertext, 0, packed, w.nonce.size, w.ciphertext.size)
        return b64(packed)
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
