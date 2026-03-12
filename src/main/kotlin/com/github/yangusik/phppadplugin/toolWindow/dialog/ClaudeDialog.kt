package com.github.yangusik.phppadplugin.toolWindow.dialog

import com.github.yangusik.phppadplugin.services.PhpPadSettings
import com.github.yangusik.phppadplugin.toolWindow.HttpServer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.border.TitledBorder

class ClaudeDialog(
    private val settings: PhpPadSettings,
    private val getHttpServer: () -> HttpServer?,
    private val onRestartServer: () -> Unit
) : JDialog() {

    private var refreshStatus: (() -> Unit)? = null

    init {
        title = "PhpPad — Claude Integration"
        isModal = false
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = buildContent()
        pack()
        setLocationRelativeTo(null)
        minimumSize = Dimension(560, 500)
        addWindowFocusListener(object : WindowAdapter() {
            override fun windowGainedFocus(e: WindowEvent) = refreshStatus?.invoke() ?: Unit
        })
    }

    private fun buildContent(): JComponent {
        val root = JPanel(BorderLayout(0, 8))
        root.border = JBUI.Borders.empty(12)

        root.add(buildServerPanel(), BorderLayout.NORTH)

        val tabs = JTabbedPane()
        tabs.addTab("Quick Start", buildQuickStartPanel())
        tabs.addTab("API Reference", buildApiRefPanel())
        tabs.addTab("CLAUDE.md", buildClaudeMdPanel())
        root.add(tabs, BorderLayout.CENTER)

        return root
    }

    private fun buildServerPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        panel.border = BorderFactory.createTitledBorder("Server")

        val statusDot = JBLabel()
        val statusLabel = JBLabel()
        val portField = JTextField(settings.httpPort.toString(), 6)
        val hostField = JTextField(settings.httpHost, 12)
        val enableBox = JCheckBox("Enabled", settings.httpEnabled)

        val doRefresh = {
            val httpServer = getHttpServer()
            if (httpServer != null && httpServer.isRunning) {
                statusDot.foreground = Color(60, 180, 60)
                statusDot.text = "●"
                statusLabel.text = "Running on ${settings.httpHost}:${httpServer.port}"
            } else {
                statusDot.foreground = Color(180, 60, 60)
                statusDot.text = "●"
                statusLabel.text = "Stopped"
            }
        }
        refreshStatus = doRefresh
        doRefresh()

        val applyBtn = JButton("Apply & Restart").apply {
            addActionListener {
                val port = portField.text.trim().toIntOrNull()
                if (port == null || port < 1024 || port > 65535) {
                    JOptionPane.showMessageDialog(this@ClaudeDialog,
                        "Invalid port. Use 1024–65535.", "Error", JOptionPane.ERROR_MESSAGE)
                    return@addActionListener
                }
                settings.httpPort = port
                settings.httpHost = hostField.text.trim().ifBlank { "0.0.0.0" }
                settings.httpEnabled = enableBox.isSelected
                onRestartServer()
                SwingUtilities.invokeLater { doRefresh() }
            }
        }

        panel.add(statusDot)
        panel.add(statusLabel)
        panel.add(Box.createHorizontalStrut(16))
        panel.add(JBLabel("Host:"))
        panel.add(hostField)
        panel.add(JBLabel("Port:"))
        panel.add(portField)
        panel.add(enableBox)
        panel.add(applyBtn)
        return panel
    }

    private fun buildQuickStartPanel(): JPanel {
        val port = settings.httpPort
        val connName = settings.connections.firstOrNull()?.name ?: "my-connection"

        val examples = """
# 1. Check available connections
curl http://localhost:$port/connections

# 2. Set code in PhpStorm editor (you'll see it update live)
curl -X POST http://localhost:$port/editor \
  -H "Content-Type: application/json" \
  -d '{
    "code": "<?php\n${'$'}user = User::find(1);\ndump(${'$'}user->name);"
  }'

# 3. Run the code in editor
curl -X POST http://localhost:$port/run \
  -H "Content-Type: application/json" \
  -d '{"connection": "$connName"}'

# 4. Or run code directly without changing editor
curl -X POST http://localhost:$port/run \
  -H "Content-Type: application/json" \
  -d '{
    "connection": "$connName",
    "code": "<?php\ndump(User::count());"
  }'
        """.trimIndent()

        return buildTextPanel(examples, "Copy Examples")
    }

    private fun buildApiRefPanel(): JPanel {
        val port = settings.httpPort
        val ref = """
GET  /connections
  Returns: [{"id":"...","name":"...","type":"ssh|docker","host":"..."}]

GET  /status
  Returns: {"running":true,"port":$port,"connections":N}

GET  /editor
  Returns: {"code":"<?php\n..."}

POST /editor
  Body:    {"code": "<?php\n..."}
  Returns: {"ok": true}

POST /run
  Body:    {"connection": "name or id", "code": "<?php\n..."}
           'code' is optional — uses current editor content if omitted
  Returns: full execution result JSON:
  {
    "duration": 57.06,
    "phpVersion": "8.2.29",
    "returnValues": [...],
    "output": "...",
    "queries": [{"sql":"...","time":1.2}],
    "exception": null | {"class":"...","message":"...","file":"...","line":N}
  }
        """.trimIndent()

        return buildTextPanel(ref, "Copy Reference")
    }

    private fun buildClaudeMdPanel(): JPanel {
        val port = settings.httpPort
        val wsl2note = if (settings.httpHost == "0.0.0.0")
            "\n# WSL2: use host.docker.internal:$port or \$(cat /etc/resolv.conf | grep nameserver | awk '{print \$2}'):$port"
        else ""

        val connList = settings.connections.joinToString(", ") { "\"${it.name}\"" }
            .ifBlank { "\"my-connection\"" }

        val md = """
## PHPPad — PHP Debugger Tool
Base URL: http://localhost:$port$wsl2note

Use PHPPad instead of `php artisan tinker` — no quote escaping issues.

### Available connections
$connList

### Usage

List connections:
  GET http://localhost:$port/connections

Set code in editor (developer sees it in PhpStorm):
  POST http://localhost:$port/editor
  {"code": "<?php\n// your code here"}

Run code (returns JSON result):
  POST http://localhost:$port/run
  {"connection": "name", "code": "<?php\ndump(User::find(1));"}

Run current editor code:
  POST http://localhost:$port/run
  {"connection": "name"}

### Result format
{
  "duration": 57.06,       // ms
  "returnValues": [...],   // dump() output as structured data
  "output": "...",         // echo/print output
  "queries": [...],        // SQL queries with time
  "exception": null | {...} // error info
}

### Tips
- Use dump() to see values
- No need to escape quotes — use JSON body
- Developer can modify the code in PhpStorm and ask you to re-run
        """.trimIndent()

        return buildTextPanel(md, "Copy CLAUDE.md snippet")
    }

    private fun buildTextPanel(text: String, copyLabel: String): JPanel {
        val area = JTextArea(text).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            background = UIManager.getColor("TextArea.background") ?: Color(30, 30, 30)
            foreground = UIManager.getColor("TextArea.foreground") ?: Color(212, 212, 212)
            border = JBUI.Borders.empty(8)
            lineWrap = false
        }
        val scroll = JBScrollPane(area)

        val copyBtn = JButton(copyLabel).apply {
            addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(StringSelection(text), null)
                this.text = "Copied!"
                Timer(1500) { this.text = copyLabel }.also { it.isRepeats = false; it.start() }
            }
        }

        val panel = JPanel(BorderLayout(0, 6))
        panel.border = JBUI.Borders.empty(8)
        panel.add(scroll, BorderLayout.CENTER)
        panel.add(copyBtn, BorderLayout.SOUTH)
        return panel
    }
}
