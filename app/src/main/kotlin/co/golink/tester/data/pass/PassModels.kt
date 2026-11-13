package co.golink.tester.data.pass

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Item do Pass decifrado. O payload fica em [JsonObject] tal como veio da web, para
 * que campos que o Android não conhece sejam preservados ao regravar.
 * Esquema (resources/js/store/modules/pass.js):
 * {type login|card|note, name, folder, favourite, notes, login{url,username,password,totp},
 *  card{holder,number,expiry,cvv,pin}, events[], password_history[], changed_at, health_ignore{}}
 */
data class PassItem(
    val id: String,
    val revision: Int,
    val deletedAt: String?,
    val createdAt: String?,
    val updatedAt: String?,
    /** payload_enc tal como veio (evita decifrar de novo se não mudou). */
    val enc: String,
    val payload: JsonObject,
    /** Não decifra com a chave atual: nunca regravar. */
    val locked: Boolean = false,
) {
    val type: String get() = payload.str("type") ?: "note"
    val name: String get() = payload.str("name").orEmpty()
    val folder: String? get() = payload.str("folder")
    val favourite: Boolean get() = payload.bool("favourite")
    val notes: String get() = payload.str("notes").orEmpty()
    val login: PassLogin? get() = payload.obj("login")?.let(PassLogin::from)
    val card: PassCard? get() = payload.obj("card")?.let(PassCard::from)
    val changedAt: String? get() = payload.str("changed_at")
    val healthIgnore: Map<String, String>
        get() = payload.obj("health_ignore")?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
    val events: List<PassEvent>
        get() = (payload["events"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PassEvent(o.str("at").orEmpty(), o.str("kind").orEmpty())
        }
    val passwordHistory: List<PassOldPassword>
        get() = (payload["password_history"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PassOldPassword(o.str("password").orEmpty(), o.str("until").orEmpty())
        }
    val isTrashed: Boolean get() = deletedAt != null
    val totp: TotpConfig? get() = Totp.parse(login?.totp)
}

data class PassLogin(val url: String, val username: String, val password: String, val totp: String) {
    fun toJson(base: JsonObject? = null): JsonObject = JsonObject(
        (base ?: JsonObject(emptyMap())) + mapOf(
            "url" to JsonPrimitive(url), "username" to JsonPrimitive(username),
            "password" to JsonPrimitive(password), "totp" to JsonPrimitive(totp),
        ),
    )

    companion object {
        fun from(o: JsonObject) = PassLogin(o.str("url").orEmpty(), o.str("username").orEmpty(), o.str("password").orEmpty(), o.str("totp").orEmpty())
    }
}

data class PassCard(val holder: String, val number: String, val expiry: String, val cvv: String, val pin: String) {
    fun toJson(base: JsonObject? = null): JsonObject = JsonObject(
        (base ?: JsonObject(emptyMap())) + mapOf(
            "holder" to JsonPrimitive(holder), "number" to JsonPrimitive(number), "expiry" to JsonPrimitive(expiry),
            "cvv" to JsonPrimitive(cvv), "pin" to JsonPrimitive(pin),
        ),
    )

    companion object {
        fun from(o: JsonObject) = PassCard(o.str("holder").orEmpty(), o.str("number").orEmpty(), o.str("expiry").orEmpty(), o.str("cvv").orEmpty(), o.str("pin").orEmpty())
    }
}

data class PassEvent(val at: String, val kind: String)
data class PassOldPassword(val password: String, val until: String)
data class PassFolder(val id: String, val name: String)

/** Preferências do cofre (guardadas em claro no servidor, PassVault::DEFAULT_SETTINGS). */
data class PassSettings(val lockAfter: Int = 5, val clipboardClear: Int = 30, val reprompt: Boolean = false) {
    companion object {
        fun from(o: JsonObject?): PassSettings = PassSettings(
            lockAfter = o?.int("lock_after") ?: 5,
            clipboardClear = o?.int("clipboard_clear") ?: 30,
            reprompt = o?.bool("reprompt") ?: false,
        )
    }
}

/** Rascunho vindo de um importador (folderName ainda por resolver em id). */
data class PassDraft(val payload: JsonObject, val folderName: String)

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
internal fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.intOrNull?.let { n -> n != 0 }) } ?: false
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** Cópia com chaves alteradas/removidas (valor null = remover). */
internal fun JsonObject.with(vararg pairs: Pair<String, JsonElement?>): JsonObject {
    val m = toMutableMap()
    for ((k, v) in pairs) if (v == null) m.remove(k) else m[k] = v
    return JsonObject(m)
}
