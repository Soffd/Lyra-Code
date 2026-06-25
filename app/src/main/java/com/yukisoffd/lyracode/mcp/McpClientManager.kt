package com.yukisoffd.lyracode.mcp

import android.content.Context
import com.yukisoffd.lyracode.R
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.McpServerConfig
import com.yukisoffd.lyracode.data.McpToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class McpCallResult(
    val serverName: String,
    val toolName: String,
    val content: String,
)

class McpClientManager(private val context: Context, private val settings: AppSettings) {
    private val job = SupervisorJob()
    private val requestIds = AtomicLong(1L)
    private val sessions = ConcurrentHashMap<String, String>()

    suspend fun testAndRefreshTools(server: McpServerConfig): Result<List<McpToolDefinition>> = runCatching {
        initialize(server)
        val response = sendRpc(server, "tools/list", JSONObject())
        val tools = parseTools(response)
        settings.updateMcpServerTools(server.id, tools)
        tools
    }

    suspend fun callTool(server: McpServerConfig, tool: McpToolDefinition, arguments: JSONObject): McpCallResult {
        initialize(server)
        val params = JSONObject()
            .put("name", tool.name)
            .put("arguments", arguments)
        val response = sendRpc(server, "tools/call", params)
        return McpCallResult(server.name, tool.name, response.toString())
    }

    private suspend fun initialize(server: McpServerConfig) {
        if (sessions.containsKey(server.id)) return
        val params = JSONObject()
            .put("protocolVersion", protocolVersion(server))
            .put(
                "capabilities",
                JSONObject()
                    .put("roots", JSONObject().put("listChanged", false))
                    .put("sampling", JSONObject()),
            )
            .put(
                "clientInfo",
                JSONObject()
                    .put("name", "Lyra Code Android")
                    .put("version", "1"),
            )
        sendRpc(server, "initialize", params)
        runCatching { sendNotification(server, "notifications/initialized", JSONObject()) }
    }

    private suspend fun sendNotification(server: McpServerConfig, method: String, params: JSONObject) {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", method)
            .put("params", params)
        execute(server, body)
    }

    private suspend fun sendRpc(server: McpServerConfig, method: String, params: JSONObject): JSONObject {
        val id = requestIds.getAndIncrement()
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        val response = execute(server, body)
        if (response.has("error")) {
            error(context.getString(R.string.error_mcp_method_failed, server.name, method, (response.optJSONObject("error") ?: response.optString("error")).toString()))
        }
        return response.optJSONObject("result") ?: response
    }

    private suspend fun execute(server: McpServerConfig, body: JSONObject): JSONObject = withContext(Dispatchers.IO + job) {
        val endpoint = endpointUrl(server)
        require(endpoint.startsWith("http://", ignoreCase = true) || endpoint.startsWith("https://", ignoreCase = true)) {
            context.getString(R.string.error_mcp_url_invalid)
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(server.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(server.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(server.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "application/json, text/event-stream")
            .addHeader("Content-Type", "application/json")
            .addHeader("Mcp-Protocol-Version", protocolVersion(server))
        effectiveAuthKey(server).takeIf { it.isNotBlank() }?.let {
            requestBuilder.addHeader("Authorization", if (it.startsWith("Bearer ", true)) it else "Bearer $it")
        }
        sessions[server.id]?.let { requestBuilder.addHeader("Mcp-Session-Id", it) }
        extraHeaders(server).forEach { (name, value) -> requestBuilder.addHeader(name, value) }
        client.newCall(requestBuilder.build()).execute().use { response ->
            response.header("Mcp-Session-Id")?.takeIf { it.isNotBlank() }?.let { sessions[server.id] = it }
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${text.take(1000)}")
            parseRpcResponse(text)
        }
    }

    private fun parseTools(result: JSONObject): List<McpToolDefinition> {
        val tools = result.optJSONArray("tools") ?: JSONArray()
        return buildList {
            for (index in 0 until tools.length()) {
                val item = tools.getJSONObject(index)
                add(
                    McpToolDefinition(
                        name = item.optString("name"),
                        description = item.optString("description"),
                        inputSchema = item.optJSONObject("inputSchema")?.toString() ?: "{}",
                    ),
                )
            }
        }.filter { it.name.isNotBlank() }
    }

    private fun parseRpcResponse(text: String): JSONObject {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return JSONObject(trimmed)
        if (trimmed.startsWith("event:") || trimmed.startsWith("data:")) {
            val data = trimmed.lineSequence()
                .filter { it.startsWith("data:") }
                .joinToString("\n") { it.removePrefix("data:").trim() }
                .trim()
            if (data.isNotBlank() && data != "[DONE]") return JSONObject(data)
        }
        error(context.getString(R.string.error_mcp_parse_failed, trimmed.take(500)))
    }

    private fun protocolVersion(server: McpServerConfig): String {
        return runCatching { JSONObject(server.rawJson).optString("protocolVersion") }
            .getOrNull()
            .orEmpty()
            .ifBlank { "2025-06-18" }
    }

    private fun extraHeaders(server: McpServerConfig): Map<String, String> {
        val node = rawServerNode(server)
        val root = runCatching { JSONObject(server.rawJson) }.getOrNull()
        val headers = node?.optJSONObject("headers") ?: root?.optJSONObject("headers") ?: return emptyMap()
        return headers.keys().asSequence().associateWith { headers.optString(it) }
            .filterValues { it.isNotBlank() }
            .filterKeys { !it.equals("Authorization", ignoreCase = true) }
    }

    private fun endpointUrl(server: McpServerConfig): String {
        val node = rawServerNode(server)
        return node?.optString("baseUrl")
            ?.ifBlank { node.optString("url") }
            ?.ifBlank { server.url }
            ?: server.url
    }

    private fun effectiveAuthKey(server: McpServerConfig): String {
        val node = rawServerNode(server)
        val headers = node?.optJSONObject("headers") ?: runCatching { JSONObject(server.rawJson).optJSONObject("headers") }.getOrNull()
        return headers?.optString("Authorization").orEmpty().removePrefix("Bearer ").trim().ifBlank { server.authKey }
    }

    private fun rawServerNode(server: McpServerConfig): JSONObject? {
        val root = runCatching { JSONObject(server.rawJson) }.getOrNull() ?: return null
        val servers = root.optJSONObject("mcpServers") ?: return root
        val keys = servers.keys()
        if (!keys.hasNext()) return root
        return servers.optJSONObject(keys.next()) ?: root
    }
}
