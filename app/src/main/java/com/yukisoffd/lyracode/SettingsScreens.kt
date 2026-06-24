package com.yukisoffd.lyracode

import android.Manifest
import android.app.Activity
import android.app.usage.StorageStatsManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.provider.MediaStore
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.ai.ChatRecord
import com.yukisoffd.lyracode.ai.AiResponseCache
import com.yukisoffd.lyracode.ai.OpenAiAgent
import com.yukisoffd.lyracode.ai.TodoItem
import com.yukisoffd.lyracode.ai.WebViewWebAgent
import com.yukisoffd.lyracode.data.ApiProfile
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.AuditEntry
import com.yukisoffd.lyracode.data.AuditLogStore
import com.yukisoffd.lyracode.data.BackupManager
import com.yukisoffd.lyracode.data.BackupOptions
import com.yukisoffd.lyracode.data.Conversation
import com.yukisoffd.lyracode.data.ConversationStore
import com.yukisoffd.lyracode.data.LocalMcpServerConfig
import com.yukisoffd.lyracode.data.McpServerConfig
import com.yukisoffd.lyracode.data.McpToolDefinition
import com.yukisoffd.lyracode.data.MiniServerConfig
import com.yukisoffd.lyracode.data.RoleplayScenario
import com.yukisoffd.lyracode.data.SkillPack
import com.yukisoffd.lyracode.data.SshServerConfig
import com.yukisoffd.lyracode.data.SystemPromptPreset
import com.yukisoffd.lyracode.data.AppUpdateInfo
import com.yukisoffd.lyracode.data.UpdateDownloadProgress
import com.yukisoffd.lyracode.data.UpdateManager
import com.yukisoffd.lyracode.data.FileTransferServerConfig
import com.yukisoffd.lyracode.data.WebDavServerConfig
import com.yukisoffd.lyracode.filetransfer.FileTransferClient
import com.yukisoffd.lyracode.mcp.LocalMcpServerManager
import com.yukisoffd.lyracode.mcp.McpClientManager
import com.yukisoffd.lyracode.server.MiniServerManager
import com.yukisoffd.lyracode.ssh.SshExecutor
import com.yukisoffd.lyracode.system.SystemCommandExecutor
import com.yukisoffd.lyracode.termux.TermuxExecutor
import com.yukisoffd.lyracode.webdav.TransferProgress
import com.yukisoffd.lyracode.webdav.WebDavClient
import com.yukisoffd.lyracode.workspace.GlobalFileManager
import com.yukisoffd.lyracode.workspace.NativeFileManager
import com.yukisoffd.lyracode.workspace.UploadedFile
import com.yukisoffd.lyracode.workspace.UploadedFileManager
import com.yukisoffd.lyracode.workspace.WorkspaceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.roundToInt
import rikka.shizuku.Shizuku
import android.graphics.Canvas as AndroidCanvas

@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    controller: ChatController,
    workspaceManager: WorkspaceManager,
    termuxExecutor: TermuxExecutor,
    mcpClientManager: McpClientManager,
    sshExecutor: SshExecutor,
    systemCommandExecutor: SystemCommandExecutor,
    webDavClient: WebDavClient,
    fileTransferClient: FileTransferClient,
    backupManager: BackupManager,
    miniServerManager: MiniServerManager,
    localMcpServerManager: LocalMcpServerManager,
    workspaceDisplayName: String,
    skills: List<SkillPack>,
    skillStatus: String,
    backupStatus: String,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    refreshRateMode: String,
    onRefreshRateModeChange: (String) -> Unit,
    fontScaleMode: String,
    customFontScale: Float,
    onFontScaleModeChange: (String) -> Unit,
    onCustomFontScaleChange: (Float) -> Unit,
    onPickWorkspace: () -> Unit,
    onImportSkillFile: () -> Unit,
    onImportSkillRepository: (String) -> Unit,
    onImportSkillMarkdown: (String) -> Unit,
    onImportBackup: (String) -> Unit,
    onBackupStatusChange: (String) -> Unit,
    updateAvailable: Boolean,
    onUpdateAvailabilityChange: (Boolean) -> Unit,
    settingsBackRequest: Int,
    onDetailTitleChange: (String?) -> Unit,
    onToggleSkill: (String, Boolean) -> Unit,
    onDeleteSkill: (String) -> Unit,
) {
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val settingsListScroll = rememberScrollState()
    fun navigateBackFromDetail() {
        detail = when (detail) {
            "device" -> "about"
            "theme_mode", "font", "refresh_rate" -> "theme"
            "mini_server_logs" -> "mini_server"
            else -> null
        }
    }
    BackHandler(enabled = detail != null) { navigateBackFromDetail() }
    LaunchedEffect(detail) {
        onDetailTitleChange(detail?.let(::settingsDetailTitle))
    }
    LaunchedEffect(settingsBackRequest) {
        if (settingsBackRequest > 0 && detail != null) navigateBackFromDetail()
    }
    AnimatedContent(
        targetState = detail,
        transitionSpec = {
            val forward = when {
                initialState == "device" && targetState == "about" -> false
                initialState == "about" && targetState == "device" -> true
                initialState in setOf("theme_mode", "font", "refresh_rate") && targetState == "theme" -> false
                initialState == "theme" && targetState in setOf("theme_mode", "font", "refresh_rate") -> true
                initialState == "mini_server_logs" && targetState == "mini_server" -> false
                initialState == "mini_server" && targetState == "mini_server_logs" -> true
                targetState == null -> false
                else -> true
            }
            slideInHorizontally(animationSpec = tween(260)) { fullWidth -> if (forward) fullWidth else -fullWidth / 3 } togetherWith
                slideOutHorizontally(animationSpec = tween(260)) { fullWidth -> if (forward) -fullWidth / 3 else fullWidth }
        },
        label = "settings-detail-transition",
    ) { target ->
        if (target != null) {
            SettingsDetailPage(
                scroll = target !in setOf("prompts", "licenses", "about", "device"),
            ) {
                when (target) {
                    "profile" -> ProfileSettingsSummary(settings)
                    "model" -> ModelServiceSettings(settings, controller)
                    "workspace" -> WorkspaceSettings(workspaceDisplayName, workspaceManager, onPickWorkspace)
                    "theme" -> ThemeSettings(
                        settings = settings,
                        themeMode = themeMode,
                        dynamicColorEnabled = dynamicColorEnabled,
                        onDynamicColorChange = onDynamicColorChange,
                        refreshRateMode = refreshRateMode,
                        onRefreshRateModeChange = onRefreshRateModeChange,
                        fontScaleMode = fontScaleMode,
                        customFontScale = customFontScale,
                        onOpenThemeModeSettings = { detail = "theme_mode" },
                        onOpenFontSettings = { detail = "font" },
                        onOpenRefreshRateSettings = { detail = "refresh_rate" },
                    )
                    "theme_mode" -> ThemeModeSettings(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                    )
                    "refresh_rate" -> RefreshRateSettings(
                        refreshRateMode = refreshRateMode,
                        onRefreshRateModeChange = onRefreshRateModeChange,
                    )
                    "font" -> FontSizeSettings(
                        fontScaleMode = fontScaleMode,
                        customFontScale = customFontScale,
                        onFontScaleModeChange = onFontScaleModeChange,
                        onCustomFontScaleChange = onCustomFontScaleChange,
                    )
                    "permissions" -> PermissionSettings(termuxExecutor)
                    "system_permissions" -> SystemPermissionSettings(settings, systemCommandExecutor)
                    "tools" -> AgentToolSettings(settings, termuxExecutor, controller.settingsRevision.intValue)
                    "termux" -> TermuxSettings(settings, termuxExecutor, workspaceManager)
                    "mcp" -> McpSettings(settings, mcpClientManager, controller.settingsRevision.intValue)
                    "local_mcp" -> LocalMcpServerSettings(settings, localMcpServerManager, controller.settingsRevision.intValue)
                    "ssh" -> SshSettings(settings, sshExecutor, controller.settingsRevision.intValue)
                    "webdav" -> WebDavSettings(settings, webDavClient, controller.settingsRevision.intValue)
                    "file_transfer" -> FileTransferSettings(settings, fileTransferClient, controller.settingsRevision.intValue)
                    "mini_server" -> MiniServerSettings(
                        settings,
                        miniServerManager,
                        controller.settingsRevision.intValue,
                        onOpenLogs = { detail = "mini_server_logs" },
                    )
                    "mini_server_logs" -> MiniServerLogSettings(miniServerManager)
                    "web_search" -> WebSearchSettings(
                        settings = settings,
                        externalRevision = controller.settingsRevision.intValue,
                        onChanged = { controller.settingsRevision.intValue++ },
                    )
                    "backup" -> BackupSettings(
                        settings = settings,
                        webDavClient = webDavClient,
                        backupManager = backupManager,
                        status = backupStatus,
                        onStatusChange = onBackupStatusChange,
                        onImportBackup = onImportBackup,
                        onConfigChanged = { controller.settingsRevision.intValue++ },
                    )
                    "storage" -> StorageCacheSettings()
                    "roleplay" -> ImmersiveRoleplaySettings(settings, controller)
                    "prompts" -> PromptSettingsScreen(settings)
                    "skills" -> SkillsScreen(
                        skills = skills,
                        status = skillStatus,
                        onImportSkillFile = onImportSkillFile,
                        onImportSkillRepository = onImportSkillRepository,
                        onImportSkillMarkdown = onImportSkillMarkdown,
                        onToggleSkill = onToggleSkill,
                        onDeleteSkill = onDeleteSkill,
                    )
                    "licenses" -> OpenSourceLicensesScreen()
                    "about" -> AboutSoftwareScreen(
                        updateAvailable = updateAvailable,
                        onUpdateAvailabilityChange = onUpdateAvailabilityChange,
                        onOpenDeviceInfo = { detail = "device" },
                    )
                    "device" -> DeviceInfoScreen()
                    else -> Text("该设置项暂未开放。", color = KimiMuted)
                }
            }
            return@AnimatedContent
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(settingsListScroll)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("设置", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium)
            KimiSectionLabel("模型与服务")
            KimiCardBox {
                KimiMenuRow(Icons.Default.AccountCircle, "个人资料", "昵称、头像与用户显示信息") { detail = "profile" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Cloud, "模型服务", "配置 AI 服务商、API Key 与默认模型") { detail = "model" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Search, "联网搜索", "配置网站黑名单，过滤垃圾或不可信来源") { detail = "web_search" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Folder, "工作目录", "当前：$workspaceDisplayName") { detail = "workspace" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Terminal, "Termux", "配置本地命令执行与路径映射") { detail = "termux" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Extension, "MCP 服务器", "配置远程 MCP 工具服务器") { detail = "mcp" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Hub, "MCP 服务端", "将本机工具提供给其他 MCP 客户端") { detail = "local_mcp" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Dns, "SSH 连接", "配置可由 AI 调用的远程服务器") { detail = "ssh" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Cloud, "WebDAV", "配置云端文件与备份服务器") { detail = "webdav" }
                KimiDivider()
                KimiMenuRow(Icons.Default.SyncAlt, "文件传输", "配置 FTP/FTPS/SFTP 存储服务器") { detail = "file_transfer" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Language, "微型服务器", "工作区 HTTP 静态站点调试") { detail = "mini_server" }
            }
            KimiSectionLabel("个性化")
            KimiCardBox {
                KimiMenuRow(
                    Icons.Default.Palette,
                    "主题设置",
                    "当前：${themeName(themeMode)} · ${refreshRateName(refreshRateMode)} · ${fontScaleName(fontScaleMode, customFontScale)}",
                ) { detail = "theme" }
                KimiDivider()
                KimiMenuRow(Icons.Default.EditNote, "系统提示词", "配置不同用途的默认提示词") { detail = "prompts" }
                KimiDivider()
                KimiMenuRow(Icons.Default.TheaterComedy, "沉浸扮演模式", "当前：${if (settings.immersiveRoleplayEnabled) "已开启" else "已关闭"}，管理角色设定与表情包") { detail = "roleplay" }
                KimiDivider()
                KimiMenuRow(Icons.Default.School, "Skills", "已安装 ${skills.size} 个能力包") { detail = "skills" }
            }
            KimiSectionLabel("通用")
            KimiCardBox {
                KimiMenuRow(Icons.Default.Build, "AI Agent 工具", "查看工具说明并启用或禁用") { detail = "tools" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Storage, "缓存与存储", "扫描占用空间并清理安全缓存") { detail = "storage" }
                KimiDivider()
                KimiMenuRow(Icons.Default.ImportExport, "数据导出导入", "本地或 WebDAV 备份与恢复") { detail = "backup" }
                KimiDivider()
                KimiMenuRow(Icons.Default.AdminPanelSettings, "系统权限", "配置 Root、Shizuku Shell 与 su 命令") { detail = "system_permissions" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Security, "应用权限", "查看媒体、摄像头、位置与 Termux 权限") { detail = "permissions" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Description, "开源许可证", "查看第三方组件许可证") { detail = "licenses" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Info, "关于软件", "版本、包名和安全说明") { detail = "about" }
            }
        }
    }
}

@Composable
internal fun SettingsDetailPage(
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val bodyModifier = if (scroll) {
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        } else {
            Modifier
                .weight(1f)
                .fillMaxWidth()
        }
        Column(bodyModifier, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

internal fun settingsDetailTitle(detail: String): String = when (detail) {
    "profile" -> "个人资料"
    "model" -> "模型服务"
    "web_search" -> "联网搜索"
    "workspace" -> "工作目录"
    "theme" -> "主题设置"
    "theme_mode" -> "主题模式"
    "font" -> "字体大小"
    "refresh_rate" -> "刷新率"
    "permissions" -> "应用权限"
    "system_permissions" -> "系统权限"
    "tools" -> "AI Agent 工具"
    "storage" -> "缓存与存储"
    "roleplay" -> "沉浸扮演模式"
    "termux" -> "Termux"
    "mcp" -> "MCP 服务器"
    "local_mcp" -> "MCP 服务端"
    "ssh" -> "SSH 连接"
    "webdav" -> "WebDAV"
    "file_transfer" -> "文件传输"
    "mini_server" -> "微型服务器"
    "mini_server_logs" -> "终端日志"
    "backup" -> "数据导出导入"
    "prompts" -> "系统提示词"
    "skills" -> "Skills"
    "licenses" -> "开源许可证"
    "about" -> "关于软件"
    "device" -> "手机信息"
    else -> "设置"
}

@Composable
internal fun ProfileSettingsSummary(settings: AppSettings) {
    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(settings.userAvatarPath, settings.userNickname.take(1).ifBlank { "L" }, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(settings.userNickname.ifBlank { "Lyra 用户" }, style = MaterialTheme.typography.titleMedium)
                Text("头像和昵称可在侧边栏顶部点击编辑。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun ImmersiveRoleplaySettings(settings: AppSettings, controller: ChatController) {
    var revision by remember { mutableIntStateOf(0) }
    var scenarios by remember(revision, settings.selectedRoleplayId, settings.immersiveRoleplayEnabled) { mutableStateOf(settings.roleplayScenarios()) }
    val current = scenarios.firstOrNull { it.id == settings.selectedRoleplayId } ?: scenarios.firstOrNull()
    var notice by remember { mutableStateOf("") }
    var stickerCode by rememberSaveable { mutableStateOf("[sti_happy]") }
    var deleteTarget by remember { mutableStateOf<RoleplayScenario?>(null) }
    var cropAsset by rememberSaveable { mutableStateOf<Pair<String, Uri>?>(null) }
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            settings.importRoleplayZip(uri).fold(
                onSuccess = {
                    settings.selectedRoleplayId = it.id
                    settings.immersiveRoleplayEnabled = true
                    controller.switchConversationScope()
                    notice = "已导入 ${it.name}"
                    revision++
                },
                onFailure = { notice = it.message.orEmpty().ifBlank { "导入失败" } },
            )
        }
    }
    val assetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) cropAsset = "avatar" to uri
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) cropAsset = "background" to uri
    }
    val stickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val id = current?.id
        if (uri != null && id != null) {
            settings.addRoleplaySticker(id, uri, stickerCode).fold(
                onSuccess = {
                    notice = "已添加表情 ${it.code}"
                    revision++
                },
                onFailure = { notice = it.message.orEmpty().ifBlank { "添加失败" } },
            )
        }
    }
    cropAsset?.let { (kind, uri) ->
        ImageCropUploadDialog(
            uri = uri,
            onDismiss = { cropAsset = null },
            onUseOriginal = {
                current?.id?.let { id ->
                    settings.saveRoleplayAsset(id, uri, kind).fold(
                        onSuccess = {
                            notice = if (kind == "avatar") "头像已保存" else "背景已保存"
                            revision++
                        },
                        onFailure = { notice = it.message.orEmpty().ifBlank { "保存失败" } },
                    )
                }
                cropAsset = null
            },
            onCropped = { cropped ->
                current?.id?.let { id ->
                    settings.saveRoleplayAsset(id, cropped, kind).fold(
                        onSuccess = {
                            notice = if (kind == "avatar") "头像已保存" else "背景已保存"
                            revision++
                        },
                        onFailure = { notice = it.message.orEmpty().ifBlank { "保存失败" } },
                    )
                }
                cropAsset = null
            },
        )
    }
    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "删除角色设定",
            message = "会删除此设定下的资源、好感度和所有沉浸对话数据。",
            targetName = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                controller.clearRoleplayData(target.id)
                settings.deleteRoleplayScenario(target.id)
                controller.switchConversationScope()
                revision++
                notice = "已删除 ${target.name}"
            },
        )
    }
    Box(Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("沉浸扮演模式", style = MaterialTheme.typography.titleMedium)
                        Text("开启后，对话页切换为聊天气泡样式，并使用当前角色设定作为系统提示词。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = settings.immersiveRoleplayEnabled,
                        onCheckedChange = {
                            settings.immersiveRoleplayEnabled = it
                            controller.switchConversationScope()
                            revision++
                        },
                    )
                }
            }
            KimiCardBox {
                Text("导入角色设定", style = MaterialTheme.typography.titleMedium)
                Text("请上传 zip 压缩包，包内放入 AI 需要扮演的角色详情 md/txt 文件，例如姓名、外貌、爱好、说话方式、关系设定、所处世界观等。可导入多个设定并切换。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { zipLauncher.launch("application/zip") }, shape = KimiPillShape) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导入设定 zip")
                }
            }
            if (scenarios.isNotEmpty()) {
                KimiCardBox {
                    Text("当前设定", style = MaterialTheme.typography.titleMedium)
                    scenarios.forEach { scenario ->
                        RoleplayScenarioRow(
                            scenario = scenario,
                            selected = scenario.id == settings.selectedRoleplayId,
                            onSelect = {
                                settings.selectedRoleplayId = scenario.id
                                settings.immersiveRoleplayEnabled = true
                                controller.switchConversationScope()
                                revision++
                            },
                            onDelete = { deleteTarget = scenario },
                        )
                    }
                }
            }
            current?.let { scenario ->
                KimiCardBox {
                    Text("角色表现", style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoleplayAssetPreview(
                            title = "头像",
                            path = scenario.aiAvatarPath,
                            modifier = Modifier.weight(1f),
                            aspectRatio = 1f,
                        )
                        RoleplayAssetPreview(
                            title = "背景",
                            path = scenario.backgroundPath,
                            modifier = Modifier.weight(1f),
                            aspectRatio = 9f / 16f,
                        )
                    }
                    OutlinedTextField(
                        value = scenario.aiNickname,
                        onValueChange = {
                            settings.updateRoleplayNickname(scenario.id, it)
                            revision++
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("AI 昵称") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { assetLauncher.launch("image/*") }, shape = KimiPillShape) { Text("上传头像") }
                        OutlinedButton(onClick = { backgroundLauncher.launch("image/*") }, shape = KimiPillShape) { Text("上传背景") }
                    }
                    Text("好感度：${scenario.affection}/100", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = {
                            controller.clearCurrentRoleplayData()
                            revision++
                            notice = "已清空当前设定对话和好感度"
                        },
                        shape = KimiPillShape,
                    ) { Text("清除所有对话数据并重置好感度") }
                }
                KimiCardBox {
                    Text("表情包", style = MaterialTheme.typography.titleMedium)
                    Text("设置短代码，例如 [sti_happy]。AI 回复中包含短代码时，软件会替换为对应表情图片。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = stickerCode,
                        onValueChange = { stickerCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("短代码") },
                    )
                    Button(onClick = { stickerLauncher.launch("image/*") }, shape = KimiPillShape) { Text("上传表情包") }
                    settings.roleplayStickers(scenario.id).forEach { sticker ->
                        Text("${sticker.code} · ${File(sticker.path).name}", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        TransientNotice(
            message = notice,
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            onDismiss = { notice = "" },
        )
    }
}

@Composable
internal fun RoleplayAssetPreview(
    title: String,
    path: String?,
    modifier: Modifier = Modifier,
    aspectRatio: Float,
) {
    val bitmap = remember(path) {
        path?.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = KimiMuted, style = MaterialTheme.typography.labelMedium)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("未上传", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun RoleplayScenarioRow(
    scenario: RoleplayScenario,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(scenario.name, style = MaterialTheme.typography.titleSmall)
            Text("好感度 ${scenario.affection}/100 · ${scenario.fileCount} 个文件", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            if (scenario.description.isNotBlank()) Text(scenario.description, color = KimiMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除")
        }
    }
}

@Composable
internal fun ModelServiceSettings(
    settings: AppSettings,
    controller: ChatController,
) {
    var profiles by remember { mutableStateOf(controller.profiles.toList()) }
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftNewProfile by remember { mutableStateOf<ApiProfile?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(controller.activeProfileId.value, controller.profiles.size, controller.settingsRevision.intValue) {
        val refreshed = controller.profiles.toList()
        profiles = refreshed
        if (editingProfileId != null && refreshed.none { it.id == editingProfileId } && draftNewProfile?.id != editingProfileId) {
            editingProfileId = null
        }
    }
    BackHandler(enabled = editingProfileId != null) {
        if (draftNewProfile?.id == editingProfileId) draftNewProfile = null
        editingProfileId = null
    }
    val editingIndex = profiles.indexOfFirst { it.id == editingProfileId }
    val current = if (draftNewProfile?.id == editingProfileId) draftNewProfile else profiles.getOrNull(editingIndex)
    var platformMenuExpanded by remember { mutableStateOf(false) }
    val editKey = editingProfileId ?: "none"
    var name by remember(editKey) { mutableStateOf(current?.name.orEmpty()) }
    var key by remember(editKey) { mutableStateOf(current?.apiKey.orEmpty()) }
    var baseUrl by remember(editKey) { mutableStateOf(current?.baseUrl.orEmpty()) }
    var apiFormat by remember(editKey) { mutableStateOf(current?.apiFormat ?: ApiProfile.API_FORMAT_OPENAI) }
    var model by remember(editKey) { mutableStateOf(current?.selectedModel.orEmpty()) }
    var savedModels by remember(editKey) { mutableStateOf(current?.savedModels.orEmpty().joinToString("\n")) }
    var status by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ApiProfile?>(null) }
    fun draftProfile(selectedModelOverride: String? = null, savedModelsOverride: List<String>? = null): ApiProfile {
        val models = savedModelsOverride ?: savedModels.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList().distinct()
        val selected = selectedModelOverride ?: model.ifBlank { models.firstOrNull().orEmpty() }
        return ApiProfile(
            id = current?.id ?: AppSettings.newId(),
            name = name.ifBlank { "未命名平台" },
            apiKey = key,
            baseUrl = baseUrl.ifBlank { defaultBaseUrlForApiFormat(apiFormat) },
            apiFormat = apiFormat,
            selectedModel = selected.ifBlank { "gpt-4o-mini" },
            savedModels = models.ifEmpty { listOf(selected.ifBlank { "gpt-4o-mini" }) }.distinct(),
        )
    }
    fun saveCurrentProfile() {
        val updated = draftProfile()
        val updatedProfiles = if (editingIndex >= 0) {
            profiles.mapIndexed { index, item -> if (index == editingIndex) updated else item }
        } else {
            profiles + updated
        }
        profiles = updatedProfiles
        draftNewProfile = null
        controller.saveProfiles(updatedProfiles, updated.id)
        editingProfileId = updated.id
        status = ""
        notice = "模型服务已保存"
    }
    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "删除模型服务配置",
            message = "该操作会删除服务商、API Key、基础 URL 和预保存模型配置。",
            targetName = target.name.ifBlank { target.baseUrl },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                val remaining = profiles.filterNot { it.id == target.id }
                if (remaining.isNotEmpty()) {
                    profiles = remaining
                    editingProfileId = null
                    controller.saveProfiles(remaining, remaining.first().id)
                }
                status = ""
                notice = "已删除 ${target.name.ifBlank { "模型服务" }}"
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = editingProfileId != null,
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) + slideInHorizontally { if (targetState) it / 6 else -it / 6 })
                    .togetherWith(fadeOut(animationSpec = tween(140)) + slideOutHorizontally { if (targetState) -it / 8 else it / 8 })
            },
            label = "model-service-page",
        ) { editing ->
        if (!editing) {
            val filtered = remember(profiles, query) {
                val q = query.trim()
                if (q.isBlank()) profiles else profiles.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.baseUrl.contains(q, ignoreCase = true) ||
                        it.selectedModel.contains(q, ignoreCase = true)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CapsuleTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = "搜索模型服务",
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                    )
                    IconButton(
                        onClick = {
                            val newProfile = ApiProfile(
                                id = AppSettings.newId(),
                                name = "新平台",
                                apiKey = "",
                                baseUrl = "https://api.openai.com/v1",
                                apiFormat = ApiProfile.API_FORMAT_OPENAI,
                                selectedModel = "gpt-4o-mini",
                                savedModels = listOf("gpt-4o-mini"),
                            )
                            draftNewProfile = newProfile
                            editingProfileId = newProfile.id
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加模型服务",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    KimiCardBox {
                        Text("没有匹配的模型服务", color = KimiMuted)
                    }
                } else {
                    filtered.forEach { profile ->
                        ModelProviderRow(
                            profile = profile,
                            selected = profile.id == controller.activeProfileId.value,
                            onClick = { editingProfileId = profile.id },
                            onDelete = { if (profiles.size > 1) deleteTarget = profile else notice = "至少保留一个模型服务" },
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(current?.name?.ifBlank { "新模型服务" } ?: "模型服务", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = {
                        if (draftNewProfile?.id == editingProfileId) draftNewProfile = null
                        editingProfileId = null
                    }) {
                        Icon(Icons.Default.ViewList, contentDescription = "返回列表")
                    }
                }
                KimiCardBox {
                    Text("接口格式", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ApiFormatOption("OpenAI SDK", ApiProfile.API_FORMAT_OPENAI, apiFormat) {
                            apiFormat = it
                            if (baseUrl.isBlank() || baseUrl in knownProviderBaseUrls()) baseUrl = defaultBaseUrlForApiFormat(it)
                        }
                        ApiFormatOption("Anthropic Messages", ApiProfile.API_FORMAT_ANTHROPIC, apiFormat) {
                            apiFormat = it
                            if (baseUrl.isBlank() || baseUrl in knownProviderBaseUrls()) baseUrl = defaultBaseUrlForApiFormat(it)
                        }
                        ApiFormatOption("Gemini GenerateContent", ApiProfile.API_FORMAT_GEMINI, apiFormat) {
                            apiFormat = it
                            if (baseUrl.isBlank() || baseUrl in knownProviderBaseUrls()) baseUrl = defaultBaseUrlForApiFormat(it)
                        }
                    }
                    Text(
                        apiFormatDescription(apiFormat),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("服务商名称") }, singleLine = true)
                    OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text(apiKeyLabel(apiFormat)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("基础 URL") }, singleLine = true)
                    if (baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                        Text(
                            "安全提示：当前基础 URL 使用 HTTP 明文传输，API Key 和对话内容可能被同一网络中的第三方截获。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        endpointHint(apiFormat, baseUrl),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text("默认模型") }, singleLine = true)
                    OutlinedTextField(value = savedModels, onValueChange = { savedModels = it }, modifier = Modifier.fillMaxWidth(), label = { Text("预保存模型，每行一个") }, minLines = 3)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { saveCurrentProfile() }, shape = KimiPillShape) { Text("保存") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val draft = draftProfile()
                                status = "正在获取模型..."
                                controller.fetchModelsForProfile(draft) { result ->
                                    result.fold(
                                        onSuccess = { models ->
                                            val distinct = models.filter { it.isNotBlank() }.distinct()
                                            if (distinct.isEmpty()) {
                                                status = "未获取到可用模型"
                                            } else {
                                                model = distinct.first()
                                                savedModels = distinct.joinToString("\n")
                                                val updated = draftProfile(selectedModelOverride = distinct.first(), savedModelsOverride = distinct)
                                                val updatedProfiles = if (editingIndex >= 0) {
                                                    profiles.mapIndexed { index, item -> if (index == editingIndex) updated else item }
                                                } else {
                                                    profiles + updated
                                                }
                                                profiles = updatedProfiles
                                                draftNewProfile = null
                                                controller.saveProfiles(updatedProfiles, updated.id)
                                                status = ""
                                                notice = "已获取 ${distinct.size} 个模型并保存"
                                            }
                                        },
                                        onFailure = { status = it.message.orEmpty().ifBlank { "获取模型失败" } },
                                    )
                                }
                            },
                            shape = KimiPillShape,
                        ) { Text("获取并替换模型", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        IconButton(
                            enabled = profiles.size > 1,
                            onClick = { current?.let { deleteTarget = it } },
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除平台")
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, color = KimiMuted)
            }
        }
        }
        TransientNotice(
            message = notice,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            onDismiss = { notice = "" },
        )
    }
}

@Composable
internal fun ApiFormatOption(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    MaterialChoiceButton(label = label, selected = selected == value, onClick = { onSelect(value) })
}

@Composable
internal fun MaterialChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = KimiPillShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = KimiPillShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun defaultBaseUrlForApiFormat(format: String): String = when (format) {
    ApiProfile.API_FORMAT_ANTHROPIC -> "https://api.anthropic.com/v1"
    ApiProfile.API_FORMAT_GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
    else -> "https://api.openai.com/v1"
}

internal fun knownProviderBaseUrls(): Set<String> = setOf(
    "https://api.openai.com/v1",
    "https://api.anthropic.com/v1",
    "https://generativelanguage.googleapis.com/v1beta",
)

internal fun apiKeyLabel(format: String): String = when (format) {
    ApiProfile.API_FORMAT_ANTHROPIC -> "Anthropic API Key"
    ApiProfile.API_FORMAT_GEMINI -> "Google API Key"
    else -> "API Key"
}

internal fun apiFormatDescription(format: String): String = when (format) {
    ApiProfile.API_FORMAT_ANTHROPIC -> "适用于 Claude 官方 Messages API 或兼容 Anthropic Messages 格式的服务。请求、工具调用和图片输入会按 Anthropic 格式转换。"
    ApiProfile.API_FORMAT_GEMINI -> "适用于 Gemini 官方 GenerateContent API 或兼容 Gemini 格式的服务。图片、音频、视频会使用 inlineData 传输。"
    else -> "适用于 OpenAI Chat Completions SDK 兼容平台。原有工具调用、流式输出和多模态 image_url 路径保持不变。"
}

internal fun endpointHint(format: String, baseUrl: String): String {
    val root = baseUrl.trim().trimEnd('/').ifBlank { defaultBaseUrlForApiFormat(format) }
    return when (format) {
        ApiProfile.API_FORMAT_ANTHROPIC -> "请求端点：$root/messages；模型列表：$root/models"
        ApiProfile.API_FORMAT_GEMINI -> "请求端点：$root/models/{model}:generateContent；模型列表：$root/models"
        else -> "请求端点：$root/chat/completions；模型列表：$root/models"
    }
}

@Composable
internal fun WorkspaceSettings(
    workspaceDisplayName: String,
    workspaceManager: WorkspaceManager,
    onPickWorkspace: () -> Unit,
) {
    KimiCardBox {
        KimiMenuRow(Icons.Default.Folder, "当前目录", workspaceDisplayName, onPickWorkspace)
        KimiDivider()
        KimiMenuRow(Icons.Default.Terminal, "Termux 路径", workspaceManager.termuxRootPath() ?: "仅 primary")
        Text("右上角加号选择目录后会立即刷新对话页顶部的小字目录名。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ThemeSettings(
    settings: AppSettings,
    themeMode: String,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    refreshRateMode: String,
    onRefreshRateModeChange: (String) -> Unit,
    fontScaleMode: String,
    customFontScale: Float,
    onOpenThemeModeSettings: () -> Unit,
    onOpenFontSettings: () -> Unit,
    onOpenRefreshRateSettings: () -> Unit,
) {
    val context = LocalContext.current
    var backgroundRevision by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf("") }
    var cropBackgroundUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val screenAspect = remember {
        val metrics = context.resources.displayMetrics
        metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat().coerceAtLeast(1f)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) cropBackgroundUri = uri
    }
    cropBackgroundUri?.let { uri ->
        ImageCropUploadDialog(
            uri = uri,
            fixedCropAspectRatio = screenAspect,
            onDismiss = { cropBackgroundUri = null },
            onUseOriginal = {
                settings.saveChatBackground(uri).fold(
                    onSuccess = {
                        notice = "聊天背景已保存"
                        backgroundRevision++
                    },
                    onFailure = { notice = it.message.orEmpty().ifBlank { "保存失败" } },
                )
                cropBackgroundUri = null
            },
            onCropped = { cropped ->
                settings.saveChatBackground(cropped).fold(
                    onSuccess = {
                        notice = "聊天背景已保存"
                        backgroundRevision++
                    },
                    onFailure = { notice = it.message.orEmpty().ifBlank { "保存失败" } },
                )
                cropBackgroundUri = null
            },
        )
    }
    val backgroundPath = remember(backgroundRevision) { settings.chatBackgroundPath }
    val hasBackground = !backgroundPath.isNullOrBlank()
    val backgroundPreview = remember(backgroundPath) {
        backgroundPath?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    var maskOpacity by remember(backgroundPath, backgroundRevision) {
        mutableStateOf(settings.chatBackgroundMaskOpacity.coerceIn(0f, 1f))
    }
    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.width(36.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Material You 动态配色", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = dynamicColorEnabled, onCheckedChange = onDynamicColorChange)
        }
        KimiDivider()
        KimiMenuRow(Icons.Default.Palette, "主题模式", themeName(themeMode), onOpenThemeModeSettings)
        KimiDivider()
        KimiMenuRow(Icons.Default.FormatSize, "字体大小", fontScaleName(fontScaleMode, customFontScale), onOpenFontSettings)
        KimiDivider()
        KimiMenuRow(Icons.Default.Speed, "刷新率", refreshRateName(refreshRateMode), onOpenRefreshRateSettings)
        KimiDivider()
        KimiMenuRow(
            Icons.Default.Image,
            "聊天背景",
            if (hasBackground) "已设置自定义背景" else "纯色背景",
        ) {
            backgroundLauncher.launch("image/*")
        }
        if (hasBackground) {
            KimiDivider()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (backgroundPreview != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Image(
                            bitmap = backgroundPreview,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 1f - maskOpacity)),
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Text(
                                "聊天背景预览",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "蒙版透明度 ${(maskOpacity * 100f).toInt().coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "越低越接近纯色背景，越高背景图越清晰。",
                            color = KimiMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Slider(
                    value = maskOpacity,
                    onValueChange = { value ->
                        maskOpacity = value.coerceIn(0f, 1f)
                        settings.chatBackgroundMaskOpacity = maskOpacity
                    },
                    valueRange = 0f..1f,
                )
            }
            KimiDivider()
            KimiMenuRow(Icons.Default.DeleteOutline, "移除聊天背景", "恢复纯色背景") {
                settings.clearChatBackground()
                notice = "已恢复纯色背景"
                backgroundRevision++
            }
        }
        if (notice.isNotBlank()) {
            Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ProviderLogoBadge(profile: ApiProfile, modifier: Modifier = Modifier) {
    val name = profile.name.ifBlank { profile.baseUrl }.trim()
    val icon = when {
        name.contains("gemini", ignoreCase = true) || profile.apiFormat == ApiProfile.API_FORMAT_GEMINI -> Icons.Default.AutoAwesome
        name.contains("anthropic", ignoreCase = true) || name.contains("claude", ignoreCase = true) || profile.apiFormat == ApiProfile.API_FORMAT_ANTHROPIC -> Icons.Default.Psychology
        name.contains("deepseek", ignoreCase = true) -> Icons.Default.WaterDrop
        name.contains("openrouter", ignoreCase = true) -> Icons.Default.Route
        name.contains("vercel", ignoreCase = true) -> Icons.Default.ChangeCircle
        name.contains("openai", ignoreCase = true) -> Icons.Default.Hub
        else -> Icons.Default.Cloud
    }
    Box(
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
    }
}

@Composable
internal fun ModelProviderRow(
    profile: ApiProfile,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.name.ifBlank { "未命名平台" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile.baseUrl.isNotBlank()) {
                    Text(profile.baseUrl, color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "启用",
                        modifier = Modifier
                            .clip(KimiPillShape)
                            .background(KimiGreen.copy(alpha = 0.28f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        color = KimiGreen,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "${profile.savedModels.size} 个模型",
                        modifier = Modifier
                            .clip(KimiPillShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        apiFormatShortName(profile.apiFormat),
                        color = KimiMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = KimiMuted)
            }
        }
    }
}

internal fun apiFormatShortName(format: String): String = when (format) {
    ApiProfile.API_FORMAT_ANTHROPIC -> "Anthropic"
    ApiProfile.API_FORMAT_GEMINI -> "Gemini"
    else -> "OpenAI"
}

@Composable
internal fun WebSearchSettings(
    settings: AppSettings,
    externalRevision: Int = 0,
    onChanged: () -> Unit,
) {
    var blacklist by rememberSaveable(externalRevision) { mutableStateOf(settings.webSearchBlacklistText) }
    var notice by remember { mutableStateOf("") }
    val blockedCount = remember(blacklist, externalRevision) {
        blacklist.lineSequence()
            .map { raw ->
                val clean = raw.trim().trimEnd('/').trim()
                if (clean.isBlank() || clean.startsWith("#")) "" else {
                    val withoutScheme = clean.substringAfter("://", clean)
                    val authority = withoutScheme
                        .substringBefore('/')
                        .substringBefore('?')
                        .substringBefore('#')
                        .substringAfterLast('@')
                    val hostPart = authority
                        .let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
                        .lowercase()
                        .trim('.')
                    val host = hostPart.removePrefix("*.").trim('.')
                    when {
                        host.isBlank() -> ""
                        hostPart.startsWith("*.") && !host.contains('.') -> ""
                        hostPart.startsWith("*.") -> "*.$host"
                        else -> host
                    }
                }
            }
            .filter { it.isNotBlank() }
            .distinct()
            .count()
    }
    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("网站黑名单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "AI 联网搜索和网页读取会跳过这些域名。普通域名精确匹配，* 通配符匹配子域名。",
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        KimiDivider()
        OutlinedTextField(
            value = blacklist,
            onValueChange = {
                blacklist = it
                notice = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("每行一个域名或 URL") },
            placeholder = { Text("x.com\nwww.x.com\n*.example.com\nhttps://baijiahao.baidu.com/") },
            minLines = 8,
            maxLines = 14,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Text(
            "保存后会自动归一化：移除协议、路径和尾部斜杠，但保留 www.。例如 x.com 只匹配 x.com；*.x.com 匹配 www.x.com、news.x.com 等子域名；如需同时拦截根域名和全部子域名，请同时填写 x.com 与 *.x.com。",
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    settings.webSearchBlacklistText = blacklist
                    blacklist = settings.webSearchBlacklistText
                    notice = "已保存 ${settings.webSearchBlockedHosts().size} 个黑名单域名"
                    onChanged()
                },
                shape = KimiPillShape,
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存")
            }
            OutlinedButton(
                onClick = {
                    blacklist = ""
                    settings.webSearchBlacklistText = ""
                    notice = "已清空联网搜索黑名单"
                    onChanged()
                },
                shape = KimiPillShape,
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("清空")
            }
        }
        val summary = if (notice.isNotBlank()) notice else "当前将保存 $blockedCount 个域名"
        Text(summary, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ThemeModeSettings(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
) {
    KimiCardBox {
        Text("主题模式", style = MaterialTheme.typography.titleMedium)
        Text(
            "选择跟随系统、浅色或深色模式。返回主题设置后，其他外观选项会保持当前位置。",
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        KimiDivider()
        ThemeOptionRow("跟随系统", AppSettings.THEME_SYSTEM, themeMode, onThemeModeChange)
        KimiDivider()
        ThemeOptionRow("浅色", AppSettings.THEME_LIGHT, themeMode, onThemeModeChange)
        KimiDivider()
        ThemeOptionRow("深色", AppSettings.THEME_DARK, themeMode, onThemeModeChange)
    }
}

@Composable
internal fun RefreshRateSettings(
    refreshRateMode: String,
    onRefreshRateModeChange: (String) -> Unit,
) {
    KimiCardBox {
        Text("刷新率", style = MaterialTheme.typography.titleMedium)
        Text(
            "跟随系统会交给设备自行在省电和流畅之间切换；固定刷新率会向系统请求指定帧率，实际是否生效取决于屏幕和系统策略。",
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        KimiDivider()
        RefreshRateOptionRow("跟随系统智能刷新率", AppSettings.REFRESH_RATE_SYSTEM, refreshRateMode, onRefreshRateModeChange)
        KimiDivider()
        RefreshRateOptionRow("30 Hz", AppSettings.REFRESH_RATE_30, refreshRateMode, onRefreshRateModeChange)
        KimiDivider()
        RefreshRateOptionRow("60 Hz", AppSettings.REFRESH_RATE_60, refreshRateMode, onRefreshRateModeChange)
        KimiDivider()
        RefreshRateOptionRow("90 Hz", AppSettings.REFRESH_RATE_90, refreshRateMode, onRefreshRateModeChange)
        KimiDivider()
        RefreshRateOptionRow("120 Hz", AppSettings.REFRESH_RATE_120, refreshRateMode, onRefreshRateModeChange)
    }
}

@Composable
internal fun ThemeOptionRow(title: String, value: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect(value) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Palette,
            contentDescription = null,
            modifier = Modifier.width(36.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (value == selected) {
            Icon(Icons.Default.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun RefreshRateOptionRow(title: String, value: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect(value) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Speed,
            contentDescription = null,
            modifier = Modifier.width(36.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (value == selected) {
            Icon(Icons.Default.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun FontSizeSettings(
    fontScaleMode: String,
    customFontScale: Float,
    onFontScaleModeChange: (String) -> Unit,
    onCustomFontScaleChange: (Float) -> Unit,
) {
    val currentDensity = LocalDensity.current
    val activeFontScale = currentDensity.fontScale
    val initialScale = remember(fontScaleMode, customFontScale) {
        when (fontScaleMode) {
            AppSettings.FONT_SCALE_SMALL -> 0.9f
            AppSettings.FONT_SCALE_LARGE -> 1.12f
            AppSettings.FONT_SCALE_EXTRA_LARGE -> 1.25f
            AppSettings.FONT_SCALE_CUSTOM -> customFontScale
            else -> 1.0f
        }.coerceIn(AppSettings.MIN_FONT_SCALE, AppSettings.MAX_FONT_SCALE)
    }
    var draftScale by remember(fontScaleMode, customFontScale) { mutableStateOf(initialScale) }
    val followSystem = fontScaleMode == AppSettings.FONT_SCALE_SYSTEM
    val previewScale = if (followSystem) activeFontScale.coerceIn(AppSettings.MIN_FONT_SCALE, AppSettings.MAX_FONT_SCALE) else draftScale
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                ) {
                    CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, previewScale)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text("预览文字大小", style = MaterialTheme.typography.titleMedium)
                            Text(fontScaleLabel(previewScale), color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, previewScale)) {
                Text("你可以拖动滑块来调整字体大小。", style = MaterialTheme.typography.titleLarge)
                Text(
                    "如果在使用过程中存在问题或建议，可在关于软件页面查看仓库链接并反馈。",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("跟随系统", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = followSystem,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onFontScaleModeChange(AppSettings.FONT_SCALE_SYSTEM)
                        } else {
                            onFontScaleModeChange(AppSettings.FONT_SCALE_CUSTOM)
                            onCustomFontScaleChange(draftScale)
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("A", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fontScaleLabel(draftScale), color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = draftScale,
                        onValueChange = {
                            draftScale = it.coerceIn(AppSettings.MIN_FONT_SCALE, AppSettings.MAX_FONT_SCALE)
                        },
                        onValueChangeFinished = {
                            val finalScale = (
                                draftScale / AppSettings.FONT_SCALE_STEP
                            ).roundToInt() * AppSettings.FONT_SCALE_STEP
                            val committedScale = finalScale.coerceIn(AppSettings.MIN_FONT_SCALE, AppSettings.MAX_FONT_SCALE)
                            draftScale = committedScale
                            onFontScaleModeChange(AppSettings.FONT_SCALE_CUSTOM)
                            onCustomFontScaleChange(committedScale)
                        },
                        valueRange = AppSettings.MIN_FONT_SCALE..AppSettings.MAX_FONT_SCALE,
                        steps = 0,
                        enabled = !followSystem,
                    )
                }
                Text("A", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

internal fun fontScaleLabel(scale: Float): String = when {
    scale < 0.65f -> "最小 ${(scale * 100).roundToInt()}%"
    scale < 0.8f -> "极小 ${(scale * 100).roundToInt()}%"
    scale < 0.95f -> "小 ${(scale * 100).roundToInt()}%"
    scale < 1.08f -> "标准 ${(scale * 100).roundToInt()}%"
    scale < 1.35f -> "大 ${(scale * 100).roundToInt()}%"
    scale < 1.65f -> "超大 ${(scale * 100).roundToInt()}%"
    scale < 2.1f -> "极大 ${(scale * 100).roundToInt()}%"
    else -> "最大 ${(scale * 100).roundToInt()}%"
}

internal fun themeName(mode: String): String = when (mode) {
    AppSettings.THEME_LIGHT -> "浅色"
    AppSettings.THEME_DARK -> "深色"
    else -> "跟随系统"
}

internal fun refreshRateName(mode: String): String = when (mode) {
    AppSettings.REFRESH_RATE_30 -> "30 Hz"
    AppSettings.REFRESH_RATE_60 -> "60 Hz"
    AppSettings.REFRESH_RATE_90 -> "90 Hz"
    AppSettings.REFRESH_RATE_120 -> "120 Hz"
    else -> "智能刷新率"
}

internal fun fontScaleName(mode: String, customFontScale: Float): String = when (mode) {
    AppSettings.FONT_SCALE_SMALL -> "小字"
    AppSettings.FONT_SCALE_NORMAL -> "标准字"
    AppSettings.FONT_SCALE_LARGE -> "大字"
    AppSettings.FONT_SCALE_EXTRA_LARGE -> "超大字"
    AppSettings.FONT_SCALE_CUSTOM -> "自定义 ${(customFontScale * 100).roundToInt()}%"
    else -> "字体跟随系统"
}

@Composable
internal fun StorageCacheSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scan by remember { mutableStateOf(scanStorageUsage(context)) }
    var status by remember { mutableStateOf("") }
    fun refresh() {
        scan = scanStorageUsage(context)
    }
    KimiCardBox {
        Text("存储占用", style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        KimiMenuRow(Icons.Default.Storage, "应用总占用", formatBytes(scan.totalBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.Android, "应用安装包", formatBytes(scan.appBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.Folder, "应用数据", formatBytes(scan.dataBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.Memory, "系统缓存", formatBytes(scan.cacheBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.CleaningServices, "可安全清理缓存", formatBytes(scan.cleanableBytes))
        Text("总占用按 Android 设置页常见口径估算：安装包 + 应用数据 + 缓存。清理范围仅包含临时上传、图片裁剪、拍照预览和 AI 响应磁盘缓存；不会删除历史对话、模型配置、API Key、MCP/SSH、Skills、头像或工作目录文件。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }
    scan.items.forEach { item ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(item.path, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    Text("${formatBytes(item.bytes)} · ${item.fileCount} 个文件", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (item.cleanable && item.bytes > 0L) {
                    OutlinedButton(
                        onClick = {
                            status = "正在清理 ${item.title}..."
                            scope.launch(Dispatchers.IO) {
                                deleteCacheTarget(item.file)
                                withContext(Dispatchers.Main) {
                                    status = "已清理 ${item.title}"
                                    refresh()
                                }
                            }
                        },
                        shape = KimiPillShape,
                    ) { Text("清理") }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { refresh(); status = "扫描完成" }, shape = KimiPillShape) { Text("重新扫描") }
        OutlinedButton(
            enabled = scan.cleanableBytes > 0L,
            onClick = {
                status = "正在清理缓存..."
                scope.launch(Dispatchers.IO) {
                    scan.items.filter { it.cleanable }.forEach { deleteCacheTarget(it.file) }
                    withContext(Dispatchers.Main) {
                        status = "缓存已清理"
                        refresh()
                    }
                }
            },
            shape = KimiPillShape,
        ) { Text("清理全部缓存") }
    }
    if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
}

internal data class StorageScanResult(
    val totalBytes: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val cleanableBytes: Long,
    val items: List<StorageCacheItem>,
)

internal data class StorageCacheItem(
    val title: String,
    val file: File,
    val path: String,
    val bytes: Long,
    val fileCount: Int,
    val cleanable: Boolean,
)

internal fun scanStorageUsage(context: Context): StorageScanResult {
    val recursiveAppBytes = safeInstalledAppBytes(context)
    val recursiveCacheBytes = safeDirSize(context.cacheDir) +
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) safeDirSize(context.codeCacheDir) else 0L
    val recursiveDataRootBytes = safeDirSize(File(context.applicationInfo.dataDir)) +
        context.getExternalFilesDirs(null).filterNotNull().sumOf { safeDirSize(it) } +
        context.externalCacheDirs.filterNotNull().sumOf { safeDirSize(it) }
    val systemStats = querySystemStorageStats(context)
    val appBytes = max(systemStats?.appBytes ?: 0L, recursiveAppBytes)
    val cacheBytes = max(systemStats?.cacheBytes ?: 0L, recursiveCacheBytes)
    val dataBytes = max(
        systemStats?.let { (it.dataBytes - it.cacheBytes).coerceAtLeast(0L) } ?: 0L,
        (recursiveDataRootBytes - recursiveCacheBytes).coerceAtLeast(0L),
    )
    val items = buildList {
        add(storageItem("AI 响应缓存", File(context.cacheDir, "ai_response_cache"), cleanable = true))
        add(storageItem("裁剪图片临时文件", File(context.cacheDir, "uploads"), cleanable = true))
        add(storageItem("拍照上传临时文件", File(context.cacheDir, "upload_crop"), cleanable = true))
        add(storageItem("系统临时缓存", context.cacheDir, cleanable = false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) add(storageItem("代码缓存", context.codeCacheDir, cleanable = false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) add(storageItem("No backup 数据", context.noBackupFilesDir, cleanable = false))
        add(storageItem("应用持久数据", context.filesDir, cleanable = false))
        context.getExternalFilesDirs(null).filterNotNull().forEachIndexed { index, dir ->
            add(storageItem("外部私有文件 ${index + 1}", dir, cleanable = false))
        }
        context.externalCacheDirs.filterNotNull().forEachIndexed { index, dir ->
            add(storageItem("外部缓存 ${index + 1}", dir, cleanable = true))
        }
    }
    val total = appBytes + dataBytes + cacheBytes
    val cleanable = items.filter { it.cleanable }.sumOf { it.bytes }
    return StorageScanResult(total, appBytes, dataBytes, cacheBytes, cleanable, items)
}

internal data class SystemStorageStats(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long)

internal fun querySystemStorageStats(context: Context): SystemStorageStats? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    return runCatching {
        val manager = context.getSystemService(StorageStatsManager::class.java)
        val stats = manager.queryStatsForUid(StorageManager.UUID_DEFAULT, context.applicationInfo.uid)
        SystemStorageStats(
            appBytes = stats.appBytes,
            dataBytes = stats.dataBytes,
            cacheBytes = stats.cacheBytes,
        )
    }.getOrNull()
}

internal fun safeInstalledAppBytes(context: Context): Long = runCatching {
    val appInfo = context.applicationInfo
    val files = buildList {
        add(File(appInfo.sourceDir))
        appInfo.splitSourceDirs?.forEach { add(File(it)) }
        appInfo.nativeLibraryDir?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
    }
    files.distinctBy { it.absolutePath }.sumOf { safeDirSize(it) }
}.getOrDefault(0L)

internal fun storageItem(title: String, file: File, cleanable: Boolean): StorageCacheItem {
    return StorageCacheItem(
        title = title,
        file = file,
        path = file.absolutePath,
        bytes = safeDirSize(file),
        fileCount = safeFileCount(file),
        cleanable = cleanable,
    )
}

internal fun safeDirSize(file: File): Long = runCatching {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

internal fun safeFileCount(file: File): Int = runCatching {
    if (!file.exists()) return 0
    if (file.isFile) return 1
    file.walkTopDown().count { it.isFile }
}.getOrDefault(0)

internal fun deleteCacheTarget(file: File) {
    runCatching {
        if (!file.exists()) return
        if (file.isFile) {
            file.delete()
        } else {
            file.listFiles()?.forEach { child ->
                if (child.isDirectory) child.deleteRecursively() else child.delete()
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble().coerceAtLeast(0.0)
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) "${bytes.coerceAtLeast(0)} ${units[index]}" else String.format(Locale.US, "%.1f %s", value, units[index])
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
    var rootStatus by remember { mutableStateOf("尚未检测") }
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
                Text("请求 Root 权限", style = MaterialTheme.typography.titleSmall)
                Text(
                    "通过 Magisk、KernelSU 等 su 管理器授权。不可用时可回退到已授权的 Shell。",
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
                Text("请求 Shell 权限", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        shellGranted -> "Shizuku Shell 已授权"
                        shizukuRunning -> "Shizuku 正在运行，开启后请求授权"
                        else -> "需要先通过无线调试或电脑 ADB 启动 Shizuku"
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
            label = { Text("自定义 su 命令") },
            supportingText = {
                Text("默认 su -c。可用 {command} 指定命令插入位置，例如 su 0 sh -c {command}。")
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
                Text(if (shellGranted) "Shell 已授权" else "授权 Shell")
            }
            OutlinedButton(
                onClick = {
                    rootStatus = "检测中..."
                    scope.launch {
                        val result = executor.probeRoot()
                        rootStatus = if (result.ok && result.stdout.trim().lineSequence().lastOrNull() == "0") {
                            "Root 可用"
                        } else {
                            "Root 不可用：${result.stderr.ifBlank { result.message }.take(120)}"
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("检测 Root")
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
        "Root 和 Shell 开关都关闭时，AI 不会看到任何系统命令工具。所有 Shell/Root 命令都会先显示完整命令并请求确认；Root 命令风险更高。普通 ADB 不会永久赋予应用 shell 身份，本应用通过 Shizuku 获取该能力。",
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
            val displayStatus = if (row.title == "读取应用列表") {
                row.status
            } else if (row.granted) {
                "已允许"
            } else {
                row.status
            }
            KimiMenuRow(row.icon, row.title, displayStatus) {
                if (row.title == "与 Termux 通信") {
                    requestTermuxRunCommandPermission(context)
                    revision++
                } else {
                    openAppSettings(context)
                }
            }
            if (index != permissions.lastIndex) KimiDivider()
        }
    }
    Text("媒体、定位、通知、摄像头等权限会跳转系统应用信息页；Termux 通信权限由 Termux 提供，点击后直接弹出授权许可。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun AgentToolSettings(settings: AppSettings, termuxExecutor: TermuxExecutor, externalRevision: Int = 0) {
    var disabled by remember(externalRevision) { mutableStateOf(settings.disabledTools()) }
    var query by rememberSaveable { mutableStateOf("") }
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
        Text("搜索工具", style = MaterialTheme.typography.titleSmall)
        CapsuleTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "搜索名称、工具名或描述",
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
        )
        Text("匹配 ${filteredLocalTools.size + filteredMcpTools.size} / ${localTools.size + mcpTools.size} 个工具", color = KimiMuted, style = MaterialTheme.typography.labelSmall)
    }
    KimiCardBox {
        if (filteredLocalTools.isEmpty() && filteredMcpTools.isEmpty()) {
            Text("没有匹配的工具", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
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
                        Text("未授予 Termux RUN_COMMAND 权限，工具已自动禁用。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    if (tool.name == "ssh_exec" && !sshToolsEnabled) {
                        Text("未配置启用的 SSH 连接，工具暂不可用。", color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    if (protectedTool) {
                        Text("保护工具，不能禁用。", color = KimiMuted, style = MaterialTheme.typography.labelSmall)
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
                    Text(tool.description.ifBlank { "远程 MCP 工具" }, color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        KimiMenuRow(Icons.Default.Terminal, "Termux", if (termuxExecutor.isTermuxInstalled()) "已安装" else "未安装")
        KimiDivider()
        KimiMenuRow(Icons.Default.Extension, "Termux:API", if (termuxExecutor.isTermuxApiInstalled()) "可用" else "未安装")
        KimiDivider()
        KimiMenuRow(Icons.Default.CheckCircle, "RUN_COMMAND 权限", if (permissionGranted) "已授予" else "点击授予") {
            requestTermuxRunCommandPermission(context)
            revision++
        }
        KimiDivider()
        KimiMenuRow(Icons.Default.Folder, "Termux 路径", workspaceManager.termuxRootPath() ?: "仅 primary")
    }
    TermuxSetupGuide()
}

@Composable
internal fun WebDavSettings(settings: AppSettings, webDavClient: WebDavClient, externalRevision: Int = 0) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val servers = remember(revision, externalRevision) { settings.webDavServers() }
    var editing by remember { mutableStateOf<WebDavServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<WebDavServerConfig?>(null) }
    var status by remember { mutableStateOf("") }

    fun saveServers(updated: List<WebDavServerConfig>) {
        settings.saveWebDavServers(updated)
        revision++
    }

    editing?.let { server ->
        WebDavServerDialog(
            initial = server,
            onDismiss = { editing = null },
            onSave = { saved ->
                val updated = servers.toMutableList()
                val index = updated.indexOfFirst { it.id == saved.id }
                if (index >= 0) updated[index] = saved else updated += saved
                saveServers(updated)
                editing = null
                status = "WebDAV 已保存"
            },
        )
    }
    deleteTarget?.let { server ->
        ConfirmDeleteDialog(
            title = "删除 WebDAV 配置",
            message = "该操作会删除此 WebDAV 服务器配置和保存的认证信息。",
            targetName = server.name.ifBlank { server.url },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                saveServers(servers.filterNot { it.id == server.id })
                status = "已删除 ${server.name}"
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WebDAV", style = MaterialTheme.typography.titleMedium)
                Text("用于远程文件搜索、上传下载和云端备份。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { editing = defaultWebDavServer() }, shape = KimiPillShape) { Text("添加") }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    if (servers.isEmpty()) {
        KimiCardBox {
            Text("暂无 WebDAV 服务器", style = MaterialTheme.typography.titleSmall)
            Text("添加后，AI 可在用户确认后把 WebDAV 文件下载到工作区，或把工作区文件上传到 WebDAV。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }

    servers.forEach { server ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (server.hideAddressInDrawer) "地址已隐藏" else server.url, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text("${server.username.ifBlank { "匿名" }} · ${server.initialPath.ifBlank { "/" }}", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    if (server.url.startsWith("http://", ignoreCase = true)) {
                        Text("安全提示：HTTP 明文连接可能泄露账号、密码和文件内容。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = { enabled ->
                        saveServers(servers.map { if (it.id == server.id) it.copy(enabled = enabled) else it })
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        status = "正在测试 ${server.name}..."
                        scope.launch {
                            status = withContext(Dispatchers.IO) {
                                webDavClient.test(server).fold(
                                    onSuccess = { "WebDAV 测试成功，当前目录 ${it.size} 项" },
                                    onFailure = { "WebDAV 测试失败：${it.message}" },
                                )
                            }
                        }
                    },
                    shape = KimiPillShape,
                ) { Text("测试连接") }
                IconButton(onClick = { editing = server }) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑 WebDAV")
                }
                IconButton(onClick = { deleteTarget = server }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除 WebDAV")
                }
            }
        }
    }
}

@Composable
internal fun WebDavServerDialog(
    initial: WebDavServerConfig,
    onDismiss: () -> Unit,
    onSave: (WebDavServerConfig) -> Unit,
) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var url by rememberSaveable(initial.id) { mutableStateOf(initial.url) }
    var username by rememberSaveable(initial.id) { mutableStateOf(initial.username) }
    var password by rememberSaveable(initial.id) { mutableStateOf(initial.password) }
    var userAgent by rememberSaveable(initial.id) { mutableStateOf(initial.userAgent) }
    var initialPath by rememberSaveable(initial.id) { mutableStateOf(initial.initialPath) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var trustAll by rememberSaveable(initial.id) { mutableStateOf(initial.trustAllCertificates) }
    var multiThread by rememberSaveable(initial.id) { mutableStateOf(initial.multiThread) }
    var hideAddress by rememberSaveable(initial.id) { mutableStateOf(initial.hideAddressInDrawer) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("服务名") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text("URL") }, singleLine = true)
                if (url.startsWith("http://", ignoreCase = true)) {
                    Text("HTTP 明文连接不安全，可能泄露账号密码和文件内容。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("用户名，可空") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("密码，可空") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(value = userAgent, onValueChange = { userAgent = it }, modifier = Modifier.fillMaxWidth(), label = { Text("自定义 UA，可空") }, singleLine = true)
                OutlinedTextField(value = initialPath, onValueChange = { initialPath = it }, modifier = Modifier.fillMaxWidth(), label = { Text("初始路径") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                WebDavSwitchRow("信任所有 HTTPS 证书", "仅用于自签名证书服务器；不建议在公网服务开启。", trustAll) { trustAll = it }
                WebDavSwitchRow("启用多线程传输", "保存此偏好，后续大文件传输可按此策略扩展。", multiThread) { multiThread = it }
                WebDavSwitchRow("在侧栏隐藏地址", "隐藏 URL 以避免旁人看到服务器地址。", hideAddress) { hideAddress = it }
                WebDavSwitchRow("启用此服务器", "禁用后 AI 无法看到或调用该服务器。", enabled) { enabled = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        WebDavServerConfig(
                            id = initial.id.ifBlank { AppSettings.newId() },
                            name = name.ifBlank { "WebDAV" },
                            url = url.trim(),
                            username = username.trim(),
                            password = password,
                            userAgent = userAgent.trim(),
                            initialPath = initialPath.ifBlank { "/" },
                            note = note,
                            trustAllCertificates = trustAll,
                            multiThread = multiThread,
                            hideAddressInDrawer = hideAddress,
                            enabled = enabled,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun WebDavSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

internal fun defaultWebDavServer(): WebDavServerConfig = WebDavServerConfig(
    id = AppSettings.newId(),
    name = "WebDAV",
    url = "",
    username = "",
    password = "",
    userAgent = "",
    initialPath = "/",
    note = "",
    trustAllCertificates = false,
    multiThread = true,
    hideAddressInDrawer = false,
    enabled = true,
)

@Composable
internal fun FileTransferSettings(settings: AppSettings, fileTransferClient: FileTransferClient, externalRevision: Int = 0) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val servers = remember(revision, externalRevision) { settings.fileTransferServers() }
    var editing by remember { mutableStateOf<FileTransferServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<FileTransferServerConfig?>(null) }
    var status by remember { mutableStateOf("") }

    fun saveServers(updated: List<FileTransferServerConfig>) {
        settings.saveFileTransferServers(updated)
        revision++
    }

    editing?.let { server ->
        FileTransferServerDialog(
            initial = server,
            onDismiss = { editing = null },
            onSave = { saved ->
                val updated = servers.toMutableList()
                val index = updated.indexOfFirst { it.id == saved.id }
                if (index >= 0) updated[index] = saved else updated += saved
                saveServers(updated)
                editing = null
                status = "文件传输配置已保存"
            },
        )
    }
    deleteTarget?.let { server ->
        ConfirmDeleteDialog(
            title = "删除文件传输配置",
            message = "该操作会删除此 ${server.protocol.uppercase(Locale.US)} 服务器配置和保存的认证信息。",
            targetName = server.name.ifBlank { server.host },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                saveServers(servers.filterNot { it.id == server.id })
                status = "已删除 ${server.name}"
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FTP / FTPS / SFTP", style = MaterialTheme.typography.titleMedium)
                Text("用于远程文件搜索、上传和下载；AI 执行上传下载前仍需用户确认。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { editing = defaultFileTransferServer(AppSettings.FILE_TRANSFER_SFTP) }, shape = KimiPillShape) { Text("添加") }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    if (servers.isEmpty()) {
        KimiCardBox {
            Text("暂无文件传输服务器", style = MaterialTheme.typography.titleSmall)
            Text("添加 FTP、FTPS 或 SFTP 后，AI 可列出远程目录、搜索文件，并在用户确认后下载到工作区或从工作区上传。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }

    servers.forEach { server ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (server.hideAddressInDrawer) "地址已隐藏" else "${server.protocol.uppercase(Locale.US)}://${server.host}:${server.port}",
                        color = KimiMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val auth = if (server.protocol == AppSettings.FILE_TRANSFER_SFTP && server.usePrivateKey) "密钥登录" else server.username.ifBlank { "匿名" }
                    Text("$auth · ${server.initialPath.ifBlank { "/" }}", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    if (server.protocol == AppSettings.FILE_TRANSFER_FTP) {
                        Text("安全提示：FTP 明文连接可能泄露账号、密码和文件内容。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = { enabled ->
                        saveServers(servers.map { if (it.id == server.id) it.copy(enabled = enabled) else it })
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        status = "正在测试 ${server.name}..."
                        scope.launch {
                            status = withContext(Dispatchers.IO) {
                                fileTransferClient.test(server).fold(
                                    onSuccess = { "${server.protocol.uppercase(Locale.US)} 测试成功，当前目录 ${it.size} 项" },
                                    onFailure = { "${server.protocol.uppercase(Locale.US)} 测试失败：${it.message}" },
                                )
                            }
                        }
                    },
                    shape = KimiPillShape,
                ) { Text("测试连接") }
                IconButton(onClick = { editing = server }) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑文件传输")
                }
                IconButton(onClick = { deleteTarget = server }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除文件传输")
                }
            }
        }
    }
}

@Composable
internal fun FileTransferServerDialog(
    initial: FileTransferServerConfig,
    onDismiss: () -> Unit,
    onSave: (FileTransferServerConfig) -> Unit,
) {
    var protocol by rememberSaveable(initial.id) { mutableStateOf(AppSettings.normalizeFileTransferProtocol(initial.protocol)) }
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var host by rememberSaveable(initial.id) { mutableStateOf(initial.host) }
    var portText by rememberSaveable(initial.id) { mutableStateOf(initial.port.toString()) }
    var username by rememberSaveable(initial.id) { mutableStateOf(initial.username) }
    var password by rememberSaveable(initial.id) { mutableStateOf(initial.password) }
    var usePrivateKey by rememberSaveable(initial.id) { mutableStateOf(initial.usePrivateKey) }
    var privateKey by rememberSaveable(initial.id) { mutableStateOf(initial.privateKey) }
    var passphrase by rememberSaveable(initial.id) { mutableStateOf(initial.passphrase) }
    var initialPath by rememberSaveable(initial.id) { mutableStateOf(initial.initialPath) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var encoding by rememberSaveable(initial.id) { mutableStateOf(initial.encoding) }
    var passiveMode by rememberSaveable(initial.id) { mutableStateOf(initial.passiveMode) }
    var explicitFtps by rememberSaveable(initial.id) { mutableStateOf(initial.explicitFtps) }
    var multiThread by rememberSaveable(initial.id) { mutableStateOf(initial.multiThread) }
    var syncPermissions by rememberSaveable(initial.id) { mutableStateOf(initial.syncPermissions) }
    var hideAddress by rememberSaveable(initial.id) { mutableStateOf(initial.hideAddressInDrawer) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.enabled) }
    var protocolMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件传输") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    OutlinedButton(onClick = { protocolMenu = true }, shape = KimiPillShape) {
                        Text(protocol.uppercase(Locale.US))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = protocolMenu, onDismissRequest = { protocolMenu = false }) {
                        listOf(AppSettings.FILE_TRANSFER_SFTP, AppSettings.FILE_TRANSFER_FTP, AppSettings.FILE_TRANSFER_FTPS).forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.uppercase(Locale.US)) },
                                onClick = {
                                    protocol = item
                                    portText = AppSettings.defaultFileTransferPort(item).toString()
                                    if (item == AppSettings.FILE_TRANSFER_SFTP && username == "anonymous") username = ""
                                    if (item != AppSettings.FILE_TRANSFER_SFTP && username.isBlank()) username = "anonymous"
                                    protocolMenu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("服务名") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, modifier = Modifier.fillMaxWidth(), label = { Text("主机") }, singleLine = true)
                OutlinedTextField(value = portText, onValueChange = { portText = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("端口") }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (protocol == AppSettings.FILE_TRANSFER_SFTP) "用户名" else "用户名，可空") }, singleLine = true)
                if (protocol == AppSettings.FILE_TRANSFER_SFTP) {
                    WebDavSwitchRow("使用密钥登录", "开启后使用私钥和可选口令登录 SFTP。", usePrivateKey) { usePrivateKey = it }
                }
                if (protocol == AppSettings.FILE_TRANSFER_SFTP && usePrivateKey) {
                    OutlinedTextField(value = privateKey, onValueChange = { privateKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("私钥内容") }, minLines = 4)
                    OutlinedTextField(value = passphrase, onValueChange = { passphrase = it }, modifier = Modifier.fillMaxWidth(), label = { Text("私钥口令，可空") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                } else {
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("密码，可空") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
                if (protocol == AppSettings.FILE_TRANSFER_FTP) {
                    Text("FTP 是明文协议，建议只在可信局域网使用；公网或敏感文件请优先使用 SFTP/FTPS。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = initialPath, onValueChange = { initialPath = it }, modifier = Modifier.fillMaxWidth(), label = { Text("初始路径") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                OutlinedTextField(value = encoding, onValueChange = { encoding = it }, modifier = Modifier.fillMaxWidth(), label = { Text("编码") }, singleLine = true)
                if (protocol != AppSettings.FILE_TRANSFER_SFTP) {
                    WebDavSwitchRow("被动模式", "FTP/FTPS 推荐开启被动模式，兼容 NAT 和多数服务器。", passiveMode) { passiveMode = it }
                }
                if (protocol == AppSettings.FILE_TRANSFER_FTPS) {
                    WebDavSwitchRow("显式 FTPS", "使用 AUTH TLS 升级连接；关闭后尝试隐式 FTPS。", explicitFtps) { explicitFtps = it }
                }
                WebDavSwitchRow("启用多线程传输", "保存此偏好，后续大文件传输可按此策略扩展。", multiThread) { multiThread = it }
                WebDavSwitchRow("传输时同步文件权限", "仅部分 SFTP 服务器支持。", syncPermissions) { syncPermissions = it }
                WebDavSwitchRow("在侧栏隐藏地址", "隐藏主机地址以避免旁人看到服务器信息。", hideAddress) { hideAddress = it }
                WebDavSwitchRow("启用此服务器", "禁用后 AI 无法看到或调用该服务器。", enabled) { enabled = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedProtocol = AppSettings.normalizeFileTransferProtocol(protocol)
                    onSave(
                        FileTransferServerConfig(
                            id = initial.id.ifBlank { AppSettings.newId() },
                            name = name.ifBlank { normalizedProtocol.uppercase(Locale.US) },
                            protocol = normalizedProtocol,
                            host = host.trim(),
                            port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: AppSettings.defaultFileTransferPort(normalizedProtocol),
                            username = username.trim().ifBlank { if (normalizedProtocol == AppSettings.FILE_TRANSFER_SFTP) "" else "anonymous" },
                            password = password,
                            usePrivateKey = normalizedProtocol == AppSettings.FILE_TRANSFER_SFTP && usePrivateKey,
                            privateKey = privateKey,
                            passphrase = passphrase,
                            initialPath = initialPath.ifBlank { "/" },
                            note = note,
                            encoding = encoding.ifBlank { "UTF-8" },
                            passiveMode = passiveMode,
                            explicitFtps = explicitFtps,
                            multiThread = multiThread,
                            syncPermissions = syncPermissions,
                            hideAddressInDrawer = hideAddress,
                            enabled = enabled,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

internal fun defaultFileTransferServer(protocol: String): FileTransferServerConfig {
    val normalized = AppSettings.normalizeFileTransferProtocol(protocol)
    return FileTransferServerConfig(
        id = AppSettings.newId(),
        name = normalized.uppercase(Locale.US),
        protocol = normalized,
        host = "",
        port = AppSettings.defaultFileTransferPort(normalized),
        username = if (normalized == AppSettings.FILE_TRANSFER_SFTP) "" else "anonymous",
        password = "",
        usePrivateKey = false,
        privateKey = "",
        passphrase = "",
        initialPath = "/",
        note = "",
        encoding = "UTF-8",
        passiveMode = true,
        explicitFtps = true,
        multiThread = true,
        syncPermissions = false,
        hideAddressInDrawer = false,
        enabled = true,
    )
}

@Composable
internal fun MiniServerSettings(
    settings: AppSettings,
    miniServerManager: MiniServerManager,
    externalRevision: Int = 0,
    onOpenLogs: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val savedConfig = remember(revision, externalRevision) { settings.miniServerConfig() }
    var protocol by remember(savedConfig) { mutableStateOf(savedConfig.protocol) }
    var host by remember(savedConfig) { mutableStateOf(savedConfig.host) }
    var portText by remember(savedConfig) { mutableStateOf(savedConfig.port.toString()) }
    var username by remember(savedConfig) { mutableStateOf(savedConfig.username.ifBlank { AppSettings.DEFAULT_MINI_SERVER_USERNAME }) }
    var password by remember(savedConfig) { mutableStateOf(savedConfig.password) }
    var customDomainsText by remember(savedConfig) { mutableStateOf(savedConfig.customDomains.joinToString("\n")) }
    var forceHttps by remember(savedConfig) { mutableStateOf(savedConfig.forceHttps) }
    var tlsKeyStoreBase64 by remember(savedConfig) { mutableStateOf(savedConfig.tlsKeyStoreBase64) }
    var tlsKeyStorePassword by remember(savedConfig) { mutableStateOf(savedConfig.tlsKeyStorePassword) }
    var tlsCertificateChain by remember(savedConfig) { mutableStateOf(savedConfig.tlsCertificateChain) }
    var tlsPrivateKey by remember(savedConfig) { mutableStateOf(savedConfig.tlsPrivateKey) }
    var spaFallback by remember(savedConfig) { mutableStateOf(savedConfig.spaFallback) }
    var directoryListing by remember(savedConfig) { mutableStateOf(savedConfig.directoryListing) }
    var mdnsEnabled by remember(savedConfig) { mutableStateOf(savedConfig.mdnsEnabled) }
    var mdnsName by remember(savedConfig) { mutableStateOf(savedConfig.mdnsName) }
    var statusText by remember { mutableStateOf("") }
    var statusRevision by remember { mutableIntStateOf(0) }
    val keyStoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tlsKeyStoreBase64 = Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
                } ?: error("无法读取证书库文件")
            }.fold(
                { statusText = "已读取证书库文件" },
                { statusText = "读取证书库失败：${it.message}" },
            )
        }
    }
    val certChainLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tlsCertificateChain = input.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("无法读取证书链文件")
            }.fold(
                { statusText = "已读取证书链文件" },
                { statusText = "读取证书链失败：${it.message}" },
            )
        }
    }
    val privateKeyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tlsPrivateKey = input.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("无法读取私钥文件")
            }.fold(
                { statusText = "已读取私钥文件" },
                { statusText = "读取私钥失败：${it.message}" },
            )
        }
    }
    val status = remember(statusRevision, revision, externalRevision) { miniServerManager.status() }
    val lanUrls = remember(statusRevision, revision, externalRevision) {
        miniServerManager.statusJson().optJSONArray("lanUrls")?.let { array ->
            buildList {
                for (index in 0 until array.length()) add(array.optString(index))
            }
        }.orEmpty()
    }
    val customUrls = remember(statusRevision, revision, externalRevision) {
        miniServerManager.statusJson().optJSONArray("customUrls")?.let { array ->
            buildList {
                for (index in 0 until array.length()) add(array.optString(index))
            }
        }.orEmpty()
    }

    fun currentConfig(enabled: Boolean = status.running): MiniServerConfig {
        return MiniServerConfig(
            protocol = if (protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTPS) AppSettings.MINI_SERVER_PROTOCOL_HTTPS else AppSettings.MINI_SERVER_PROTOCOL_HTTP,
            host = host.trim().ifBlank { AppSettings.DEFAULT_MINI_SERVER_HOST },
            port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: AppSettings.DEFAULT_MINI_SERVER_PORT,
            username = username.trim().ifBlank { AppSettings.DEFAULT_MINI_SERVER_USERNAME },
            password = password,
            customDomains = customDomainsText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct().toList(),
            forceHttps = forceHttps,
            tlsKeyStoreBase64 = tlsKeyStoreBase64,
            tlsKeyStorePassword = tlsKeyStorePassword,
            tlsCertificateChain = tlsCertificateChain,
            tlsPrivateKey = tlsPrivateKey,
            spaFallback = spaFallback,
            directoryListing = directoryListing,
            mdnsEnabled = mdnsEnabled,
            mdnsName = mdnsName.ifBlank { AppSettings.DEFAULT_MINI_SERVER_MDNS_NAME },
            enabled = enabled,
        )
    }

    fun refresh(message: String) {
        statusText = message
        statusRevision++
        revision++
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("工作区微型服务器", style = MaterialTheme.typography.titleMedium)
                Text("以当前工作目录作为静态站点根目录，适合调试 Vue/Vite 文档站或普通 HTML/CSS/JS。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (status.running) "运行中" else "已停止", color = if (status.running) MaterialTheme.colorScheme.primary else KimiMuted)
        }
        KimiDivider()
        Text("本地地址：${status.url}", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        if (lanUrls.isNotEmpty()) {
            Text("局域网地址：${lanUrls.joinToString("  ")}", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (customUrls.isNotEmpty()) {
            Text("绑定域名：${customUrls.joinToString("  ")}", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (status.message.isNotBlank()) {
            Text(status.message, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (statusText.isNotBlank()) {
            Text(statusText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = onOpenLogs,
            shape = KimiPillShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Article, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("查看终端日志")
        }
    }

    KimiCardBox {
        Text("监听配置", style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { protocol = AppSettings.MINI_SERVER_PROTOCOL_HTTP },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text(if (protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTP) "HTTP ✓" else "HTTP") }
            OutlinedButton(
                onClick = { protocol = AppSettings.MINI_SERVER_PROTOCOL_HTTPS },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text(if (protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTPS) "HTTPS ✓" else "HTTPS") }
        }
        if (protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTPS) {
            Text("HTTPS 使用内置自签名证书，浏览器会提示不受信任；公网或正式分享建议使用内网穿透/反向代理提供可信 TLS。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(value = host, onValueChange = { host = it }, modifier = Modifier.fillMaxWidth(), label = { Text("监听主机") }, singleLine = true)
        Text("127.0.0.1 仅本机访问；0.0.0.0 可被局域网、内网穿透或公网映射访问。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = portText, onValueChange = { portText = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("端口") }, singleLine = true)
        OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("访问用户名") }, singleLine = true)
        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("访问密码，可空") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        OutlinedTextField(
            value = customDomainsText,
            onValueChange = { customDomainsText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            label = { Text("绑定域名，每行一个") },
            placeholder = { Text("docs.example.com\nhttps://preview.example.com") },
        )
        WebDavSwitchRow("强制 HTTPS 连接", "HTTP 访问会返回 308 跳转到 HTTPS；适合反向代理或同端口 HTTPS 调试。", forceHttps) { forceHttps = it }
        if (host.trim() == "0.0.0.0" || password.isBlank()) {
            Text("安全提示：面向局域网或公网映射时建议设置用户名和密码；HTTP 明文会暴露访问内容和账号密码。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    KimiCardBox {
        Text("HTTPS 证书", style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        Text("未配置自定义证书时会使用内置自签名证书。证书库支持 PKCS12/JKS；PEM 私钥需为未加密 PKCS#8 格式。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { keyStoreLauncher.launch("*/*") },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text(if (tlsKeyStoreBase64.isBlank()) "上传证书库" else "替换证书库") }
            OutlinedButton(
                onClick = { tlsKeyStoreBase64 = "" },
                shape = KimiPillShape,
                enabled = tlsKeyStoreBase64.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("清除证书库") }
        }
        OutlinedTextField(
            value = tlsKeyStorePassword,
            onValueChange = { tlsKeyStorePassword = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("证书库/私钥密码，可空") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { certChainLauncher.launch("*/*") }, shape = KimiPillShape, modifier = Modifier.weight(1f)) { Text("上传证书链") }
            OutlinedButton(onClick = { privateKeyLauncher.launch("*/*") }, shape = KimiPillShape, modifier = Modifier.weight(1f)) { Text("上传私钥") }
        }
        OutlinedTextField(
            value = tlsCertificateChain,
            onValueChange = { tlsCertificateChain = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text("证书链 PEM，可粘贴") },
            placeholder = { Text("-----BEGIN CERTIFICATE-----") },
        )
        OutlinedTextField(
            value = tlsPrivateKey,
            onValueChange = { tlsPrivateKey = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text("私钥 PEM，可粘贴") },
            placeholder = { Text("-----BEGIN PRIVATE KEY-----") },
            visualTransformation = PasswordVisualTransformation(),
        )
    }

    KimiCardBox {
        Text("站点行为", style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        WebDavSwitchRow("SPA 回退到 index.html", "适合 Vue Router / VitePress / 单页应用刷新路径。", spaFallback) { spaFallback = it }
        WebDavSwitchRow("允许目录列表", "没有 index.html 时显示目录文件；公网环境不建议开启。", directoryListing) { directoryListing = it }
        WebDavSwitchRow("发布 mDNS", "在局域网内尝试发布 _http._tcp 服务，便于支持 mDNS 的设备发现。", mdnsEnabled) { mdnsEnabled = it }
        if (mdnsEnabled) {
            OutlinedTextField(value = mdnsName, onValueChange = { mdnsName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("mDNS 名称") }, singleLine = true)
        }
    }

    KimiCardBox {
        Text("操作", style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    settings.saveMiniServerConfig(currentConfig())
                    refresh("微型服务器配置已保存")
                },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text("保存") }
            Button(
                onClick = {
                    statusText = "正在启动..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { miniServerManager.start(currentConfig(enabled = true)) }
                                .fold({ "已启动：${it.url}" }, { "启动失败：${it.message}" })
                        }
                        refresh(result)
                    }
                },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text(if (status.running) "重启" else "启动") }
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { miniServerManager.stop() }
                            .fold({ "已停止" }, { "停止失败：${it.message}" })
                    }
                    refresh(result)
                }
            },
            shape = KimiPillShape,
            enabled = status.running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("停止服务") }
    }
}

@Composable
internal fun MiniServerLogSettings(miniServerManager: MiniServerManager) {
    var revision by remember { mutableIntStateOf(0) }
    var levelFilter by rememberSaveable { mutableStateOf("") }
    val payload = remember(revision, levelFilter) { miniServerManager.logsJson(200, levelFilter) }
    val logs = remember(payload) {
        payload.optJSONArray("logs")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { add(it) }
                }
            }
        }.orEmpty()
    }
    val levels = listOf("" to "ALL", "info" to "INFO", "warn" to "WARN", "error" to "ERROR")
    val terminalScroll = rememberScrollState()
    val terminalHorizontalScroll = rememberScrollState()
    val filterScroll = rememberScrollState()

    LaunchedEffect(levelFilter) {
        while (true) {
            delay(1_000)
            revision++
        }
    }

    LaunchedEffect(logs.size, levelFilter) {
        terminalScroll.animateScrollTo(terminalScroll.maxValue)
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("终端日志", style = MaterialTheme.typography.titleMedium)
                Text(
                    "自动跟随连接、资源加载、认证失败、404 和页面 JavaScript 报错。",
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(
                onClick = {
                    miniServerManager.clearLogs()
                    revision++
                },
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "清空日志", tint = MaterialTheme.colorScheme.primary)
            }
        }
        KimiDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(filterScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${if (payload.optBoolean("running")) "RUNNING" else "STOPPED"} · ${payload.optString("workspace")} · ${payload.optInt("count")} lines",
                color = KimiMuted,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            levels.forEach { (value, label) ->
                TextButton(
                    onClick = { levelFilter = value },
                    shape = KimiPillShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (levelFilter == value) "[$label]" else label,
                        maxLines = 1,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 620.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101114))
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .horizontalScroll(terminalHorizontalScroll)
                    .verticalScroll(terminalScroll),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (logs.isEmpty()) {
                    Text(
                        "$ lyra mini-server logs --follow\n# 暂无日志。启动微型服务器并访问站点后，这里会自动显示请求记录和客户端错误。",
                        color = Color(0xFF8BE9FD),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    logs.forEach { log ->
                        MiniServerTerminalLine(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniServerTerminalLine(log: JSONObject) {
    val level = log.optString("level", "info")
    val color = when (level.lowercase(Locale.US)) {
        "error" -> Color(0xFFFF6B6B)
        "warn" -> Color(0xFFFFC857)
        else -> Color(0xFF7BD88F)
    }
    val time = remember(log.optLong("timestamp")) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.optLong("timestamp")))
    }
    val status = log.optInt("status").takeIf { it > 0 }?.toString().orEmpty()
    val method = log.optString("method")
    val path = log.optString("path")
    val durationMs = log.optLong("durationMs")
    val message = log.optString("message")
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$time ",
            color = Color(0xFF8D99AE),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Text(
            level.uppercase(Locale.US).padEnd(5),
            color = color,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildString {
                if (method.isNotBlank()) append(method).append(' ')
                if (status.isNotBlank()) append(status).append(' ')
                append(path.ifBlank { "-" })
                append(" (").append(durationMs).append("ms)")
                if (message.isNotBlank()) append(" - ").append(message)
            },
            color = Color(0xFFE8EAED),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
@Composable
internal fun BackupSettings(
    settings: AppSettings,
    webDavClient: WebDavClient,
    backupManager: BackupManager,
    status: String,
    onStatusChange: (String) -> Unit,
    onImportBackup: (String) -> Unit,
    onConfigChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val webDavServers = settings.webDavServers().filter { it.enabled }
    var includeProfile by rememberSaveable { mutableStateOf(true) }
    var includeConversations by rememberSaveable { mutableStateOf(true) }
    var includeRoleplay by rememberSaveable { mutableStateOf(true) }
    var includeModelProfiles by rememberSaveable { mutableStateOf(true) }
    var includeMcp by rememberSaveable { mutableStateOf(true) }
    var includeSsh by rememberSaveable { mutableStateOf(true) }
    var includePrompts by rememberSaveable { mutableStateOf(true) }
    var includeSkills by rememberSaveable { mutableStateOf(true) }
    var includeWebDav by rememberSaveable { mutableStateOf(true) }
    var includeFileTransfer by rememberSaveable { mutableStateOf(true) }
    var includeSecrets by rememberSaveable { mutableStateOf(false) }
    var selectedServerId by rememberSaveable { mutableStateOf(webDavServers.firstOrNull()?.id.orEmpty()) }
    var remotePath by rememberSaveable { mutableStateOf(DEFAULT_WEBDAV_BACKUP_PATH) }
    var transferStatus by remember { mutableStateOf("") }
    val selectedServer = webDavServers.firstOrNull { it.id == selectedServerId } ?: webDavServers.firstOrNull()

    fun options() = BackupOptions(
        includeProfile = includeProfile,
        includeConversations = includeConversations,
        includeRoleplay = includeRoleplay,
        includeModelProfiles = includeModelProfiles,
        includeMcp = includeMcp,
        includeSsh = includeSsh,
        includePrompts = includePrompts,
        includeSkills = includeSkills,
        includeWebDav = includeWebDav,
        includeFileTransfer = includeFileTransfer,
        includeSecrets = includeSecrets,
    )

    KimiCardBox {
        Text("导出内容", style = MaterialTheme.typography.titleMedium)
        Text("可单独选择导出范围；跨版本导入时会跳过不兼容结构并导入可兼容部分。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        BackupIncludeRow("个人资料", includeProfile) { includeProfile = it }
        BackupIncludeRow("对话历史", includeConversations) { includeConversations = it }
        BackupIncludeRow("沉浸扮演设定", includeRoleplay) { includeRoleplay = it }
        BackupIncludeRow("模型服务配置", includeModelProfiles) { includeModelProfiles = it }
        BackupIncludeRow("MCP 服务器配置", includeMcp) { includeMcp = it }
        BackupIncludeRow("SSH 连接配置", includeSsh) { includeSsh = it }
        BackupIncludeRow("系统提示词", includePrompts) { includePrompts = it }
        BackupIncludeRow("Skills", includeSkills) { includeSkills = it }
        BackupIncludeRow("WebDAV 配置", includeWebDav) { includeWebDav = it }
        BackupIncludeRow("文件传输配置", includeFileTransfer) { includeFileTransfer = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("包含 API Key / 密码 / 私钥", style = MaterialTheme.typography.titleSmall)
                Text("包含密钥的备份可直接导入使用，但必须妥善保管，不要分享给他人。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = includeSecrets, onCheckedChange = { includeSecrets = it })
        }
        Button(
            onClick = {
                scope.launch {
                    onStatusChange("正在导出到 Download/LyraCode...")
                    onStatusChange(withContext(Dispatchers.IO) {
                        runCatching { backupManager.exportToDownloads(options()) }
                            .fold({ it }, { "导出失败：${it.message}" })
                    })
                }
            },
            shape = KimiPillShape,
        ) { Text("导出到本地") }
    }

    KimiCardBox {
        Text("导入备份", style = MaterialTheme.typography.titleMedium)
        Text("补充模式会在现有数据上新增并去重，推荐使用。覆盖模式会替换已有兼容配置，存在数据丢失风险。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onImportBackup("supplement") }, shape = KimiPillShape) { Text("补充导入") }
            OutlinedButton(onClick = { onImportBackup("replace") }, shape = KimiPillShape) { Text("覆盖导入") }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    KimiCardBox {
        Text("WebDAV 云备份", style = MaterialTheme.typography.titleMedium)
        if (webDavServers.isEmpty()) {
            Text("暂无启用的 WebDAV 服务器。先在 WebDAV 设置中添加服务器后，可直接上传或下载备份。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            WebDavServerPicker(webDavServers, selectedServerId) { selectedServerId = it }
            OutlinedTextField(value = remotePath, onValueChange = { remotePath = it }, modifier = Modifier.fillMaxWidth(), label = { Text("远程备份路径") }, singleLine = true)
            if (transferStatus.isNotBlank()) Text(transferStatus, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val server = selectedServer ?: return@OutlinedButton
                        scope.launch {
                            onStatusChange("正在导出并上传 WebDAV...")
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val bytes = backupManager.exportZip(options())
                                    val targetPath = remotePath.ifBlank { DEFAULT_WEBDAV_BACKUP_PATH }
                                    webDavClient.upload(server, targetPath, bytes) { progress ->
                                        scope.launch { transferStatus = formatTransferProgress(progress) }
                                    }
                                    "已上传到 ${server.name}:$targetPath"
                                }.fold({ it }, { "上传失败：${it.message}" })
                            }
                            transferStatus = ""
                            onStatusChange(result)
                        }
                    },
                    shape = KimiPillShape,
                ) { Text("上传备份") }
                OutlinedButton(
                    onClick = {
                        val server = selectedServer ?: return@OutlinedButton
                        scope.launch {
                            onStatusChange("正在从 WebDAV 下载并补充导入...")
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val requested = remotePath.trim().ifBlank { DEFAULT_WEBDAV_BACKUP_PATH }
                                    var usedPath = requested
                                    val bytes = runCatching {
                                        webDavClient.download(server, requested) { progress ->
                                            scope.launch { transferStatus = formatTransferProgress(progress) }
                                        }
                                    }.getOrElse {
                                        usedPath = resolveLatestWebDavBackupPath(webDavClient, server, requested)
                                        webDavClient.download(server, usedPath) { progress ->
                                            scope.launch { transferStatus = formatTransferProgress(progress) }
                                        }
                                    }
                                    "从 $usedPath 补充导入：${backupManager.importZip(bytes, "supplement")}"
                                }.fold({ "导入完成：$it" }, { "导入失败：${it.message}" })
                            }
                            transferStatus = ""
                            onStatusChange(result)
                            onConfigChanged()
                        }
                    },
                    shape = KimiPillShape,
                ) { Text("从云端导入") }
            }
        }
    }
}

private const val DEFAULT_WEBDAV_BACKUP_PATH = "/LyraCode/lyra_backup_latest.zip"

private fun resolveLatestWebDavBackupPath(client: WebDavClient, server: WebDavServerConfig, rawPath: String): String {
    val requested = rawPath.trim().ifBlank { DEFAULT_WEBDAV_BACKUP_PATH }
    val directory = requested.substringBeforeLast('/', "/").ifBlank { "/" }.let { if (it.endsWith("/")) it else "$it/" }
    val candidates = client.list(server, directory, depth = 1)
        .filter {
            val name = it.path.substringAfterLast('/')
            name.endsWith(".zip", ignoreCase = true) && name.contains("lyra_backup", ignoreCase = true)
        }
        .sortedWith(compareByDescending<com.yukisoffd.lyracode.webdav.WebDavFile> { it.modified }.thenByDescending { it.path.substringAfterLast('/') })
    return candidates.firstOrNull()?.path ?: requested
}

@Composable
internal fun BackupIncludeRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun WebDavServerPicker(servers: List<WebDavServerConfig>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()
    Box {
        OutlinedButton(onClick = { expanded = true }, shape = KimiPillShape) {
            Text(selected?.name ?: "选择 WebDAV")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            servers.forEach { server ->
                DropdownMenuItem(
                    text = { Text(server.name) },
                    onClick = {
                        onSelect(server.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun formatTransferProgress(progress: TransferProgress): String {
    val total = if (progress.totalBytes > 0) formatBytes(progress.totalBytes) else "未知大小"
    val percent = if (progress.totalBytes > 0) " · ${(progress.doneBytes * 100 / progress.totalBytes).coerceIn(0, 100)}%" else ""
    return "${progress.title}: ${formatBytes(progress.doneBytes)} / $total$percent · ${formatBytes(progress.bytesPerSecond)}/s"
}

@Composable
internal fun SshSettings(settings: AppSettings, sshExecutor: SshExecutor, externalRevision: Int = 0) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val servers = remember(revision, externalRevision) { settings.sshServers() }
    var editing by remember { mutableStateOf<SshServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<SshServerConfig?>(null) }
    var status by remember { mutableStateOf("") }

    fun saveServers(updated: List<SshServerConfig>) {
        settings.saveSshServers(updated)
        revision++
    }

    editing?.let { server ->
        SshServerDialog(
            initial = server,
            onDismiss = { editing = null },
            onSave = { saved ->
                val updated = servers.toMutableList()
                val index = updated.indexOfFirst { it.id == saved.id }
                if (index >= 0) updated[index] = saved else updated += saved
                saveServers(updated)
                editing = null
                status = "SSH 连接已保存"
            },
        )
    }
    deleteTarget?.let { server ->
        ConfirmDeleteDialog(
            title = "删除 SSH 连接",
            message = "该操作会删除服务器地址、用户名、密码或私钥配置。",
            targetName = "${server.name} · ${server.stableId}",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                saveServers(servers.filterNot { it.id == server.id })
                status = "已删除 ${server.name}"
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SSH 连接", style = MaterialTheme.typography.titleMedium)
                Text("用于连接 Git 服务器或公网 Linux/Windows 服务器。命令执行前会弹出确认。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { editing = defaultSshServer() }, shape = KimiPillShape) { Text("添加") }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    if (servers.isEmpty()) {
        KimiCardBox {
            Text("暂无 SSH 连接", style = MaterialTheme.typography.titleSmall)
            Text("可使用密码或私钥连接 GitHub/GitLab 服务器、VPS、云主机或局域网主机。配置会加密保存。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }

    servers.forEach { server ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(server.stableId, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    Text("${server.username} · ${sshAuthLabel(server.authType)} · ${server.timeoutSeconds}s", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = { enabled ->
                        saveServers(servers.map { if (it.id == server.id) it.copy(enabled = enabled) else it })
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        status = "正在测试 ${server.name}..."
                        scope.launch {
                            val result = sshExecutor.execute(
                                server = server,
                                command = "printf 'lyra_ssh_ok\\n' && uname -a 2>/dev/null || ver",
                                cwd = "",
                                inputLines = emptyList(),
                                timeoutSeconds = 15,
                            )
                            status = if (result.ok) "SSH 测试成功: ${server.stableId}" else result.message.take(200)
                        }
                    },
                    shape = KimiPillShape,
                ) { Text("测试连接") }
                IconButton(onClick = { editing = server }) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑 SSH")
                }
                IconButton(onClick = { deleteTarget = server }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除 SSH")
                }
            }
        }
    }

    KimiCardBox {
        Text("使用约束", style = MaterialTheme.typography.titleSmall)
        Text(
            "AI 使用 SSH 执行命令会像文件修改一样请求确认。安装软件或修改服务器前，AI 应先检查系统、CPU/GPU、内存、磁盘和权限。复杂交互式 shell（如 vim/top/交互 ssh）不适合由内置 SSH 工具执行。",
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun SshServerDialog(
    initial: SshServerConfig,
    onDismiss: () -> Unit,
    onSave: (SshServerConfig) -> Unit,
) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var host by rememberSaveable(initial.id) { mutableStateOf(initial.host) }
    var port by rememberSaveable(initial.id) { mutableStateOf(initial.port.toString()) }
    var username by rememberSaveable(initial.id) { mutableStateOf(initial.username) }
    var authType by rememberSaveable(initial.id) { mutableStateOf(initial.authType.ifBlank { AppSettings.SSH_AUTH_PASSWORD }) }
    var password by rememberSaveable(initial.id) { mutableStateOf(initial.password) }
    var privateKey by rememberSaveable(initial.id) { mutableStateOf(initial.privateKey) }
    var passphrase by rememberSaveable(initial.id) { mutableStateOf(initial.passphrase) }
    var timeout by rememberSaveable(initial.id) { mutableStateOf(initial.timeoutSeconds.toString()) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SSH 连接") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("显示名称") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, modifier = Modifier.fillMaxWidth(), label = { Text("主机/IP") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, modifier = Modifier.weight(1f), label = { Text("端口") }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.weight(2f), label = { Text("用户名") }, singleLine = true)
                }
                Text("认证方式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaterialChoiceButton("密码", authType == AppSettings.SSH_AUTH_PASSWORD) { authType = AppSettings.SSH_AUTH_PASSWORD }
                    MaterialChoiceButton("私钥", authType == AppSettings.SSH_AUTH_KEY) { authType = AppSettings.SSH_AUTH_KEY }
                }
                if (authType == AppSettings.SSH_AUTH_PASSWORD) {
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                } else {
                    OutlinedTextField(value = privateKey, onValueChange = { privateKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("私钥内容") }, minLines = 5, maxLines = 10)
                    OutlinedTextField(value = passphrase, onValueChange = { passphrase = it }, modifier = Modifier.fillMaxWidth(), label = { Text("私钥口令（可空）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
                OutlinedTextField(value = timeout, onValueChange = { timeout = it.filter(Char::isDigit).take(3) }, modifier = Modifier.fillMaxWidth(), label = { Text("默认超时秒数") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用此连接", style = MaterialTheme.typography.titleSmall)
                        Text("禁用后 AI 无法看到或调用该服务器。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text("固定标识将使用 ${host.ifBlank { "host" }}:${port.ifBlank { "22" }}，AI 调用 SSH 工具时会使用这个标识。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SshServerConfig(
                            id = initial.id.ifBlank { AppSettings.newId() },
                            name = name.ifBlank { host.ifBlank { "SSH Server" } },
                            host = host.trim(),
                            port = port.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
                            username = username.trim(),
                            authType = authType,
                            password = password,
                            privateKey = privateKey,
                            passphrase = passphrase,
                            timeoutSeconds = timeout.toIntOrNull()?.coerceIn(5, 600) ?: 60,
                            enabled = enabled,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

internal fun defaultSshServer(): SshServerConfig = SshServerConfig(
    id = AppSettings.newId(),
    name = "SSH Server",
    host = "",
    port = 22,
    username = "",
    authType = AppSettings.SSH_AUTH_PASSWORD,
    password = "",
    privateKey = "",
    passphrase = "",
    timeoutSeconds = 60,
    enabled = true,
)

internal fun sshAuthLabel(authType: String): String = when (authType) {
    AppSettings.SSH_AUTH_KEY -> "私钥"
    else -> "密码"
}

@Composable
internal fun McpSettings(
    settings: AppSettings,
    mcpClientManager: McpClientManager,
    externalRevision: Int = 0,
) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val servers = remember(revision, externalRevision) { settings.mcpServers() }
    var editing by remember { mutableStateOf<McpServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<McpServerConfig?>(null) }
    var status by remember { mutableStateOf("") }
    var expandedToolServerIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    editing?.let { server ->
        McpServerDialog(
            initial = server,
            onDismiss = { editing = null },
            onSave = {
                settings.upsertMcpServer(it)
                editing = null
                status = "MCP 服务器已保存"
                revision++
            },
        )
    }
    deleteTarget?.let { server ->
        ConfirmDeleteDialog(
            title = "删除 MCP 服务器",
            message = "该操作会删除此 MCP 服务器连接、认证信息和已拉取的工具列表。",
            targetName = server.name.ifBlank { server.url },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                settings.deleteMcpServer(server.id)
                status = "已删除 ${server.name}"
                revision++
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MCP 服务器", style = MaterialTheme.typography.titleMedium)
                Text("支持 Streamable HTTP 与 SSE。Android 端不直接启动 stdio MCP Server。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                editing = defaultMcpServer()
            }, shape = KimiPillShape) { Text("添加") }
        }
        if (status.isNotBlank()) {
            Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (servers.isEmpty()) {
        KimiCardBox {
            Text("暂无 MCP 服务器", style = MaterialTheme.typography.titleSmall)
            Text("请添加远程或局域网 MCP Server URL。若服务器使用 HTTP 明文连接，API Key 和工具参数可能被同一网络中的第三方截获。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    servers.forEach { server ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(server.url, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text("${transportLabel(server.transport)} · ${server.timeoutSeconds}s · ${server.tools.size} 个 tools", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    if (server.url.startsWith("http://", ignoreCase = true)) {
                        Text("安全提示：HTTP 明文连接可能泄露认证 key、工具参数和返回内容。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = {
                        settings.setMcpServerEnabled(server.id, it)
                        revision++
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        status = "正在测试 ${server.name}..."
                        scope.launch {
                            mcpClientManager.testAndRefreshTools(server).fold(
                                onSuccess = {
                                    status = "已连接 ${server.name}，拉取 ${it.size} 个 tools"
                                    revision++
                                },
                                onFailure = { status = "MCP 连接失败: ${it.message}" },
                            )
                        }
                    },
                    shape = KimiPillShape,
                ) { Text("测试并拉取") }
                IconButton(onClick = { editing = server }) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑 MCP")
                }
                IconButton(onClick = { deleteTarget = server }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除 MCP")
                }
            }
            if (server.tools.isNotEmpty()) {
                val toolsExpanded = server.id in expandedToolServerIds
                KimiDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            expandedToolServerIds = if (toolsExpanded) {
                                expandedToolServerIds - server.id
                            } else {
                                expandedToolServerIds + server.id
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("已拉取 ${server.tools.size} 个工具", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (toolsExpanded) "点击收起工具名称和简介" else "点击展开查看工具名称和简介",
                            color = KimiMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(
                        if (toolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = KimiMuted,
                    )
                }
                AnimatedVisibility(visible = toolsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        server.tools.forEachIndexed { index, tool ->
                            McpToolSummaryRow(tool)
                            if (index != server.tools.lastIndex) KimiDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LocalMcpServerSettings(
    settings: AppSettings,
    localMcpServerManager: LocalMcpServerManager,
    externalRevision: Int = 0,
) {
    var revision by remember { mutableIntStateOf(0) }
    val localConfig = remember(revision, externalRevision) { settings.localMcpServerConfig() }
    val localStatus = remember(revision, externalRevision) { localMcpServerManager.status() }
    var editing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        localMcpServerManager.syncWithSettings()
        revision++
    }

    if (editing) {
        LocalMcpServerDialog(
            initial = localConfig,
            onDismiss = { editing = false },
            onSave = { config ->
                settings.saveLocalMcpServerConfig(config)
                if (config.enabled) {
                    localMcpServerManager.start(config)
                } else {
                    localMcpServerManager.stop()
                }
                editing = false
                status = "本机 MCP 服务端配置已保存"
                revision++
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("本机作为 MCP 服务端", style = MaterialTheme.typography.titleMedium)
                Text(
                    "将 Lyra Code 已启用的 Agent 工具和已启用 MCP 工具暴露给其他 MCP Client。",
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = localConfig.enabled && localStatus.running,
                onCheckedChange = { enabled ->
                    val updated = localConfig.copy(enabled = enabled)
                    if (enabled) {
                        localMcpServerManager.start(updated)
                    } else {
                        settings.saveLocalMcpServerConfig(updated)
                        localMcpServerManager.stop()
                    }
                    revision++
                },
            )
        }
        KimiDivider()
        Text(
            "状态：${if (localStatus.running) "运行中" else "已停止"} · ${localStatus.message}",
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text("本地地址：${localStatus.url}", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        if (localStatus.lanUrls.isNotEmpty()) {
            Text(
                "局域网地址：${localStatus.lanUrls.joinToString("  ")}",
                color = KimiMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            if (localConfig.authKey.isBlank()) "认证：未设置 key，局域网或公网暴露时不安全。" else "认证：已启用 Authorization Bearer / X-Lyra-MCP-Key",
            color = if (localConfig.authKey.isBlank()) MaterialTheme.colorScheme.error else KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editing = true }, shape = KimiPillShape) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("配置")
            }
            OutlinedButton(
                onClick = {
                    if (localStatus.running) {
                        localMcpServerManager.stop()
                    }
                    localMcpServerManager.start(localConfig.copy(enabled = true))
                    status = "本机 MCP 服务端已重启"
                    revision++
                },
                shape = KimiPillShape,
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("重启")
            }
        }
    }

    KimiCardBox {
        Text("外部调用说明", style = MaterialTheme.typography.titleMedium)
        Text(
            "外部 MCP Client 调用工具时，Lyra Code 默认不再弹出二次确认。请在外部 MCP Client 中配置是否需要用户确认，并避免把未设置认证 Key 的服务暴露到不可信网络。",
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun McpToolSummaryRow(tool: McpToolDefinition) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(tool.name, style = MaterialTheme.typography.titleSmall)
        Text(
            tool.description.ifBlank { "无描述" },
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun LocalMcpServerDialog(
    initial: LocalMcpServerConfig,
    onDismiss: () -> Unit,
    onSave: (LocalMcpServerConfig) -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(initial.host.ifBlank { AppSettings.DEFAULT_LOCAL_MCP_SERVER_HOST }) }
    var port by rememberSaveable { mutableStateOf(initial.port.toString()) }
    var authKey by rememberSaveable { mutableStateOf(initial.authKey) }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本机 MCP 服务端") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "其他 MCP Client 可通过 http://主机:端口/mcp 连接。若监听局域网或公网，建议设置认证 Key。",
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("监听主机") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("端口") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = authKey,
                    onValueChange = { authKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("认证 Key，可空") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (authKey.isBlank()) {
                    Text("未设置认证 Key 时，同网络内能访问该端口的客户端都可请求工具调用。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("保存后立即启用", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        LocalMcpServerConfig(
                            host = host.trim().ifBlank { AppSettings.DEFAULT_LOCAL_MCP_SERVER_HOST },
                            port = port.toIntOrNull()?.coerceIn(1, 65535) ?: AppSettings.DEFAULT_LOCAL_MCP_SERVER_PORT,
                            authKey = authKey.trim(),
                            enabled = enabled,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun McpServerDialog(
    initial: McpServerConfig,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var url by rememberSaveable(initial.id) { mutableStateOf(initial.url) }
    var authKey by rememberSaveable(initial.id) { mutableStateOf(initial.authKey) }
    var transport by rememberSaveable(initial.id) { mutableStateOf(initial.transport.ifBlank { AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP }) }
    var timeout by rememberSaveable(initial.id) { mutableStateOf(initial.timeoutSeconds.toString()) }
    var rawJson by rememberSaveable(initial.id) { mutableStateOf(initial.rawJson.ifBlank { "{}" }) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MCP 服务器") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL") },
                    singleLine = true,
                )
                if (url.startsWith("http://", ignoreCase = true)) {
                    Text("HTTP 明文连接不安全，但不会阻止添加。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = authKey,
                    onValueChange = {
                        authKey = it
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("认证 Key，可空") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaterialChoiceButton("Streamable HTTP", transport == AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP) {
                        transport = AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    }
                    MaterialChoiceButton("SSE", transport == AppSettings.MCP_TRANSPORT_SSE) {
                        transport = AppSettings.MCP_TRANSPORT_SSE
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    }
                }
                OutlinedTextField(value = timeout, onValueChange = { timeout = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("超时秒数 5-300") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = rawJson,
                    onValueChange = {
                        rawJson = it
                        parseMcpRawJson(it)?.let { parsed ->
                            name = parsed.name.ifBlank { name }
                            url = parsed.url.ifBlank { url }
                            authKey = parsed.authKey.ifBlank { authKey }
                            transport = parsed.transport.ifBlank { transport }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    label = { Text("原始 JSON：实际以此连接") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.ifBlank { "MCP Server" },
                            url = url.trim(),
                            authKey = authKey.trim(),
                            transport = transport,
                            timeoutSeconds = timeout.toIntOrNull()?.coerceIn(5, 300) ?: 30,
                            enabled = enabled,
                            rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

internal data class ParsedMcpRawConfig(
    val name: String,
    val url: String,
    val authKey: String,
    val transport: String,
    val serverKey: String,
)

internal fun parseMcpRawJson(rawJson: String): ParsedMcpRawConfig? = runCatching {
    val root = JSONObject(rawJson)
    val servers = root.optJSONObject("mcpServers")
    val serverKey = servers?.keys()?.asSequence()?.firstOrNull().orEmpty()
    val node = if (serverKey.isNotBlank()) servers?.optJSONObject(serverKey) else root
    node ?: return@runCatching null
    val headers = node.optJSONObject("headers") ?: root.optJSONObject("headers")
    val auth = headers?.optString("Authorization").orEmpty().removePrefix("Bearer ").trim()
    val rawType = node.optString("type").ifBlank { node.optString("transport") }
    ParsedMcpRawConfig(
        name = node.optString("name").ifBlank { serverKey.ifBlank { root.optString("name") } },
        url = node.optString("baseUrl").ifBlank { node.optString("url").ifBlank { root.optString("baseUrl").ifBlank { root.optString("url") } } },
        authKey = auth,
        transport = when {
            rawType.equals("sse", ignoreCase = true) -> AppSettings.MCP_TRANSPORT_SSE
            else -> AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP
        },
        serverKey = serverKey.ifBlank { node.optString("id").ifBlank { "mcp_server" } },
    )
}.getOrNull()

internal fun buildMcpRawJson(rawJson: String, name: String, url: String, authKey: String, transport: String): String {
    val parsed = parseMcpRawJson(rawJson)
    val serverKey = parsed?.serverKey?.ifBlank { null } ?: name.ifBlank { "mcp_server" }
    val root = runCatching { JSONObject(rawJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
    val servers = root.optJSONObject("mcpServers") ?: JSONObject()
    val node = servers.optJSONObject(serverKey) ?: JSONObject()
    node.put("type", if (transport == AppSettings.MCP_TRANSPORT_SSE) "sse" else "streamableHttp")
    node.put("name", name.ifBlank { parsed?.name ?: "MCP Server" })
    node.put("baseUrl", url)
    val headers = node.optJSONObject("headers") ?: JSONObject()
    if (authKey.isNotBlank()) {
        headers.put("Authorization", if (authKey.startsWith("Bearer ", ignoreCase = true)) authKey else "Bearer $authKey")
    }
    node.put("headers", headers)
    servers.put(serverKey, node)
    root.put("mcpServers", servers)
    if (!root.has("protocolVersion")) root.put("protocolVersion", "2025-06-18")
    return root.toString(2)
}

internal fun defaultMcpServer(): McpServerConfig = McpServerConfig(
    id = AppSettings.newId(),
    name = "MCP Server",
    url = "",
    authKey = "",
    transport = AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP,
    timeoutSeconds = 30,
    enabled = true,
    rawJson = """
        {
          "protocolVersion": "2025-06-18",
          "headers": {}
        }
    """.trimIndent(),
    tools = emptyList(),
)

internal fun transportLabel(transport: String): String = when (transport) {
    AppSettings.MCP_TRANSPORT_SSE -> "SSE"
    else -> "Streamable HTTP"
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
        PermissionRow(Icons.Default.PhotoLibrary, "访问手机媒体文件", mediaGranted, "未允许"),
        PermissionRow(Icons.Default.LocationOn, "位置信息", locationGranted, "未允许"),
        PermissionRow(Icons.Default.PhotoCamera, "摄像头", granted(Manifest.permission.CAMERA), "未允许"),
        PermissionRow(Icons.Default.Notifications, "通知", notificationGranted, "未允许"),
        PermissionRow(Icons.Default.Apps, "读取应用列表", true, "已声明"),
        PermissionRow(Icons.Default.Terminal, "与 Termux 通信", termuxExecutor.hasRunCommandPermission(), "点击授予"),
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
    AgentToolInfo("list_directory", "列出目录", "浏览工作目录内文件和子目录。"),
    AgentToolInfo("read_file", "读取文件", "读取工作目录内文本文件。"),
    AgentToolInfo("write_file", "写入文件", "创建或覆盖工作目录内文本文件。"),
    AgentToolInfo("append_file", "追加文件", "在现有文件末尾追加文本。"),
    AgentToolInfo("create_folder", "创建目录", "在工作目录内创建文件夹。"),
    AgentToolInfo("delete_file_or_folder", "删除文件/目录", "删除工作目录内文件或空目录。"),
    AgentToolInfo("rename_move", "重命名/移动", "调整工作目录内文件路径。"),
    AgentToolInfo("global_list_directory", "全局列目录", "列出 Android 共享存储目录，支持 Download。"),
    AgentToolInfo("global_read_file", "全局读取文件", "读取工作区外共享存储内的文本文件。"),
    AgentToolInfo("global_write_file", "全局写入文件", "写入工作区外共享存储文件，执行前需要用户确认。"),
    AgentToolInfo("global_append_file", "全局追加文件", "追加工作区外共享存储文件，执行前需要用户确认。"),
    AgentToolInfo("global_create_folder", "全局创建目录", "在工作区外共享存储创建目录，执行前需要用户确认。"),
    AgentToolInfo("global_delete_file_or_folder", "全局删除文件/目录", "删除工作区外共享存储内容，执行前需要用户确认。"),
    AgentToolInfo("global_rename_move", "全局移动/重命名", "移动工作区外共享存储内容，执行前需要用户确认。"),
    AgentToolInfo("download_file", "下载文件", "使用应用原生 HTTP/HTTPS 客户端下载到工作区或共享存储，支持请求头和 SHA-256 校验。"),
    AgentToolInfo("manage_scheduled_tasks", "定时任务", "列出或管理一次性、每日、每周和每月后台 AI 任务。"),
    AgentToolInfo("get_mini_server_status", "微型服务器状态", "读取内置 HTTP 静态服务器状态和访问地址。"),
    AgentToolInfo("read_mini_server_logs", "终端日志读取", "读取微型服务器连接、资源加载和页面错误日志，便于自动化调试。"),
    AgentToolInfo("manage_mini_server", "微型服务器控制", "启动、停止、重启或修改工作区静态站点服务，执行前需要用户确认。"),
    AgentToolInfo("search_conversation_history", "搜索会话记录", "跨普通会话按关键词和时间段搜索历史记录，不读取思维链或工具日志。"),
    AgentToolInfo("read_conversation_history", "读取会话记录", "读取指定历史会话的用户消息和 AI 最终回复，用于总结与趋势分析。"),
    AgentToolInfo("search_files", "工作区搜索", "按文件名或路径片段搜索工作区。"),
    AgentToolInfo("global_search_files", "全局文件搜索", "搜索 Android 共享存储中的文件路径。"),
    AgentToolInfo("get_file_info", "文件信息", "读取文件大小、修改时间等元数据。"),
    AgentToolInfo("list_skill_files", "列出 Skill 文件", "浏览已启用 Skill 包内文件。"),
    AgentToolInfo("read_skill_file", "读取 Skill 文件", "读取相关 Skill 包内说明或脚本。"),
    AgentToolInfo("set_conversation_topic", "话题总结", "新会话首次对话时，根据用户第一条消息设置简短主题标题。"),
    AgentToolInfo("update_roleplay_state", "角色扮演状态", "沉浸扮演模式下调整好感度并触发表情短代码。"),
    AgentToolInfo("run_command", "执行命令", "通过 Termux 执行命令并返回 stdout/stderr。"),
    AgentToolInfo("web_search", "联网搜索", "使用内嵌 WebView 搜索互联网。"),
    AgentToolInfo("read_web_page", "读取网页", "读取 http/https 网页正文。"),
    AgentToolInfo("mark_web_sources", "网页来源标注", "声明网页引用来源，并要求最终回答就近标注来源链接。"),
    AgentToolInfo("manage_app_config", "配置管理", "通过用户确认后添加、修改、启用、禁用或删除 MCP、SSH、WebDAV、Skills 与其他 Agent 工具配置。"),
    AgentToolInfo("get_current_time", "时间感知", "读取设备当前时间和时区。"),
    AgentToolInfo("get_current_location", "地理感知", "读取设备最近系统定位。"),
    AgentToolInfo("get_device_hardware_info", "硬件检查", "读取设备系统、CPU、内存、存储、分辨率、网络、蓝牙、电池等诊断信息。"),
    AgentToolInfo("list_installed_apps", "应用列表识别", "读取用户应用和系统应用的名称、包名、版本、大小及签名证书 SHA-256。"),
    AgentToolInfo("execute_shell_command", "Shell 系统命令", "通过 Shizuku 以 Android shell 身份执行系统命令，每次执行前都需要用户确认。"),
    AgentToolInfo("execute_root_command", "Root 系统命令", "通过自定义 su 命令执行 Root 命令，每次执行前都需要用户确认；不可用时可按设置回退到 Shell。"),
    AgentToolInfo("list_ssh_servers", "列出 SSH 连接", "查看用户已配置且启用的 SSH 服务器标识。"),
    AgentToolInfo("ssh_exec", "SSH 执行命令", "登录远程服务器执行命令并返回 stdout/stderr，执行前需要用户确认。"),
    AgentToolInfo("list_webdav_servers", "列出 WebDAV", "查看用户已配置且启用的 WebDAV 服务器标识。"),
    AgentToolInfo("webdav_list", "WebDAV 列目录", "通过 PROPFIND 列出 WebDAV 目录文件详情。"),
    AgentToolInfo("webdav_search", "WebDAV 搜索", "搜索 WebDAV 服务器上的文件路径。"),
    AgentToolInfo("webdav_download_to_workspace", "WebDAV 下载", "从 WebDAV 下载文件到工作区，执行前需要用户确认。"),
    AgentToolInfo("webdav_upload_from_workspace", "WebDAV 上传", "把工作区文件上传到 WebDAV，执行前需要用户确认。"),
    AgentToolInfo("list_file_transfer_servers", "列出文件传输服务器", "查看用户已配置且启用的 FTP/FTPS/SFTP 服务器标识。"),
    AgentToolInfo("file_transfer_list", "文件传输列目录", "列出 FTP/FTPS/SFTP 目录文件详情。"),
    AgentToolInfo("file_transfer_search", "文件传输搜索", "搜索 FTP/FTPS/SFTP 服务器上的文件路径。"),
    AgentToolInfo("file_transfer_download_to_workspace", "文件传输下载", "从 FTP/FTPS/SFTP 下载文件到工作区，执行前需要用户确认。"),
    AgentToolInfo("file_transfer_upload_from_workspace", "文件传输上传", "把工作区文件上传到 FTP/FTPS/SFTP，执行前需要用户确认。"),
    AgentToolInfo("export_backup", "导出备份", "导出 Lyra Code 数据到本地或 WebDAV，执行前需要用户确认。"),
    AgentToolInfo("import_backup", "导入备份", "从本地或 WebDAV 用补充模式导入备份，执行前需要用户确认。"),
    AgentToolInfo("set_todo_list", "设置 TODO", "展示 Agent 当前任务计划。"),
    AgentToolInfo("update_todo_item", "更新 TODO", "更新任务步骤状态。"),
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
                Text("Termux 配置教程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                "首次使用前，请在 Termux 中开启外部应用调用权限。Termux:API 可选；未安装 Termux:API 时，Lyra Code 会使用 RunCommandService 后台静默执行命令。",
                color = KimiMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            TermuxGuideStep("1", "安装并打开 Termux，建议使用 F-Droid 或 GitHub 版本。")
            SettingsExternalLinkRow(
                icon = Icons.Default.Terminal,
                title = "Termux GitHub",
                subtitle = "termux/termux-app",
                url = "https://github.com/termux/termux-app",
            )
            TermuxGuideStep("2", "复制下面的配置命令到 Termux 执行，开启外部应用调用权限。")
            CommandCopyCard(
                command = setupCommand,
                buttonText = "复制配置命令",
                onCopy = { clipboard.setText(AnnotatedString(setupCommand)) },
            )
            TermuxGuideStep("3", "重新打开 Lyra Code，在设置的应用权限页面授予 RUN_COMMAND 权限。")
            TermuxGuideStep("4", "选择内部存储下可读写的工作目录，例如 /storage/emulated/0/Fonts。")
            TermuxGuideStep("5", "run_command 会直接回传 exit_code、stdout、stderr；只有输出过大或超时时，再重定向到工作目录文件。")
            CommandCopyCard(
                command = testCommand,
                buttonText = "复制测试命令",
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

internal data class LicenseNotice(
    val name: String,
    val license: String,
    val note: String,
    val licenseText: String,
)

@Composable
internal fun OpenSourceLicensesScreen() {
    var selectedNotice by remember { mutableStateOf<LicenseNotice?>(null) }
    val notices = remember {
        listOf(
            LicenseNotice("AndroidX Core KTX", "Apache License 2.0", "Android Kotlin 扩展与兼容层。", LicenseTexts.APACHE_2_0),
            LicenseNotice("AndroidX Activity Compose", "Apache License 2.0", "Compose Activity 集成。", LicenseTexts.APACHE_2_0),
            LicenseNotice("Jetpack Compose UI", "Apache License 2.0", "声明式 UI 框架。", LicenseTexts.APACHE_2_0),
            LicenseNotice("Jetpack Compose Material 3", "Apache License 2.0", "Material Design 3 组件。", LicenseTexts.APACHE_2_0),
            LicenseNotice("Jetpack Compose Material Icons Extended", "Apache License 2.0", "界面图标库。", LicenseTexts.APACHE_2_0),
            LicenseNotice("AndroidX DocumentFile", "Apache License 2.0", "SAF 工作区文件访问。", LicenseTexts.APACHE_2_0),
            LicenseNotice("AndroidX Security Crypto", "Apache License 2.0", "本地敏感配置加密存储。", LicenseTexts.APACHE_2_0),
            LicenseNotice("Kotlinx Coroutines", "Apache License 2.0", "异步任务与流式请求。", LicenseTexts.APACHE_2_0),
            LicenseNotice("OkHttp", "Apache License 2.0", "HTTP、SSE 兼容读取与 MCP Streamable HTTP 通信。", LicenseTexts.APACHE_2_0),
            LicenseNotice("JetBrains Markdown / RikkaHub Markdown fork", "Apache License 2.0", "Markdown GFM AST 解析，支持表格、列表和数学节点。", LicenseTexts.APACHE_2_0),
            LicenseNotice("Android Gradle Plugin", "Apache License 2.0", "Android 构建工具链。", LicenseTexts.APACHE_2_0),
            LicenseNotice("Kotlin", "Apache License 2.0", "主要开发语言与编译器。", LicenseTexts.APACHE_2_0),
            LicenseNotice("JSch / mwiede fork", "BSD 3-Clause License", "SSH 连接与远程命令执行。", LicenseTexts.BSD_3_CLAUSE),
            LicenseNotice("JLatexMath Android / Soffd fork", "GNU General Public License v2.0 with linking exception", "本地 LaTeX 数学公式渲染。源码随工程 third_party/jlatexmath 保留。", LicenseTexts.JLATEXMATH_GPL_2_WITH_EXCEPTION),
            LicenseNotice("JLatexMath fonts", "OFL / Knuth / Public Domain / GPL v2", "数学公式渲染字体。完整字体许可随 third_party/jlatexmath/assets 分发。", LicenseTexts.JLATEXMATH_FONT_LICENSES),
            LicenseNotice("JSON-java / org.json", "JSON License", "JSON 解析与序列化。", LicenseTexts.JSON_LICENSE),
            LicenseNotice("JUnit", "Eclipse Public License 1.0", "单元测试框架，仅测试构建使用。", LicenseTexts.EPL_1_0),
            LicenseNotice("Simple Icons", "CC0 1.0 Universal", "关于页面仓库与社交群聊 SVG 图标。", LicenseTexts.CC0_1_0),
        )
    }
    selectedNotice?.let { notice ->
        Dialog(
            onDismissRequest = { selectedNotice = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 34.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(notice.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(notice.license, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { selectedNotice = null }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                    KimiDivider()
                    SelectionContainer {
                        Text(
                            notice.licenseText,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 18.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            KimiCardBox {
                Text("开源许可证", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lyra Code 使用以下开源组件。点击条目可查看内置的原始许可证文本。",
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(notices) { notice ->
            KimiCardBox(
                modifier = Modifier.clickable { selectedNotice = notice },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(notice.name, style = MaterialTheme.typography.titleSmall)
                        Text(notice.license, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(notice.note, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KimiMuted)
                }
            }
        }
    }
}

@Composable
internal fun AboutSoftwareScreen(
    updateAvailable: Boolean,
    onUpdateAvailabilityChange: (Boolean) -> Unit,
    onOpenDeviceInfo: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val updateManager = remember(context) { UpdateManager(context) }
    val packageInfo = remember(context.packageName) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
    }
    val versionName = packageInfo?.versionName.orEmpty().ifBlank { "未知" }
    val versionCode = packageInfo?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toString() else {
            @Suppress("DEPRECATION")
            it.versionCode.toString()
        }
    } ?: "未知"
    var notice by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf<UpdateDownloadProgress?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var pendingApk by remember { mutableStateOf(updateManager.pendingDownloadedApk()) }
    var updatePromptDisabled by remember { mutableStateOf(updateManager.updatePromptDisabled()) }
    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val apk = updateManager.pendingDownloadedApk()
        pendingApk = apk
        if (apk != null && !updateManager.needsInstallPermission()) {
            runCatching { context.startActivity(updateManager.installIntent(apk)) }
                .onFailure { notice = it.message.orEmpty().ifBlank { "无法打开安装器" } }
        } else if (apk != null) {
            notice = "授权未完成，可稍后点击继续安装"
        }
    }

    fun openInstaller(apk: File) {
        if (updateManager.needsInstallPermission()) {
            notice = "请授权安装未知来源应用，返回后将继续安装"
            installPermissionLauncher.launch(updateManager.installPermissionIntent())
        } else {
            runCatching { context.startActivity(updateManager.installIntent(apk)) }
                .onFailure { notice = it.message.orEmpty().ifBlank { "无法打开安装器" } }
        }
    }

    fun checkUpdate() {
        if (checking) return
        checking = true
        notice = "正在检查更新..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { updateManager.checkForUpdate() }
            checking = false
            result.fold(
                onSuccess = { info ->
                    if (info == null) {
                        updateManager.clearLatestAvailableUpdate()
                        onUpdateAvailabilityChange(false)
                        notice = "当前已是最新版本"
                    } else {
                        updateManager.saveLatestAvailableUpdate(info)
                        onUpdateAvailabilityChange(true)
                        notice = ""
                        updateInfo = info
                    }
                },
                onFailure = { notice = it.message.orEmpty().ifBlank { "检查更新失败" } },
            )
        }
    }

    updateInfo?.let { info ->
        UpdateDialog(
            info = info,
            progress = downloadProgress,
            downloading = downloading,
            onDismiss = {
                if (!downloading) {
                    updateInfo = null
                    downloadProgress = null
                }
            },
            onOpenWeb = {
                val target = info.webUrl.ifBlank { info.apkUrl }
                if (target.isNotBlank()) runCatching { uriHandler.openUri(target) }
            },
            onDownload = {
                if (downloading) return@UpdateDialog
                downloading = true
                downloadProgress = UpdateDownloadProgress(status = "准备下载")
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        updateManager.downloadApk(info) { progress -> downloadProgress = progress }
                    }
                    downloading = false
                    result.fold(
                        onSuccess = { apk ->
                            pendingApk = apk
                            notice = "下载完成，准备安装"
                            openInstaller(apk)
                        },
                        onFailure = {
                            val message = it.message.orEmpty().ifBlank { "下载失败" }
                            downloadProgress = UpdateDownloadProgress(status = message)
                            notice = message
                        },
                    )
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AboutLogoHeader()
            KimiCardBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "面向 Android 的本地 AI Agent 工具，支持多平台模型、流式对话、Termux、工作区文件操作、联网搜索、MCP、Skills、TODO 进度和文件变更审查。",
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            KimiSectionLabel("版本与更新")
            KimiCardBox {
                AboutVersionRow(
                    versionText = "版本 $versionName ($versionCode)",
                    value = if (checking) "正在检查更新..." else if (updateAvailable) "发现新版本，点击查看" else "点击检测新版本",
                    updateAvailable = updateAvailable,
                    onClick = ::checkUpdate,
                )
                pendingApk?.let { apk ->
                    KimiDivider()
                    KimiMenuRow(
                        Icons.Default.InstallMobile,
                        updateManager.pendingDownloadedApkLabel(),
                        "已下载 ${formatBytes(apk.length())}，无需重新下载",
                        onClick = { openInstaller(apk) },
                    )
                }
                KimiDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("不弹出更新提示", style = MaterialTheme.typography.titleSmall)
                        Text("关闭进入软件时每日一次的新版本弹窗，不影响手动检测更新。", color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = updatePromptDisabled,
                        onCheckedChange = {
                            updatePromptDisabled = it
                            updateManager.setUpdatePromptDisabled(it)
                        },
                    )
                }
                KimiDivider()
                KimiMenuRow(Icons.Default.Apps, "应用 ID", context.packageName)
            }
            KimiSectionLabel("仓库")
            KimiCardBox {
                SocialLinkRow(
                    logo = { Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "官网",
                    value = "lyracode.app",
                    onClick = { uriHandler.openUri("https://lyracode.app") },
                )
                KimiDivider()
                SocialLinkRow(
                    logo = { SocialLogoBadge(R.drawable.ic_simple_github) },
                    title = "GitHub",
                    value = "Soffd/Lyra-Code",
                    onClick = { uriHandler.openUri("https://github.com/Soffd/Lyra-Code") },
                )
                KimiDivider()
                SocialLinkRow(
                    logo = { SocialLogoBadge(R.drawable.ic_simple_gitee) },
                    title = "Gitee",
                    value = "yukisoffd/lyra-code",
                    onClick = { uriHandler.openUri("https://gitee.com/yukisoffd/lyra-code") },
                )
            }
            KimiSectionLabel("社交群聊")
            KimiCardBox {
                SocialLinkRow(
                    logo = { SocialLogoBadge(R.drawable.ic_simple_qq) },
                    title = "QQ 群",
                    value = "加入 Lyra Code QQ 群聊",
                    onClick = { uriHandler.openUri("https://qm.qq.com/q/Ws8objzR84") },
                )
                KimiDivider()
                SocialLinkRow(
                    logo = { SocialLogoBadge(R.drawable.ic_simple_discord) },
                    title = "Discord",
                    value = "加入 Lyra Code Discord 社区",
                    onClick = { uriHandler.openUri("https://discord.gg/3Mx3F4RTP9") },
                )
            }
            KimiSectionLabel("隐私与安全")
            KimiCardBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("隐私与安全", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "API Key 保存在本机配置中；对话、工具输出、缓存和审查日志默认留在本机。使用第三方模型接口、HTTP 明文 URL、联网搜索、MCP 或 Termux 命令时，数据会按用户配置发送到对应服务或本机执行环境。",
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "应用内更新会下载 APK 二进制文件并校验 SHA-256。安装前 Android 会要求用户允许 Lyra Code 安装未知来源应用。",
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            KimiSectionLabel("构建信息")
            KimiCardBox {
                KimiMenuRow(Icons.Default.PhoneAndroid, "手机信息", "${Build.MANUFACTURER} ${Build.MODEL}", onClick = onOpenDeviceInfo)
                KimiDivider()
                KimiMenuRow(Icons.Default.CloudDownload, "更新清单", updateManager.manifestUrl().ifBlank { "未配置" })
            }
        }
        TransientNotice(
            message = notice,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            onDismiss = { notice = "" },
        )
    }
}

@Composable
internal fun DeviceInfoScreen() {
    val context = LocalContext.current
    val snapshot = remember { DeviceInfoCollector.collect(context) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            KimiCardBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("手机信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "用于截图反馈、排查兼容性问题，以及让硬件检查 Agent 分析当前设备环境。部分项目受系统权限和 Android 沙箱限制，可能只能显示近似信息。",
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        snapshot.sections.forEach { section ->
            item { KimiSectionLabel(section.title) }
            item {
                KimiCardBox {
                    SelectionContainer {
                        Column {
                            section.items.forEachIndexed { index, item ->
                                DeviceInfoRow(item)
                                if (index != section.items.lastIndex) KimiDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeviceInfoRow(item: DeviceInfoItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            item.label,
            modifier = Modifier.widthIn(min = 88.dp, max = 112.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            item.value,
            modifier = Modifier.weight(1f),
            color = KimiMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun AboutVersionRow(
    versionText: String,
    value: String,
    updateAvailable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(42.dp), contentAlignment = Alignment.CenterStart) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(versionText, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (updateAvailable) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
            Text(
                value,
                color = if (updateAvailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun SocialLinkRow(
    logo: @Composable () -> Unit,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            logo()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun SocialLogoBadge(
    iconRes: Int,
) {
    Box(
        modifier = Modifier
            .size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun AboutLogoHeader() {
    val context = LocalContext.current
    val backgroundArgb = MaterialTheme.colorScheme.background.toArgb()
    val isDark = remember(backgroundArgb) {
        val red = (backgroundArgb shr 16) and 0xFF
        val green = (backgroundArgb shr 8) and 0xFF
        val blue = backgroundArgb and 0xFF
        (0.299 * red + 0.587 * green + 0.114 * blue) < 128.0
    }
    val logoAsset = if (isDark) "img/logo-white.png" else "img/logo-black.png"
    val logoBitmap = remember(logoAsset) {
        runCatching {
            context.assets.open(logoAsset).use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
    val transition = rememberInfiniteTransition(label = "about-logo-background")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo-bg-pulse",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.52f else 0.24f),
                                Color(0xFFFF7AB6).copy(alpha = if (isDark) 0.42f else 0.18f),
                                Color(0xFF7CFFCB).copy(alpha = if (isDark) 0.38f else 0.16f),
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.30f else 0.12f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.52f else 0.24f),
                            ),
                        ),
                    ),
            ) {
                val c1 = Offset(size.width * (0.28f + 0.08f * pulse), size.height * 0.30f)
                val c2 = Offset(size.width * 0.76f, size.height * (0.34f + 0.10f * (1f - pulse)))
                val c3 = Offset(size.width * (0.54f - 0.07f * pulse), size.height * 0.72f)
                drawCircle(Color(0xFF66D9FF).copy(alpha = if (isDark) 0.30f else 0.18f), size.minDimension * 0.34f, c1)
                drawCircle(Color(0xFFFFD166).copy(alpha = if (isDark) 0.22f else 0.15f), size.minDimension * 0.30f, c2)
                drawCircle(Color(0xFFFF6FD8).copy(alpha = if (isDark) 0.24f else 0.14f), size.minDimension * 0.28f, c3)
            }
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = "Lyra Code Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            "Lyra Code",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
internal fun UpdateDialog(
    info: AppUpdateInfo,
    progress: UpdateDownloadProgress?,
    downloading: Boolean,
    onDismiss: () -> Unit,
    onOpenWeb: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text("发现新版本 ${info.versionName.ifBlank { info.versionCode.toString() }}") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (info.mandatory) {
                    Text("这是重要更新，建议尽快安装。", color = MaterialTheme.colorScheme.error)
                }
                RichMarkdownContent(
                    markdown = info.releaseNotes,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.apkSha256.isNotBlank()) {
                    Text(
                        "SHA-256：${info.apkSha256}",
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                progress?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { it.percent },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val totalText = if (it.totalBytes > 0) " / ${formatBytes(it.totalBytes)}" else ""
                        Text(
                            "${it.status} ${formatBytes(it.downloadedBytes)}$totalText",
                            color = KimiMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDownload, enabled = !downloading && info.apkUrl.isNotBlank()) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (downloading) "下载中" else "应用内下载")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info.webUrl.isNotBlank() || info.apkUrl.isNotBlank()) {
                    TextButton(onClick = onOpenWeb) { Text("网页下载") }
                }
                TextButton(onClick = onDismiss, enabled = !downloading) { Text("稍后") }
            }
        },
    )
}

@Composable
internal fun PromptSettingsScreen(settings: AppSettings) {
    fun visiblePresets() = settings.systemPromptPresets().filterNot { it.id == "roleplay" }
    var presets by remember { mutableStateOf(visiblePresets()) }
    var selectedId by remember { mutableStateOf(settings.selectedSystemPromptId.takeUnless { it == "roleplay" } ?: "default") }
    var editing by remember { mutableStateOf<SystemPromptPreset?>(null) }
    var notice by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KimiCardBox {
                Text("系统提示词", style = MaterialTheme.typography.titleMedium)
                Text(
                    "选择不同用途的系统提示词。修改后会保存到当前预设；恢复预设只影响当前选中的提示词。",
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            KimiCardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("提示词配置", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            editing = SystemPromptPreset(
                                id = AppSettings.newId(),
                                name = "自定义提示词",
                                prompt = "",
                                builtIn = false,
                            )
                        },
                        shape = KimiPillShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新增")
                    }
                }
                presets.forEach { preset ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                selectedId = preset.id
                                settings.selectedSystemPromptId = preset.id
                                notice = "已切换到 ${preset.name}"
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            val desc = preset.prompt.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                            Text(
                                if (preset.exampleConversation.isBlank()) desc else "$desc · 含示例对话",
                                color = KimiMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (preset.id == selectedId) Icon(Icons.Default.Check, contentDescription = "已选择")
                        IconButton(onClick = { editing = preset }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                    if (preset != presets.last()) KimiDivider()
                }
            }
            Spacer(Modifier.height(72.dp))
        }
        editing?.let { preset ->
            PromptEditDialog(
                preset = preset,
                onDismiss = { editing = null },
                onSave = { updated ->
                    settings.saveSystemPromptConfig(updated)
                    presets = visiblePresets()
                    selectedId = settings.selectedSystemPromptId.takeUnless { it == "roleplay" } ?: "default"
                    editing = null
                    notice = "提示词已保存"
                },
                onRestore = {
                    settings.restoreSystemPrompt(preset.id)
                    presets = visiblePresets()
                    editing = null
                    notice = "已恢复预设"
                },
                onDelete = {
                    settings.deleteSystemPromptConfig(preset.id)
                    presets = visiblePresets()
                    selectedId = settings.selectedSystemPromptId.takeUnless { it == "roleplay" } ?: "default"
                    editing = null
                    notice = "提示词已删除"
                },
            )
        }
        TransientNotice(
            message = notice,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            onDismiss = { notice = "" },
        )
    }
}

@Composable
internal fun PromptEditDialog(
    preset: SystemPromptPreset,
    onDismiss: () -> Unit,
    onSave: (SystemPromptPreset) -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(preset.id) { mutableStateOf(preset.name) }
    var prompt by remember(preset.id) { mutableStateOf(preset.prompt) }
    var example by remember(preset.id) { mutableStateOf(preset.exampleConversation) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preset.builtIn) "编辑内置提示词" else "编辑自定义提示词") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("提示词名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("提示词内容") },
                    minLines = 8,
                    maxLines = 16,
                )
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("示例对话（可选）") },
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = prompt.isNotBlank(),
                onClick = {
                    onSave(
                        preset.copy(
                            name = name.trim().ifBlank { "自定义提示词" },
                            prompt = prompt,
                            exampleConversation = example,
                        ),
                    )
                },
                shape = KimiPillShape,
            ) { Text("保存") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (preset.builtIn) {
                    TextButton(onClick = onRestore) { Text("恢复预设") }
                } else {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

internal fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

