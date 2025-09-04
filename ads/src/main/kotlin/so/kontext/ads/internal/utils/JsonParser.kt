package so.kontext.ads.internal.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal fun String.jsonToMap(): Map<String, Any?>? {
    return try {
        val jsonObject = Json.decodeFromString<JsonObject>(this)
        jsonObject.toMap()
    } catch (_: Exception) {
        null
    }
}

/**
 * Recursively converts a JsonObject to a standard Kotlin Map.
 */
private fun JsonObject.toMap(): Map<String, Any?> {
    return this.entries.associate { (key, element) ->
        key to element.toAny()
    }
}

/**
 * Recursively converts a JsonArray to a standard Kotlin List.
 */
private fun JsonArray.toList(): List<Any?> {
    return this.map { it.toAny() }
}

/**
 * Recursively converts any JsonElement to its corresponding standard Kotlin type.
 * This is the core of the conversion logic.
 */
private fun JsonElement.toAny(): Any? {
    return when (this) {
        is JsonNull -> null
        is JsonObject -> this.toMap()
        is JsonArray -> this.toList()
        is JsonPrimitive -> {
            // Try to parse the primitive into the most specific type possible
            if (this.isString) {
                this.content
            } else {
                this.longOrNull ?: this.doubleOrNull ?: this.booleanOrNull ?: this.content
            }
        }
    }
}
