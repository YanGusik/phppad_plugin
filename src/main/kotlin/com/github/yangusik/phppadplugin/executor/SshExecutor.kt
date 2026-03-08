package com.github.yangusik.phppadplugin.executor

import com.github.yangusik.phppadplugin.services.SshConnection
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

class SshExecutor(private val conn: SshConnection) {

    private val log = logger<SshExecutor>()

    companion object {
        private const val REMOTE_RUNNER = "/tmp/.phppad_runner.php"

        private val RUNNER_BYTES: ByteArray by lazy {
            SshExecutor::class.java.getResourceAsStream("/phppad/runner.php")!!.readBytes()
        }

        private val RUNNER_HASH: String by lazy {
            MessageDigest.getInstance("SHA-1")
                .digest(RUNNER_BYTES)
                .joinToString("") { "%02x".format(it) }
        }
    }

    fun execute(code: String): ExecutionResult {
        val jsch = JSch()
        if (conn.privateKeyPath.isNotBlank()) {
            jsch.addIdentity(conn.privateKeyPath)
        }
        val session = jsch.getSession(conn.username, conn.host, conn.port)
        if (conn.password.isNotBlank()) {
            session.setPassword(conn.password)
        }
        session.setConfig("StrictHostKeyChecking", "no")

        return try {
            log.info("PhpPad SSH: connecting...")
            session.connect(15_000)
            log.info("PhpPad SSH: connected, deploying runner...")
            ensureRunner(session)
            log.info("PhpPad SSH: runner ready, executing code...")
            val payload = buildPayload(code)
            val raw = execCommand(session, "php $REMOTE_RUNNER '$payload' 2>&1")
            log.info("PhpPad SSH: raw output (first 300 chars): ${raw.take(300)}")
            parseResult(raw)
        } catch (e: Exception) {
            log.warn("PhpPad SSH: exception", e)
            ExecutionResult(error = e.message ?: "SSH error")
        } finally {
            runCatching { session.disconnect() }
        }
    }

    private fun buildPayload(code: String): String {
        val escaped = code
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        val pathEscaped = conn.projectPath.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """{"code":"$escaped","projectPath":"$pathEscaped"}"""
        return Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
    }

    private fun ensureRunner(session: Session) {
        val remoteHash = runCatching {
            execCommand(session, "sha1sum $REMOTE_RUNNER 2>/dev/null | awk '{print \$1}'").trim()
        }.getOrDefault("")

        if (remoteHash.lowercase() != RUNNER_HASH) {
            val sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10_000)
            try {
                sftp.put(ByteArrayInputStream(RUNNER_BYTES), REMOTE_RUNNER)
            } finally {
                sftp.disconnect()
            }
        }
    }

    private fun execCommand(session: Session, command: String): String {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        val out = ByteArrayOutputStream()
        channel.setOutputStream(out)
        channel.connect(30_000)
        while (!channel.isClosed) Thread.sleep(50)
        channel.disconnect()
        return out.toString(Charsets.UTF_8.name())
    }

    private fun parseResult(raw: String): ExecutionResult {
        val marker = "__TWLITE_JSON__"
        val idx = raw.indexOf(marker)
        if (idx == -1) return ExecutionResult(error = "Runner output:\n$raw")
        val jsonStr = raw.substring(idx + marker.length).trim()
        return try {
            ExecutionResult(json = JsonParser.parseString(jsonStr).asJsonObject)
        } catch (e: Exception) {
            ExecutionResult(error = "JSON parse failed: ${e.message}\n$jsonStr")
        }
    }
}
