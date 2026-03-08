package com.github.yangusik.phppadplugin.toolWindow

import com.github.yangusik.phppadplugin.executor.DockerExecutor
import com.github.yangusik.phppadplugin.executor.SshExecutor
import com.github.yangusik.phppadplugin.services.PhpPadHistoryEntry
import com.github.yangusik.phppadplugin.services.PhpPadSettings
import com.github.yangusik.phppadplugin.services.SshConnection
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBSplitter
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.jetbrains.php.lang.PhpLanguage
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*

class PhpPadPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val log = logger<PhpPadPanel>()
    private val settings = PhpPadSettings.getInstance()
    private val connectionBox = JComboBox<SshConnection>()
    private val statusLabel = JBLabel("Ready")
    private val resultView: ResultView = createResultView()
    private val splitter = JBSplitter(settings.splitterVertical, 0.5f)

    private var editorEx: com.intellij.openapi.editor.ex.EditorEx? = null

    private val editor = LanguageTextField(PhpLanguage.INSTANCE, project, settings.lastCode).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        setOneLineMode(false)
        addSettingsProvider { ex ->
            editorEx = ex

            // Отключаем инспекции (типа "Expression not used") — оставляем только синтаксис
            ApplicationManager.getApplication().invokeLater {
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(ex.document)
                if (psiFile != null) {
                    HighlightingSettingsPerFile.getInstance(project)
                        .setHighlightingSettingForRoot(psiFile, FileHighlightingSetting.SKIP_INSPECTION)
                }
            }

            // Ctrl+Enter — запуск кода. Регистрируем на редакторе (работает когда фокус в редакторе).
            // Ссылаемся на action из plugin.xml чтобы shortcut читался из настроек Keymap.
            val registeredAction = ActionManager.getInstance().getAction("PhpPad.Run")
            val shortcutSet = registeredAction?.shortcutSet
                ?: CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK))
            object : com.intellij.openapi.actionSystem.AnAction() {
                override fun actionPerformed(e: AnActionEvent) { triggerRun() }
            }.registerCustomShortcutSet(shortcutSet, ex.contentComponent)

            ex.document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    PhpPadInlayManager.clear(ex)
                }
            })
        }
    }

    init {
        border = JBUI.Borders.empty()
        add(buildToolbar(), BorderLayout.NORTH)

        val editorScrollPane = JBScrollPane(editor).apply {
                border = JBUI.Borders.empty()
            }
        splitter.firstComponent = editorScrollPane

        splitter.secondComponent = resultView.component
        add(splitter, BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)
        refreshConnections()
    }

    fun triggerRun() = runCode()

    private fun createResultView(): ResultView {
        return if (settings.outputMode != "tree" && PhpPadJcefRenderer.isSupported()) {
            PhpPadJcefRenderer()
        } else {
            ResultRenderer()
        }
    }

    private fun buildToolbar(): JPanel {
        refreshConnections()

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

        // "+" кнопка — выпадающее меню SSH / Docker
        val addMenu = JPopupMenu()
        addMenu.add(JMenuItem("Add SSH").apply { addActionListener { addConnection("ssh") } })
        addMenu.add(JMenuItem("Add Docker").apply { addActionListener { addConnection("docker") } })
        val addBtn = JButton("+").apply {
            toolTipText = "Add Connection"
            addActionListener { e ->
                addMenu.show(this, 0, this.height)
            }
        }

        val editBtn = JButton("✎").apply {
            toolTipText = "Edit Connection"
            addActionListener { editConnection() }
        }
        val deleteBtn = JButton("✕").apply {
            toolTipText = "Delete Connection"
            addActionListener { deleteConnection() }
        }
        val runBtn = JButton("▶ Run").apply {
            toolTipText = "Run (Ctrl+Enter)"
            addActionListener { triggerRun() }
            background = Color(60, 130, 60)
            foreground = Color.WHITE
            isBorderPainted = false
            isOpaque = true
        }
        val snippetsBtn = JButton("☰").apply {
            toolTipText = "Snippets & History"
            addActionListener { openSnippets() }
        }
        val modeBtn = JButton(if (settings.outputMode == "tree") "Tree" else "JCEF").apply {
            toolTipText = "Switch output renderer (restart required)"
            addActionListener {
                settings.outputMode = if (settings.outputMode == "tree") "jcef" else "tree"
                text = if (settings.outputMode == "tree") "Tree" else "JCEF"
                JOptionPane.showMessageDialog(this@PhpPadPanel, "Restart required to apply renderer change.", "Renderer", JOptionPane.INFORMATION_MESSAGE)
            }
        }
        val splitBtn = JButton(if (settings.splitterVertical) "↕" else "↔").apply {
            toolTipText = "Toggle split orientation"
            addActionListener {
                settings.splitterVertical = !settings.splitterVertical
                splitter.orientation = settings.splitterVertical
                text = if (settings.splitterVertical) "↕" else "↔"
            }
        }

        return JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
            add(JBLabel("Connection:"))
            add(connectionBox)
            add(addBtn)
            add(editBtn)
            add(deleteBtn)
            add(Box.createHorizontalStrut(8))
            add(runBtn)
            add(snippetsBtn)
            add(modeBtn)
            add(splitBtn)
        }
    }

    private fun buildStatusBar(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
        border = JBUI.Borders.customLine(UIManager.getColor("Separator.foreground"), 1, 0, 0, 0)
        add(statusLabel)
    }

    private fun refreshConnections() {
        connectionBox.removeAllItems()
        settings.connections.forEach { connectionBox.addItem(it) }
        val active = settings.activeConnection()
        if (active != null) connectionBox.selectedItem = active
        else if (settings.connections.isNotEmpty()) connectionBox.selectedIndex = 0
    }

    private fun addConnection(type: String) {
        val conn = SshConnection().apply { this.type = type }
        val dialog = ConnectionDialog(project, conn)
        if (dialog.showAndGet()) {
            val list = settings.connections
            list.add(dialog.getConnection())
            settings.connections = list
            refreshConnections()
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
            refreshConnections()
        }
    }

    private fun deleteConnection() {
        val selected = connectionBox.selectedItem as? SshConnection ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this, "Delete connection '${selected.name}'?", "Confirm", JOptionPane.YES_NO_OPTION
        )
        if (confirm == JOptionPane.YES_OPTION) {
            val list = settings.connections
            list.removeIf { it.id == selected.id }
            settings.connections = list
            refreshConnections()
        }
    }

    private fun openSnippets() {
        val dialog = SnippetsDialog(project, settings, editor.text)
        dialog.show()
        val code = dialog.selectedCode
        if (code != null) {
            ApplicationManager.getApplication().runWriteAction {
                editor.document.setText(code)
            }
        }
    }

    private fun runCode() {
        val conn = connectionBox.selectedItem as? SshConnection
        if (conn == null) {
            JOptionPane.showMessageDialog(this, "Please add a connection first.", "No Connection", JOptionPane.WARNING_MESSAGE)
            return
        }
        val code = editor.text
        settings.lastCode = code
        statusLabel.text = "Running..."
        resultView.clear()

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = if (conn.type == "docker") {
                log.info("PhpPad: docker exec ${conn.containerName}, project=${conn.projectPath}")
                DockerExecutor(conn).execute(code)
            } else {
                log.info("PhpPad: ssh ${conn.host}:${conn.port} as ${conn.username}, project=${conn.projectPath}")
                SshExecutor(conn).execute(code)
            }

            // Сохраняем в историю
            if (!result.isError) {
                val duration = result.json?.get("duration")?.asDouble ?: 0.0
                settings.addHistory(PhpPadHistoryEntry().also {
                    it.connectionName = conn.name
                    it.code = code
                    it.duration = duration
                })
            }

            ApplicationManager.getApplication().invokeLater {
                resultView.showResult(result)

                val ex = editorEx
                if (ex != null && !ex.isDisposed) {
                    val magicValues = result.json
                        ?.get("magicValues")?.takeIf { it.isJsonObject }?.asJsonObject
                    if (magicValues != null) PhpPadInlayManager.show(ex, magicValues)
                    else PhpPadInlayManager.clear(ex)
                }

                if (result.isError) {
                    statusLabel.text = "Error"
                } else {
                    val duration = result.json?.get("duration")?.asDouble ?: 0.0
                    statusLabel.text = "Done in %.2fms".format(duration)
                }
            }
        }
    }
}
