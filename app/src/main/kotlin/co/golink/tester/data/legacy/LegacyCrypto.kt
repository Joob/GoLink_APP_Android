package co.golink.tester.data.legacy

import co.golink.tester.data.encryption.CalendarCrypto
import co.golink.tester.data.encryption.Envelope
import co.golink.tester.domain.legacy.LegacyGuardian
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest

/**
 * Cripto do Legacy — espelho de resources/js/services/legacyCrypto.js (ver
 * docs/legacy-threat-model.md no repo web antes de mexer).
 *
 * K_vault (selada ao dono) abre os meta_enc, os códigos dos recipients e as
 * partes dos guardiões. Blobs simétricos no formato do [CalendarCrypto]
 * (base64 de nonce || secretbox).
 */
object LegacyCrypto {
    private const val VAULT_CHECK = "golink-legacy-vault-v1"
    private const val KDF_CONTEXT = "golink-legacy-v1"
    private const val CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford (sem I, L, O, U)
    private const val CODE_LENGTH = 25 // 125 bits: o servidor tem o ciphertext, tem de resistir offline

    private val json = Json { ignoreUnknownKeys = true }

    // ---- chave do cofre ----

    data class VaultKeys(val kVault: ByteArray, val sealedVaultKey: String, val vaultCheck: String)

    /** [sealForSelf] sela à própria chave pública (E2EKeyManager.sealForSelf). */
    fun createVaultKeys(sealForSelf: (ByteArray) -> String): VaultKeys {
        val kVault = CalendarCrypto.genKey()
        return VaultKeys(kVault, sealForSelf(kVault), CalendarCrypto.encryptString(VAULT_CHECK, kVault))
    }

    /** Confirma que uma chave (aberta ou reconstruída) é mesmo K_vault. */
    fun isVaultKey(kVault: ByteArray, vaultCheck: String): Boolean =
        runCatching { CalendarCrypto.decryptString(vaultCheck, kVault) == VAULT_CHECK }.getOrDefault(false)

    fun encryptJson(obj: JsonObject, key: ByteArray): String = CalendarCrypto.encryptString(obj.toString(), key)

    fun decryptJson(b64: String, key: ByteArray): JsonObject =
        json.parseToJsonElement(CalendarCrypto.decryptString(b64, key)).jsonObject

    // ---- Shamir ----

    /** "1 de n" é uma cópia da chave por guardião (a lib exige t ≥ 2), como na web. */
    fun splitVaultKey(kVault: ByteArray, t: Int, n: Int): List<ByteArray> {
        if (t < 1 || t > n) error("legacy_bad_threshold")
        if (t == 1) return List(n) { i -> kVault.copyOf(kVault.size + 1).also { it[kVault.size] = (i + 1).toByte() } }
        return Shamir.split(kVault, n, t)
    }

    /** Com partes a mais ou a menos dá lixo: validar com [isVaultKey]. */
    fun combineShares(shares: List<ByteArray>, t: Int): ByteArray =
        if (t == 1) shares[0].copyOf(shares[0].size - 1) else Shamir.combine(shares)

    // ---- blobs selados aos guardiões ----

    data class RosterEntry(val slot: Int, val guardianId: String, val publicKey: String)

    fun sealJsonTo(obj: JsonObject, publicKeyB64: String): String =
        Envelope.b64(Envelope.sealName(obj.toString(), Envelope.unb64(publicKeyB64)))

    /** [open] = E2EKeyManager.openNameOrNull. */
    fun openSealedJson(sealed: String, open: (String) -> String?): JsonObject =
        json.parseToJsonElement(open(sealed) ?: error("legacy_open_failed")).jsonObject

    fun sealGuardianBlob(vaultId: String, epoch: Int, slot: Int, share: ByteArray, threshold: Int, count: Int, roster: List<RosterEntry>, publicKey: String): String =
        sealJsonTo(buildJsonObject {
            put("vault_id", vaultId)
            put("epoch", epoch)
            put("slot", slot)
            put("share", Envelope.b64(share))
            put("threshold", threshold)
            put("count", count)
            put("roster", buildJsonArray {
                roster.forEach { r -> add(buildJsonObject { put("slot", r.slot); put("guardian_id", r.guardianId); put("public_key", r.publicKey) }) }
            })
        }, publicKey)

    data class GuardianBlob(val vaultId: String, val epoch: Int, val slot: Int, val share: ByteArray, val threshold: Int, val count: Int, val roster: List<RosterEntry>)

    /** Recusa o blob se não for deste cofre, deste lugar ou desta chave. */
    fun openGuardianBlob(sealed: String, vaultId: String, slot: Int, myPublicKey: String, open: (String) -> String?): GuardianBlob {
        val o = openSealedJson(sealed, open)
        val roster = (o["roster"] as? JsonArray ?: JsonArray(emptyList())).map {
            val r = it.jsonObject
            RosterEntry(r["slot"]!!.jsonPrimitive.int, r["guardian_id"]!!.jsonPrimitive.content, r["public_key"]!!.jsonPrimitive.content)
        }
        val blobSlot = o["slot"]?.jsonPrimitive?.int
        val mine = roster.firstOrNull { it.slot == blobSlot }
        if (o["vault_id"]?.jsonPrimitive?.content != vaultId || blobSlot != slot || mine?.publicKey != myPublicKey) error("legacy_blob_mismatch")
        return GuardianBlob(
            vaultId, o["epoch"]!!.jsonPrimitive.int, slot, Envelope.unb64(o["share"]!!.jsonPrimitive.content),
            o["threshold"]!!.jsonPrimitive.int, o["count"]!!.jsonPrimitive.int, roster,
        )
    }

    /** A parte de um guardião para outro. Só para chaves do roster do blob selado. */
    fun sealRelay(vaultId: String, epoch: Int, slot: Int, share: ByteArray, publicKey: String): String =
        sealJsonTo(buildJsonObject { put("vault_id", vaultId); put("epoch", epoch); put("slot", slot); put("share", Envelope.b64(share)) }, publicKey)

    fun openRelay(sealed: String, vaultId: String, epoch: Int, open: (String) -> String?): Pair<Int, ByteArray> {
        val o = openSealedJson(sealed, open)
        if (o["vault_id"]?.jsonPrimitive?.content != vaultId || o["epoch"]?.jsonPrimitive?.int != epoch) error("legacy_relay_mismatch")
        return o["slot"]!!.jsonPrimitive.int to Envelope.unb64(o["share"]!!.jsonPrimitive.content)
    }

    // ---- prova de posse (reconfirmar guardião) ----

    data class Challenge(val sealedChallenge: String, val challengeHash: String)

    fun createChallenge(publicKey: String): Challenge {
        val challenge = Envelope.b64(Envelope.randomBytes(32))
        return Challenge(Envelope.b64(Envelope.sealName(challenge, Envelope.unb64(publicKey))), hashChallenge(challenge))
    }

    fun hashChallenge(challenge: String): String =
        MessageDigest.getInstance("SHA-256").digest(challenge.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    // ---- recipients ----

    fun generateCode(): String {
        // & 31 sem enviesamento: 256 é múltiplo de 32.
        val chars = Envelope.randomBytes(CODE_LENGTH).map { CODE_ALPHABET[it.toInt() and 31] }.joinToString("")
        return chars.chunked(5).joinToString("-")
    }

    /** Tolera minúsculas, espaços e as confusões habituais ao ditar. null = inválido. */
    fun normalizeCode(text: String?): String? {
        val code = (text ?: "").uppercase()
            .replace(Regex("[\\s-]"), "")
            .replace('O', '0')
            .replace(Regex("[IL]"), "1")
        if (code.length != CODE_LENGTH || code.any { it !in CODE_ALPHABET }) return null
        return code.chunked(5).joinToString("-")
    }

    fun deriveRecipientKey(code: String, recipientId: String): ByteArray {
        val normalized = normalizeCode(code) ?: error("legacy_bad_code")
        return Envelope.genericHash("$KDF_CONTEXT\u0000$recipientId\u0000$normalized".toByteArray(Charsets.UTF_8))
    }

    // ---- guardiões ----

    /**
     * Chave de um guardião autorizada pelo dono. Só conta dentro de meta_enc (K_vault) e
     * ligada ao id e ao email da linha, para o servidor não a mudar de guardião. O que
     * vem em public_key é o que o servidor diz, e nunca chega para selar uma parte.
     */
    fun pinnedKey(g: LegacyGuardian): String? {
        val pinned = g.meta.str("pinned_key") ?: return null
        return pinned.takeIf { g.meta.str("id") == g.dto.id && g.meta.str("email") == g.dto.email }
    }

    /** null = ainda sem chave; "unverified" | "pinned" | "changed". */
    fun keyState(g: LegacyGuardian): String? {
        if (g.dto.status != "accepted" || g.dto.public_key == null) return null
        val pinned = pinnedKey(g) ?: return "unverified"
        return if (pinned == g.dto.public_key) "pinned" else "changed"
    }

    // ---- CSV do Activity log ----

    /** Evita que um nome comece por =, +, - ou @ e vire fórmula na folha de cálculo. */
    fun csvCell(value: String?): String {
        val text = value ?: ""
        val safe = if (text.isNotEmpty() && text[0] in "=+-@\t\r") "'$text" else text
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
