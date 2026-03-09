package com.github.yangusik.phppadplugin

import com.google.gson.JsonObject

/**
 * Pure formatting logic for PHP values serialized by runner.php.
 * No IntelliJ platform dependencies — fully unit-testable.
 */
object PhpValueFormatter {

    private const val STRING_INLAY_MAX = 40

    /** Short one-line format used in inlay hints: "← value" */
    fun formatInlay(obj: JsonObject): String = when (obj.get("type")?.asString) {
        "null"         -> "null"
        "bool"         -> if (obj.get("value")?.asBoolean == true) "true" else "false"
        "int", "float" -> obj.get("value")?.asString ?: "?"
        "string"       -> "\"${obj.get("value")?.asString?.take(STRING_INLAY_MAX) ?: ""}\""
        "array"        -> "array(${obj.get("count")?.asInt ?: 0})"
        "object"       -> obj.get("class")?.asString?.substringAfterLast("\\") ?: "object"
        else           -> "?"
    }

    /**
     * Inlay text for a magic-comment line.
     * rawValue is the JSON produced by runner.php for a single line:
     * it is always {"type":"array","items":[…]} wrapping actual values.
     */
    fun formatInlayLine(rawValue: JsonObject): String {
        val items = if (rawValue.get("type")?.asString == "array") {
            rawValue.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject } ?: emptyList()
        } else {
            listOf(rawValue)
        }
        return items.joinToString(", ") { formatInlay(it) }
    }

    /** Full label used in tree result renderer */
    fun formatLabel(obj: JsonObject): String = when (obj.get("type")?.asString) {
        "null"  -> "null"
        "bool"  -> if (obj.get("value")?.asBoolean == true) "true" else "false"
        "int"   -> obj.get("value")?.asString ?: "0"
        "float" -> obj.get("value")?.asString ?: "0.0"
        "string" -> {
            val v = obj.get("value")?.asString ?: ""
            val len = obj.get("length")?.asInt ?: v.length
            "\"$v\" ($len)"
        }
        "array"  -> "array(${obj.get("count")?.asInt ?: 0})"
        "object" -> obj.get("class")?.asString ?: "object"
        else     -> obj.toString()
    }
}
