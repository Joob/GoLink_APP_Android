package co.golink.tester.data.encryption

import com.goterl.lazysodium.interfaces.SecretBox

/**
 * Cripto do calendário — espelho de resources/js/services/calendarCrypto.js.
 * K_cal (por calendário) e K_ev (por evento) são chaves secretbox de 32 bytes.
 * K_cal vai selada à pubkey de cada membro; K_ev vai embrulhada com K_cal
 * (key_cal), selada a cada convidado registado e, para externos, só no #k=.
 *
 * Blobs: base64 standard de nonce(24) || secretbox(bytes em bruto). Ao
 * contrário de [Envelope.wrapKeyForShare], as chaves vão em bytes, não em b64.
 */
object CalendarCrypto {
    fun genKey(): ByteArray = Envelope.generateDataKey()

    fun sealBytes(bytes: ByteArray, key: ByteArray): String {
        val w = Envelope.wrapSecretKey(bytes, key)
        return Envelope.b64(w.nonce + w.ciphertext)
    }

    fun openBytes(b64: String, key: ByteArray): ByteArray {
        val raw = Envelope.unb64(b64)
        val n = SecretBox.NONCEBYTES
        require(raw.size > n + SecretBox.MACBYTES) { "blob do calendário curto" }
        return Envelope.unwrapSecretKey(raw.copyOfRange(n, raw.size), raw.copyOfRange(0, n), key)
    }

    fun encryptString(text: String, key: ByteArray): String = sealBytes(text.toByteArray(Charsets.UTF_8), key)

    fun decryptString(b64: String, key: ByteArray): String = String(openBytes(b64, key), Charsets.UTF_8)

    /** K_ev embrulhada com K_cal. */
    fun wrapKey(key: ByteArray, with: ByteArray): String = sealBytes(key, with)

    fun unwrapKey(b64: String, with: ByteArray): ByteArray = openBytes(b64, with)

    /** Chave → texto do #k= (base64url sem padding), igual a keyToFragment. */
    fun keyToFragment(key: ByteArray): String =
        Envelope.b64(key).replace('+', '-').replace('/', '_').trimEnd('=')
}
