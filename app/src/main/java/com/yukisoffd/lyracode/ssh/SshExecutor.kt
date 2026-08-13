package com.yukisoffd.lyracode.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import com.jcraft.jsch.Session
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.SshServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Properties

data class SshExecutionResult(
    val ok: Boolean,
    val message: String,
)

class SshExecutor(private val settings: AppSettings) {
    fun availableServers(): String {
        val servers = settings.sshServers().filter { it.enabled }
        return JSONObject()
            .put("schema", "lyra_ssh_servers_v1")
            .put(
                "servers",
                org.json.JSONArray().also { array ->
                    servers.forEach { server ->
                        array.put(
                            JSONObject()
                                .put("id", server.stableId)
                                .put("name", server.name)
                                .put("host", server.host)
                                .put("port", server.port)
                                .put("username", server.username)
                                .put("auth_type", server.authType)
                                .put("timeout_seconds", server.timeoutSeconds),
                        )
                    }
                },
            )
            .put("note", "AI 调用 ssh_exec 时必须使用 id 字段，通常是 host:port。")
            .toString()
    }

    suspend fun execute(
        server: SshServerConfig,
        command: String,
        cwd: String,
        inputLines: List<String>,
        timeoutSeconds: Int,
    ): SshExecutionResult = withContext(Dispatchers.IO) {
        runCatching {
            require(command.isNotBlank()) { "SSH 命令不能为空" }
            require(!looksLikeDirectLogRead(command)) {
                "为避免上下文爆炸和误读日志，禁止直接读取 /var/log 或 *.log。请先用 ls/stat/du/wc -l 查看文件属性，确认范围安全后再读取小片段。"
            }
            val session = createSession(server)
            session.connect(timeoutSeconds.coerceIn(5, 600) * 1000)
            try {
                execConnected(session, command, cwd, inputLines, timeoutSeconds.coerceIn(5, 600))
            } finally {
                session.disconnect()
            }
        }.getOrElse { error ->
            SshExecutionResult(false, "SSH_ERROR: ${error.message}")
        }
    }

    internal fun createSession(server: SshServerConfig): Session {
        val jsch = JSch()
        jsch.setKnownHosts(settings.sshKnownHostsFile().absolutePath)
        if (server.authType == AppSettings.SSH_AUTH_KEY) {
            val key = server.privateKey.trim()
            require(key.isNotBlank()) { "SSH 私钥为空" }
            val passphrase = server.passphrase.takeIf { it.isNotBlank() }?.toByteArray()
            jsch.addIdentity("lyra_${server.id}", key.toByteArray(), null, passphrase)
        }
        val session = jsch.getSession(server.username, server.host, server.port)
        if (server.authType == AppSettings.SSH_AUTH_PASSWORD) {
            session.setPassword(server.password)
        }
        session.userInfo = SshUserInfo(server.password)
        session.setConfig(
            Properties().apply {
                // Trust on first use. The key is persisted in app-private storage; a changed
                // key is never accepted silently, protecting later connections from MITM.
                put("StrictHostKeyChecking", "ask")
                put("HashKnownHosts", "yes")
                put("PreferredAuthentications", if (server.authType == AppSettings.SSH_AUTH_KEY) "publickey" else "password,keyboard-interactive")
            },
        )
        session.setServerAliveInterval(30_000)
        session.setServerAliveCountMax(6)
        return session
    }

    private fun execConnected(
        session: Session,
        command: String,
        cwd: String,
        inputLines: List<String>,
        timeoutSeconds: Int,
    ): SshExecutionResult {
        val channel = session.openChannel("exec") as ChannelExec
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val fullCommand = buildCommand(command, cwd)
        channel.setCommand(fullCommand)
        channel.outputStream = stdout
        channel.setErrStream(stderr)
        val input = channel.outputStream
        val startedAt = System.currentTimeMillis()
        channel.connect(10_000)
        if (inputLines.isNotEmpty()) {
            inputLines.forEach { line ->
                input.write((line + "\n").toByteArray())
                input.flush()
                Thread.sleep(250)
            }
        }
        while (!channel.isClosed) {
            if (System.currentTimeMillis() - startedAt > timeoutSeconds * 1000L) {
                channel.disconnect()
                return SshExecutionResult(false, sshResultJson(fullCommand, -1, stdout, stderr, timeout = true))
            }
            Thread.sleep(120)
        }
        val exitCode = channel.exitStatus
        channel.disconnect()
        return SshExecutionResult(exitCode == 0, sshResultJson(fullCommand, exitCode, stdout, stderr, timeout = false))
    }

    private fun buildCommand(command: String, cwd: String): String {
        val cleanCwd = cwd.trim()
        return if (cleanCwd.isBlank()) {
            command
        } else {
            "cd ${shellQuote(cleanCwd)} && $command"
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private fun sshResultJson(
        command: String,
        exitCode: Int,
        stdout: ByteArrayOutputStream,
        stderr: ByteArrayOutputStream,
        timeout: Boolean,
    ): String {
        return JSONObject()
            .put("schema", "lyra_ssh_exec_result_v1")
            .put("command", command)
            .put("exit_code", exitCode)
            .put("timeout", timeout)
            .put("stdout", stdout.toString(Charsets.UTF_8.name()).take(MAX_OUTPUT_CHARS))
            .put("stderr", stderr.toString(Charsets.UTF_8.name()).take(MAX_OUTPUT_CHARS))
            .put("output_truncated", stdout.size() > MAX_OUTPUT_CHARS || stderr.size() > MAX_OUTPUT_CHARS)
            .toString()
    }

    private fun looksLikeDirectLogRead(command: String): Boolean {
        val lower = command.lowercase()
        val reads = listOf("cat ", "tail ", "head ", "less ", "more ", "grep ", "awk ", "sed ")
        return reads.any { lower.contains(it) } && (lower.contains("/var/log") || Regex("""\S+\.log(\s|$)""").containsMatchIn(lower))
    }

    companion object {
        private const val MAX_OUTPUT_CHARS = 120_000
    }
}

private class SshUserInfo(private val password: String) : UserInfo, UIKeyboardInteractive {
    override fun getPassword(): String = password
    override fun getPassphrase(): String? = null
    override fun promptPassword(message: String?): Boolean = password.isNotEmpty()
    override fun promptPassphrase(message: String?): Boolean = false

    override fun promptYesNo(message: String?): Boolean {
        // JSch asks once for an unknown key and separately when a stored key changed.
        // Only the former is accepted (TOFU); changed/revoked keys remain hard failures.
        return message.orEmpty().contains("can't be established", ignoreCase = true)
    }

    override fun showMessage(message: String?) = Unit

    override fun promptKeyboardInteractive(
        destination: String?,
        name: String?,
        instruction: String?,
        prompt: Array<out String>?,
        echo: BooleanArray?,
    ): Array<String>? = if (password.isNotEmpty()) {
        Array(prompt?.size ?: 0) { password }
    } else {
        null
    }
}
