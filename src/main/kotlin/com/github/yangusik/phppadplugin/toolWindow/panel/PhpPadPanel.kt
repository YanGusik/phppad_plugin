package com.github.yangusik.phppadplugin.toolWindow.panel

import com.github.yangusik.phppadplugin.executor.DockerExecutor
import com.github.yangusik.phppadplugin.executor.ExecutionResult
import com.github.yangusik.phppadplugin.executor.SshExecutor
import com.github.yangusik.phppadplugin.services.PhpPadHistoryEntry
import com.github.yangusik.phppadplugin.services.PhpPadSettings
import com.github.yangusik.phppadplugin.services.SshConnection
import com.github.yangusik.phppadplugin.toolWindow.HttpServer
import com.github.yangusik.phppadplugin.toolWindow.MagicComments
import com.github.yangusik.phppadplugin.toolWindow.renderer.JcefRenderer
import com.github.yangusik.phppadplugin.toolWindow.renderer.ResultView
import com.github.yangusik.phppadplugin.toolWindow.renderer.TreeRenderer
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
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
    val settings = PhpPadSettings.getInstance()
    private var httpServer: HttpServer? = null
    private val statusLabel = JBLabel("Ready")
    private var resultView: ResultView = createResultView()
    val splitter = JBSplitter(settings.splitterVertical, 0.5f)
    val resultContainer = JPanel(BorderLayout())
    val connectionBox = JComboBox<SshConnection>()

    private var editorEx: EditorEx? = null

    val editor = LanguageTextField(PhpLanguage.INSTANCE, project, settings.lastCode).apply {
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

            // Ctrl+Enter — запуск кода
            val registeredAction = ActionManager.getInstance().getAction("PhpPad.Run")
            val shortcutSet = registeredAction?.shortcutSet
                ?: CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK))
            object : com.intellij.openapi.actionSystem.AnAction() {
                override fun actionPerformed(e: AnActionEvent) { triggerRun() }
            }.registerCustomShortcutSet(shortcutSet, ex.contentComponent)

            ex.document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    MagicComments.clear(ex)
                }
            })
        }
    }

    val editorScrollPane = JBScrollPane(editor).apply {
        border = JBUI.Borders.empty()
    }

    val editorMode = EditorMode(
        project = project,
        settings = settings,
        editor = editor,
        editorScrollPane = editorScrollPane,
        splitter = splitter
    )

    val toolbar = Toolbar(
        project = project,
        settings = settings,
        connectionBox = connectionBox,
        editor = editor,
        editorMode = editorMode,
        resultContainer = resultContainer,
        getHttpServer = { httpServer },
        onTriggerRun = { triggerRun() },
        onRefreshConnections = { refreshConnections() },
        onCreateResultView = {
            resultView = createResultView()
            resultContainer.removeAll()
            resultContainer.add(resultView.component, BorderLayout.CENTER)
            resultContainer.revalidate()
            resultContainer.repaint()
        },
        onStartHttpServer = { startHttpServer() },
        onSplitterOrientationChanged = { vertical ->
            splitter.orientation = vertical
        }
    )

    init {
        border = JBUI.Borders.empty()
        add(toolbar.build(), BorderLayout.NORTH)
        editorMode.setScratchBtnTooltip = { tooltip -> toolbar.scratchBtn?.toolTipText = tooltip }

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
        if (settings.editorMode == "scratch") editorMode.switchToScratch()
    }

    override fun removeNotify() {
        super.removeNotify()
        httpServer?.stop()
    }

    fun triggerRun() = runCode()

    fun getActiveEditorEx(): EditorEx? {
        return if (settings.editorMode == "scratch") editorMode.getActiveEditorEx() else editorEx
    }

    // Called by HTTP API: runs code on given connection, updates UI, calls callback with result
    fun executeCode(code: String, conn: SshConnection, callback: (ExecutionResult) -> Unit) {
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

    fun refreshConnections() {
        connectionBox.removeAllItems()
        settings.connections.forEach { connectionBox.addItem(it) }
        val active = settings.activeConnection()
        if (active != null) connectionBox.selectedItem = active
        else if (settings.connections.isNotEmpty()) connectionBox.selectedIndex = 0
    }

    fun startHttpServer() {
        httpServer?.stop()
        if (!settings.httpEnabled) return
        val server = HttpServer(
            settings = settings,
            getCode = { if (settings.editorMode == "scratch") editorMode.getScratchCode() else editor.text },
            setCode = { code ->
                ApplicationManager.getApplication().invokeLater {
                    if (settings.editorMode == "scratch") editorMode.setScratchCode(code)
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
        return if (settings.outputMode != "tree" && JcefRenderer.isSupported()) {
            JcefRenderer()
        } else {
            TreeRenderer()
        }
    }

    private fun buildStatusBar(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
        border = JBUI.Borders.customLine(UIManager.getColor("Separator.foreground"), 1, 0, 0, 0)
        add(statusLabel)
    }

    private fun runCode() {
        val conn = connectionBox.selectedItem as? SshConnection
        if (conn == null) {
            JOptionPane.showMessageDialog(this, "Please add a connection first.", "No Connection", JOptionPane.WARNING_MESSAGE)
            return
        }
        val code = if (settings.editorMode == "scratch") editorMode.getScratchCode() else editor.text
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

                val ex = getActiveEditorEx()
                if (ex != null && !ex.isDisposed) {
                    val magicValues = result.json
                        ?.get("magicValues")?.takeIf { it.isJsonObject }?.asJsonObject
                    if (magicValues != null) MagicComments.show(ex, magicValues)
                    else MagicComments.clear(ex)
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
