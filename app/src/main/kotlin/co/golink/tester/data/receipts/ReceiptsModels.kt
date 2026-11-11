package co.golink.tester.data.receipts

import co.golink.tester.data.pass.bool
import co.golink.tester.data.pass.int
import co.golink.tester.data.pass.obj
import co.golink.tester.data.pass.str
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Valor em cêntimos de um campo JSON (número ou texto numérico). */
internal fun JsonObject.cents(key: String): Long? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.longOrNull ?: p.doubleOrNull?.roundToLong()
}

data class VatLine(val rate: String, val base: Long, val vat: Long)

/**
 * Recibo decifrado (blob de store/modules/receipts.js). O payload fica em JSON para
 * preservar campos que esta versão não conhece; os getters dão os campos tipados.
 * Valores em cêntimos.
 */
data class Receipt(
    val id: String,
    val revision: Int,
    val year: Int?,
    val hasPhoto: Boolean,
    val deletedAt: String?,
    val createdAt: String?,
    val updatedAt: String?,
    /** payload_enc tal como veio do servidor (evita decifrar outra vez a mesma revisão). */
    val enc: String,
    val payload: JsonObject,
    val locked: Boolean,
) {
    val isTrashed: Boolean get() = deletedAt != null
    val merchant: String get() = if (locked) "🔒" else payload.str("merchant").orEmpty()
    val merchantNif: String get() = payload.str("merchant_nif").orEmpty()
    val buyerNif: String get() = payload.str("buyer_nif").orEmpty()
    val date: String get() = if (locked) "" else payload.str("date").orEmpty()
    val time: String get() = payload.str("time").orEmpty()
    val total: Long get() = if (locked) 0 else payload.cents("total") ?: 0
    val vat: Long get() = payload.cents("vat") ?: 0
    val vatExempt: Boolean get() = payload.bool("vat_exempt")
    val vatLines: List<VatLine>
        get() = (payload["vat_lines"] as? JsonArray).orEmpty().mapNotNull {
            val o = it as? JsonObject ?: return@mapNotNull null
            VatLine(o.str("rate").orEmpty(), o.cents("base") ?: 0, o.cents("vat") ?: 0)
        }
    val atcud: String get() = payload.str("atcud").orEmpty()
    val docNo: String get() = payload.str("doc_no").orEmpty()
    val docType: String get() = payload.str("doc_type").orEmpty()
    val spendCat: String get() = payload.str("spend_cat").orEmpty()
    val irsCat: String get() = payload.str("irs_cat").orEmpty()
    val multiActivity: Boolean get() = payload.bool("multi_activity")
    val validated: Boolean get() = payload.bool("validated")
    val source: String get() = payload.str("source").orEmpty()
    val note: String get() = payload.str("note").orEmpty()

    /** Nome a mostrar: comerciante ou, sem nome, o NIF. */
    val title: String get() = merchant.ifBlank { merchantNif }
}

data class Member(val id: String, val name: String, val nif: String, val role: String) {
    val isDependent: Boolean get() = role == "dependent"
}

data class Budget(val amount: Long, val warn: Int, val rollover: Boolean)

/**
 * Meta cifrado do cofre: {household, budgets, alerted}. `raw` guarda o objeto
 * inteiro para nunca perder chaves que outra versão (web) tenha acrescentado.
 */
data class ReceiptsMeta(
    val household: List<Member> = DEFAULT_HOUSEHOLD,
    val budgets: Map<String, Budget> = emptyMap(),
    val alerted: Map<String, Int> = emptyMap(),
    val raw: JsonObject = JsonObject(emptyMap()),
) {
    fun toJson(): JsonObject {
        val m = raw.toMutableMap()
        m["household"] = JsonArray(household.map {
            JsonObject(mapOf("id" to JsonPrimitive(it.id), "name" to JsonPrimitive(it.name), "nif" to JsonPrimitive(it.nif), "role" to JsonPrimitive(it.role)))
        })
        m["budgets"] = JsonObject(budgets.mapValues { (_, b) ->
            JsonObject(mapOf("amount" to JsonPrimitive(b.amount), "warn" to JsonPrimitive(b.warn), "rollover" to JsonPrimitive(b.rollover)))
        })
        m["alerted"] = JsonObject(alerted.mapValues { JsonPrimitive(it.value) })
        return JsonObject(m)
    }

    companion object {
        val DEFAULT_HOUSEHOLD = listOf(Member("me", "", "", "taxpayer"))

        /** normaliseMeta da web: valores em falta ou de tipo errado → defeitos. */
        fun from(el: JsonElement?): ReceiptsMeta {
            val o = el as? JsonObject ?: return ReceiptsMeta()
            val household = (o["household"] as? JsonArray).orEmpty().mapNotNull {
                val m = it as? JsonObject ?: return@mapNotNull null
                Member(m.str("id") ?: return@mapNotNull null, m.str("name").orEmpty(), m.str("nif").orEmpty(), m.str("role") ?: "taxpayer")
            }.ifEmpty { DEFAULT_HOUSEHOLD }
            val budgets = o.obj("budgets")?.mapNotNull { (k, v) ->
                val b = v as? JsonObject ?: return@mapNotNull null
                k to Budget(b.cents("amount") ?: 0, b.int("warn") ?: Budgets.DEFAULT_WARN, (b["rollover"] as? JsonPrimitive)?.booleanOrNull ?: false)
            }?.toMap().orEmpty()
            val alerted = o.obj("alerted")?.mapNotNull { (k, v) -> ((v as? JsonPrimitive)?.longOrNull ?: 1L).let { k to it.toInt() } }?.toMap().orEmpty()
            return ReceiptsMeta(household, budgets, alerted, o)
        }
    }
}

/** Definições em claro no servidor (só booleanos; o servidor não sabe valores). */
data class ReceiptsSettings(val alertClose: Boolean = true, val alertOver: Boolean = true, val weekly: Boolean = false) {
    companion object {
        fun from(o: JsonObject?): ReceiptsSettings = ReceiptsSettings(
            alertClose = (o?.get("alert_close") as? JsonPrimitive)?.booleanOrNull ?: true,
            alertOver = (o?.get("alert_over") as? JsonPrimitive)?.booleanOrNull ?: true,
            weekly = (o?.get("weekly") as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }
}

data class ReceiptsState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** Sem K_rcpt e sem chaves E2E em memória: é preciso o E2EGate. */
    val needsKeys: Boolean = false,
    val items: List<Receipt> = emptyList(),
    val meta: ReceiptsMeta = ReceiptsMeta(),
    val settings: ReceiptsSettings = ReceiptsSettings(),
    val trashDays: Int = 30,
    /** O meta do servidor não abre com K (nunca gravar por cima). */
    val metaLocked: Boolean = false,
) {
    val active: List<Receipt> by lazy { items.filter { !it.isTrashed } }
    val trash: List<Receipt> by lazy { items.filter { it.isTrashed } }
    fun item(id: String?): Receipt? = items.firstOrNull { it.id == id }
}

class ReceiptsConflictException : Exception("conflict")

/** O recibo ficou gravado mas a foto falhou: repetir só a foto (sem duplicar). */
class ReceiptPhotoException(val saved: Receipt, cause: Throwable) : Exception("photo", cause)
