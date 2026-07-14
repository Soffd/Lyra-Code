package com.yukisoffd.lyracode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.ai.ModelReachabilityResult
import com.yukisoffd.lyracode.ai.ProviderReachabilityResult
import com.yukisoffd.lyracode.data.ApiProfile
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.SubAgentConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import kotlin.math.min
import kotlin.math.max



@Composable
internal fun ProfileSettingsSummary(settings: AppSettings) {
    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(settings.userAvatarPath, settings.userNickname.take(1).ifBlank { "L" }, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(settings.userNickname.ifBlank { uiText("Lyra 用户") }, style = MaterialTheme.typography.titleMedium)
                Text(uiText("头像和昵称可在侧边栏顶部点击编辑。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun TopicSummaryModelSettings(settings: AppSettings, controller: ChatController) {
    val profiles = controller.profiles.toList()
    var profileId by remember { mutableStateOf(settings.topicSummaryProfile().id) }
    val selectedProfile = profiles.firstOrNull { it.id == profileId } ?: profiles.firstOrNull()
    var model by remember(profileId) {
        mutableStateOf(
            settings.topicSummaryModel.takeIf { profileId == settings.topicSummaryProfileId && it.isNotBlank() }
                ?: selectedProfile?.selectedModel.orEmpty(),
        )
    }
    var notice by remember { mutableStateOf("") }
    KimiCardBox {
        Text(uiText("独立话题总结模型"), style = MaterialTheme.typography.titleMedium)
        Text(
            uiText("每次新对话首次发送消息后，由此模型单独生成会话标题，不再占用主对话模型的工具调用。可选择轻量模型以降低消耗。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        SubAgentDropdownPicker(
            label = uiText("模型服务"),
            value = selectedProfile?.name ?: uiText("未配置"),
            subtitle = selectedProfile?.selectedModel.orEmpty(),
            items = profiles,
            itemTitle = { it.name },
            itemSubtitle = { it.selectedModel },
            isSelected = { it.id == profileId },
            onSelect = { profile ->
                profileId = profile.id
                model = profile.selectedModel
            },
        )
        SubAgentDropdownPicker(
            label = uiText("话题总结模型"),
            value = model.ifBlank { uiText("未选择") },
            items = selectedProfile?.savedModels.orEmpty(),
            itemTitle = { it },
            isSelected = { it == model },
            onSelect = { model = it },
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(uiText("话题总结模型")) },
            singleLine = true,
        )
        Button(
            enabled = selectedProfile != null && model.isNotBlank(),
            onClick = {
                settings.topicSummaryProfileId = selectedProfile?.id.orEmpty()
                settings.topicSummaryModel = model
                controller.settingsRevision.intValue++
                notice = uiText("话题总结模型已保存")
            },
            shape = KimiPillShape,
        ) { Text(uiText("保存")) }
        if (notice.isNotBlank()) Text(notice, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
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
    var showReachabilityPage by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controller.activeProfileId.value, controller.profiles.size, controller.settingsRevision.intValue) {
        val refreshed = controller.profiles.toList()
        profiles = refreshed
        if (editingProfileId != null && refreshed.none { it.id == editingProfileId } && draftNewProfile?.id != editingProfileId) {
            editingProfileId = null
            showReachabilityPage = false
        }
    }
    BackHandler(enabled = editingProfileId != null) {
        if (showReachabilityPage) {
            showReachabilityPage = false
        } else {
            if (draftNewProfile?.id == editingProfileId) draftNewProfile = null
            editingProfileId = null
        }
    }
    val editingIndex = profiles.indexOfFirst { it.id == editingProfileId }
    val current = if (draftNewProfile?.id == editingProfileId) draftNewProfile else profiles.getOrNull(editingIndex)
    var platformMenuExpanded by remember { mutableStateOf(false) }
    val editKey = editingProfileId ?: "none"
    var name by remember(editKey) { mutableStateOf(current?.name.orEmpty()) }
    var key by remember(editKey) { mutableStateOf(current?.apiKey.orEmpty()) }
    var baseUrl by remember(editKey) { mutableStateOf(current?.baseUrl.orEmpty()) }
    var apiFormat by remember(editKey) { mutableStateOf(current?.apiFormat ?: ApiProfile.API_FORMAT_OPENAI) }
    var chatPath by remember(editKey) { mutableStateOf(current?.chatPath ?: ApiProfile.defaultChatPath(apiFormat)) }
    var model by remember(editKey) { mutableStateOf(current?.selectedModel.orEmpty()) }
    var savedModels by remember(editKey) { mutableStateOf(current?.savedModels.orEmpty().joinToString("\n")) }
    var selectedReachabilityModels by remember(editKey) { mutableStateOf<Set<String>>(emptySet()) }
    var providerReachabilityResult by remember(editKey) { mutableStateOf<ProviderReachabilityResult?>(null) }
    var modelReachabilityResults by remember(editKey) { mutableStateOf<List<ModelReachabilityResult>>(emptyList()) }
    var reachabilityChecking by remember(editKey) { mutableStateOf(false) }
    var activeReachabilityModel by remember(editKey) { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ApiProfile?>(null) }
    val reachabilityModels = remember(savedModels, model) {
        (savedModels.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList() + model.trim())
            .filter { it.isNotBlank() }
            .distinct()
    }
    LaunchedEffect(editKey, reachabilityModels.joinToString("\u0000"), model) {
        val available = reachabilityModels.toSet()
        val retained = selectedReachabilityModels.intersect(available)
        selectedReachabilityModels = if (retained.isNotEmpty()) {
            retained
        } else {
            val preferred = model.trim().takeIf { it in available } ?: reachabilityModels.firstOrNull()
            preferred?.let { setOf(it) } ?: emptySet()
        }
    }
    fun draftProfile(selectedModelOverride: String? = null, savedModelsOverride: List<String>? = null): ApiProfile {
        val models = savedModelsOverride ?: savedModels.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList().distinct()
        val selected = selectedModelOverride ?: model.ifBlank { models.firstOrNull().orEmpty() }
        return ApiProfile(
            id = current?.id ?: AppSettings.newId(),
            name = name.ifBlank { uiText("未命名平台") },
            apiKey = key,
            baseUrl = baseUrl.ifBlank { defaultBaseUrlForApiFormat(apiFormat) },
            chatPath = ApiProfile.normalizedChatPath(apiFormat, chatPath),
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
        notice = uiText("模型服务已保存")
    }
    fun startReachabilityCheck(targetModels: List<String>) {
        val targets = targetModels.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty()) {
            status = uiText("请先选择要检测的模型")
            return
        }
        val draft = draftProfile(savedModelsOverride = targets)
        providerReachabilityResult = null
        modelReachabilityResults = emptyList()
        activeReachabilityModel = ""
        reachabilityChecking = true
        status = uiText("正在检测模型可达性...")
        controller.checkReachabilityForProfileIncremental(
            profile = draft,
            models = targets,
            onProviderResult = { result -> providerReachabilityResult = result },
            onModelChecking = { model -> activeReachabilityModel = model },
            onModelResult = { result ->
                modelReachabilityResults = modelReachabilityResults.filterNot { it.model == result.model } + result
            },
            onDone = { result ->
                reachabilityChecking = false
                activeReachabilityModel = ""
                result.fold(
                    onSuccess = {
                        status = ""
                        notice = uiText("模型可达性检测完成")
                    },
                    onFailure = { error ->
                        status = error.message.orEmpty().ifBlank { uiText("模型可达性检测失败") }
                    },
                )
            },
        )
    }
    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = uiText("删除模型服务配置"),
            message = uiText("该操作会删除服务商、API Key、基础 URL 和预保存模型配置。"),
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
                notice = uiText("已删除 ") + target.name.ifBlank { uiText("模型服务") }
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
                        placeholder = uiText("搜索模型服务"),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                    )
                    IconButton(
                        onClick = {
                            val newProfile = ApiProfile(
                                id = AppSettings.newId(),
                                name = uiText("新平台"),
                                apiKey = "",
                                baseUrl = "https://api.openai.com/v1",
                                chatPath = ApiProfile.DEFAULT_OPENAI_CHAT_PATH,
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
                            contentDescription = uiText("添加模型服务"),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    KimiCardBox {
                        Text(uiText("没有匹配的模型服务"), color = KimiMuted)
                    }
                } else {
                    filtered.forEach { profile ->
                        ModelProviderRow(
                            profile = profile,
                            selected = profile.id == controller.activeProfileId.value,
                            onClick = { editingProfileId = profile.id },
                            onDelete = { if (profiles.size > 1) deleteTarget = profile else notice = uiText("至少保留一个模型服务") },
                        )
                    }
                }
            }
        } else if (showReachabilityPage) {
            ReachabilitySelectionPage(
                providerName = current?.name?.ifBlank { uiText("模型服务") } ?: uiText("模型服务"),
                models = reachabilityModels,
                selectedModels = selectedReachabilityModels,
                checking = reachabilityChecking,
                provider = providerReachabilityResult,
                modelResults = modelReachabilityResults,
                activeModel = activeReachabilityModel,
                status = status,
                onSelectedModelsChange = { selectedReachabilityModels = it },
                onBack = { showReachabilityPage = false },
                onStartCheck = { startReachabilityCheck(reachabilityModels.filter { it in selectedReachabilityModels }) },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(current?.name?.ifBlank { uiText("新模型服务") } ?: uiText("模型服务"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = {
                        showReachabilityPage = false
                        if (draftNewProfile?.id == editingProfileId) draftNewProfile = null
                        editingProfileId = null
                    }) {
                        Icon(Icons.Default.ViewList, contentDescription = uiText("返回列表"))
                    }
                }
                KimiCardBox {
                    Text(uiText("接口格式"), style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ApiFormatOption("OpenAI SDK", ApiProfile.API_FORMAT_OPENAI, apiFormat) {
                            apiFormat = it
                            if (baseUrl.isBlank() || baseUrl in knownProviderBaseUrls()) baseUrl = defaultBaseUrlForApiFormat(it)
                            if (chatPath.isBlank() || chatPath in knownProviderChatPaths()) chatPath = ApiProfile.defaultChatPath(it)
                        }
                        ApiFormatOption("Anthropic Messages", ApiProfile.API_FORMAT_ANTHROPIC, apiFormat) {
                            apiFormat = it
                            if (baseUrl.isBlank() || baseUrl in knownProviderBaseUrls()) baseUrl = defaultBaseUrlForApiFormat(it)
                            if (chatPath.isBlank() || chatPath in knownProviderChatPaths()) chatPath = ApiProfile.defaultChatPath(it)
                        }
                        ApiFormatOption("Gemini GenerateContent", ApiProfile.API_FORMAT_GEMINI, apiFormat) {
                            apiFormat = it
                            if (baseUrl.isBlank() || baseUrl in knownProviderBaseUrls()) baseUrl = defaultBaseUrlForApiFormat(it)
                            if (chatPath.isBlank() || chatPath in knownProviderChatPaths()) chatPath = ApiProfile.defaultChatPath(it)
                        }
                    }
                    Text(
                        apiFormatDescription(apiFormat),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("服务商名称")) }, singleLine = true)
                    OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text(apiKeyLabel(apiFormat)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("基础 URL")) }, singleLine = true)
                    OutlinedTextField(
                        value = chatPath,
                        onValueChange = { chatPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(uiText("请求路径")) },
                        placeholder = { Text(ApiProfile.defaultChatPath(apiFormat)) },
                        singleLine = true,
                    )
                    Text(
                        uiText("用于兼容非默认 OpenAI 路径的服务商。留空时使用当前接口格式的默认请求路径。"),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                        Text(
                            uiText("安全提示：当前基础 URL 使用 HTTP 明文传输，API Key 和对话内容可能被同一网络中的第三方截获。"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        endpointHint(apiFormat, baseUrl, chatPath),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("默认模型")) }, singleLine = true)
                    OutlinedTextField(value = savedModels, onValueChange = { savedModels = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("预保存模型，每行一个")) }, minLines = 3)
                    OutlinedButton(
                        enabled = reachabilityModels.isNotEmpty(),
                        onClick = { showReachabilityPage = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = KimiPillShape,
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(uiText("选择模型检测可达性"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { saveCurrentProfile() }, shape = KimiPillShape) { Text(uiText("保存")) }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val draft = draftProfile()
                                status = uiText("正在获取模型...")
                                controller.fetchModelsForProfile(draft) { result ->
                                    result.fold(
                                        onSuccess = { models ->
                                            val distinct = models.filter { it.isNotBlank() }.distinct()
                                            if (distinct.isEmpty()) {
                                                status = uiText("未获取到可用模型")
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
                                                notice = uiText("已获取 ${distinct.size} 个模型并保存")
                                            }
                                        },
                                        onFailure = { status = it.message.orEmpty().ifBlank { uiText("获取模型失败") } },
                                    )
                                }
                            },
                            shape = KimiPillShape,
                        ) { Text(uiText("获取并替换模型"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        IconButton(
                            enabled = profiles.size > 1,
                            onClick = { current?.let { deleteTarget = it } },
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = uiText("删除平台"))
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, color = KimiMuted)
            }
        }
        }
        ScreenCenterNotice(
            message = notice,
            onDismiss = { notice = "" },
        )
    }
}


@Composable
internal fun ReachabilitySelectionPage(
    providerName: String,
    models: List<String>,
    selectedModels: Set<String>,
    checking: Boolean,
    provider: ProviderReachabilityResult?,
    modelResults: List<ModelReachabilityResult>,
    activeModel: String,
    status: String,
    onSelectedModelsChange: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onStartCheck: () -> Unit,
) {
    val resultByModel = remember(modelResults) { modelResults.associateBy { it.model } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = uiText("返回模型服务"))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(uiText("可达性检测"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(providerName, color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        KimiCardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${uiText("已选择")} ${selectedModels.size}/${models.size}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { onSelectedModelsChange(models.toSet()) }) { Text(uiText("全选")) }
                TextButton(onClick = { onSelectedModelsChange(emptySet()) }) { Text(uiText("清空选择")) }
            }
            Button(
                enabled = !checking && selectedModels.isNotEmpty(),
                onClick = onStartCheck,
                modifier = Modifier.fillMaxWidth(),
                shape = KimiPillShape,
            ) {
                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (checking) uiText("检测中...") else uiText("开始检测"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val providerLine = when {
                provider != null -> {
                    val providerText = if (provider.available) uiText("服务商可用") else uiText("服务商不可用")
                    "$providerText · ${formatReachabilityLatency(provider.latencyMs)} · ${provider.message}"
                }
                checking -> uiText("服务商检测中...")
                else -> ""
            }
            if (providerLine.isNotBlank()) {
                Text(
                    providerLine,
                    color = if (provider?.available == false) MaterialTheme.colorScheme.error else KimiMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (status.isNotBlank()) {
                Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        KimiCardBox {
            Text(uiText("选择要检测的模型"), style = MaterialTheme.typography.titleSmall)
            if (models.isEmpty()) {
                Text(uiText("没有可检测的模型"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                models.forEach { model ->
                    val selected = model in selectedModels
                    val result = resultByModel[model]
                    val detail = when {
                        result != null -> "${formatReachabilityLatency(result.latencyMs)} · ${result.message}"
                        checking && selected && activeModel == model -> uiText("检测中...")
                        checking && selected -> uiText("等待检测")
                        else -> ""
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSelectedModelsChange(
                                    if (selected) selectedModels - model else selectedModels + model,
                                )
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked ->
                                onSelectedModelsChange(if (checked) selectedModels + model else selectedModels - model)
                            },
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            if (detail.isNotBlank()) {
                                Text(
                                    detail,
                                    color = if (result?.available == false) MaterialTheme.colorScheme.error else KimiMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        when {
                            result != null -> Icon(
                                if (result.available) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (result.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            checking && selected && activeModel == model -> LinearProgressIndicator(modifier = Modifier.width(48.dp))
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun ScreenCenterNotice(
    message: String,
    durationMillis: Long = 2400L,
    onDismiss: () -> Unit,
) {
    if (message.isBlank()) return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(durationMillis)
        onDismiss()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            tonalElevation = 8.dp,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
@Composable
internal fun SubAgentSettings(settings: AppSettings, controller: ChatController) {
    var revision by remember { mutableIntStateOf(0) }
    var agents by remember(revision, controller.settingsRevision.intValue) { mutableStateOf(settings.subAgents()) }
    val profiles = controller.profiles.toList()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<SubAgentConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<SubAgentConfig?>(null) }
    var notice by remember { mutableStateOf("") }
    fun save(updated: List<SubAgentConfig>) {
        settings.saveSubAgents(updated)
        agents = updated
        controller.settingsRevision.intValue++
        revision++
    }
    Box(Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KimiCardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(uiText(stringResource(R.string.title_sub_agent_orchestration)), style = MaterialTheme.typography.titleMedium)
                        Text(uiText(stringResource(R.string.sub_agent_settings_desc)), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = settings.subAgentOrchestrationEnabled,
                        onCheckedChange = {
                            settings.subAgentOrchestrationEnabled = it
                            controller.settingsRevision.intValue++
                            revision++
                        },
                    )
                }
            }
            KimiCardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText(stringResource(R.string.label_sub_agent_models)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Button(
                        enabled = profiles.isNotEmpty(),
                        onClick = {
                            val profile = profiles.firstOrNull()
                            editing = SubAgentConfig(
                                id = AppSettings.newId(),
                                name = uiText(context.getString(R.string.label_sub_agent_default_name)),
                                profileId = profile?.id.orEmpty(),
                                model = profile?.selectedModel.orEmpty(),
                                description = uiText(context.getString(R.string.sub_agent_default_desc)),
                                enabled = true,
                            )
                        },
                        shape = KimiPillShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(uiText(stringResource(R.string.action_new)))
                    }
                }
                if (agents.isEmpty()) {
                    Text(uiText(stringResource(R.string.sub_agent_empty_hint)), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    agents.forEach { agent ->
                        val profile = profiles.firstOrNull { it.id == agent.profileId }
                        SubAgentRow(
                            agent = agent,
                            profileName = profile?.name ?: uiText(stringResource(R.string.label_not_configured)),
                            onEdit = { editing = agent },
                            onToggle = { enabled -> save(agents.map { if (it.id == agent.id) it.copy(enabled = enabled) else it }) },
                            onDelete = { deleteTarget = agent },
                        )
                        if (agent != agents.last()) KimiDivider()
                    }
                }
            }
        }
        editing?.let { agent ->
            SubAgentEditDialog(
                initial = agent,
                profiles = profiles,
                onDismiss = { editing = null },
                onSave = { saved ->
                    val updated = if (agents.any { it.id == saved.id }) agents.map { if (it.id == saved.id) saved else it } else agents + saved
                    save(updated)
                    editing = null
                    notice = uiText(context.getString(R.string.notice_sub_agent_saved))
                },
            )
        }
        deleteTarget?.let { agent ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(uiText(stringResource(R.string.title_delete_sub_agent))) },
                text = { Text(uiText(stringResource(R.string.confirm_delete_sub_agent, agent.name))) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            save(agents.filterNot { it.id == agent.id })
                            deleteTarget = null
                            notice = uiText(context.getString(R.string.notice_sub_agent_deleted))
                        },
                    ) { Text(uiText(stringResource(R.string.action_delete))) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(uiText(stringResource(R.string.action_cancel))) } },
            )
        }
        TransientNotice(message = notice, modifier = Modifier.align(Alignment.Center).padding(24.dp), onDismiss = { notice = "" })
    }
}

@Composable
internal fun SubAgentRow(agent: SubAgentConfig, profileName: String, onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onEdit).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(agent.name, style = MaterialTheme.typography.titleSmall)
            Text("$profileName · ${agent.model}", color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (agent.description.isNotBlank()) Text(agent.description, color = KimiMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = agent.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = uiText(stringResource(R.string.action_delete))) }
    }
}

@Composable
internal fun SubAgentEditDialog(initial: SubAgentConfig, profiles: List<ApiProfile>, onDismiss: () -> Unit, onSave: (SubAgentConfig) -> Unit) {
    val context = LocalContext.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var profileId by remember(initial.id) { mutableStateOf(initial.profileId.ifBlank { profiles.firstOrNull()?.id.orEmpty() }) }
    val selectedProfile = profiles.firstOrNull { it.id == profileId } ?: profiles.firstOrNull()
    var model by remember(initial.id, profileId) { mutableStateOf(initial.model.ifBlank { selectedProfile?.selectedModel.orEmpty() }) }
    var description by remember(initial.id) { mutableStateOf(initial.description) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(stringResource(R.string.title_edit_sub_agent))) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText(stringResource(R.string.label_sub_agent_name))) }, singleLine = true)
                SubAgentDropdownPicker(
                    label = uiText(stringResource(R.string.label_provider)),
                    value = selectedProfile?.name ?: uiText(stringResource(R.string.label_not_configured)),
                    subtitle = selectedProfile?.selectedModel.orEmpty(),
                    items = profiles,
                    itemTitle = { it.name },
                    itemSubtitle = { it.selectedModel },
                    isSelected = { it.id == profileId },
                    onSelect = { profile ->
                        profileId = profile.id
                        model = profile.selectedModel
                    },
                )
                SubAgentDropdownPicker(
                    label = uiText(stringResource(R.string.label_model)),
                    value = model.ifBlank { uiText(stringResource(R.string.label_not_selected)) },
                    items = selectedProfile?.savedModels.orEmpty(),
                    itemTitle = { it },
                    isSelected = { it == model },
                    onSelect = { model = it },
                )
                OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText(stringResource(R.string.label_model))) }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText(stringResource(R.string.label_sub_agent_desc))) }, minLines = 3, maxLines = 6)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText(stringResource(R.string.action_enable)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = profileId.isNotBlank() && model.isNotBlank(),
                onClick = { onSave(initial.copy(name = name.trim().ifBlank { uiText(context.getString(R.string.label_sub_agent_default_name)) }, profileId = profileId, model = model.trim(), description = description.trim(), enabled = enabled)) },
                shape = KimiPillShape,
            ) { Text(uiText(stringResource(R.string.action_save))) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText(stringResource(R.string.action_cancel))) } },
    )
}
@Composable
internal fun <T> SubAgentDropdownPicker(
    label: String,
    value: String,
    subtitle: String = "",
    items: List<T>,
    itemTitle: (T) -> String,
    itemSubtitle: (T) -> String = { "" },
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.Start) {
                    Text("$label: $value", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    if (subtitle.isNotBlank()) Text(subtitle, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 280.dp).heightIn(max = 320.dp),
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(itemTitle(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val childSubtitle = itemSubtitle(item)
                                    if (childSubtitle.isNotBlank()) Text(childSubtitle, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                                if (isSelected(item)) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            onSelect(item)
                            expanded = false
                        },
                    )
                }
            }
        }
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


@Composable
internal fun ReachabilityReportCard(
    provider: ProviderReachabilityResult?,
    modelResults: List<ModelReachabilityResult>,
    checking: Boolean,
) {
    KimiCardBox {
        Text(uiText("可达性检测结果"), style = MaterialTheme.typography.titleSmall)
        if (provider == null) {
            Text(
                uiText("服务商检测中..."),
                color = KimiMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val providerText = if (provider.available) uiText("服务商可用") else uiText("服务商不可用")
            Text(
                "$providerText · ${formatReachabilityLatency(provider.latencyMs)} · ${provider.message}",
                color = if (provider.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        modelResults.forEach { result ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (result.available) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (result.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(result.model, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${formatReachabilityLatency(result.latencyMs)} · ${result.message}",
                        color = KimiMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (checking) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(uiText("检测中..."), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
internal fun formatReachabilityLatency(latencyMs: Long): String {
    return if (latencyMs > 0L) "${latencyMs}ms" else uiText("无延迟数据")
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

internal fun knownProviderChatPaths(): Set<String> = setOf(
    ApiProfile.DEFAULT_OPENAI_CHAT_PATH,
    ApiProfile.DEFAULT_ANTHROPIC_CHAT_PATH,
    "/models/{model}:generateContent",
)
internal fun apiKeyLabel(format: String): String = when (format) {
    ApiProfile.API_FORMAT_ANTHROPIC -> "Anthropic API Key"
    ApiProfile.API_FORMAT_GEMINI -> "Google API Key"
    else -> "API Key"
}

internal fun apiFormatDescription(format: String): String = when (format) {
    ApiProfile.API_FORMAT_ANTHROPIC -> uiText("适用于 Claude 官方 Messages API 或兼容 Anthropic Messages 格式的服务。请求、工具调用和图片输入会按 Anthropic 格式转换。")
    ApiProfile.API_FORMAT_GEMINI -> uiText("适用于 Gemini 官方 GenerateContent API 或兼容 Gemini 格式的服务。图片、音频、视频会使用 inlineData 传输。")
    else -> uiText("适用于 OpenAI Chat Completions SDK 兼容平台。原有工具调用、流式输出和多模态 image_url 路径保持不变。")
}

internal fun endpointHint(format: String, baseUrl: String, chatPath: String): String {
    val root = baseUrl.trim().trimEnd('/').ifBlank { defaultBaseUrlForApiFormat(format) }
    val path = ApiProfile.normalizedChatPath(format, chatPath)
    return when (format) {
        ApiProfile.API_FORMAT_GEMINI -> uiText("请求端点：$root/models/{model}:generateContent；模型列表：$root/models")
        else -> uiText("请求端点：$root$path；模型列表：$root/models")
    }
}

