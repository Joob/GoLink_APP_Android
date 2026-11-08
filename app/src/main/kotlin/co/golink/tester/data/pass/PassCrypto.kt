package co.golink.tester.data.pass

import co.golink.tester.data.encryption.CalendarCrypto
import co.golink.tester.data.encryption.Envelope
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Cifra do Pass, igual à web (calendarCrypto.js): K_pass de 32 bytes selada à pubkey
 * E2E do utilizador; payload_enc / meta_enc = b64(nonce24 ‖ secretbox(JSON, K)).
 */
object PassCrypto {
    private val json = Json { ignoreUnknownKeys = true }

    fun encryptJson(obj: JsonElement, key: ByteArray): String = CalendarCrypto.encryptString(obj.toString(), key)

    fun decryptJson(b64: String, key: ByteArray): JsonElement = json.parseToJsonElement(CalendarCrypto.decryptString(b64, key))

    fun sha256Hex(text: String?): String =
        MessageDigest.getInstance("SHA-256").digest((text ?: "").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Id curto das pastas (igual a cc.uuid().slice(0, 8)). */
    fun newFolderId(): String = UUID.randomUUID().toString().substring(0, 8)

    /**
     * Export cifrado do Pass (exporter.js): {format:'golink-pass', version:1,
     * kdf:{alg, ops, mem, salt(b64)}, data: secretbox(JSON portátil)}.
     */
    fun decryptExport(file: JsonObject, password: String): List<PassDraft> {
        val kdf = file["kdf"] as? JsonObject ?: throw IllegalArgumentException("kdf")
        val alg = (kdf["alg"] as? JsonPrimitive)?.contentOrNull ?: Envelope.KDF_ALG_PBKDF2
        val ops = (kdf["ops"] as? JsonPrimitive)?.longOrNull ?: 600_000L
        val mem = (kdf["mem"] as? JsonPrimitive)?.longOrNull ?: 0L
        val salt = Envelope.unb64((kdf["salt"] as JsonPrimitive).content)
        val key = Envelope.deriveKey(password, salt, ops, mem, alg)
        val data = (file["data"] as JsonPrimitive).content
        val list = decryptJson(data, key) as? JsonArray ?: return emptyList()
        return list.mapNotNull { (it as? JsonObject)?.let(PassImporters::fromPortable) }
    }

    fun encryptExport(portable: JsonArray, password: String): String {
        val salt = Envelope.generateSalt()
        val ops = Envelope.defaultKdfOps()
        val mem = Envelope.defaultKdfMem()
        val alg = Envelope.defaultKdfAlg()
        val key = Envelope.deriveKey(password, salt, ops, mem, alg)
        return JsonObject(
            mapOf(
                "format" to JsonPrimitive("golink-pass"),
                "version" to JsonPrimitive(1),
                "kdf" to JsonObject(mapOf("alg" to JsonPrimitive(alg), "ops" to JsonPrimitive(ops), "mem" to JsonPrimitive(mem), "salt" to JsonPrimitive(Envelope.b64(salt)))),
                "data" to JsonPrimitive(encryptJson(portable, key)),
            ),
        ).toString()
    }
}
