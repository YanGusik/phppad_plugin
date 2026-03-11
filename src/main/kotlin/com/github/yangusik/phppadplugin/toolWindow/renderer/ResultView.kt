package com.github.yangusik.phppadplugin.toolWindow.renderer

import com.github.yangusik.phppadplugin.executor.ExecutionResult
import javax.swing.JComponent

interface ResultView {
    val component: JComponent
    fun showResult(result: ExecutionResult)
    fun clear()
}
