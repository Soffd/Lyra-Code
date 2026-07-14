package com.yukisoffd.lyracode

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.AppUpdateInfo
import com.yukisoffd.lyracode.data.UpdateDownloadProgress
import com.yukisoffd.lyracode.data.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.math.min
import kotlin.math.max



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
            LicenseNotice("AndroidX Core KTX", "Apache License 2.0", uiText("Android Kotlin 扩展与兼容层。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("AndroidX Activity Compose", "Apache License 2.0", uiText("Compose Activity 集成。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("Jetpack Compose UI", "Apache License 2.0", uiText("声明式 UI 框架。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("Jetpack Compose Material 3", "Apache License 2.0", uiText("Material Design 3 组件。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("Jetpack Compose Material Icons Extended", "Apache License 2.0", uiText("界面图标库。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("AndroidX DocumentFile", "Apache License 2.0", uiText("SAF 工作区文件访问。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("AndroidX Security Crypto", "Apache License 2.0", uiText("本地敏感配置加密存储。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("Kotlinx Coroutines", "Apache License 2.0", uiText("异步任务与流式请求。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("OkHttp", "Apache License 2.0", uiText("HTTP、SSE 兼容读取与 MCP Streamable HTTP 通信。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("JetBrains Markdown / RikkaHub Markdown fork", "Apache License 2.0", uiText("Markdown GFM AST 解析，支持表格、列表和数学节点。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("Android Gradle Plugin", "Apache License 2.0", uiText("Android 构建工具链。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("Kotlin", "Apache License 2.0", uiText("主要开发语言与编译器。"), LicenseTexts.APACHE_2_0),
            LicenseNotice("JSch / mwiede fork", "BSD 3-Clause License", uiText("SSH 连接与远程命令执行。"), LicenseTexts.BSD_3_CLAUSE),
            LicenseNotice("JLatexMath Android / Soffd fork", "GNU General Public License v2.0 with linking exception", uiText("本地 LaTeX 数学公式渲染。源码随工程 third_party/jlatexmath 保留。"), LicenseTexts.JLATEXMATH_GPL_2_WITH_EXCEPTION),
            LicenseNotice("JLatexMath fonts", "OFL / Knuth / Public Domain / GPL v2", uiText("数学公式渲染字体。完整字体许可随 third_party/jlatexmath/assets 分发。"), LicenseTexts.JLATEXMATH_FONT_LICENSES),
            LicenseNotice("JSON-java / org.json", "JSON License", uiText("JSON 解析与序列化。"), LicenseTexts.JSON_LICENSE),
            LicenseNotice("JUnit", "Eclipse Public License 1.0", uiText("单元测试框架，仅测试构建使用。"), LicenseTexts.EPL_1_0),
            LicenseNotice("Simple Icons", "CC0 1.0 Universal", uiText("关于页面仓库与社交群聊 SVG 图标。"), LicenseTexts.CC0_1_0),
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
                            Icon(Icons.Default.Close, contentDescription = uiText("关闭"))
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
                Text(uiText("开源许可证"), style = MaterialTheme.typography.titleMedium)
                Text(
                    uiText("Lyra Code 使用以下开源组件。点击条目可查看内置的原始许可证文本。"),
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
    val versionName = packageInfo?.versionName.orEmpty().ifBlank { uiText("未知") }
    val versionCode = packageInfo?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toString() else {
            @Suppress("DEPRECATION")
            it.versionCode.toString()
        }
    } ?: uiText("未知")
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
                .onFailure { notice = it.message.orEmpty().ifBlank { uiText("无法打开安装器") } }
        } else if (apk != null) {
            notice = uiText("授权未完成，可稍后点击继续安装")
        }
    }

    fun openInstaller(apk: File) {
        if (updateManager.needsInstallPermission()) {
            notice = uiText("请授权安装未知来源应用，返回后将继续安装")
            installPermissionLauncher.launch(updateManager.installPermissionIntent())
        } else {
            runCatching { context.startActivity(updateManager.installIntent(apk)) }
                .onFailure { notice = it.message.orEmpty().ifBlank { uiText("无法打开安装器") } }
        }
    }

    fun checkUpdate() {
        if (checking) return
        checking = true
        notice = uiText("正在检查更新...")
        scope.launch {
            val result = withContext(Dispatchers.IO) { updateManager.checkForUpdate() }
            checking = false
            result.fold(
                onSuccess = { info ->
                    if (info == null) {
                        updateManager.clearLatestAvailableUpdate()
                        onUpdateAvailabilityChange(false)
                        notice = uiText("当前已是最新版本")
                    } else {
                        updateManager.saveLatestAvailableUpdate(info)
                        onUpdateAvailabilityChange(true)
                        notice = ""
                        updateInfo = info
                    }
                },
                onFailure = { notice = it.message.orEmpty().ifBlank { uiText("检查更新失败") } },
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
                downloadProgress = UpdateDownloadProgress(status = uiText("准备下载"))
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        updateManager.downloadApk(info) { progress -> downloadProgress = progress }
                    }
                    downloading = false
                    result.fold(
                        onSuccess = { apk ->
                            pendingApk = apk
                            notice = uiText("下载完成，准备安装")
                            openInstaller(apk)
                        },
                        onFailure = {
                            val message = it.message.orEmpty().ifBlank { uiText("下载失败") }
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
                        uiText("面向 Android 的本地 AI Agent 工具，支持多平台模型、流式对话、Termux、工作区文件操作、联网搜索、MCP、Skills、TODO 进度和文件变更审查。"),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            KimiSectionLabel(context.getString(R.string.section_version_update))
            KimiCardBox {
                AboutVersionRow(
                    versionText = context.getString(R.string.label_version, versionName, versionCode),
                    value = if (checking) context.getString(R.string.action_checking_update) else if (updateAvailable) context.getString(R.string.notice_new_version_found) else context.getString(R.string.action_check_update),
                    updateAvailable = updateAvailable,
                    onClick = ::checkUpdate,
                )
                pendingApk?.let { apk ->
                    KimiDivider()
                    KimiMenuRow(
                        Icons.Default.InstallMobile,
                        updateManager.pendingDownloadedApkLabel(),
                        uiText("已下载 ${formatBytes(apk.length())}，无需重新下载"),
                        onClick = { openInstaller(apk) },
                    )
                }
                KimiDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(uiText("不弹出更新提示"), style = MaterialTheme.typography.titleSmall)
                        Text(uiText("关闭进入软件时每日一次的新版本弹窗，不影响手动检测更新。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
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
                KimiMenuRow(Icons.Default.Apps, uiText("应用 ID"), context.packageName)
            }
            KimiSectionLabel(uiText("仓库"))
            KimiCardBox {
                SocialLinkRow(
                    logo = { Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = uiText("官网"),
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
            KimiSectionLabel(uiText("社交群聊"))
            KimiCardBox {
                SocialLinkRow(
                    logo = { SocialLogoBadge(R.drawable.ic_simple_qq) },
                    title = uiText("QQ 群"),
                    value = uiText("加入 Lyra Code QQ 群聊"),
                    onClick = { uriHandler.openUri("https://qm.qq.com/q/Ws8objzR84") },
                )
                KimiDivider()
                SocialLinkRow(
                    logo = { SocialLogoBadge(R.drawable.ic_simple_discord) },
                    title = "Discord",
                    value = uiText("加入 Lyra Code Discord 社区"),
                    onClick = { uriHandler.openUri("https://discord.gg/3Mx3F4RTP9") },
                )
            }
            KimiSectionLabel(uiText("隐私与安全"))
            KimiCardBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("隐私与安全"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        uiText("API Key 保存在本机配置中；对话、工具输出、缓存和审查日志默认留在本机。使用第三方模型接口、HTTP 明文 URL、联网搜索、MCP 或 Termux 命令时，数据会按用户配置发送到对应服务或本机执行环境。"),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        uiText("应用内更新会下载 APK 二进制文件并校验 SHA-256。安装前 Android 会要求用户允许 Lyra Code 安装未知来源应用。"),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            KimiSectionLabel(uiText("构建信息"))
            KimiCardBox {
                KimiMenuRow(Icons.Default.PhoneAndroid, uiText("手机信息"), "${Build.MANUFACTURER} ${Build.MODEL}", onClick = onOpenDeviceInfo)
                KimiDivider()
                KimiMenuRow(Icons.Default.CloudDownload, uiText("更新清单"), updateManager.manifestUrl().ifBlank { uiText("未配置") })
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
                        Text(uiText("手机信息"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        uiText("用于截图反馈、排查兼容性问题，以及让硬件检查 Agent 分析当前设备环境。部分项目受系统权限和 Android 沙箱限制，可能只能显示近似信息。"),
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
        title = { Text(uiText("发现新版本 ${info.versionName.ifBlank { info.versionCode.toString() }}")) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (info.mandatory) {
                    Text(uiText("这是重要更新，建议尽快安装。"), color = MaterialTheme.colorScheme.error)
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
                Text(if (downloading) uiText("下载中") else uiText("应用内下载"))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info.webUrl.isNotBlank() || info.apkUrl.isNotBlank()) {
                    TextButton(onClick = onOpenWeb) { Text(uiText("网页下载")) }
                }
                TextButton(onClick = onDismiss, enabled = !downloading) { Text(uiText("稍后")) }
            }
        },
    )
}

