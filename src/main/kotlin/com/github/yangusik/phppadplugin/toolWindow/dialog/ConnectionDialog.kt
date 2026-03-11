package com.github.yangusik.phppadplugin.toolWindow.dialog

import com.github.yangusik.phppadplugin.services.SshConnection
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*

class ConnectionDialog(
    project: Project?,
    private val conn: SshConnection = SshConnection()
) : DialogWrapper(project) {

    // Type
    private val sshRadio = JRadioButton("SSH", conn.type != "docker")
    private val dockerRadio = JRadioButton("Docker", conn.type == "docker")

    // Common
    private val nameField = JBTextField(conn.name)

    // SSH
    private val hostField = JBTextField(conn.host)
    private val portSpinner = JSpinner(SpinnerNumberModel(conn.port, 1, 65535, 1))
    private val userField = JBTextField(conn.username)
    private val authCombo = JComboBox(arrayOf("Password", "Private Key"))
    private val passwordField = JPasswordField(conn.password)
    private val privateKeyField = JBTextField(conn.privateKeyPath)
    private val sshPathField = JBTextField(conn.projectPath)

    // Docker
    private val containerCombo = JComboBox<String>().apply { isEditable = true }
    private val dockerPathField = JBTextField(conn.projectPath)

    // Status
    private val statusLabel = JBLabel(" ").apply { font = font.deriveFont(11f) }

    private val cardPanel = JPanel(CardLayout())

    init {
        title = if (conn.type.isBlank() && conn.host.isBlank() && conn.containerName.isBlank())
            "Add Connection" else "Edit Connection"

        ButtonGroup().apply { add(sshRadio); add(dockerRadio) }
        sshRadio.addActionListener { switchType("ssh") }
        dockerRadio.addActionListener { switchType("docker") }

        authCombo.selectedIndex = if (conn.privateKeyPath.isNotBlank()) 1 else 0
        authCombo.addActionListener { updateAuthFields() }

        // Init docker container field
        if (conn.containerName.isNotBlank()) {
            containerCombo.addItem(conn.containerName)
            containerCombo.selectedItem = conn.containerName
        }

        cardPanel.add(buildSshPanel(), "ssh")
        cardPanel.add(buildDockerPanel(), "docker")

        init()
        switchType(conn.type.ifBlank { "ssh" })
        updateAuthFields()
    }

    private fun switchType(type: String) {
        (cardPanel.layout as CardLayout).show(cardPanel, type)
        sshRadio.isSelected = type == "ssh"
        dockerRadio.isSelected = type == "docker"
    }

    private fun updateAuthFields() {
        val isPassword = authCombo.selectedIndex == 0
        passwordField.isVisible = isPassword
        privateKeyField.isVisible = !isPassword
        cardPanel.revalidate(); cardPanel.repaint()
    }

    private fun buildSshPanel(): JPanel {
        val hostRow = JPanel(BorderLayout(4, 0)).apply {
            add(hostField, BorderLayout.CENTER)
            add(JPanel(BorderLayout(2, 0)).apply {
                add(JBLabel("Port:"), BorderLayout.WEST)
                add(portSpinner.apply { preferredSize = Dimension(65, preferredSize.height) }, BorderLayout.CENTER)
            }, BorderLayout.EAST)
        }

        return JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insets(3, 0)
            }
            fun label(text: String, row: Int) {
                add(JBLabel(text), gbc.also { it.gridx = 0; it.gridy = row; it.weightx = 0.0; it.insets = JBUI.insets(3, 0, 3, 8) })
            }
            fun field(comp: JComponent, row: Int) {
                add(comp, gbc.also { it.gridx = 1; it.gridy = row; it.weightx = 1.0 })
            }
            label("Host:", 0); field(hostRow, 0)
            label("Username:", 1); field(userField, 1)
            label("Auth method:", 2); field(authCombo, 2)
            label("Password:", 3); field(passwordField, 3)
            label("Private key:", 4); field(privateKeyField, 4)
            label("Project path:", 5); field(sshPathField, 5)
            // spacer
            add(JPanel(), gbc.also { it.gridx = 0; it.gridy = 6; it.gridwidth = 2; it.weighty = 1.0; it.fill = GridBagConstraints.BOTH })
        }
    }

    private fun buildDockerPanel(): JPanel {
        val containerRow = JPanel(BorderLayout(4, 0)).apply {
            add(containerCombo, BorderLayout.CENTER)
            add(JButton("Refresh").apply { addActionListener { refreshContainers() } }, BorderLayout.EAST)
        }

        return JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insets(3, 0)
            }
            fun label(text: String, row: Int) {
                add(JBLabel(text), gbc.also { it.gridx = 0; it.gridy = row; it.weightx = 0.0; it.insets = JBUI.insets(3, 0, 3, 8) })
            }
            fun field(comp: JComponent, row: Int) {
                add(comp, gbc.also { it.gridx = 1; it.gridy = row; it.weightx = 1.0 })
            }
            label("Container:", 0); field(containerRow, 0)
            label("Working dir:", 1); field(dockerPathField, 1)
            add(JPanel(), gbc.also { it.gridx = 0; it.gridy = 2; it.gridwidth = 2; it.weighty = 1.0; it.fill = GridBagConstraints.BOTH })
        }
    }

    private fun refreshContainers() {
        statusLabel.text = "Refreshing containers..."; statusLabel.foreground = Color(150, 150, 150)
        Thread {
            val result = runCatching {
                ProcessBuilder("docker", "ps", "--format", "{{.Names}}")
                    .redirectErrorStream(true).start()
                    .inputStream.bufferedReader().readText()
                    .trim().lines().filter { it.isNotBlank() }
            }
            SwingUtilities.invokeLater {
                result.onSuccess { names ->
                    val current = containerCombo.editor.editorComponent.let { (it as? JTextField)?.text } ?: ""
                    containerCombo.removeAllItems()
                    names.forEach { containerCombo.addItem(it) }
                    if (current.isNotBlank()) containerCombo.selectedItem = current
                    statusLabel.text = if (names.isEmpty()) "No running containers found" else "${names.size} container(s) found"
                    statusLabel.foreground = Color(100, 180, 100)
                }.onFailure {
                    statusLabel.text = "Docker error: ${it.message}"
                    statusLabel.foreground = Color(220, 80, 80)
                }
            }
        }.start()
    }

    override fun createCenterPanel(): JComponent {
        val typeRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JBLabel("Type:")); add(sshRadio); add(dockerRadio)
        }

        val nameRow = JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply { fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insets(3, 0) }
            add(JBLabel("Label:"), gbc.also { it.gridx = 0; it.weightx = 0.0; it.insets = JBUI.insets(3, 0, 3, 8) })
            add(nameField, gbc.also { it.gridx = 1; it.weightx = 1.0 })
        }

        return JPanel(BorderLayout(0, 6)).apply {
            add(JPanel(BorderLayout(0, 4)).apply {
                add(typeRow, BorderLayout.NORTH)
                add(nameRow, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(cardPanel, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
            border = JBUI.Borders.empty(4)
            preferredSize = Dimension(430, 280)
        }
    }

    override fun createLeftSideActions(): Array<Action> = arrayOf(
        object : AbstractAction("Test Connection") {
            override fun actionPerformed(e: ActionEvent) { testConnection() }
        }
    )

    private fun testConnection() {
        statusLabel.text = "Testing..."; statusLabel.foreground = Color(150, 150, 150)
        val c = buildConnection()
        Thread {
            val (ok, msg) = try {
                if (c.type == "docker") {
                    val out = ProcessBuilder("docker", "exec", c.containerName, "echo", "phppad_ok")
                        .redirectErrorStream(true).start()
                        .inputStream.bufferedReader().readText().trim()
                    (out == "phppad_ok") to (if (out == "phppad_ok") "Connected!" else "Error: $out")
                } else {
                    val jsch = com.jcraft.jsch.JSch()
                    if (c.privateKeyPath.isNotBlank()) jsch.addIdentity(c.privateKeyPath)
                    val session = jsch.getSession(c.username, c.host, c.port)
                    if (c.password.isNotBlank()) session.setPassword(c.password)
                    session.setConfig("StrictHostKeyChecking", "no")
                    session.connect(10_000)
                    val ver = session.serverVersion
                    session.disconnect()
                    true to "Connected! ($ver)"
                }
            } catch (ex: Exception) {
                false to "Error: ${ex.message}"
            }
            SwingUtilities.invokeLater {
                statusLabel.text = msg
                statusLabel.foreground = if (ok) Color(100, 200, 100) else Color(220, 80, 80)
            }
        }.start()
    }

    private fun buildConnection(): SshConnection = conn.also {
        it.type = if (dockerRadio.isSelected) "docker" else "ssh"
        it.name = nameField.text.trim().ifBlank { if (dockerRadio.isSelected) "Docker" else hostField.text }
        if (dockerRadio.isSelected) {
            it.containerName = (containerCombo.editor.editorComponent as? JTextField)?.text?.trim()
                ?: containerCombo.selectedItem?.toString() ?: ""
            it.projectPath = dockerPathField.text.trim()
        } else {
            it.host = hostField.text.trim()
            it.port = portSpinner.value as Int
            it.username = userField.text.trim()
            it.password = if (authCombo.selectedIndex == 0) String(passwordField.password) else ""
            it.privateKeyPath = if (authCombo.selectedIndex == 1) privateKeyField.text.trim() else ""
            it.projectPath = sshPathField.text.trim()
        }
    }

    fun getConnection(): SshConnection = buildConnection()
}
