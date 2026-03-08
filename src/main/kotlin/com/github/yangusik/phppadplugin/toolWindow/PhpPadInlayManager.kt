package com.github.yangusik.phppadplugin.toolWindow

import com.google.gson.JsonObject
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle

object PhpPadInlayManager {

    fun show(editor: EditorEx, magicValues: JsonObject) {
        clear(editor)
        val document = editor.document

        magicValues.entrySet().forEach { (lineStr, rawValue) ->
            // runner.php нумерует строки с 1, document — с 0
            val lineNum = (lineStr.toIntOrNull() ?: return@forEach) - 1
            if (lineNum < 0 || lineNum >= document.lineCount) return@forEach

            val lineEndOffset = document.getLineEndOffset(lineNum)

            // magicValues[line] = serializeValue(array_of_values) → тип "array", items = реальные значения
            val serialized = rawValue.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val items = if (serialized.get("type")?.asString == "array") {
                serialized.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject } ?: emptyList()
            } else {
                listOf(serialized)
            }

            val text = items.joinToString(", ") { formatValue(it) }
            editor.inlayModel.addInlineElement(lineEndOffset, true, InlayRenderer("  // ← $text"))
        }
    }

    fun clear(editor: EditorEx) {
        editor.inlayModel
            .getInlineElementsInRange(0, editor.document.textLength, InlayRenderer::class.java)
            .forEach { it.dispose() }
    }

    private fun formatValue(obj: JsonObject): String = when (obj.get("type")?.asString) {
        "null"         -> "null"
        "bool"         -> if (obj.get("value")?.asBoolean == true) "true" else "false"
        "int", "float" -> obj.get("value")?.asString ?: "?"
        "string"       -> "\"${obj.get("value")?.asString?.take(40) ?: ""}\""
        "array"        -> "array(${obj.get("count")?.asInt ?: 0})"
        "object"       -> obj.get("class")?.asString?.substringAfterLast("\\") ?: "object"
        else           -> "?"
    }

    // EditorCustomElementRenderer — рисует текст прямо в строке редактора
    class InlayRenderer(private val text: String) : EditorCustomElementRenderer {

        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val fm = inlay.editor.contentComponent.getFontMetrics(getFont(inlay.editor))
            return fm.stringWidth(text) + 2
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.font = getFont(inlay.editor)
            // мутный зеленоватый цвет как у комментариев, чуть светлее
            g.color = JBColor(Color(120, 160, 100, 200), Color(130, 180, 110, 200))
            g.drawString(text, targetRegion.x, targetRegion.y + targetRegion.height - 3)
        }

        private fun getFont(editor: Editor): Font {
            val scheme = (editor as EditorEx).colorsScheme
            return Font(scheme.editorFontName, Font.ITALIC, (scheme.editorFontSize - 1).toInt().coerceAtLeast(9))
        }
    }
}
