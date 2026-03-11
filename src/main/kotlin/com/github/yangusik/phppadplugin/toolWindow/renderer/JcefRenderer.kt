package com.github.yangusik.phppadplugin.toolWindow.renderer

import com.github.yangusik.phppadplugin.executor.ExecutionResult
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class JcefRenderer : JPanel(BorderLayout()), ResultView {

    private val log = logger<JcefRenderer>()
    private val browser = JBCefBrowser()
    private var pageLoaded = false
    private var pendingResult: ExecutionResult? = null
    private var pendingClear = false

    override val component: JComponent get() = this

    init {
        val html = JcefRenderer::class.java
            .getResourceAsStream("/phppad/result.html")!!
            .bufferedReader().readText()

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                pageLoaded = true
                if (pendingClear) {
                    execJs("window.clearResult()")
                    pendingClear = false
                }
                pendingResult?.let {
                    renderResult(it)
                    pendingResult = null
                }
            }
        }, browser.cefBrowser)

        browser.loadHTML(html)
        add(browser.component, BorderLayout.CENTER)
    }

    override fun showResult(result: ExecutionResult) {
        if (!pageLoaded) { pendingResult = result; return }
        renderResult(result)
    }

    override fun clear() {
        if (!pageLoaded) { pendingClear = true; return }
        execJs("window.clearResult()")
    }

    private fun renderResult(result: ExecutionResult) {
        val json = if (result.isError) {
            val msg = result.error.orEmpty()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
            """{"error":"$msg"}"""
        } else {
            result.json!!.toString()
        }
        execJs("window.renderResult($json)")
    }

    private fun execJs(js: String) {
        browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url ?: "", 0)
    }

    companion object {
        fun isSupported(): Boolean = try { JBCefApp.isSupported() } catch (e: Exception) { false }
    }
}
