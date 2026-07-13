package co.golink.tester.data.encryption

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Codec do formato de ficheiro E2E (chunked AES-256-GCM seekable).
 *
 * Byte-compatível com:
 *   - PHP  Support\Encryption\EncryptedFileCodec
 *   - JS   resources/js/services/e2eFile.js
 *
 * header(18) = "GLK1" | ver(1) | cipher(1) | chunk_size(uint32 BE, 4) | prefix(8)
 * bloco i: nonce = prefix||u32be(i) (12) ; aad = header||u32be(i)||is_last(1)
 *          saída = AES-256-GCM(dataKey, nonce, aad) = ciphertext||tag(16)
 */
object EncryptedFileCodec {
    private val MAGIC = byteArrayOf(0x47, 0x4c, 0x4b, 0x31) // "GLK1"
    private const val VERSION = 1
    private const val CIPHER_AES256GCM = 1
    const val HEADER_LEN = 18
    const val TAG_LEN = 16
    private const val PREFIX_LEN = 8
    const val DEFAULT_CHUNK = 65536

    private fun u32be(n: Int): ByteArray =
        byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())

    private fun u32be(bytes: ByteArray, off: Int): Int =
        ((bytes[off].toInt() and 0xff) shl 24) or
            ((bytes[off + 1].toInt() and 0xff) shl 16) or
            ((bytes[off + 2].toInt() and 0xff) shl 8) or
            (bytes[off + 3].toInt() and 0xff)

    private fun keyOf(dataKey: ByteArray): SecretKeySpec {
        require(dataKey.size == 32) { "data key tem de ter 32 bytes" }
        return SecretKeySpec(dataKey, "AES")
    }

    private fun buildHeader(chunkSize: Int, prefix: ByteArray): ByteArray =
        MAGIC + byteArrayOf(VERSION.toByte(), CIPHER_AES256GCM.toByte()) + u32be(chunkSize) + prefix

    private data class Header(val chunkSize: Int, val prefix: ByteArray)

    private fun parseHeader(header: ByteArray): Header {
        require(header.size == HEADER_LEN) { "header inválido" }
        require(header.copyOfRange(0, 4).contentEquals(MAGIC)) { "magic inválido" }
        require(header[4].toInt() == VERSION && header[5].toInt() == CIPHER_AES256GCM) { "versão/cifra não suportada" }
        return Header(u32be(header, 6), header.copyOfRange(10, 18))
    }

    private fun sealChunk(key: SecretKeySpec, plain: ByteArray, i: Int, isLast: Boolean, prefix: ByteArray, header: ByteArray): ByteArray {
        val nonce = prefix + u32be(i)
        val aad = header + u32be(i) + byteArrayOf(if (isLast) 1 else 0)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        c.updateAAD(aad)
        return c.doFinal(plain) // ciphertext||tag
    }

    private fun openChunk(key: SecretKeySpec, ctWithTag: ByteArray, i: Int, isLast: Boolean, prefix: ByteArray, header: ByteArray): ByteArray {
        require(ctWithTag.size >= TAG_LEN) { "bloco truncado" }
        val nonce = prefix + u32be(i)
        val aad = header + u32be(i) + byteArrayOf(if (isLast) 1 else 0)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        c.updateAAD(aad)
        return c.doFinal(ctWithTag) // lança AEADBadTagException em adulteração
    }

    private data class Last(val index: Int, val len: Int)

    private fun lastBlock(dataLen: Int, ctFull: Int): Last {
        val rem = dataLen % ctFull
        return if (rem == 0) Last(dataLen / ctFull - 1, ctFull) else Last(dataLen / ctFull, rem)
    }

    /** Cifra o plaintext -> objeto cifrado. prefix opcional (testes determinísticos). */
    fun encrypt(plain: ByteArray, dataKey: ByteArray, chunkSize: Int = DEFAULT_CHUNK, prefix: ByteArray? = null): ByteArray {
        val key = keyOf(dataKey)
        val pfx = prefix ?: java.security.SecureRandom().let { ByteArray(PREFIX_LEN).also(it::nextBytes) }
        require(pfx.size == PREFIX_LEN) { "prefix tem de ter $PREFIX_LEN bytes" }
        val header = buildHeader(chunkSize, pfx)
        val out = java.io.ByteArrayOutputStream()
        out.write(header)

        if (plain.isEmpty()) {
            out.write(sealChunk(key, ByteArray(0), 0, true, pfx, header))
        } else {
            var i = 0
            var off = 0
            while (off < plain.size) {
                val end = minOf(off + chunkSize, plain.size)
                out.write(sealChunk(key, plain.copyOfRange(off, end), i, end >= plain.size, pfx, header))
                off = end; i++
            }
        }
        return out.toByteArray()
    }

    /** Cifra em streaming (memory-safe) input -> output. Igual ao encryptStream do PHP. */
    fun encryptStream(input: java.io.InputStream, output: java.io.OutputStream, dataKey: ByteArray, chunkSize: Int = DEFAULT_CHUNK, prefix: ByteArray? = null) {
        val key = keyOf(dataKey)
        val pfx = prefix ?: java.security.SecureRandom().let { ByteArray(PREFIX_LEN).also(it::nextBytes) }
        require(pfx.size == PREFIX_LEN) { "prefix tem de ter $PREFIX_LEN bytes" }
        val header = buildHeader(chunkSize, pfx)
        output.write(header)

        var i = 0
        var cur = readFully(input, chunkSize)
        while (true) {
            val next = if (cur.size == chunkSize) readFully(input, chunkSize) else ByteArray(0)
            val isLast = next.isEmpty()
            output.write(sealChunk(key, cur, i, isLast, pfx, header))
            if (isLast) break
            cur = next; i++
        }
    }

    /** Decifra em streaming (memory-safe) input -> output. Igual ao decryptStream do PHP. */
    fun decryptStream(input: java.io.InputStream, output: java.io.OutputStream, dataKey: ByteArray) {
        val key = keyOf(dataKey)
        val header = readFully(input, HEADER_LEN)
        val (chunkSize, prefix) = parseHeader(header)
        val ctFull = chunkSize + TAG_LEN
        var i = 0
        var cur = readFully(input, ctFull)
        while (true) {
            val next = if (cur.size == ctFull) readFully(input, ctFull) else ByteArray(0)
            val isLast = next.isEmpty()
            output.write(openChunk(key, cur, i, isLast, prefix, header))
            if (isLast) break
            cur = next; i++
        }
    }

    private fun readFully(input: java.io.InputStream, len: Int): ByteArray {
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val r = input.read(buf, off, len - off)
            if (r < 0) break
            off += r
        }
        return if (off == len) buf else buf.copyOf(off)
    }

    /** Decifra o objeto completo. */
    fun decryptFull(obj: ByteArray, dataKey: ByteArray): ByteArray {
        val key = keyOf(dataKey)
        val header = obj.copyOfRange(0, HEADER_LEN)
        val (chunkSize, prefix) = parseHeader(header)
        val ctFull = chunkSize + TAG_LEN
        val dataLen = obj.size - HEADER_LEN
        require(dataLen >= TAG_LEN) { "objeto cifrado demasiado pequeno" }
        val last = lastBlock(dataLen, ctFull)

        val out = java.io.ByteArrayOutputStream()
        var off = HEADER_LEN
        for (i in 0..last.index) {
            val len = if (i == last.index) last.len else ctFull
            out.write(openChunk(key, obj.copyOfRange(off, off + len), i, i == last.index, prefix, header))
            off += len
        }
        return out.toByteArray()
    }

    /**
     * Decifra apenas [plainStart, plainStart+plainLen) — seek eficiente.
     * reader(offset, length) lê bytes do ciphertext (ficheiro/Range).
     */
    fun decryptRange(reader: (Int, Int) -> ByteArray, objectSize: Int, dataKey: ByteArray, plainStart: Int, plainLen: Int): ByteArray {
        if (plainLen <= 0 || plainStart < 0) return ByteArray(0)
        val key = keyOf(dataKey)
        val header = reader(0, HEADER_LEN)
        val (chunkSize, prefix) = parseHeader(header)
        val ctFull = chunkSize + TAG_LEN
        val dataLen = objectSize - HEADER_LEN
        val last = lastBlock(dataLen, ctFull)

        val plainEnd = plainStart + plainLen
        val firstChunk = plainStart / chunkSize
        val lastChunk = (plainEnd - 1) / chunkSize

        val buf = java.io.ByteArrayOutputStream()
        var i = firstChunk
        while (i <= lastChunk && i <= last.index) {
            val off = HEADER_LEN + i * ctFull
            val len = if (i == last.index) last.len else ctFull
            buf.write(openChunk(key, reader(off, len), i, i == last.index, prefix, header))
            i++
        }
        val all = buf.toByteArray()
        val skip = plainStart - firstChunk * chunkSize
        return all.copyOfRange(skip, minOf(skip + plainLen, all.size))
    }

    /** Comprimento do plaintext dado o tamanho do objeto e o chunkSize. */
    fun plaintextLength(objectSize: Int, chunkSize: Int): Int {
        val ctFull = chunkSize + TAG_LEN
        val dataLen = objectSize - HEADER_LEN
        if (dataLen <= 0) return 0
        val rem = dataLen % ctFull
        val nFull = dataLen / ctFull
        val lastLen = if (rem == 0) ctFull else rem
        val nBlocks = if (rem == 0) nFull else nFull + 1
        return (nBlocks - 1) * chunkSize + (lastLen - TAG_LEN)
    }
}
