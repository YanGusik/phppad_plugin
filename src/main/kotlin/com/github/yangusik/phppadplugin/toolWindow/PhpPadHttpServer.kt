package com.github.yangusik.phppadplugin.toolWindow

import com.github.yangusik.phppadplugin.executor.ExecutionResult
import com.github.yangusik.phppadplugin.services.PhpPadSettings
import com.github.yangusik.phppadplugin.services.SshConnection
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.BindException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PhpPadHttpServer(
    private val settings: PhpPadSettings,
    private val getCode: () -> String,
    private val setCode: (String) -> Unit,
    private val runCode: (code: String, conn: SshConnection, callback: (ExecutionResult) -> Unit) -> Unit
) {
    private val log = logger<PhpPadHttpServer>()
    private val gson = GsonBuilder().setLenient().create()
    private var server: HttpServer? = null

    val isRunning get() = server != null
    val port get() = server?.address?.port ?: 0

    fun start(host: String, port: Int): String? {
        stop()
        return try {
            val s = HttpServer.create(InetSocketAddress(host, port), 0)
            s.executor = Executors.newFixedThreadPool(4)
            s.createContext("/connections", ::handleConnections)
            s.createContext("/editor", ::handleEditor)
            s.createContext("/run", ::handleRun)
            s.createContext("/status", ::handleStatus)
            s.start()
            server = s
            log.info("PhpPad HTTP server started on $host:$port")
            null // no error
        } catch (e: BindException) {
            "Port $port is already in use"
        } catch (e: Exception) {
            e.message ?: "Unknown error"
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun handleConnections(ex: HttpExchange) {
        if (!cors(ex)) return
        if (!checkMethod(ex, "GET")) return
        val list = settings.connections.map {
            mapOf("id" to it.id, "name" to it.name, "type" to it.type,
                  "host" to (if (it.type == "docker") it.containerName else it.host))
        }
        respond(ex, 200, gson.toJson(list))
    }

    private fun handleEditor(ex: HttpExchange) {
        if (!cors(ex)) return
        when (ex.requestMethod) {
            "GET" -> respond(ex, 200, gson.toJson(mapOf("code" to getCode())))
            "POST" -> {
                val body = ex.requestBody.bufferedReader().readText()
                val obj = runCatching { gson.fromJson(body, Map::class.java) }.getOrNull()
                val code = obj?.get("code") as? String
                    ?: run { respond(ex, 400, """{"error":"missing 'code' field"}"""); return }
                setCode(code)
                respond(ex, 200, """{"ok":true}""")
            }
            else -> respond(ex, 405, """{"error":"method not allowed"}""")
        }
    }

    private fun handleRun(ex: HttpExchange) {
        if (!cors(ex)) return
        if (!checkMethod(ex, "POST")) return
        val body = ex.requestBody.bufferedReader().readText()
        val obj = runCatching { gson.fromJson(body, Map::class.java) }.getOrNull() ?: emptyMap<String, Any>()
        val codeArg = obj["code"] as? String
        val connArg = obj["connection"] as? String

        val conn = when {
            connArg != null -> settings.connections.find { it.name == connArg || it.id == connArg }
            else -> settings.activeConnection()
        } ?: run { respond(ex, 400, """{"error":"connection not found, pass 'connection' field"}"""); return }

        val code = codeArg ?: getCode()

        val latch = CountDownLatch(1)
        var result: ExecutionResult? = null
        runCode(code, conn) { r -> result = r; latch.countDown() }

        if (!latch.await(60, TimeUnit.SECONDS)) {
            respond(ex, 504, """{"error":"execution timeout"}"""); return
        }

        val r = result!!
        val responseBody = if (r.isError) gson.toJson(mapOf("error" to r.error))
                           else r.json!!.toString()
        respond(ex, 200, responseBody)
    }

    private fun handleStatus(ex: HttpExchange) {
        if (!cors(ex)) return
        if (!checkMethod(ex, "GET")) return
        respond(ex, 200, gson.toJson(mapOf(
            "running" to true,
            "port" to port,
            "connections" to settings.connections.size
        )))
    }

    // Returns false and sends 200 if OPTIONS preflight
    private fun cors(ex: HttpExchange): Boolean {
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        ex.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")
        if (ex.requestMethod == "OPTIONS") {
            ex.sendResponseHeaders(200, -1)
            return false
        }
        return true
    }

    private fun checkMethod(ex: HttpExchange, method: String): Boolean {
        if (ex.requestMethod != method) {
            respond(ex, 405, """{"error":"method not allowed"}""")
            return false
        }
        return true
    }

    private fun respond(ex: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }
}
