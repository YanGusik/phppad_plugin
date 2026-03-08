package com.github.yangusik.phppadplugin.executor

import com.github.yangusik.phppadplugin.services.SshConnection
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

class DockerExecutor(private val conn: SshConnection) {

    private val log = logger<DockerExecutor>()

    companion object {
        private const val REMOTE_RUNNER = "/tmp/.phppad_runner.php"

        private val RUNNER_BYTES: ByteArray by lazy {
            DockerExecutor::class.java.getResourceAsStream("/phppad/runner.php")!!.readBytes()
        }

        private val RUNNER_HASH: String by lazy {
            MessageDigest.getInstance("SHA-1")
                .digest(RUNNER_BYTES)
                .joinToString("") { "%02x".format(it) }
        }
    }

    fun execute(code: String): ExecutionResult {
        return try {
            log.info("PhpPad Docker: ensuring runner in ${conn.containerName}")
            ensureRunner()
            val payload = buildPayload(code)
            val raw = execDocker("php", REMOTE_RUNNER, payload)
            log.info("PhpPad Docker: raw output (first 300): ${raw.take(300)}")
            parseResult(raw)
        } catch (e: Exception) {
            log.warn("PhpPad Docker: exception", e)
            ExecutionResult(error = e.message ?: "Docker error")
        }
    }

    private fun ensureRunner() {
        val remoteHash = runCatching {
            execDocker("sh", "-c", "sha1sum $REMOTE_RUNNER 2>/dev/null | awk '{print \$1}'").trim()
        }.getOrDefault("")

        if (remoteHash.lowercase() != RUNNER_HASH) {
            val tmp = File.createTempFile("phppad_runner", ".php")
            try {
                tmp.writeBytes(RUNNER_BYTES)
                execRaw(listOf("docker", "cp", tmp.absolutePath, "${conn.containerName}:$REMOTE_RUNNER"))
            } finally {
                tmp.delete()
            }
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

    private fun execDocker(vararg args: String): String =
        execRaw(listOf("docker", "exec", conn.containerName) + args.toList())

    private fun execRaw(cmd: List<String>): String {
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        return output
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
