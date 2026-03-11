package com.github.yangusik.phppadplugin.actions

import com.github.yangusik.phppadplugin.toolWindow.panel.PhpPadPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

class PhpPadRunInEditorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tw = ToolWindowManager.getInstance(project).getToolWindow("PhpPad") ?: return
        tw.show()
        val panel = tw.contentManager.selectedContent?.component as? PhpPadPanel ?: return
        panel.triggerRun()
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isVisible = file?.name == "phppad.php"
        e.presentation.isEnabled = e.project != null
    }
}
