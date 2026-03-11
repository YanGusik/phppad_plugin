package com.github.yangusik.phppadplugin.toolWindow.panel

import com.github.yangusik.phppadplugin.services.PhpPadSettings
import com.github.yangusik.phppadplugin.services.SshConnection
import com.github.yangusik.phppadplugin.toolWindow.HttpServer
import com.github.yangusik.phppadplugin.toolWindow.dialog.ClaudeDialog
import com.github.yangusik.phppadplugin.toolWindow.dialog.ConnectionDialog
import com.github.yangusik.phppadplugin.toolWindow.dialog.SnippetsDialog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class Toolbar(
    private val project: Project,
    private val settings: PhpPadSettings,
    private val connectionBox: JComboBox<SshConnection>,
    private val editor: LanguageTextField,
    private val editorMode: EditorMode,
    private val resultContainer: JPanel,
    private val getHttpServer: () -> HttpServer?,
    private val onTriggerRun: () -> Unit,
    private val onRefreshConnections: () -> Unit,
    private val onCreateResultView: () -> Unit,
    private val onStartHttpServer: () -> Unit,
    private val onSplitterOrientationChanged: (Boolean) -> Unit
) {
    var scratchBtn: JButton? = null
        private set

    fun build(): JComponent {
        onRefreshConnections()

        // ── Connection JComboBox ─────────────────────────────────────────────
        connectionBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val conn = value as? SshConnection
                if (conn != null) {
                    val icon = if (conn.type == "docker") "🐳" else "⚡"
                    val sub = if (conn.type == "docker") conn.containerName else conn.host
                    label.text = "$icon ${conn.name}  $sub"
                }
                return label
            }
        }
        connectionBox.addActionListener {
            val selected = connectionBox.selectedItem as? SshConnection
            if (selected != null) settings.activeConnectionId = selected.id
        }

        val iconSize = JBUI.scale(22)

        fun compactIconBtn(icon: javax.swing.Icon, tooltip: String) = JButton(icon).apply {
            toolTipText = tooltip
            isBorderPainted = false
            isContentAreaFilled = false
            margin = JBUI.insets(0)
            preferredSize = Dimension(iconSize, iconSize)
            minimumSize = Dimension(iconSize, iconSize)
            maximumSize = Dimension(iconSize, iconSize)
        }

        // ── Run ─────────────────────────────────────────────────────────────
        val runBtn = JButton("Run", com.intellij.icons.AllIcons.Actions.Execute).apply {
            toolTipText = "Run (Ctrl+Enter)"
            addActionListener { onTriggerRun() }
            background = Color(60, 130, 60)
            foreground = Color.WHITE
            isBorderPainted = false
            isOpaque = true
            margin = JBUI.insets(2, 8, 2, 8)
        }

        // ── Add connection ───────────────────────────────────────────────────
        val addMenu = JPopupMenu()
        addMenu.add(JMenuItem("Add SSH").apply { addActionListener { addConnection("ssh") } })
        addMenu.add(JMenuItem("Add Docker").apply { addActionListener { addConnection("docker") } })
        val addBtn = compactIconBtn(com.intellij.icons.AllIcons.General.Add, "Add Connection").also {
            it.addActionListener { _ -> addMenu.show(it, 0, it.height) }
        }

        connectionBox.preferredSize = Dimension(JBUI.scale(160), connectionBox.preferredSize.height)
        connectionBox.maximumSize = Dimension(JBUI.scale(160), connectionBox.preferredSize.height)

        val editConnBtn = compactIconBtn(com.intellij.icons.AllIcons.Actions.Edit, "Edit Connection").also {
            it.addActionListener { _ -> editConnection() }
        }
        val deleteConnBtn = compactIconBtn(com.intellij.icons.AllIcons.General.Remove, "Delete Connection").also {
            it.addActionListener { _ -> deleteConnection() }
        }

        // ── Snippets & History ───────────────────────────────────────────────
        val snippetsBtn = compactIconBtn(com.intellij.icons.AllIcons.Actions.ListFiles, "Snippets & History").also {
            it.addActionListener { _ -> openSnippets() }
        }

        // ── Scratch toggle ───────────────────────────────────────────────────
        scratchBtn = compactIconBtn(com.intellij.icons.AllIcons.General.OpenDisk, "Toggle scratch file mode").also {
            it.addActionListener { _ ->
                if (settings.editorMode == "scratch") editorMode.switchToEmbedded()
                else editorMode.switchToScratch()
            }
        }

        // ── Settings ⚙ ──────────────────────────────────────────────────────
        val settingsBtn = compactIconBtn(com.intellij.icons.AllIcons.General.GearPlain, "Settings").also { btn ->
            btn.addActionListener {
                val menu = JPopupMenu()
                val rendererLabel = if (settings.outputMode == "tree") "Output: Tree  →  JCEF"
                                    else "Output: JCEF  →  Tree"
                menu.add(JMenuItem(rendererLabel).apply {
                    addActionListener {
                        settings.outputMode = if (settings.outputMode == "tree") "jcef" else "tree"
                        onCreateResultView()
                    }
                })
                val splitLabel = if (settings.splitterVertical) "Split: Vertical  →  Horizontal"
                                 else "Split: Horizontal  →  Vertical"
                menu.add(JMenuItem(splitLabel).apply {
                    addActionListener {
                        settings.splitterVertical = !settings.splitterVertical
                        onSplitterOrientationChanged(settings.splitterVertical)
                    }
                })
                menu.addSeparator()
                menu.add(JMenuItem("Claude API…").apply {
                    addActionListener {
                        ClaudeDialog(settings, getHttpServer) { onStartHttpServer() }.isVisible = true
                    }
                })
                menu.show(btn, 0, btn.height)
            }
        }

        val toolbarPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            add(runBtn)
            add(connectionBox)
            add(addBtn)
            add(editConnBtn)
            add(deleteConnBtn)
            add(snippetsBtn)
            add(scratchBtn!!)
            add(settingsBtn)
        }

        // Адаптивность: при узкой панели скрываем connectionBox, оставляем только иконки
        toolbarPanel.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                val narrow = toolbarPanel.width < JBUI.scale(300)
                connectionBox.isVisible = !narrow
            }
        })

        return JBScrollPane(toolbarPanel).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
            border = null
            val h = toolbarPanel.preferredSize.height
            minimumSize = Dimension(0, h)
            maximumSize = Dimension(Int.MAX_VALUE, h)
            preferredSize = Dimension(Int.MAX_VALUE, h)
        }
    }

    private fun addConnection(type: String) {
        val conn = SshConnection().apply { this.type = type }
        val dialog = ConnectionDialog(project, conn)
        if (dialog.showAndGet()) {
            val list = settings.connections
            list.add(dialog.getConnection())
            settings.connections = list
            onRefreshConnections()
        }
    }

    private fun editConnection() {
        val selected = connectionBox.selectedItem as? SshConnection ?: return
        val dialog = ConnectionDialog(project, selected)
        if (dialog.showAndGet()) {
            val list = settings.connections
            val idx = list.indexOfFirst { it.id == selected.id }
            if (idx >= 0) list[idx] = dialog.getConnection()
            settings.connections = list
            onRefreshConnections()
        }
    }

    private fun deleteConnection() {
        val selected = connectionBox.selectedItem as? SshConnection ?: return
        val confirm = JOptionPane.showConfirmDialog(
            null, "Delete connection '${selected.name}'?", "Confirm", JOptionPane.YES_NO_OPTION
        )
        if (confirm == JOptionPane.YES_OPTION) {
            val list = settings.connections
            list.removeIf { it.id == selected.id }
            settings.connections = list
            onRefreshConnections()
        }
    }

    private fun openSnippets() {
        val currentCode = if (settings.editorMode == "scratch") editorMode.getScratchCode() else editor.text
        val dialog = SnippetsDialog(project, settings, currentCode)
        dialog.show()
        val code = dialog.selectedCode ?: return
        if (settings.editorMode == "scratch") {
            editorMode.setScratchCode(code)
            editorMode.getOrCreateScratchFile()?.let {
                FileEditorManager.getInstance(project).openFile(it, true)
            }
        } else {
            ApplicationManager.getApplication().runWriteAction {
                editor.document.setText(code)
            }
        }
    }
}
