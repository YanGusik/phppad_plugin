package com.github.yangusik.phppadplugin.toolWindow

import com.github.yangusik.phppadplugin.executor.ExecutionResult
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.tree.*

class ResultRenderer : JPanel(BorderLayout()), ResultView {

    override val component: JComponent get() = this

    private val tree = JTree(DefaultMutableTreeNode("No results")).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = ValueCellRenderer()
        background = UIManager.getColor("Editor.background")
        rowHeight = 22
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        // Ctrl+C копирует текст выбранного узла в буфер обмена
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_C && e.isControlDown) {
                    val node = lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                    val text = (node.userObject as? NodeData)?.label ?: return
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(StringSelection(text), null)
                }
            }
        })
    }

    init {
        val scroll = JBScrollPane(tree).apply {
            border = null
            background = UIManager.getColor("Editor.background")
            viewport.background = UIManager.getColor("Editor.background")
        }
        add(scroll, BorderLayout.CENTER)
        background = UIManager.getColor("Editor.background")
    }

    override fun clear() {
        tree.model = DefaultTreeModel(DefaultMutableTreeNode("root"))
    }

    fun showError(message: String) {
        val root = DefaultMutableTreeNode("root")
        root.add(DefaultMutableTreeNode(NodeData("ERROR: $message", NodeType.EXCEPTION)))
        tree.model = DefaultTreeModel(root)
        expandAll()
    }

    override fun showResult(result: ExecutionResult) {
        if (result.isError) { showError(result.error!!); return }
        val json = result.json!!
        val root = DefaultMutableTreeNode("root")

        // Bootstrap error
        val bootstrapError = json.get("bootstrapError")?.takeUnless { it is JsonNull }?.asString
        if (bootstrapError != null) {
            root.add(leaf("Bootstrap Error: $bootstrapError", NodeType.EXCEPTION))
        }

        // Duration / PHP version header
        val duration = json.get("duration")?.asDouble ?: 0.0
        val phpVersion = json.get("phpVersion")?.asString ?: ""
        val bootstrapped = json.get("bootstrapped")?.asBoolean ?: false
        val headerText = buildString {
            append("%.2fms".format(duration))
            if (phpVersion.isNotBlank()) append("  PHP $phpVersion")
            if (!bootstrapped && bootstrapError == null) append("  (no Laravel)")
        }
        root.add(leaf(headerText, NodeType.HEADER))

        // Magic values показываются inline в редакторе через PhpPadInlayManager, здесь не нужны

        // Return values
        val returnValues = json.get("returnValues")?.takeIf { it.isJsonArray }?.asJsonArray
        if (returnValues != null && returnValues.size() > 0) {
            val retNode = DefaultMutableTreeNode(NodeData("Return Values", NodeType.SECTION))
            returnValues.forEachIndexed { i, v ->
                val child = valueNode(v.asJsonObject)
                (child.userObject as? NodeData)?.let {
                    child.userObject = it.copy(label = "${i + 1}: ${it.label}")
                }
                retNode.add(child)
            }
            root.add(retNode)
        }

        // Output
        val output = json.get("output")?.asString?.trim() ?: ""
        if (output.isNotBlank()) {
            val outNode = DefaultMutableTreeNode(NodeData("Output", NodeType.SECTION))
            output.lines().forEach { line ->
                outNode.add(leaf(line, NodeType.OUTPUT))
            }
            root.add(outNode)
        }

        // SQL queries
        val queries = json.get("queries")?.takeIf { it.isJsonArray }?.asJsonArray
        if (queries != null && queries.size() > 0) {
            val sqlNode = DefaultMutableTreeNode(NodeData("SQL Queries (${queries.size()})", NodeType.SECTION))
            queries.forEach { q ->
                val obj = q.asJsonObject
                val sql = obj.get("sql")?.asString ?: ""
                val time = obj.get("time")?.asDouble ?: 0.0
                sqlNode.add(leaf("%.2fms  %s".format(time, sql), NodeType.SQL))
            }
            root.add(sqlNode)
        }

        // Exception
        val exc = json.get("exception")?.takeIf { it.isJsonObject }?.asJsonObject
        if (exc != null) {
            val excClass = exc.get("class")?.asString ?: "Exception"
            val excMsg = exc.get("message")?.asString ?: ""
            val excFile = exc.get("file")?.asString ?: ""
            val excLine = exc.get("line")?.asInt ?: 0
            val excNode = DefaultMutableTreeNode(NodeData("$excClass: $excMsg", NodeType.EXCEPTION))
            excNode.add(leaf("$excFile:$excLine", NodeType.KEY))
            val trace = exc.getAsJsonArray("trace")
            trace?.forEach { f ->
                val frame = f.asJsonObject
                val file = frame.get("file")?.asString ?: ""
                val line = frame.get("line")?.asInt ?: 0
                val call = frame.get("call")?.asString ?: ""
                excNode.add(leaf("$call  $file:$line", NodeType.TRACE))
            }
            root.add(excNode)
        }

        tree.model = DefaultTreeModel(root)
        expandAll()
        tree.clearSelection()
    }

    private fun expandAll() {
        var i = 0
        while (i < tree.rowCount) {
            tree.expandRow(i++)
        }
    }

    private fun leaf(text: String, type: NodeType) =
        DefaultMutableTreeNode(NodeData(text, type))

    private fun valueNode(obj: JsonObject): DefaultMutableTreeNode {
        val type = obj.get("type")?.asString ?: "null"
        return when (type) {
            "null" -> leaf("null", NodeType.NULL)
            "bool" -> leaf(if (obj.get("value")?.asBoolean == true) "true" else "false", NodeType.BOOL)
            "int" -> leaf(obj.get("value")?.asString ?: "0", NodeType.NUMBER)
            "float" -> leaf(obj.get("value")?.asString ?: "0.0", NodeType.NUMBER)
            "string" -> {
                val v = obj.get("value")?.asString ?: ""
                val len = obj.get("length")?.asInt ?: v.length
                leaf("\"$v\" (${len})", NodeType.STRING)
            }
            "array" -> {
                val count = obj.get("count")?.asInt ?: 0
                val node = DefaultMutableTreeNode(NodeData("array($count)", NodeType.ARRAY))
                val items = obj.getAsJsonArray("items")
                val keys = obj.getAsJsonArray("keys")
                items?.forEachIndexed { i, item ->
                    val key = keys?.get(i)?.asString ?: i.toString()
                    val child = valueNode(item.asJsonObject)
                    (child.userObject as? NodeData)?.let {
                        child.userObject = it.copy(label = "$key: ${it.label}")
                    }
                    node.add(child)
                }
                node
            }
            "object" -> {
                val cls = obj.get("class")?.asString ?: "object"
                val node = DefaultMutableTreeNode(NodeData(cls, NodeType.OBJECT))
                val items = obj.getAsJsonArray("items")
                val keys = obj.getAsJsonArray("keys")
                items?.forEachIndexed { i, item ->
                    val key = keys?.get(i)?.asString ?: i.toString()
                    val child = valueNode(item.asJsonObject)
                    (child.userObject as? NodeData)?.let {
                        child.userObject = it.copy(label = "$key: ${it.label}")
                    }
                    node.add(child)
                }
                node
            }
            else -> leaf(obj.toString(), NodeType.KEY)
        }
    }

    enum class NodeType { HEADER, SECTION, KEY, STRING, NUMBER, BOOL, NULL, ARRAY, OBJECT, OUTPUT, SQL, EXCEPTION, TRACE }
    data class NodeData(val label: String, val type: NodeType)

    private class ValueCellRenderer : DefaultTreeCellRenderer() {
        private val editorBg = UIManager.getColor("Editor.background")

        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any, sel: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            // sel=false, hasFocus=false — полностью отключаем selection highlight и focus border
            super.getTreeCellRendererComponent(tree, value, false, expanded, leaf, row, false)
            val node = (value as? DefaultMutableTreeNode)?.userObject
            if (node is NodeData) {
                text = node.label
                icon = null
                background = editorBg
                backgroundNonSelectionColor = editorBg
                backgroundSelectionColor = editorBg
                foreground = when (node.type) {
                    NodeType.HEADER  -> JBColor(Color(100, 150, 100), Color(120, 190, 120))
                    NodeType.SECTION -> JBColor(Color(70, 120, 180), Color(100, 160, 220))
                    NodeType.STRING  -> JBColor(Color(180, 80, 80),   Color(210, 110, 110))
                    NodeType.NUMBER  -> JBColor(Color(0, 100, 180),   Color(100, 170, 230))
                    NodeType.BOOL    -> JBColor(Color(150, 0, 150),   Color(190, 100, 210))
                    NodeType.NULL    -> JBColor(Color(120, 120, 120), Color(160, 160, 160))
                    NodeType.ARRAY, NodeType.OBJECT -> JBColor(Color(50, 140, 50), Color(100, 190, 100))
                    NodeType.SQL     -> JBColor(Color(0, 100, 180),   Color(100, 170, 230))
                    NodeType.EXCEPTION -> JBColor(Color(180, 0, 0),   Color(230, 80, 80))
                    NodeType.TRACE   -> JBColor(Color(150, 60, 60),   Color(190, 100, 100))
                    NodeType.OUTPUT, NodeType.KEY -> JBColor.foreground()
                }
            }
            return this
        }
    }
}
