package co.golink.tester.data.notes

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifra dos updates Yjs de uma nota — igual a createCipher(K_note, id, 'note') de
 * services/office/collab.js: AES-256-GCM com chave HKDF-SHA256(K_note, salt vazio,
 * info "golink-note-v1:<id>"); blob = base64(iv(12) || ciphertext+tag).
 */
class NoteCipher(noteKey: ByteArray, noteId: String) {
    private val key = SecretKeySpec(hkdf(noteKey, "golink-note-v1:$noteId".toByteArray(Charsets.UTF_8)), "AES")

    fun encrypt(plain: ByteArray): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return b64e.encodeToString(iv + cipher.doFinal(plain))
    }

    fun decrypt(blob: String): ByteArray {
        val data = b64d.decode(blob)
        require(data.size > 12 + 16) { "blob curto" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        return cipher.doFinal(data, 12, data.size - 12)
    }

    /** O núcleo (QuickJS) fala base64 em claro. */
    fun encryptB64(plainB64: String): String = encrypt(b64d.decode(plainB64))

    fun decryptToB64(blob: String): String = b64e.encodeToString(decrypt(blob))

    companion object {
        private val random = SecureRandom()
        private val b64e = Base64.getEncoder()
        private val b64d = Base64.getDecoder()

        /**
         * HKDF-SHA256 de 32 bytes. Salt vazio = HMAC com chave vazia, que é o mesmo
         * que 32 zeros (o HMAC completa a chave com zeros); o javax não aceita chave vazia.
         */
        fun hkdf(ikm: ByteArray, info: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
            val prk = mac.doFinal(ikm)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(info)
            mac.update(1)
            return mac.doFinal().copyOf(32)
        }
    }
}
