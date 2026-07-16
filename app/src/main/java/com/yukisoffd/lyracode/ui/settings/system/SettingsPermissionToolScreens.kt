package com.yukisoffd.lyracode

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebView
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.system.SystemCommandExecutor
import com.yukisoffd.lyracode.termux.TermuxExecutor
import com.yukisoffd.lyracode.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import java.net.URL
import rikka.shizuku.Shizuku

internal fun requestTermuxRunCommandPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    if (context.checkSelfPermission(MainActivity.TERMUX_RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED) return
    (context as? Activity)?.requestPermissions(arrayOf(MainActivity.TERMUX_RUN_COMMAND_PERMISSION), 1001)
}



@Composable
internal fun SystemPermissionSettings(
    settings: AppSettings,
    executor: SystemCommandExecutor,
) {
    val scope = rememberCoroutineScope()
    var rootEnabled by remember { mutableStateOf(settings.requestRootAccess) }
    var shellEnabled by remember { mutableStateOf(settings.requestShellAccess) }
    var suCommand by remember { mutableStateOf(settings.customSuCommand) }
    var revision by remember { mutableIntStateOf(0) }
    var rootStatus by remember { mutableStateOf(uiText("尚未检测")) }
    val shizukuRunning = remember(revision) { executor.isShizukuRunning() }
    val shellGranted = remember(revision) { executor.hasShellPermission() }
    val permissionListener = remember {
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) revision++
        }
    }
    val binderReceivedListener = remember {
        Shizuku.OnBinderReceivedListener { revision++ }
    }
    val binderDeadListener = remember {
        Shizuku.OnBinderDeadListener { revision++ }
    }
    DisposableEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
    }
    KimiCardBox {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(uiText("请求 Root 权限"), style = MaterialTheme.typography.titleSmall)
                Text(
                    uiText("通过 Magisk、KernelSU 等 su 管理器授权。不可用时可回退到已授权的 Shell。"),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = rootEnabled,
                onCheckedChange = {
                    rootEnabled = it
                    settings.requestRootAccess = it
                },
            )
        }
        KimiDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(uiText("请求 Shell 权限"), style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        shellGranted -> uiText("Shizuku Shell 已授权")
                        shizukuRunning -> uiText("Shizuku 正在运行，开启后请求授权")
                        else -> uiText("需要先通过无线调试或电脑 ADB 启动 Shizuku")
                    },
                    color = if (shellGranted) MaterialTheme.colorScheme.primary else KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = shellEnabled,
                onCheckedChange = { enabled ->
                    shellEnabled = enabled
                    settings.requestShellAccess = enabled
                    if (enabled && shizukuRunning && !shellGranted) {
                        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    }
                },
            )
        }
    }
    KimiCardBox {
        OutlinedTextField(
            value = suCommand,
            onValueChange = {
                suCommand = it
                settings.customSuCommand = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(uiText("自定义 su 命令")) },
            supportingText = {
                Text(uiText("默认 su -c。可用 {command} 指定命令插入位置，例如 su 0 sh -c {command}。"))
            },
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    if (shizukuRunning && !shellGranted) {
                        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    } else {
                        revision++
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (shellGranted) uiText("Shell 已授权") else uiText("授权 Shell"))
            }
            OutlinedButton(
                onClick = {
                    rootStatus = uiText("检测中...")
                    scope.launch {
                        val result = executor.probeRoot()
                        rootStatus = if (result.ok && result.stdout.trim().lineSequence().lastOrNull() == "0") {
                            uiText("Root 可用")
                        } else {
                            uiText("Root 不可用：${result.stderr.ifBlank { result.message }.take(120)}")
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(uiText("检测 Root"))
            }
        }
        Text(rootStatus, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        KimiDivider()
        SettingsExternalLinkRow(
            icon = Icons.Default.Link,
            title = "Shizuku GitHub",
            subtitle = "RikkaApps/Shizuku",
            url = "https://github.com/RikkaApps/Shizuku",
        )
    }
    Text(
        uiText("Root 和 Shell 开关都关闭时，AI 不会看到任何系统命令工具。所有 Shell/Root 命令都会先显示完整命令并请求确认；Root 命令风险更高。普通 ADB 不会永久赋予应用 shell 身份，本应用通过 Shizuku 获取该能力。"),
        color = KimiMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 2300

@Composable
internal fun PermissionSettings(termuxExecutor: TermuxExecutor) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val permissions = remember(context, termuxExecutor, revision) {
        appPermissionRows(context, termuxExecutor)
    }
    KimiCardBox {
        permissions.forEachIndexed { index, row ->
            val displayStatus = if (row.title == uiText("读取应用列表")) {
                row.status
            } else if (row.granted) {
                uiText("已允许")
            } else {
                row.status
            }
            KimiMenuRow(row.icon, row.title, displayStatus) {
                if (row.title == uiText("与 Termux 通信")) {
                    requestTermuxRunCommandPermission(context)
                    revision++
                } else {
                    openAppSettings(context)
                }
            }
            if (index != permissions.lastIndex) KimiDivider()
        }
    }
    Text(uiText("媒体、定位、通知、摄像头等权限会跳转系统应用信息页；Termux 通信权限由 Termux 提供，点击后直接弹出授权许可。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun AgentToolSettings(settings: AppSettings, termuxExecutor: TermuxExecutor, externalRevision: Int = 0) {
    var disabled by remember(externalRevision) { mutableStateOf(settings.disabledTools()) }
    var query by rememberSaveable { mutableStateOf("") }
    var showReachabilityPage by rememberSaveable { mutableStateOf(false) }
    val localTools = agentToolCatalog()
    val mcpTools = remember(disabled, externalRevision) { settings.enabledMcpTools() }
    val sshToolsEnabled = remember(disabled, externalRevision) { settings.sshServers().any { it.enabled } }
    val termuxGranted = termuxExecutor.hasRunCommandPermission()
    fun matches(text: String): Boolean = query.isBlank() || text.contains(query.trim(), ignoreCase = true)
    val filteredLocalTools = remember(localTools, query) {
        localTools.filter { matches("${it.title}\n${it.name}\n${it.description}") }
    }
    val filteredMcpTools = remember(mcpTools, query) {
        mcpTools.filter { (server, tool) ->
            matches("MCP ${server.name} ${tool.name} ${tool.description} ${settings.mcpToolFunctionName(server, tool)}")
        }
    }
    KimiCardBox {
        Text(uiText("搜索工具"), style = MaterialTheme.typography.titleSmall)
        CapsuleTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = uiText("搜索名称、工具名或描述"),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
        )
        Text(uiText("匹配 ${filteredLocalTools.size + filteredMcpTools.size} / ${localTools.size + mcpTools.size} 个工具"), color = KimiMuted, style = MaterialTheme.typography.labelSmall)
    }
    KimiCardBox {
        if (filteredLocalTools.isEmpty() && filteredMcpTools.isEmpty()) {
            Text(uiText("没有匹配的工具"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        filteredLocalTools.forEachIndexed { index, tool ->
            val lockedByPermission = tool.name == "run_command" && !termuxGranted
            val protectedTool = tool.name == "manage_app_config"
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tool.title, style = MaterialTheme.typography.titleSmall)
                    Text(tool.name, color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                    Text(tool.description, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    if (lockedByPermission) {
                        Text(uiText("未授予 Termux RUN_COMMAND 权限，工具已自动禁用。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    if (tool.name == "ssh_exec" && !sshToolsEnabled) {
                        Text(uiText("未配置启用的 SSH 连接，工具暂不可用。"), color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    if (protectedTool) {
                        Text(uiText("保护工具，不能禁用。"), color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(
                    checked = protectedTool || (!lockedByPermission && tool.name !in disabled),
                    enabled = !lockedByPermission && !protectedTool,
                    onCheckedChange = { enabled ->
                        settings.setToolEnabled(tool.name, enabled)
                        disabled = settings.disabledTools()
                    },
                )
            }
            if (index != filteredLocalTools.lastIndex || filteredMcpTools.isNotEmpty()) KimiDivider()
        }
        filteredMcpTools.forEachIndexed { index, (server, tool) ->
            val functionName = settings.mcpToolFunctionName(server, tool)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("MCP · ${server.name} / ${tool.name}", style = MaterialTheme.typography.titleSmall)
                    Text(functionName, color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                    Text(tool.description.ifBlank { uiText("远程 MCP 工具") }, color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Switch(
                    checked = functionName !in disabled,
                    onCheckedChange = { enabled ->
                        settings.setToolEnabled(functionName, enabled)
                        disabled = settings.disabledTools()
                    },
                )
            }
            if (index != filteredMcpTools.lastIndex) KimiDivider()
        }
    }
}

@Composable
internal fun TermuxSettings(settings: AppSettings, termuxExecutor: TermuxExecutor, workspaceManager: WorkspaceManager) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val permissionGranted = remember(revision) { termuxExecutor.hasRunCommandPermission() }
    KimiCardBox {
        KimiMenuRow(Icons.Default.Terminal, "Termux", if (termuxExecutor.isTermuxInstalled()) uiText("已安装") else uiText("未安装"))
        KimiDivider()
        KimiMenuRow(Icons.Default.Extension, "Termux:API", if (termuxExecutor.isTermuxApiInstalled()) uiText("可用") else uiText("未安装"))
        KimiDivider()
        KimiMenuRow(Icons.Default.CheckCircle, uiText("RUN_COMMAND 权限"), if (permissionGranted) uiText("已授予") else uiText("点击授予")) {
            requestTermuxRunCommandPermission(context)
            revision++
        }
        KimiDivider()
        KimiMenuRow(Icons.Default.Folder, uiText("Termux 路径"), workspaceManager.termuxRootPath() ?: uiText("仅 primary"))
    }
    TermuxSetupGuide()
}

internal data class PermissionRow(
    val icon: ImageVector,
    val title: String,
    val granted: Boolean,
    val status: String,
)

internal fun appPermissionRows(context: Context, termuxExecutor: TermuxExecutor): List<PermissionRow> {
    fun granted(permission: String): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        granted(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        true
    }
    val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        granted(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }
    val locationGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    return listOf(
        PermissionRow(Icons.Default.PhotoLibrary, uiText("访问手机媒体文件"), mediaGranted, uiText("未允许")),
        PermissionRow(Icons.Default.LocationOn, uiText("位置信息"), locationGranted, uiText("未允许")),
        PermissionRow(Icons.Default.PhotoCamera, uiText("摄像头"), granted(Manifest.permission.CAMERA), uiText("未允许")),
        PermissionRow(Icons.Default.Notifications, uiText("通知"), notificationGranted, uiText("未允许")),
        PermissionRow(Icons.Default.Apps, uiText("读取应用列表"), true, uiText("已声明")),
        PermissionRow(Icons.Default.Terminal, uiText("与 Termux 通信"), termuxExecutor.hasRunCommandPermission(), uiText("点击授予")),
    )
}

internal fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

internal data class AgentToolInfo(
    val name: String,
    val title: String,
    val description: String,
)

internal fun agentToolCatalog(): List<AgentToolInfo> = listOf(
    AgentToolInfo("list_directory", uiText("列出目录"), uiText("浏览工作目录内文件和子目录。")),
    AgentToolInfo("read_file", uiText("读取文件"), uiText("读取工作目录内文本文件。")),
    AgentToolInfo("read_file_lines", uiText("分段读取文件"), uiText("按真实行号读取大文件的指定行范围。")),
    AgentToolInfo("write_file", uiText("写入文件"), uiText("创建或整体覆盖工作目录内文本文件。")),
    AgentToolInfo("edit_file", uiText("精确编辑文件"), uiText("按唯一原文或指定行范围局部修改工作区文本文件。")),
    AgentToolInfo("append_file", uiText("追加文件"), uiText("在现有文件末尾追加文本。")),
    AgentToolInfo("create_folder", uiText("创建目录"), uiText("在工作目录内创建文件夹。")),
    AgentToolInfo("delete_file_or_folder", uiText("删除文件/目录"), uiText("删除工作目录内文件或空目录。")),
    AgentToolInfo("rename_move", uiText("重命名/移动"), uiText("调整工作目录内文件路径。")),
    AgentToolInfo("global_list_directory", uiText("全局列目录"), uiText("列出 Android 共享存储目录，支持 Download。")),
    AgentToolInfo("global_read_file", uiText("全局读取文件"), uiText("读取工作区外共享存储内的文本文件。")),
    AgentToolInfo("global_read_file_lines", uiText("全局分段读取文件"), uiText("按真实行号读取共享存储大文件的指定行范围。")),
    AgentToolInfo("global_write_file", uiText("全局写入文件"), uiText("创建或整体覆盖工作区外共享存储文件，执行前需要用户确认。")),
    AgentToolInfo("global_edit_file", uiText("全局精确编辑文件"), uiText("按唯一原文或指定行范围局部修改共享存储文件，执行前需要用户确认。")),
    AgentToolInfo("global_append_file", uiText("全局追加文件"), uiText("追加工作区外共享存储文件，执行前需要用户确认。")),
    AgentToolInfo("global_create_folder", uiText("全局创建目录"), uiText("在工作区外共享存储创建目录，执行前需要用户确认。")),
    AgentToolInfo("global_delete_file_or_folder", uiText("全局删除文件/目录"), uiText("删除工作区外共享存储内容，执行前需要用户确认。")),
    AgentToolInfo("global_rename_move", uiText("全局移动/重命名"), uiText("移动工作区外共享存储内容，执行前需要用户确认。")),
    AgentToolInfo("download_file", uiText("下载文件"), uiText("使用应用原生 HTTP/HTTPS 客户端下载到工作区或共享存储，支持请求头和 SHA-256 校验。")),
    AgentToolInfo("manage_scheduled_tasks", uiText("定时任务"), uiText("列出或管理一次性、每日、每周和每月后台 AI 任务。")),
    AgentToolInfo("get_mini_server_status", uiText("微型服务器状态"), uiText("读取内置 HTTP 静态服务器状态和访问地址。")),
    AgentToolInfo("read_mini_server_logs", uiText("终端日志读取"), uiText("读取微型服务器连接、资源加载和页面错误日志，便于自动化调试。")),
    AgentToolInfo("manage_mini_server", uiText("微型服务器控制"), uiText("启动、停止、重启或修改工作区静态站点服务，执行前需要用户确认。")),
    AgentToolInfo("search_conversation_history", uiText("搜索会话记录"), uiText("跨普通会话按关键词和时间段搜索历史记录，不读取思维链或工具日志。")),
    AgentToolInfo("read_conversation_history", uiText("读取会话记录"), uiText("读取指定历史会话的用户消息和 AI 最终回复，用于总结与趋势分析。")),
    AgentToolInfo("read_memories", uiText("读取记忆"), uiText("读取跨对话个性化记忆及其标识，用于核对或修改。")),
    AgentToolInfo("save_memory", uiText("保存记忆"), uiText("保存长期有用的用户偏好、工作风格或沟通习惯。")),
    AgentToolInfo("update_memory", uiText("修改记忆"), uiText("修改、纠正或停用已有个性化记忆。")),
    AgentToolInfo("delete_memory", uiText("删除记忆"), uiText("删除不再适用或用户要求忘记的个性化记忆。")),
    AgentToolInfo("search_files", uiText("工作区搜索"), uiText("按文件名或路径片段搜索工作区。")),
    AgentToolInfo("global_search_files", uiText("全局文件搜索"), uiText("搜索 Android 共享存储中的文件路径。")),
    AgentToolInfo("get_file_info", uiText("文件信息"), uiText("读取文件大小、修改时间等元数据。")),
    AgentToolInfo("list_skill_files", uiText("列出 Skill 文件"), uiText("浏览已启用 Skill 包内文件。")),
    AgentToolInfo("read_skill_file", uiText("读取 Skill 文件"), uiText("读取相关 Skill 包内说明或脚本。")),
    AgentToolInfo("run_command", uiText("执行命令"), uiText("通过 Termux 执行命令并返回 stdout/stderr。")),
    AgentToolInfo("web_search", uiText("联网搜索"), uiText("使用内嵌 WebView 搜索互联网。")),
    AgentToolInfo("read_web_page", uiText("读取网页"), uiText("读取 http/https 网页正文。")),
    AgentToolInfo("mark_web_sources", uiText("网页来源标注"), uiText("声明网页引用来源，并要求最终回答就近标注来源链接。")),
    AgentToolInfo("manage_app_config", uiText("配置管理"), uiText("通过用户确认后添加、修改、启用、禁用或删除 MCP、SSH、WebDAV、Skills 与其他 Agent 工具配置。")),
    AgentToolInfo("get_current_time", uiText("时间感知"), uiText("读取设备当前时间和时区。")),
    AgentToolInfo("get_current_location", uiText("地理感知"), uiText("读取设备最近系统定位。")),
    AgentToolInfo("get_device_hardware_info", uiText("硬件检查"), uiText("读取设备系统、CPU、内存、存储、分辨率、网络、蓝牙、电池等诊断信息。")),
    AgentToolInfo("list_installed_apps", uiText("应用列表识别"), uiText("读取用户应用和系统应用的名称、包名、版本、大小及签名证书 SHA-256。")),
    AgentToolInfo("execute_shell_command", uiText("Shell 系统命令"), uiText("通过 Shizuku 以 Android shell 身份执行系统命令，每次执行前都需要用户确认。")),
    AgentToolInfo("execute_root_command", uiText("Root 系统命令"), uiText("通过自定义 su 命令执行 Root 命令，每次执行前都需要用户确认；不可用时可按设置回退到 Shell。")),
    AgentToolInfo("list_ssh_servers", uiText("列出 SSH 连接"), uiText("查看用户已配置且启用的 SSH 服务器标识。")),
    AgentToolInfo("ssh_exec", uiText("SSH 执行命令"), uiText("登录远程服务器执行命令并返回 stdout/stderr，执行前需要用户确认。")),
    AgentToolInfo("list_webdav_servers", uiText("列出 WebDAV"), uiText("查看用户已配置且启用的 WebDAV 服务器标识。")),
    AgentToolInfo("webdav_list", uiText("WebDAV 列目录"), uiText("通过 PROPFIND 列出 WebDAV 目录文件详情。")),
    AgentToolInfo("webdav_search", uiText("WebDAV 搜索"), uiText("搜索 WebDAV 服务器上的文件路径。")),
    AgentToolInfo("webdav_download_to_workspace", uiText("WebDAV 下载"), uiText("从 WebDAV 下载文件到工作区，执行前需要用户确认。")),
    AgentToolInfo("webdav_upload_from_workspace", uiText("WebDAV 上传"), uiText("把工作区文件上传到 WebDAV，执行前需要用户确认。")),
    AgentToolInfo("list_file_transfer_servers", uiText("列出文件传输服务器"), uiText("查看用户已配置且启用的 FTP/FTPS/SFTP 服务器标识。")),
    AgentToolInfo("file_transfer_list", uiText("文件传输列目录"), uiText("列出 FTP/FTPS/SFTP 目录文件详情。")),
    AgentToolInfo("file_transfer_search", uiText("文件传输搜索"), uiText("搜索 FTP/FTPS/SFTP 服务器上的文件路径。")),
    AgentToolInfo("file_transfer_download_to_workspace", uiText("文件传输下载"), uiText("从 FTP/FTPS/SFTP 下载文件到工作区，执行前需要用户确认。")),
    AgentToolInfo("file_transfer_upload_from_workspace", uiText("文件传输上传"), uiText("把工作区文件上传到 FTP/FTPS/SFTP，执行前需要用户确认。")),
    AgentToolInfo("export_backup", uiText("导出备份"), uiText("导出 Lyra Code 数据到本地或 WebDAV，执行前需要用户确认。")),
    AgentToolInfo("import_backup", uiText("导入备份"), uiText("从本地或 WebDAV 用补充模式导入备份，执行前需要用户确认。")),
    AgentToolInfo("set_todo_list", uiText("设置 TODO"), uiText("展示 Agent 当前任务计划。")),
    AgentToolInfo("update_todo_item", uiText("更新 TODO"), uiText("更新任务步骤状态。")),
)

@Composable
internal fun TermuxSetupGuide() {
    val clipboard = LocalClipboardManager.current
    val setupCommand = remember {
        "mkdir -p ~/.termux && (grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties || echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && termux-reload-settings"
    }
    val testCommand = remember { "python --version && pwd" }
    KimiCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(uiText("Termux 配置教程"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                uiText("首次使用前，请在 Termux 中开启外部应用调用权限。Termux:API 可选；未安装 Termux:API 时，Lyra Code 会使用 RunCommandService 后台静默执行命令。"),
                color = KimiMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            TermuxGuideStep("1", uiText("安装并打开 Termux，建议使用 F-Droid 或 GitHub 版本。"))
            SettingsExternalLinkRow(
                icon = Icons.Default.Terminal,
                title = "Termux GitHub",
                subtitle = "termux/termux-app",
                url = "https://github.com/termux/termux-app",
            )
            TermuxGuideStep("2", uiText("复制下面的配置命令到 Termux 执行，开启外部应用调用权限。"))
            CommandCopyCard(
                command = setupCommand,
                buttonText = uiText("复制配置命令"),
                onCopy = { clipboard.setText(AnnotatedString(setupCommand)) },
            )
            TermuxGuideStep("3", uiText("重新打开 Lyra Code，在设置的应用权限页面授予 RUN_COMMAND 权限。"))
            TermuxGuideStep("4", uiText("选择内部存储下可读写的工作目录，例如 /storage/emulated/0/Fonts。"))
            TermuxGuideStep("5", uiText("run_command 会直接回传 exit_code、stdout、stderr；只有输出过大或超时时，再重定向到工作目录文件。"))
            CommandCopyCard(
                command = testCommand,
                buttonText = uiText("复制测试命令"),
                onCopy = { clipboard.setText(AnnotatedString(testCommand)) },
            )
        }
    }
}

@Composable
internal fun SettingsExternalLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    url: String,
) {
    val uriHandler = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { uriHandler.openUri(url) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KimiMuted)
    }
}

@Composable
internal fun TermuxGuideStep(index: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(index, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        Text(text, modifier = Modifier.weight(1f), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun CommandCopyCard(command: String, buttonText: String, onCopy: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SelectionContainer {
            Text(
                command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
        OutlinedButton(onClick = onCopy, shape = KimiPillShape) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}

