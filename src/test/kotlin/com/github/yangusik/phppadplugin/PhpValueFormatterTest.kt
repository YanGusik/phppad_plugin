package com.github.yangusik.phppadplugin

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import com.google.gson.JsonNull
import org.junit.Assert.*
import org.junit.Test

class PhpValueFormatterTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject().also { o ->
        pairs.forEach { (k, v) ->
            when (v) {
                null        -> o.add(k, JsonNull.INSTANCE)
                is Boolean  -> o.addProperty(k, v)
                is Int      -> o.addProperty(k, v)
                is Double   -> o.addProperty(k, v)
                is String   -> o.addProperty(k, v)
                is JsonObject -> o.add(k, v)
                is JsonArray  -> o.add(k, v)
                else        -> o.addProperty(k, v.toString())
            }
        }
    }

    private fun scalar(type: String, value: Any?): JsonObject = when (value) {
        null       -> obj("type" to type)
        is Boolean -> obj("type" to type, "value" to value)
        is Int     -> obj("type" to type, "value" to value.toString())
        is Double  -> obj("type" to type, "value" to value.toString())
        is String  -> obj("type" to type, "value" to value)
        else       -> obj("type" to type, "value" to value.toString())
    }

    // ── PhpValueFormatter.formatInlay ─────────────────────────────────────────

    @Test fun `formatInlay null`() {
        assertEquals("null", PhpValueFormatter.formatInlay(scalar("null", null)))
    }

    @Test fun `formatInlay bool true`() {
        assertEquals("true", PhpValueFormatter.formatInlay(scalar("bool", true)))
    }

    @Test fun `formatInlay bool false`() {
        assertEquals("false", PhpValueFormatter.formatInlay(scalar("bool", false)))
    }

    @Test fun `formatInlay int`() {
        assertEquals("42", PhpValueFormatter.formatInlay(scalar("int", 42)))
    }

    @Test fun `formatInlay float`() {
        assertEquals("3.14", PhpValueFormatter.formatInlay(scalar("float", 3.14)))
    }

    @Test fun `formatInlay string short`() {
        assertEquals("\"hello\"", PhpValueFormatter.formatInlay(scalar("string", "hello")))
    }

    @Test fun `formatInlay string truncated at 40`() {
        val long = "a".repeat(50)
        val result = PhpValueFormatter.formatInlay(scalar("string", long))
        assertEquals("\"${"a".repeat(40)}\"", result)
    }

    @Test fun `formatInlay string exactly 40 chars not truncated`() {
        val exactly40 = "b".repeat(40)
        val result = PhpValueFormatter.formatInlay(scalar("string", exactly40))
        assertEquals("\"$exactly40\"", result)
    }

    @Test fun `formatInlay array shows count`() {
        val arr = obj("type" to "array", "count" to 5)
        assertEquals("array(5)", PhpValueFormatter.formatInlay(arr))
    }

    @Test fun `formatInlay array empty`() {
        val arr = obj("type" to "array", "count" to 0)
        assertEquals("array(0)", PhpValueFormatter.formatInlay(arr))
    }

    @Test fun `formatInlay object shows short class name`() {
        val o = obj("type" to "object", "class" to "App\\Models\\User")
        assertEquals("User", PhpValueFormatter.formatInlay(o))
    }

    @Test fun `formatInlay object no namespace`() {
        val o = obj("type" to "object", "class" to "stdClass")
        assertEquals("stdClass", PhpValueFormatter.formatInlay(o))
    }

    @Test fun `formatInlay unknown type returns question mark`() {
        assertEquals("?", PhpValueFormatter.formatInlay(obj("type" to "resource")))
    }

    // ── PhpValueFormatter.formatInlayLine ────────────────────────────────────

    private fun wrapItems(vararg items: JsonObject): JsonObject {
        val arr = JsonArray()
        items.forEach { arr.add(it) }
        return obj("type" to "array", "count" to items.size).also { it.add("items", arr) }
    }

    @Test fun `formatInlayLine single int`() {
        val line = wrapItems(scalar("int", 7))
        assertEquals("7", PhpValueFormatter.formatInlayLine(line))
    }

    @Test fun `formatInlayLine multiple values joined`() {
        val line = wrapItems(scalar("int", 1), scalar("string", "hi"), scalar("bool", true))
        assertEquals("1, \"hi\", true", PhpValueFormatter.formatInlayLine(line))
    }

    @Test fun `formatInlayLine empty array`() {
        val line = wrapItems()
        assertEquals("", PhpValueFormatter.formatInlayLine(line))
    }

    @Test fun `formatInlayLine non-array raw value treated as single item`() {
        // если runner вернул не array-wrapper, берём как есть
        val raw = scalar("bool", false)
        assertEquals("false", PhpValueFormatter.formatInlayLine(raw))
    }

    // ── PhpValueFormatter.formatLabel ────────────────────────────────────────

    @Test fun `formatLabel string shows length`() {
        val s = obj("type" to "string", "value" to "hello", "length" to 5)
        assertEquals("\"hello\" (5)", PhpValueFormatter.formatLabel(s))
    }

    @Test fun `formatLabel string uses value length as fallback`() {
        val s = obj("type" to "string", "value" to "hi")
        assertEquals("\"hi\" (2)", PhpValueFormatter.formatLabel(s))
    }

    @Test fun `formatLabel object full class name`() {
        val o = obj("type" to "object", "class" to "Illuminate\\Database\\Eloquent\\Collection")
        assertEquals("Illuminate\\Database\\Eloquent\\Collection", PhpValueFormatter.formatLabel(o))
    }

    @Test fun `formatLabel null`() {
        assertEquals("null", PhpValueFormatter.formatLabel(scalar("null", null)))
    }

    @Test fun `formatLabel int`() {
        assertEquals("100", PhpValueFormatter.formatLabel(scalar("int", 100)))
    }
}
