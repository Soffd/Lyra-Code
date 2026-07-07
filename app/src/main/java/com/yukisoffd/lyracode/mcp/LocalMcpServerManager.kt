package com.yukisoffd.lyracode.mcp

import com.yukisoffd.lyracode.ai.OpenAiAgent
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.LocalMcpServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class LocalMcpServerStatus(
    val running: Boolean,
    val host: String,
    val port: Int,
    val url: String,
    val lanUrls: List<String>,
    val startedAt: Long,
    val message: String,
)

class LocalMcpServerManager(private val settings: AppSettings) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var currentConfig: LocalMcpServerConfig = settings.localMcpServerConfig()
    @Volatile private var agent: OpenAiAgent? = null
    @Volatile private var startedAt: Long = 0L
    @Volatile private var message: String = "未启动"

    fun attachAgent(agent: OpenAiAgent) {
        this.agent = agent
    }

    fun status(): LocalMcpServerStatus {
        val config = currentConfig
        return LocalMcpServerStatus(
            running = running.get(),
            host = config.host,
            port = config.port,
            url = serviceUrl(config),
            lanUrls = lanUrls(config.port),
            startedAt = startedAt,
            message = message,
        )
    }

    fun statusJson(): JSONObject {
        val status = status()
        return JSONObject()
            .put("running", status.running)
            .put("host", status.host)
            .put("port", status.port)
            .put("url", status.url)
            .put("lanUrls", JSONArray(status.lanUrls))
            .put("startedAt", status.startedAt)
            .put("message", status.message)
    }

    @Synchronized
    fun syncWithSettings(): LocalMcpServerStatus {
        val config = settings.localMcpServerConfig()
        return if (config.enabled) start(config) else stop(saveDisabled = false)
    }

    @Synchronized
    fun start(config: LocalMcpServerConfig = settings.localMcpServerConfig()): LocalMcpServerStatus {
        if (running.get() && currentConfig == config) return status()
        stop(saveDisabled = false)
        val normalized = config.copy(
            host = config.host.ifBlank { AppSettings.DEFAULT_LOCAL_MCP_SERVER_HOST },
            port = config.port.coerceIn(1, 65535),
            enabled = true,
        )
        currentConfig = normalized
        return runCatching {
            val socket = ServerSocket(normalized.port, 50, java.net.InetAddress.getByName(normalized.host))
            serverSocket = socket
            running.set(true)
            startedAt = System.currentTimeMillis()
            message = "运行中"
            settings.saveLocalMcpServerConfig(normalized)
            scope.launch {
                while (running.get()) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClient(client, normalized) }
                }
            }
            status()
        }.getOrElse { error ->
            running.set(false)
            message = error.message ?: "启动失败"
            status()
        }
    }

    @Synchronized
    fun stop(saveDisabled: Boolean = true): LocalMcpServerStatus {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        message = "已停止"
        if (saveDisabled) {
            val disabled = settings.localMcpServerConfig().copy(enabled = false)
            currentConfig = disabled
            settings.saveLocalMcpServerConfig(disabled)
        }
        return status()
    }

    fun close() {
        stop(saveDisabled = false)
        scope.cancel()
    }

    private fun handleClient(socket: Socket, config: LocalMcpServerConfig) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            if (requestLine.isBlank()) return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0).orEmpty().uppercase(Locale.US)
            val path = parts.getOrNull(1).orEmpty()
            val headers = readHeaders(reader)
            when {
                method == "OPTIONS" -> writeResponse(client.getOutputStream(), 204, "")
                method == "GET" -> writeJson(client.getOutputStream(), statusJson())
                method == "POST" && path.substringBefore("?") == "/mcp" -> {
                    if (!authorized(config, headers)) {
                        writeJson(client.getOutputStream(), rpcError(null, -32001, "未授权"), 401)
                        return
                    }
                    val length = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val body = readBody(reader, length)
                    val response = handleRpc(body)
                    if (response == null) {
                        writeResponse(client.getOutputStream(), 202, "")
                    } else {
                        writeJson(client.getOutputStream(), response)
                    }
                }
                else -> writeJson(client.getOutputStream(), rpcError(null, -32600, "只支持 POST /mcp"), 404)
            }
        }
    }

    private fun readBody(reader: BufferedReader, length: Int): String {
        if (length <= 0) return ""
        val buffer = CharArray(length)
        var offset = 0
        while (offset < length) {
            val read = reader.read(buffer, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return buffer.concatToString(0, offset)
    }

    private fun handleRpc(body: String): JSONObject? {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return rpcError(null, -32700, "JSON 解析失败")
        val hasId = payload.has("id")
        val id = if (hasId) payload.opt("id") else null
        val method = payload.optString("method")
        val params = payload.optJSONObject("params") ?: JSONObject()
        val result = when (method) {
            "initialize" -> JSONObject()
                .put("protocolVersion", "2025-06-18")
                .put("capabilities", JSONObject().put("tools", JSONObject().put("listChanged", true)))
                .put("serverInfo", JSONObject().put("name", "Lyra Code").put("version", "1"))
            "notifications/initialized" -> return null
            "tools/list" -> JSONObject().put("tools", toolsForMcp())
            "tools/call" -> callTool(params)
            else -> return if (hasId) rpcError(id, -32601, "未知 MCP 方法: $method") else null
        }
        if (!hasId) return null
        return JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("result", result)
    }

    private fun toolsForMcp(): JSONArray {
        val definitions = agent?.localMcpToolDefinitions() ?: JSONArray()
        val tools = JSONArray()
        for (index in 0 until definitions.length()) {
            val function = definitions.optJSONObject(index)?.optJSONObject("function") ?: continue
            val name = function.optString("name")
            if (name.isBlank()) continue
            tools.put(
                JSONObject()
                    .put("name", name)
                    .put("description", function.optString("description"))
                    .put("inputSchema", function.optJSONObject("parameters") ?: JSONObject().put("type", "object")),
            )
        }
        return tools
    }

    private fun callTool(params: JSONObject): JSONObject {
        val name = params.optString("name")
        if (name.isBlank()) return toolResult("缺少工具名称", isError = true)
        val args = params.optJSONObject("arguments") ?: JSONObject()
        val output = runBlocking(Dispatchers.IO) {
            agent?.executeLocalMcpTool(name, args) ?: """{"ok":false,"error":"本机 MCP 服务端尚未绑定 Agent"}"""
        }
        val failed = runCatching { JSONObject(output).optBoolean("ok", true).not() }.getOrDefault(false)
        return toolResult(output, failed)
    }

    private fun toolResult(text: String, isError: Boolean): JSONObject {
        return JSONObject()
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
            .put("isError", isError)
    }

    private fun rpcError(id: Any?, code: Int, message: String): JSONObject {
        return JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("error", JSONObject().put("code", code).put("message", message))
    }

    private fun readHeaders(reader: BufferedReader): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase(Locale.US)] = line.substring(separator + 1).trim()
            }
        }
        return headers
    }

    private fun authorized(config: LocalMcpServerConfig, headers: Map<String, String>): Boolean {
        val key = config.authKey.trim()
        if (key.isBlank()) return true
        val authorization = headers["authorization"].orEmpty()
        val bearerKey = authorization.substringAfter(' ', "").trim().takeIf {
            authorization.substringBefore(' ').equals("Bearer", ignoreCase = true)
        }.orEmpty()
        val xKey = headers["x-lyra-mcp-key"].orEmpty()
        val xApiKey = headers["x-api-key"].orEmpty()
        val apiKey = headers["api-key"].orEmpty()
        return authorization == key ||
            bearerKey == key ||
            xKey == key ||
            xApiKey == key ||
            apiKey == key
    }

    private fun writeJson(output: OutputStream, json: JSONObject, status: Int = 200) {
        writeResponse(output, status, json.toString())
    }

    private fun writeResponse(output: OutputStream, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            204 -> "No Content"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "OK"
        }
        output.write(
            buildString {
                append("HTTP/1.1 $status $reason\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Headers: Content-Type, Authorization, X-Lyra-MCP-Key, X-API-Key, Api-Key, Mcp-Protocol-Version\r\n")
                append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.UTF_8),
        )
        output.write(bytes)
        output.flush()
    }

    private fun serviceUrl(config: LocalMcpServerConfig): String {
        val host = when (config.host) {
            "0.0.0.0", "::" -> "127.0.0.1"
            else -> config.host
        }
        return "http://$host:${config.port}/mcp"
    }

    private fun lanUrls(port: Int): List<String> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress }
                .map { "http://${it.hostAddress}:$port/mcp" }
                .distinct()
        }.getOrDefault(emptyList())
    }
}
