package com.github.yangusik.phppadplugin.actions

import com.github.yangusik.phppadplugin.toolWindow.PhpPadPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class PhpPadRunAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tw = ToolWindowManager.getInstance(project).getToolWindow("PhpPad") ?: return
        val panel = tw.contentManager.selectedContent?.component as? PhpPadPanel ?: return
        panel.triggerRun()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
