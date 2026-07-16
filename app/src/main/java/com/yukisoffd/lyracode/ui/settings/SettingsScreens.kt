package com.yukisoffd.lyracode

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.BackupManager
import com.yukisoffd.lyracode.data.SkillPack
import com.yukisoffd.lyracode.filetransfer.FileTransferClient
import com.yukisoffd.lyracode.mcp.LocalMcpServerManager
import com.yukisoffd.lyracode.mcp.McpClientManager
import com.yukisoffd.lyracode.server.MiniServerManager
import com.yukisoffd.lyracode.ssh.SshExecutor
import com.yukisoffd.lyracode.system.SystemCommandExecutor
import com.yukisoffd.lyracode.termux.TermuxExecutor
import com.yukisoffd.lyracode.webdav.WebDavClient
import com.yukisoffd.lyracode.workspace.WorkspaceManager



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
    skills: List<SkillPack>,
    skillStatus: String,
    backupStatus: String,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    languageMode: String,
    onLanguageModeChange: (String) -> Unit,
    refreshRateMode: String,
    onRefreshRateModeChange: (String) -> Unit,
    fontScaleMode: String,
    customFontScale: Float,
    onFontScaleModeChange: (String) -> Unit,
    onCustomFontScaleChange: (Float) -> Unit,
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
    val context = LocalContext.current
    fun navigateBackFromDetail() {
        detail = when (detail) {
            "device" -> "about"
            "custom_theme_color" -> "theme_mode"
            "font_library" -> "font"
            "theme_mode", "language", "font", "refresh_rate", "chat_background", "streaming_output" -> "theme"
            "mini_server_logs" -> "mini_server"
            else -> null
        }
    }
    BackHandler(enabled = detail != null) { navigateBackFromDetail() }
    LaunchedEffect(detail, context) {
        onDetailTitleChange(detail?.let { settingsDetailTitle(context, it) })
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
                initialState == "custom_theme_color" && targetState == "theme_mode" -> false
                initialState == "font_library" && targetState == "font" -> false
                initialState == "theme_mode" && targetState == "custom_theme_color" -> true
                initialState == "font" && targetState == "font_library" -> true
                initialState in setOf("theme_mode", "language", "font", "refresh_rate", "chat_background", "streaming_output") && targetState == "theme" -> false
                initialState == "theme" && targetState in setOf("theme_mode", "language", "font", "refresh_rate", "chat_background", "streaming_output") -> true
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
                scroll = target !in setOf("prompts", "memories", "licenses", "about", "device", "font_library"),
            ) {
                when (target) {
                    "profile" -> ProfileSettingsSummary(settings)
                    "model" -> ModelServiceSettings(settings, controller)
                    "topic_summary_model" -> TopicSummaryModelSettings(settings, controller)
                    "sub_agents" -> SubAgentSettings(settings, controller)

                    "theme" -> ThemeSettings(
                        settings = settings,
                        themeMode = themeMode,
                        dynamicColorEnabled = dynamicColorEnabled,
                        onDynamicColorChange = onDynamicColorChange,
                        languageMode = languageMode,
                        refreshRateMode = refreshRateMode,
                        onRefreshRateModeChange = onRefreshRateModeChange,
                        fontScaleMode = fontScaleMode,
                        customFontScale = customFontScale,
                        onOpenThemeModeSettings = { detail = "theme_mode" },
                        onOpenLanguageSettings = { detail = "language" },
                        onOpenFontSettings = { detail = "font" },
                        onOpenRefreshRateSettings = { detail = "refresh_rate" },
                        onOpenChatBackgroundSettings = { detail = "chat_background" },
                        onOpenStreamingOutputSettings = { detail = "streaming_output" },
                    )
                    "theme_mode" -> ThemeModeSettings(
                        settings = settings,
                        controller = controller,
                        themeMode = themeMode,
                        onOpenCustomThemeColor = { detail = "custom_theme_color" },
                        onThemeModeChange = onThemeModeChange,
                    )
                    "custom_theme_color" -> CustomThemeColorSettings(settings, controller)
                    "language" -> LanguageSettings(
                        languageMode = languageMode,
                        onLanguageModeChange = onLanguageModeChange,
                    )
                    "refresh_rate" -> RefreshRateSettings(
                        refreshRateMode = refreshRateMode,
                        onRefreshRateModeChange = onRefreshRateModeChange,
                    )
                    "chat_background" -> ChatBackgroundSettings(settings)
                    "streaming_output" -> StreamingOutputSettings(settings, controller)
                    "font" -> FontSizeSettings(
                        settings = settings,
                        controller = controller,
                        fontScaleMode = fontScaleMode,
                        customFontScale = customFontScale,
                        onFontScaleModeChange = onFontScaleModeChange,
                        onCustomFontScaleChange = onCustomFontScaleChange,
                        onOpenFontLibrary = { detail = "font_library" },
                    )
                    "font_library" -> FontLibrarySettings(settings, controller)
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
                    "prompts" -> PromptSettingsScreen(settings)
                    "memories" -> MemorySettingsScreen(settings)
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
                    else -> Text(context.getString(R.string.settings_not_available), color = KimiMuted)
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
            KimiSectionLabel(context.getString(R.string.section_model_service))
            KimiCardBox {
                KimiMenuRow(Icons.Default.AccountCircle, context.getString(R.string.menu_profile), context.getString(R.string.menu_profile_desc)) { detail = "profile" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Cloud, context.getString(R.string.menu_model_service), context.getString(R.string.menu_model_service_desc)) { detail = "model" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Topic, context.getString(R.string.menu_topic_summary_model), context.getString(R.string.menu_topic_summary_model_desc)) { detail = "topic_summary_model" }
                KimiDivider()
                KimiMenuRow(Icons.Default.AccountTree, context.getString(R.string.menu_sub_agents), context.getString(R.string.menu_sub_agents_desc)) { detail = "sub_agents" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Search, context.getString(R.string.menu_web_search), context.getString(R.string.menu_web_search_desc)) { detail = "web_search" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Terminal, context.getString(R.string.menu_termux), context.getString(R.string.menu_termux_desc)) { detail = "termux" }
                KimiDivider()
                KimiMenuRow(ImageVector.vectorResource(R.drawable.ic_mcp), context.getString(R.string.menu_mcp_server), context.getString(R.string.menu_mcp_server_desc)) { detail = "mcp" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Hub, context.getString(R.string.menu_local_mcp), context.getString(R.string.menu_local_mcp_desc)) { detail = "local_mcp" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Dns, context.getString(R.string.menu_ssh), context.getString(R.string.menu_ssh_desc)) { detail = "ssh" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Cloud, context.getString(R.string.menu_webdav), context.getString(R.string.menu_webdav_desc)) { detail = "webdav" }
                KimiDivider()
                KimiMenuRow(Icons.Default.SyncAlt, context.getString(R.string.menu_file_transfer), context.getString(R.string.menu_file_transfer_desc)) { detail = "file_transfer" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Language, context.getString(R.string.menu_mini_server), context.getString(R.string.menu_mini_server_desc)) { detail = "mini_server" }
            }
            KimiSectionLabel(context.getString(R.string.section_personalization))
            KimiCardBox {
                KimiMenuRow(
                    Icons.Default.Palette,
                    context.getString(R.string.menu_theme),
                    context.getString(R.string.menu_theme_current_full, if (settings.customThemeColorEnabled) uiText("自定义 ${settings.customThemeColor}") else themeName(themeMode), languageName(languageMode), refreshRateName(refreshRateMode), fontScaleName(fontScaleMode, customFontScale)),
                ) { detail = "theme" }
                KimiDivider()
                KimiMenuRow(Icons.Default.EditNote, context.getString(R.string.menu_system_prompt), context.getString(R.string.menu_system_prompt_desc)) { detail = "prompts" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Psychology, context.getString(R.string.menu_memory), context.getString(R.string.menu_memory_desc, settings.memories().count { it.enabled })) { detail = "memories" }
                KimiDivider()
                KimiMenuRow(Icons.Default.School, context.getString(R.string.menu_skills), context.getString(R.string.menu_skills_desc, skills.size)) { detail = "skills" }
            }
            KimiSectionLabel(context.getString(R.string.section_general))
            KimiCardBox {
                KimiMenuRow(Icons.Default.Build, context.getString(R.string.menu_agent_tools), context.getString(R.string.menu_agent_tools_desc)) { detail = "tools" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Storage, context.getString(R.string.menu_storage), context.getString(R.string.menu_storage_desc)) { detail = "storage" }
                KimiDivider()
                KimiMenuRow(Icons.Default.ImportExport, context.getString(R.string.menu_backup), context.getString(R.string.menu_backup_desc)) { detail = "backup" }
                KimiDivider()
                KimiMenuRow(Icons.Default.AdminPanelSettings, context.getString(R.string.menu_system_permissions), context.getString(R.string.menu_system_permissions_desc)) { detail = "system_permissions" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Security, context.getString(R.string.menu_app_permissions), context.getString(R.string.menu_app_permissions_desc)) { detail = "permissions" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Description, context.getString(R.string.menu_licenses), context.getString(R.string.menu_licenses_desc)) { detail = "licenses" }
                KimiDivider()
                KimiMenuRow(Icons.Default.Info, context.getString(R.string.menu_about), context.getString(R.string.menu_about_desc)) { detail = "about" }
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
            .imePadding()
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

internal fun settingsDetailTitle(context: Context, detail: String): String = when (detail) {
    "profile" -> context.getString(R.string.detail_profile)
    "model" -> context.getString(R.string.detail_model)
    "topic_summary_model" -> context.getString(R.string.detail_topic_summary_model)
    "sub_agents" -> context.getString(R.string.detail_sub_agents)
    "web_search" -> context.getString(R.string.detail_web_search)
    "workspace" -> context.getString(R.string.detail_workspace)
    "theme" -> context.getString(R.string.detail_theme)
    "custom_theme_color" -> uiText("设置自定义主题色")
    "theme_mode" -> context.getString(R.string.detail_theme_mode)
    "language" -> context.getString(R.string.detail_language)
    "font" -> uiText("字体与大小")
    "font_library" -> uiText("字体库")
    "refresh_rate" -> context.getString(R.string.detail_refresh_rate)
    "chat_background" -> context.getString(R.string.detail_chat_background)
    "streaming_output" -> context.getString(R.string.detail_streaming_output)
    "permissions" -> context.getString(R.string.detail_permissions)
    "system_permissions" -> context.getString(R.string.detail_system_permissions)
    "tools" -> context.getString(R.string.detail_tools)
    "storage" -> context.getString(R.string.detail_storage)
    "termux" -> context.getString(R.string.detail_termux)
    "mcp" -> context.getString(R.string.detail_mcp)
    "local_mcp" -> context.getString(R.string.detail_local_mcp)
    "ssh" -> context.getString(R.string.detail_ssh)
    "webdav" -> context.getString(R.string.detail_webdav)
    "file_transfer" -> context.getString(R.string.detail_file_transfer)
    "mini_server" -> context.getString(R.string.detail_mini_server)
    "mini_server_logs" -> context.getString(R.string.detail_mini_server_logs)
    "backup" -> context.getString(R.string.detail_backup)
    "prompts" -> context.getString(R.string.detail_prompts)
    "memories" -> context.getString(R.string.detail_memories)
    "skills" -> context.getString(R.string.detail_skills)
    "licenses" -> context.getString(R.string.detail_licenses)
    "about" -> context.getString(R.string.detail_about)
    "device" -> context.getString(R.string.detail_device)
    else -> context.getString(R.string.detail_default)
}

@Composable
internal fun WorkspaceSettings(
    workspaceDisplayName: String,
    workspaceManager: WorkspaceManager,
    onPickWorkspace: () -> Unit,
) {
    KimiCardBox {
        KimiMenuRow(Icons.Default.Folder, uiText("当前目录"), workspaceDisplayName, onPickWorkspace)
        KimiDivider()
        KimiMenuRow(Icons.Default.Terminal, uiText("Termux 路径"), workspaceManager.termuxRootPath() ?: uiText("仅 primary"))
        Text(uiText("右上角加号选择目录后会立即刷新对话页顶部的小字目录名。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }
}

