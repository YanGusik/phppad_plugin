package com.github.yangusik.phppadplugin.actions

import com.github.yangusik.phppadplugin.toolWindow.PhpPadPanel
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Function

class PhpPadGutterProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Только leaf-токены (нет дочерних элементов)
        if (element.firstChild != null) return null

        val file = element.containingFile ?: return null
        if (file.name != "phppad.php") return null
        val vFile = file.virtualFile ?: return null
        val scratchRoot = ScratchFileService.getInstance()
            .getRootType(vFile) ?: return null
        if (scratchRoot !is ScratchRootType) return null

        // Только первый токен файла (нет предшествующих leaf-сиблингов)
        if (element.parent !is PsiFile) return null
        if (element.prevSibling != null) return null

        val icon = com.intellij.icons.AllIcons.Actions.Execute
        val tooltipFn = Function<PsiElement, String> { "▶ Run in PhpPad" }

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            tooltipFn,
            { mouseEvent, elt ->
                val project = elt.project
                val tw = ToolWindowManager.getInstance(project).getToolWindow("PhpPad") ?: return@LineMarkerInfo
                tw.show()
                val panel = tw.contentManager.selectedContent?.component as? PhpPadPanel ?: return@LineMarkerInfo
                panel.triggerRun()
            },
            GutterIconRenderer.Alignment.LEFT,
            { "PhpPad run" }
        )
    }
}
