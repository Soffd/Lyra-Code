package com.yukisoffd.lyracode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.FontLibraryItem
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt



@Composable
internal fun ThemeSettings(
    settings: AppSettings,
    themeMode: String,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    languageMode: String,
    refreshRateMode: String,
    onRefreshRateModeChange: (String) -> Unit,
    fontScaleMode: String,
    customFontScale: Float,
    onOpenThemeModeSettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onOpenFontSettings: () -> Unit,
    onOpenRefreshRateSettings: () -> Unit,
    onOpenChatBackgroundSettings: () -> Unit,
    onOpenStreamingOutputSettings: () -> Unit,
) {
    val hasBackground = !settings.chatBackgroundPath.isNullOrBlank()
    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.width(36.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(uiText("Material You 动态配色"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = dynamicColorEnabled, onCheckedChange = onDynamicColorChange)
        }
        KimiDivider()
        KimiMenuRow(Icons.Default.Palette, uiText("主题模式"), if (settings.customThemeColorEnabled) uiText("自定义 ${settings.customThemeColor}") else themeName(themeMode), onOpenThemeModeSettings)
        KimiDivider()
        KimiMenuRow(Icons.Default.Language, uiText("文本语言"), languageName(languageMode), onOpenLanguageSettings)
        KimiDivider()
        KimiMenuRow(Icons.Default.FormatSize, uiText("字体与大小"), fontScaleName(fontScaleMode, customFontScale), onOpenFontSettings)
        KimiDivider()
        KimiMenuRow(Icons.Default.Speed, uiText("刷新率"), refreshRateName(refreshRateMode), onOpenRefreshRateSettings)
        KimiDivider()
        KimiMenuRow(
            Icons.Default.Animation,
            stringResource(R.string.streaming_output_title),
            streamingAnimationModeName(settings.streamingAnimationMode),
            onOpenStreamingOutputSettings,
        )
        KimiDivider()
        KimiMenuRow(
            Icons.Default.Image,
            uiText("聊天背景"),
            if (hasBackground) uiText("已设置自定义背景") else uiText("纯色背景"),
            onOpenChatBackgroundSettings,
        )
    }
}

internal fun streamingAnimationModeName(mode: String): String = when (AppSettings.normalizeStreamingAnimationMode(mode)) {
    AppSettings.STREAMING_ANIMATION_FADE -> uiText("渐变显示")
    else -> uiText("逐字显示")
}

@Composable
internal fun StreamingOutputSettings(settings: AppSettings, controller: ChatController) {
    var selected by remember { mutableStateOf(settings.streamingAnimationMode) }
    KimiCardBox {
        StreamingAnimationOptionRow(
            icon = Icons.Default.Keyboard,
            title = stringResource(R.string.streaming_typewriter_title),
            subtitle = stringResource(R.string.streaming_typewriter_desc),
            value = AppSettings.STREAMING_ANIMATION_TYPEWRITER,
            selected = selected,
        ) { value ->
            selected = value
            settings.streamingAnimationMode = value
            controller.settingsRevision.intValue++
        }
        KimiDivider()
        StreamingAnimationOptionRow(
            icon = Icons.Default.Gradient,
            title = stringResource(R.string.streaming_fade_title),
            subtitle = stringResource(R.string.streaming_fade_desc),
            value = AppSettings.STREAMING_ANIMATION_FADE,
            selected = selected,
        ) { value ->
            selected = value
            settings.streamingAnimationMode = value
            controller.settingsRevision.intValue++
        }
    }
}

@Composable
private fun StreamingAnimationOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onSelect(value) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.width(36.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (value == selected) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.streaming_selected), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun ChatBackgroundSettings(settings: AppSettings) {
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
                        notice = uiText("聊天背景已保存")
                        backgroundRevision++
                    },
                    onFailure = { notice = it.message.orEmpty().ifBlank { uiText("保存失败") } },
                )
                cropBackgroundUri = null
            },
            onCropped = { cropped ->
                settings.saveChatBackground(cropped).fold(
                    onSuccess = {
                        notice = uiText("聊天背景已保存")
                        backgroundRevision++
                    },
                    onFailure = { notice = it.message.orEmpty().ifBlank { uiText("保存失败") } },
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
        KimiMenuRow(
            Icons.Default.Image,
            uiText("上传背景"),
            if (hasBackground) uiText("已设置自定义背景") else uiText("纯色背景"),
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
                                uiText("聊天背景预览"),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            uiText("蒙版透明度 ${(maskOpacity * 100f).toInt().coerceIn(0, 100)}%"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            uiText("越低越接近纯色背景，越高背景图越清晰。"),
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
            KimiMenuRow(Icons.Default.DeleteOutline, uiText("移除聊天背景"), uiText("恢复纯色背景")) {
                settings.clearChatBackground()
                notice = uiText("已恢复纯色背景")
                backgroundRevision++
            }
        }
        if (notice.isNotBlank()) {
            Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ThemeModeSettings(
    settings: AppSettings,
    controller: ChatController,
    themeMode: String,
    onOpenCustomThemeColor: () -> Unit,
    onThemeModeChange: (String) -> Unit,
) {
    var customEnabled by remember { mutableStateOf(settings.customThemeColorEnabled) }
    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText("自定义主题色"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("开启后会使用所选颜色协调界面背景、卡片、输入框和侧边栏等表面。按钮动态取色和聊天背景图不受影响。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = customEnabled,
                onCheckedChange = {
                    customEnabled = it
                    settings.customThemeColorEnabled = it
                    controller.settingsRevision.intValue++
                },
            )
        }
        if (customEnabled) Text(
            uiText("自定义主题色已接管明暗判断，关闭后可恢复主题模式选择。"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        KimiDivider()
        KimiMenuRow(
            Icons.Default.ColorLens,
            uiText("设置自定义主题色"),
            uiText("当前颜色：${AppSettings.normalizeHexColor(settings.customThemeColor)}"),
            onOpenCustomThemeColor,
        )
    }
    KimiCardBox {
        Text(uiText("主题模式"), style = MaterialTheme.typography.titleMedium)
        Text(
            if (customEnabled) uiText("关闭自定义主题色后可重新选择主题模式。") else uiText("选择跟随系统、浅色或深色模式。返回主题设置后，其他外观选项会保持当前位置。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        KimiDivider()
        ThemeOptionRow(uiText("跟随系统"), AppSettings.THEME_SYSTEM, themeMode, onThemeModeChange, !customEnabled)
        KimiDivider()
        ThemeOptionRow(uiText("浅色"), AppSettings.THEME_LIGHT, themeMode, onThemeModeChange, !customEnabled)
        KimiDivider()
        ThemeOptionRow(uiText("深色"), AppSettings.THEME_DARK, themeMode, onThemeModeChange, !customEnabled)
    }
}

@Composable
internal fun CustomThemeColorSettings(settings: AppSettings, controller: ChatController) {
    var colorText by remember { mutableStateOf(sanitizeThemeColorInput(settings.customThemeColor)) }
    var confirmSave by remember { mutableStateOf(false) }
    var savedNotice by remember { mutableStateOf("") }
    val inputValid = colorText.matches(Regex("#[0-9A-F]{6}"))
    val normalized = if (inputValid) colorText else AppSettings.normalizeHexColor(settings.customThemeColor)

    KimiCardBox {
        Text(uiText("选择主题色"), style = MaterialTheme.typography.titleMedium)
        Text(
            uiText("色环和输入框只修改预览，保存并再次确认后才会应用，返回不会改变当前主题色。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        ThemeColorRing(normalized) { selected ->
            colorText = selected
            savedNotice = ""
        }
        OutlinedTextField(
            value = colorText,
            onValueChange = { value ->
                colorText = sanitizeThemeColorInput(value)
                savedNotice = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(uiText("16 进制颜色代码")) },
            supportingText = {
                Text(if (inputValid) colorText else uiText("请输入 6 位颜色代码，例如 #F6F6F4"))
            },
            isError = !inputValid,
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { colorText = sanitizeThemeColorInput(settings.customThemeColor) }) {
                Text(uiText("撤销更改"))
            }
            Button(onClick = { confirmSave = true }, enabled = inputValid) {
                Text(uiText("保存主题色"))
            }
        }
        if (savedNotice.isNotBlank()) Text(savedNotice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }

    if (confirmSave) AlertDialog(
        onDismissRequest = { confirmSave = false },
        title = { Text(uiText("确认应用主题色？")) },
        text = { Text(uiText("主题色将更新为 $colorText，界面配色会立即变化。")) },
        confirmButton = {
            TextButton(
                onClick = {
                    settings.customThemeColor = colorText
                    controller.settingsRevision.intValue++
                    savedNotice = uiText("主题色已更新为 $colorText")
                    confirmSave = false
                },
            ) { Text(uiText("确认应用")) }
        },
        dismissButton = { TextButton(onClick = { confirmSave = false }) { Text(uiText("取消")) } },
    )
}

internal fun sanitizeThemeColorInput(value: String): String {
    val candidate = if (value.count { it == '#' } > 1) value.substringAfterLast('#') else value.removePrefix("#")
    val digits = candidate.filter { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }.take(6).uppercase(Locale.ROOT)
    return "#$digits"
}

@Composable
private fun ThemeColorRing(color: String, onColorSelected: (String) -> Unit) {
    val selectedColor = remember(color) { runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Color.White) }
    val ringOutline = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> selectThemeRingColor(offset, size.width, size.height, onColorSelected) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> selectThemeRingColor(offset, size.width, size.height, onColorSelected) },
                    onDrag = { change, _ -> selectThemeRingColor(change.position, size.width, size.height, onColorSelected) },
                )
            },
    ) {
        val radius = size.minDimension * 0.38f
        drawCircle(
            brush = Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red), center),
            radius = radius,
            style = Stroke(width = 30.dp.toPx()),
        )
        drawCircle(selectedColor, radius = radius * 0.52f)
        drawCircle(ringOutline, radius = radius * 0.52f, style = Stroke(1.dp.toPx()))
    }
}
private fun selectThemeRingColor(offset: Offset, width: Int, height: Int, onColorSelected: (String) -> Unit) {
    val center = Offset(width / 2f, height / 2f)
    val angle = (Math.toDegrees(kotlin.math.atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())) + 360.0) % 360.0
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(angle.toFloat(), 0.82f, 0.92f))
    onColorSelected(String.format("#%06X", 0xFFFFFF and argb))
}
@Composable
internal fun LanguageSettings(
    languageMode: String,
    onLanguageModeChange: (String) -> Unit,
) {
    KimiCardBox {
        Text(uiText("文本语言"), style = MaterialTheme.typography.titleMedium)
        Text(
            uiText("默认跟随系统语言；当前未提供对应翻译或系统语言无法识别时，会使用默认简体中文。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        KimiDivider()
        LanguageOptionRow(uiText("跟随系统"), uiText("优先使用设备语言"), AppSettings.LANGUAGE_SYSTEM, languageMode, onLanguageModeChange)
        KimiDivider()
        LanguageOptionRow(uiText("简体中文"), uiText("默认语言"), AppSettings.LANGUAGE_ZH_CN, languageMode, onLanguageModeChange)
        KimiDivider()
        LanguageOptionRow("English", "English interface resources", AppSettings.LANGUAGE_EN, languageMode, onLanguageModeChange)
    }
}

@Composable
internal fun RefreshRateSettings(
    refreshRateMode: String,
    onRefreshRateModeChange: (String) -> Unit,
) {
    KimiCardBox {
        Text(uiText("刷新率"), style = MaterialTheme.typography.titleMedium)
        Text(
            uiText("跟随系统会交给设备自行在省电和流畅之间切换；固定刷新率会向系统请求指定帧率，实际是否生效取决于屏幕和系统策略。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        KimiDivider()
        RefreshRateOptionRow(uiText("跟随系统智能刷新率"), AppSettings.REFRESH_RATE_SYSTEM, refreshRateMode, onRefreshRateModeChange)
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
internal fun ThemeOptionRow(title: String, value: String, selected: String, onSelect: (String) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onSelect(value) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Palette,
            contentDescription = null,
            modifier = Modifier.width(36.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, modifier = Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else KimiMuted, style = MaterialTheme.typography.titleMedium)
        if (value == selected && enabled) {
            Icon(Icons.Default.Check, contentDescription = uiText("已选择"), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun LanguageOptionRow(
    title: String,
    subtitle: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect(value) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Language,
            contentDescription = null,
            modifier = Modifier.width(36.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (value == selected) {
            Icon(Icons.Default.Check, contentDescription = uiText("已选择"), tint = MaterialTheme.colorScheme.primary)
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
            Icon(Icons.Default.Check, contentDescription = uiText("已选择"), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun FontSizeSettings(
    settings: AppSettings,
    controller: ChatController,
    fontScaleMode: String,
    customFontScale: Float,
    onFontScaleModeChange: (String) -> Unit,
    onCustomFontScaleChange: (Float) -> Unit,
    onOpenFontLibrary: () -> Unit,
) {
    val fontSettingsRevision = controller.settingsRevision.intValue
    val currentTextFontName = remember(fontSettingsRevision) { settings.textFontName?.takeIf { settings.textFontPath != null } }
    val currentCodeFontName = remember(fontSettingsRevision) { settings.codeFontName?.takeIf { settings.codeFontPath != null } }
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
        Modifier.fillMaxSize().padding(top = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                ) {
                    CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, previewScale)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text(uiText("预览文字大小"), style = MaterialTheme.typography.titleMedium)
                            Text(fontScaleLabel(previewScale), color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, previewScale)) {
                Text(uiText("你可以拖动滑块来调整字体大小。"), style = MaterialTheme.typography.titleLarge)
                Text(
                    uiText("如果在使用过程中存在问题或建议，可在关于软件页面查看仓库链接并反馈。"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        KimiCardBox {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenFontLibrary).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.FontDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(uiText("字体库"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("在字体库中批量导入、预览和切换字体。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = uiText("进入字体库"), tint = KimiMuted)
            }
            KimiDivider()
            FontSelectionSummary(
                title = uiText("文本字体"),
                currentName = currentTextFontName,
                onClear = {
                    settings.clearFont(codeFont = false)
                    controller.settingsRevision.intValue++
                },
            )
            FontSelectionSummary(
                title = uiText("代码字体"),
                currentName = currentCodeFontName,
                onClear = {
                    settings.clearFont(codeFont = true)
                    controller.settingsRevision.intValue++
                },
            )
        }
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("跟随系统"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
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
                        onValueChange = { draftScale = it.coerceIn(AppSettings.MIN_FONT_SCALE, AppSettings.MAX_FONT_SCALE) },
                        onValueChangeFinished = {
                            val finalScale = (draftScale / AppSettings.FONT_SCALE_STEP).roundToInt() * AppSettings.FONT_SCALE_STEP
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

@Composable
internal fun FontLibrarySettings(
    settings: AppSettings,
    controller: ChatController,
) {
    var fontRevision by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf("") }
    var previewItem by remember { mutableStateOf<FontLibraryItem?>(null) }
    val fontLibrary = remember(fontRevision) { settings.fontLibrary() }
    val fontMimeTypes = arrayOf(
        "font/ttf", "font/otf", "font/ttc", "font/collection", "application/x-font-ttf",
        "application/x-font-opentype", "application/x-font-ttc", "application/octet-stream",
    )
    val fontLibraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            settings.importFonts(uris).fold(
                onSuccess = { imported ->
                    notice = uiText("已导入 ${imported.size} 个字体")
                    fontRevision++
                },
                onFailure = { error -> notice = uiText(error.message.orEmpty().ifBlank { "字体导入失败" }) },
            )
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("字体库"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            uiText("支持批量导入 TTF、OTF 和 TTC 字体。导入后可预览，并分别设为文本字体或代码字体。"),
                            color = KimiMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = { fontLibraryLauncher.launch(fontMimeTypes) }, shape = KimiPillShape) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(uiText("导入字体"), maxLines = 1)
                    }
                }
            }
        }
        if (notice.isNotBlank()) {
            item { Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
        if (fontLibrary.isEmpty()) {
            item {
                KimiCardBox {
                    Text(uiText("字体库为空，点击“导入字体”可一次选择多个文件。"), color = KimiMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(fontLibrary, key = { it.id }) { item ->
                FontLibraryRow(
                    item = item,
                    textSelected = settings.textFontPath == item.path,
                    codeSelected = settings.codeFontPath == item.path,
                    onPreview = { previewItem = item },
                    onSelectText = {
                        settings.selectFont(item, codeFont = false)
                        notice = uiText("已切换文本字体：${item.name}")
                        fontRevision++
                        controller.settingsRevision.intValue++
                    },
                    onSelectCode = {
                        settings.selectFont(item, codeFont = true)
                        notice = uiText("已切换代码字体：${item.name}")
                        fontRevision++
                        controller.settingsRevision.intValue++
                    },
                    onDelete = {
                        settings.deleteFont(item)
                        notice = uiText("已从字体库删除：${item.name}")
                        fontRevision++
                        controller.settingsRevision.intValue++
                    },
                )
            }
        }
    }
    previewItem?.let { item ->
        val previewFamily = remember(item.path) {
            runCatching { FontFamily(Typeface.createFromFile(item.path)) }.getOrDefault(FontFamily.Default)
        }
        AlertDialog(
            onDismissRequest = { previewItem = null },
            title = { Text(uiText("字体预览")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.name, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        uiText("春风又绿江南岸 · The quick brown fox jumps over the lazy dog. · 0123456789"),
                        fontFamily = previewFamily,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        uiText("常规 Regular  粗体 Bold  斜体 Italic"),
                        fontFamily = previewFamily,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { previewItem = null }) { Text(uiText("关闭")) } },
        )
    }
}
@Composable
private fun FontSelectionSummary(
    title: String,
    currentName: String?,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                currentName ?: uiText("系统默认"),
                color = KimiMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (currentName != null) TextButton(onClick = onClear) { Text(uiText("恢复默认"), maxLines = 1) }
    }
}

@Composable
private fun FontLibraryRow(
    item: FontLibraryItem,
    textSelected: Boolean,
    codeSelected: Boolean,
    onPreview: () -> Unit,
    onSelectText: () -> Unit,
    onSelectCode: () -> Unit,
    onDelete: () -> Unit,
) {
    val itemFamily = remember(item.path) {
        runCatching { FontFamily(Typeface.createFromFile(item.path)) }.getOrDefault(FontFamily.Default)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontFamily = itemFamily, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.extension, color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onPreview) { Icon(Icons.Default.Visibility, contentDescription = uiText("预览")) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = uiText("从字体库删除")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onSelectText) {
                    if (textSelected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (textSelected) uiText("文本字体（当前）") else uiText("设为文本字体"))
                }
                TextButton(onClick = onSelectCode) {
                    if (codeSelected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (codeSelected) uiText("代码字体（当前）") else uiText("设为代码字体"))
                }
            }
        }
    }
}
internal fun fontScaleLabel(scale: Float): String = when {
    scale < 0.65f -> uiText("最小 ${(scale * 100).roundToInt()}%")
    scale < 0.8f -> uiText("极小 ${(scale * 100).roundToInt()}%")
    scale < 0.95f -> uiText("小 ${(scale * 100).roundToInt()}%")
    scale < 1.08f -> uiText("标准 ${(scale * 100).roundToInt()}%")
    scale < 1.35f -> uiText("大 ${(scale * 100).roundToInt()}%")
    scale < 1.65f -> uiText("超大 ${(scale * 100).roundToInt()}%")
    scale < 2.1f -> uiText("极大 ${(scale * 100).roundToInt()}%")
    else -> uiText("最大 ${(scale * 100).roundToInt()}%")
}

internal fun themeName(mode: String): String = when (mode) {
    AppSettings.THEME_LIGHT -> uiText("浅色")
    AppSettings.THEME_DARK -> uiText("深色")
    else -> uiText("跟随系统")
}

internal fun languageName(mode: String): String = when (AppSettings.normalizeLanguageMode(mode)) {
    AppSettings.LANGUAGE_ZH_CN -> uiText("简体中文")
    AppSettings.LANGUAGE_EN -> "English"
    else -> uiText("跟随系统")
}

internal fun refreshRateName(mode: String): String = when (mode) {
    AppSettings.REFRESH_RATE_30 -> "30 Hz"
    AppSettings.REFRESH_RATE_60 -> "60 Hz"
    AppSettings.REFRESH_RATE_90 -> "90 Hz"
    AppSettings.REFRESH_RATE_120 -> "120 Hz"
    else -> uiText("智能刷新率")
}

internal fun fontScaleName(mode: String, customFontScale: Float): String = when (mode) {
    AppSettings.FONT_SCALE_SMALL -> uiText("小字")
    AppSettings.FONT_SCALE_NORMAL -> uiText("标准字")
    AppSettings.FONT_SCALE_LARGE -> uiText("大字")
    AppSettings.FONT_SCALE_EXTRA_LARGE -> uiText("超大字")
    AppSettings.FONT_SCALE_CUSTOM -> uiText("自定义 ${(customFontScale * 100).roundToInt()}%")
    else -> uiText("字体跟随系统")
}


