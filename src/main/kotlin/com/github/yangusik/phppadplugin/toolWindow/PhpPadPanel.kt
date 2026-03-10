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
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
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
    private var httpServer: PhpPadHttpServer? = null
    private val statusLabel = JBLabel("Ready")
    private var resultView: ResultView = createResultView()
    private val splitter = JBSplitter(settings.splitterVertical, 0.5f)
    private val resultContainer = JPanel(BorderLayout())
    private lateinit var editorScrollPane: JBScrollPane
    private var scratchBtn: JButton? = null

    private var editorEx: com.intellij.openapi.editor.ex.EditorEx? = null

    private val editor = LanguageTextField(PhpLanguage.INSTANCE, project, settings.lastCode).apply {
        setOneLineMode(false)
        addSettingsProvider { ex ->
            editorEx = ex
            val globalScheme = EditorColorsManager.getInstance().globalScheme
            ex.colorsScheme.editorFontName = globalScheme.editorFontName
            ex.colorsScheme.editorFontSize = globalScheme.editorFontSize
            ex.colorsScheme.lineSpacing = globalScheme.lineSpacing

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

        editorScrollPane = JBScrollPane(editor).apply {
            border = JBUI.Borders.empty()
        }
        splitter.firstComponent = editorScrollPane

        resultContainer.add(resultView.component, BorderLayout.CENTER)
        splitter.secondComponent = resultContainer
        add(splitter, BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)
        refreshConnections()
    }

    override fun addNotify() {
        super.addNotify()
        startHttpServer()
        if (settings.editorMode == "scratch") switchToScratchMode()
    }

    override fun removeNotify() {
        super.removeNotify()
        httpServer?.stop()
    }

    fun triggerRun() = runCode()

    // Called by HTTP API: runs code on given connection, updates UI, calls callback with result
    fun executeCode(code: String, conn: SshConnection, callback: (com.github.yangusik.phppadplugin.executor.ExecutionResult) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = if (conn.type == "docker") DockerExecutor(conn).execute(code)
                         else SshExecutor(conn).execute(code)
            ApplicationManager.getApplication().invokeLater {
                resultView.showResult(result)
                if (result.isError) statusLabel.text = "Error"
                else statusLabel.text = "Done in %.2fms".format(result.json?.get("duration")?.asDouble ?: 0.0)
            }
            callback(result)
        }
    }

    // ── Scratch file mode ────────────────────────────────────────────────────

    private fun getOrCreateScratchFile(): com.intellij.openapi.vfs.VirtualFile? {
        val existing = ApplicationManager.getApplication().runReadAction<com.intellij.openapi.vfs.VirtualFile?> {
            ScratchFileService.getInstance()
                .findFile(ScratchRootType.getInstance(), "phppad.php", ScratchFileService.Option.existing_only)
        }
        if (existing != null) return existing
        return ScratchRootType.getInstance().createScratchFile(
            project, "phppad.php", PhpLanguage.INSTANCE, settings.lastCode
        )
    }

    private fun scratchDocument(file: com.intellij.openapi.vfs.VirtualFile) =
        ApplicationManager.getApplication().runReadAction<com.intellij.openapi.editor.Document?> {
            FileDocumentManager.getInstance().getDocument(file)
        }

    private fun getScratchCode(): String {
        val file = getOrCreateScratchFile() ?: return editor.text
        return ApplicationManager.getApplication().runReadAction<String> {
            FileDocumentManager.getInstance().getDocument(file)?.text ?: editor.text
        }
    }

    private fun setScratchCode(code: String) {
        val file = getOrCreateScratchFile() ?: run {
            ApplicationManager.getApplication().runWriteAction { editor.document.setText(code) }
            return
        }
        val doc = scratchDocument(file) ?: return
        ApplicationManager.getApplication().runWriteAction { doc.setText(code) }
    }

    private fun switchToScratchMode() {
        settings.editorMode = "scratch"
        scratchBtn?.text = "Scratch ✓"
        val file = getOrCreateScratchFile()
        if (file != null) {
            val doc = scratchDocument(file)
            val isEmpty = ApplicationManager.getApplication().runReadAction<Boolean> {
                doc?.text?.isBlank() ?: true
            }
            if (doc != null && isEmpty) {
                ApplicationManager.getApplication().runWriteAction { doc.setText(editor.text) }
            }
            FileEditorManager.getInstance(project).openFile(file, true)
        }
        remove(splitter)
        add(resultContainer, BorderLayout.CENTER)
        revalidate(); repaint()
    }

    private fun switchToEmbeddedMode() {
        settings.editorMode = "embedded"
        scratchBtn?.text = "Scratch"
        remove(resultContainer)
        splitter.secondComponent = resultContainer
        add(splitter, BorderLayout.CENTER)
        revalidate(); repaint()
    }

    private fun startHttpServer() {
        httpServer?.stop()
        if (!settings.httpEnabled) return
        val server = PhpPadHttpServer(
            settings = settings,
            getCode = { if (settings.editorMode == "scratch") getScratchCode() else editor.text },
            setCode = { code ->
                ApplicationManager.getApplication().invokeLater {
                    if (settings.editorMode == "scratch") setScratchCode(code)
                    else ApplicationManager.getApplication().runWriteAction { editor.document.setText(code) }
                }
            },
            runCode = { code, conn, callback ->
                val connToUse = settings.connections.find { it.id == conn.id } ?: conn
                executeCode(code, connToUse, callback)
            }
        )
        val error = server.start(settings.httpHost, settings.httpPort)
        if (error != null) log.warn("PhpPad HTTP server: $error")
        else httpServer = server
    }

    private fun createResultView(): ResultView {
        return if (settings.outputMode != "tree" && PhpPadJcefRenderer.isSupported()) {
            PhpPadJcefRenderer()
        } else {
            ResultRenderer()
        }
    }

    private fun buildToolbar(): JComponent {
        refreshConnections()

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
            addActionListener { triggerRun() }
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

        // ── Edit / Delete через правый клик на connectionBox ─────────────────
        connectionBox.componentPopupMenu = JPopupMenu().apply {
            add(JMenuItem("✎ Edit connection").apply { addActionListener { editConnection() } })
            add(JMenuItem("✕ Delete connection").apply { addActionListener { deleteConnection() } })
        }
        connectionBox.preferredSize = Dimension(JBUI.scale(140), connectionBox.preferredSize.height)
        connectionBox.maximumSize = Dimension(JBUI.scale(140), connectionBox.preferredSize.height)

        // ── Snippets & History ───────────────────────────────────────────────
        val snippetsBtn = compactIconBtn(com.intellij.icons.AllIcons.Actions.ListFiles, "Snippets & History").also {
            it.addActionListener { _ -> openSnippets() }
        }

        // ── Scratch toggle ───────────────────────────────────────────────────
        scratchBtn = JButton(if (settings.editorMode == "scratch") "Scratch ✓" else "Scratch").apply {
            toolTipText = "Toggle scratch file mode"
            isBorderPainted = false
            isContentAreaFilled = false
            margin = JBUI.insets(2, 6, 2, 6)
            addActionListener {
                if (settings.editorMode == "scratch") switchToEmbeddedMode()
                else switchToScratchMode()
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
                        resultView = createResultView()
                        resultContainer.removeAll()
                        resultContainer.add(resultView.component, BorderLayout.CENTER)
                        resultContainer.revalidate()
                        resultContainer.repaint()
                    }
                })
                val splitLabel = if (settings.splitterVertical) "Split: Vertical  →  Horizontal"
                                 else "Split: Horizontal  →  Vertical"
                menu.add(JMenuItem(splitLabel).apply {
                    addActionListener {
                        settings.splitterVertical = !settings.splitterVertical
                        splitter.orientation = settings.splitterVertical
                    }
                })
                menu.addSeparator()
                menu.add(JMenuItem("Claude API…").apply {
                    addActionListener {
                        val server = httpServer ?: PhpPadHttpServer(settings, { editor.text }, {}, { _, _, _ -> })
                        PhpPadClaudeDialog(settings, server) { startHttpServer() }.isVisible = true
                    }
                })
                menu.show(btn, 0, btn.height)
            }
        }

        val toolbarPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            add(runBtn)
            add(connectionBox)
            add(addBtn)
            add(snippetsBtn)
            add(scratchBtn!!)
            add(settingsBtn)
        }
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
        val currentCode = if (settings.editorMode == "scratch") getScratchCode() else editor.text
        val dialog = SnippetsDialog(project, settings, currentCode)
        dialog.show()
        val code = dialog.selectedCode ?: return
        if (settings.editorMode == "scratch") {
            setScratchCode(code)
            // Обновляем открытый scratch файл
            getOrCreateScratchFile()?.let { FileEditorManager.getInstance(project).openFile(it, true) }
        } else {
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
        val code = if (settings.editorMode == "scratch") getScratchCode() else editor.text
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
