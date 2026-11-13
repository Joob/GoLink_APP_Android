package co.golink.tester.data.legacy

import co.golink.tester.data.encryption.CalendarCrypto
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.legacy.LegacyGuardingRow
import co.golink.tester.domain.legacy.LegacyMeta
import co.golink.tester.network.LegacyApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import retrofit2.Response

/** Um recipient com o código já aberto pela K_vault reconstruída. */
data class LegacyCombinedRecipient(val id: String, val email: String, val meta: LegacyMeta, val code: String?)

/** Passo do código por email: email mascarado + espera (0 = enviado agora). */
data class LegacyGuardianCode(val email: String, val waitSeconds: Int)

data class LegacyCombineResult(val threshold: Int, val have: Int, val status: String, val recipients: List<LegacyCombinedRecipient>)

/**
 * Lado do guardião (legacyLoadGuarding/legacyCombine da web): responder aos desafios,
 * aceitar/recusar e, depois de o cofre abrir, juntar as partes e mostrar os códigos.
 */
@Singleton
class LegacyGuardingRepository @Inject constructor(
    private val api: LegacyApi,
    private val keys: E2EKeyManager,
    private val owner: LegacyRepository,
) {
    /** Responde sozinho aos desafios pendentes: é o que mostra ao dono "Confirmed". */
    suspend fun load(): List<LegacyGuardingRow> = withContext(Dispatchers.IO) {
        val rows = api.guarding().bodyOrThrow().data.map { row ->
            val sealed = row.sealed_challenge ?: return@map row
            runCatching {
                val challenge = keys.openNameOrNull(sealed) ?: error("challenge ilegível")
                api.guardingConfirm(row.id, buildJsonObject { put("challenge", challenge) }).bodyOrThrow().data
            }.getOrElse {
                android.util.Log.w("Legacy", "desafio de guardião falhou", it)
                row
            }
        }
        owner.setGuardingCount(rows.size)
        rows
    }

    /**
     * Pede o código para o email do convite. Devolve o email mascarado e os
     * segundos até poder pedir outro; 429 (enviado há pouco) não é erro — o
     * código anterior continua válido.
     */
    suspend fun requestCode(id: String): LegacyGuardianCode = withContext(Dispatchers.IO) {
        val response = api.guardingCode(id)
        val raw = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
        if (!response.isSuccessful && response.code() != 429) throw LegacyException("legacy_code_failed", response.code())
        val obj = runCatching { Json.parseToJsonElement(raw.orEmpty()).jsonObject }.getOrNull()
        val email = (obj?.get("email") as? JsonPrimitive)?.content.orEmpty()
        val wait = (obj?.get("resend_in") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        if (email.isEmpty()) throw LegacyException("legacy_code_failed", response.code())
        LegacyGuardianCode(email, if (response.code() == 429) wait else 0)
    }

    /** accept | decline com o código do email. */
    suspend fun act(id: String, action: String, code: String) = withContext(Dispatchers.IO) {
        api.guardingAction(id, action, buildJsonObject { put("code", code) }).bodyOrThrow()
        Unit
    }

    suspend fun resendLink(id: String, recipientId: String) = withContext(Dispatchers.IO) {
        api.resendLink(id, recipientId).bodyOrThrow()
        Unit
    }

    /**
     * Entrega a minha parte aos outros guardiões (selada ao roster do blob, nunca ao
     * que o servidor disser) e tenta reconstruir K_vault com o que já chegou.
     */
    suspend fun combine(id: String): LegacyCombineResult = withContext(Dispatchers.IO) {
        val myKey = keys.ownPublicKeyB64() ?: throw LegacyException("legacy_locked")
        val open: (String) -> String? = keys::openNameOrNull
        var release = api.release(id).bodyOrThrow().data
        val blob = LegacyCrypto.openGuardianBlob(release.sealed_share, release.vault_id, release.slot, myKey, open)

        if (!release.contributed) {
            val relays = buildJsonArray {
                blob.roster.filter { it.slot != blob.slot }.forEach { peer ->
                    add(buildJsonObject {
                        put("to_guardian_id", peer.guardianId)
                        put("sealed_share", LegacyCrypto.sealRelay(release.vault_id, release.epoch, blob.slot, blob.share, peer.publicKey))
                    })
                }
            }
            api.contribute(id, buildJsonObject { put("relays", relays) }).bodyOrThrow()
            release = api.release(id).bodyOrThrow().data
        }

        val bySlot = linkedMapOf(blob.slot to blob.share)
        for (sealed in release.relays) {
            runCatching { LegacyCrypto.openRelay(sealed, release.vault_id, release.epoch, open) }
                // A minha parte vem do blob selado a mim; um relay (qualquer um o sela à
                // minha chave pública) nunca a pode substituir.
                .onSuccess { (slot, share) -> if (slot != blob.slot && blob.roster.any { it.slot == slot }) bySlot[slot] = share }
                .onFailure { android.util.Log.w("Legacy", "parte de outro guardião ilegível", it) }
        }

        // O quórum vem do blob selado; o do servidor só tem de bater certo.
        val threshold = blob.threshold
        if (threshold != release.threshold) throw LegacyException("legacy_combine_failed")

        if (bySlot.size < threshold) return@withContext LegacyCombineResult(threshold, bySlot.size, release.status, emptyList())

        val kVault = LegacyCrypto.combineShares(bySlot.values.toList(), threshold)
        try {
            if (!LegacyCrypto.isVaultKey(kVault, release.vault_check)) throw LegacyException("legacy_combine_failed")

            LegacyCombineResult(
                threshold,
                bySlot.size,
                release.status,
                release.recipients.map { r ->
                    LegacyCombinedRecipient(
                        r.id,
                        r.email,
                        r.meta_enc?.let { runCatching { LegacyMeta(LegacyCrypto.decryptJson(it, kVault)) }.getOrNull() } ?: LegacyMeta(JsonObject(emptyMap())),
                        // Um código ilegível não pode esconder os dos outros.
                        r.code_vault?.let { runCatching { CalendarCrypto.decryptString(it, kVault) }.getOrNull() },
                    )
                },
            )
        } finally {
            kVault.fill(0)
            bySlot.values.forEach { it.fill(0) }
        }
    }

    private fun <T> Response<T>.bodyOrThrow(): T = legacyBody()
}
