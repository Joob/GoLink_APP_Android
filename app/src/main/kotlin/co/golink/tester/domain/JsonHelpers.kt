package co.golink.tester.domain

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun JsonElement?.asUrl(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> if (isString) contentOrNullSafe() else null
    is JsonObject -> {
        val preferred = listOf("original", "lg", "md", "sm", "xs", "thumbnail")
        preferred.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNullSafe()
        } ?: this.values.firstNotNullOfOrNull { v ->
            (v as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNullSafe()
        }
    }
    else -> null
}

private fun JsonPrimitive.contentOrNullSafe(): String? = runCatching { content }.getOrNull()

fun JsonElement?.asEmoji(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> if (isString) contentOrNullSafe() else null
    is JsonObject -> (this["char"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNullSafe()
        ?: (this["codes"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNullSafe()
    else -> null
}
