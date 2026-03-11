package com.github.yangusik.phppadplugin.toolWindow.panel

import com.github.yangusik.phppadplugin.services.PhpPadSettings
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBSplitter
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.php.lang.PhpLanguage

class EditorMode(
    private val project: Project,
    private val settings: PhpPadSettings,
    private val editor: LanguageTextField,
    private val editorScrollPane: JBScrollPane,
    private val splitter: JBSplitter
) {
    var setScratchBtnTooltip: (String) -> Unit = {}

    fun getOrCreateScratchFile(): VirtualFile? {
        val existing = ApplicationManager.getApplication().runReadAction<VirtualFile?> {
            ScratchFileService.getInstance()
                .findFile(ScratchRootType.getInstance(), "phppad.php", ScratchFileService.Option.existing_only)
        }
        if (existing != null) return existing
        return ScratchRootType.getInstance().createScratchFile(
            project, "phppad.php", PhpLanguage.INSTANCE, settings.lastCode
        )
    }

    fun getActiveEditorEx(): com.intellij.openapi.editor.ex.EditorEx? {
        if (settings.editorMode == "scratch") {
            val file = getOrCreateScratchFile() ?: return null
            val fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file)
            return (fileEditor as? TextEditor)?.editor
                as? com.intellij.openapi.editor.ex.EditorEx
        }
        return null // embedded editorEx is managed by PhpPadPanel
    }

    fun scratchDocument(file: VirtualFile): Document? =
        ApplicationManager.getApplication().runReadAction<Document?> {
            FileDocumentManager.getInstance().getDocument(file)
        }

    fun getScratchCode(): String {
        val file = getOrCreateScratchFile() ?: return editor.text
        return ApplicationManager.getApplication().runReadAction<String> {
            FileDocumentManager.getInstance().getDocument(file)?.text ?: editor.text
        }
    }

    fun setScratchCode(code: String) {
        val file = getOrCreateScratchFile() ?: run {
            ApplicationManager.getApplication().runWriteAction { editor.document.setText(code) }
            return
        }
        val doc = scratchDocument(file) ?: return
        ApplicationManager.getApplication().runWriteAction { doc.setText(code) }
    }

    fun switchToScratch() {
        settings.editorMode = "scratch"
        setScratchBtnTooltip("Scratch mode ON — click to switch back")
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
        // Скрываем editor panel через proportion — resultContainer остаётся в splitter
        editorScrollPane.isVisible = false
        splitter.proportion = 0.0f
        splitter.revalidate()
        splitter.repaint()
    }

    fun switchToEmbedded() {
        settings.editorMode = "embedded"
        setScratchBtnTooltip("Toggle scratch file mode")
        editorScrollPane.isVisible = true
        splitter.proportion = 0.5f
        splitter.revalidate()
        splitter.repaint()
    }
}
