package com.yukisoffd.lyracode

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.provider.MediaStore
import android.util.Base64
import android.util.DisplayMetrics
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
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.yukisoffd.lyracode.data.McpServerConfig
import com.yukisoffd.lyracode.data.McpToolDefinition
import com.yukisoffd.lyracode.data.RoleplaySticker
import com.yukisoffd.lyracode.data.SkillPack
import com.yukisoffd.lyracode.data.SshServerConfig
import com.yukisoffd.lyracode.data.WebDavServerConfig
import com.yukisoffd.lyracode.mcp.McpClientManager
import com.yukisoffd.lyracode.ssh.SshExecutor
import com.yukisoffd.lyracode.termux.TermuxExecutor
import com.yukisoffd.lyracode.webdav.TransferProgress
import com.yukisoffd.lyracode.webdav.WebDavClient
import com.yukisoffd.lyracode.workspace.GlobalFileManager
import com.yukisoffd.lyracode.workspace.NativeFileManager
import com.yukisoffd.lyracode.workspace.UploadedFile
import com.yukisoffd.lyracode.workspace.UploadedFileManager
import com.yukisoffd.lyracode.workspace.WorkspaceManager
import com.yukisoffd.lyracode.workspace.WorkspaceFileReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import android.graphics.Canvas as AndroidCanvas

@Composable
internal fun rememberAnimatedKeyboardAvoidanceOffsetPx(): Int {
    val targetOffsetPx = rememberKeyboardAvoidanceOffsetPx()
    val bottomOffsetPx by animateIntAsState(
        targetValue = targetOffsetPx,
        animationSpec = tween(durationMillis = 180),
        label = "keyboardAvoidanceOffset",
    )
    return bottomOffsetPx
}

internal fun Modifier.keyboardAwareInputOffset(bottomOffsetPx: Int): Modifier {
    return offset { IntOffset(x = 0, y = -bottomOffsetPx) }
}

@Composable
internal fun rememberKeyboardAvoidanceOffsetPx(): Int {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val composeImeBottom = WindowInsets.ime.getBottom(density)
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    var legacyMetrics by remember(view, context) { mutableStateOf(KeyboardAvoidanceMetrics()) }

    DisposableEffect(view, context) {
        val visibleFrame = Rect()
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val rootView = view.rootView ?: view
            val rootHeight = rootView.height.takeIf { it > 0 } ?: view.height
            if (rootHeight <= 0) {
                legacyMetrics = KeyboardAvoidanceMetrics()
                return@OnGlobalLayoutListener
            }
            rootView.getWindowVisibleDisplayFrame(visibleFrame)
            val composeViewHeight = view.height.takeIf { it > 0 } ?: rootHeight
            val realHeight = context.realDisplayHeightPx().takeIf { it > 0 } ?: rootHeight
            val keyboardThreshold = (realHeight * LEGACY_IME_VISIBLE_THRESHOLD).toInt()
            val windowResizedByIme =
                realHeight - rootHeight > keyboardThreshold ||
                    realHeight - composeViewHeight > keyboardThreshold ||
                    rootHeight - composeViewHeight > keyboardThreshold
            val rootHiddenBottom = (rootHeight - visibleFrame.bottom).coerceAtLeast(0)
            val realHiddenBottom = (realHeight - visibleFrame.bottom).coerceAtLeast(0)
            val frameHiddenBottom = if (windowResizedByIme) {
                0
            } else {
                max(rootHiddenBottom, realHiddenBottom)
            }
            val compatImeBottom = ViewCompat.getRootWindowInsets(rootView)
                ?.getInsets(WindowInsetsCompat.Type.ime())
                ?.bottom
                ?: 0
            legacyMetrics = KeyboardAvoidanceMetrics(
                frameHiddenBottom = frameHiddenBottom,
                compatImeBottom = compatImeBottom,
                windowResizedByIme = windowResizedByIme,
            )
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
    }

    if (legacyMetrics.windowResizedByIme) {
        return 0
    }
    val keyboardBottom = max(composeImeBottom, max(legacyMetrics.compatImeBottom, legacyMetrics.frameHiddenBottom))
    val minKeyboardBottom = with(density) { LEGACY_IME_MIN_BOTTOM_DP.dp.roundToPx() }
    if (keyboardBottom < minKeyboardBottom) {
        return 0
    }
    return (keyboardBottom - navigationBottom).coerceAtLeast(0)
}

private const val LEGACY_IME_VISIBLE_THRESHOLD = 0.15f
private const val LEGACY_IME_MIN_BOTTOM_DP = 80

private data class KeyboardAvoidanceMetrics(
    val frameHiddenBottom: Int = 0,
    val compatImeBottom: Int = 0,
    val windowResizedByIme: Boolean = false,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.realDisplayHeightPx(): Int {
    val activity = findActivity()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && activity != null) {
        activity.windowManager.currentWindowMetrics.bounds.height()
    } else {
        @Suppress("DEPRECATION")
        val display = activity?.windowManager?.defaultDisplay
        if (display != null) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            metrics.heightPixels
        } else {
            resources.displayMetrics.heightPixels
        }
    }
}

@Composable
internal fun ChatScreen(controller: ChatController, settings: AppSettings, termuxExecutor: TermuxExecutor) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var fetchStatus by remember { mutableStateOf("") }
    var attachmentMenuOpen by rememberSaveable { mutableStateOf(false) }
    var attachmentMenuPage by rememberSaveable { mutableStateOf("root") }
    var attachmentMenuSearch by rememberSaveable { mutableStateOf("") }
    var cropUploadUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectionResetKey by remember { mutableIntStateOf(0) }
    var selectedWorkspaceFiles by remember { mutableStateOf<List<WorkspaceFileReference>>(emptyList()) }
    var workspaceFileMatches by remember { mutableStateOf<List<WorkspaceFileReference>>(emptyList()) }
    var workspaceFileSearchLoading by remember { mutableStateOf(false) }
    val fileUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) controller.attachUploadedFile(uri)
    }
    val imageUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) cropUploadUri = uri
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) cropUploadUri = saveTemporaryUploadImage(context, bitmap)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardLiftPx = rememberAnimatedKeyboardAvoidanceOffsetPx()
    val keyboardLiftDp = with(LocalDensity.current) { keyboardLiftPx.toDp() }
    val messageSnapshot = controller.messages.value
    val renderItems = remember(messageSnapshot) { chatRenderItems(messageSnapshot) }
    val pendingUploads = controller.pendingUploads
    val isRunning = controller.isActiveConversationRunning()
    val hasWorkspace = controller.hasWorkspace()
    var forcedSkillIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val installedSkills = settings.installedSkills()
    val canSend = (input.isNotBlank() || pendingUploads.isNotEmpty() || selectedWorkspaceFiles.isNotEmpty()) && !isRunning
    val draftKey = controller.inputDraftKey()
    var loadedDraftKey by remember { mutableStateOf("") }
    LaunchedEffect(draftKey) {
        input = controller.loadInputDraft()
        selectedWorkspaceFiles = emptyList()
        workspaceFileMatches = emptyList()
        workspaceFileSearchLoading = false
        loadedDraftKey = draftKey
    }
    LaunchedEffect(input, loadedDraftKey) {
        if (loadedDraftKey == draftKey) controller.saveInputDraft(input)
    }
    LaunchedEffect(input, controller.activeConversationId.value, controller.settingsRevision.intValue) {
        if (!input.startsWith("@") || !hasWorkspace) {
            workspaceFileMatches = emptyList()
            workspaceFileSearchLoading = false
            return@LaunchedEffect
        }
        workspaceFileSearchLoading = true
        try {
            delay(120L)
            val mentionBody = input.drop(1)
            val query = mentionBody.substringBefore(' ').trim()
            workspaceFileMatches = withContext(Dispatchers.IO) {
                controller.searchWorkspaceFiles(query)
            }
        } finally {
            workspaceFileSearchLoading = false
        }
    }
    var autoFollowOutput by remember(controller.activeConversationId.value) { mutableStateOf(true) }
    var keyboardShouldLiftOutput by remember(controller.activeConversationId.value) { mutableStateOf(false) }
    val isInterrupted = controller.activeConversation()?.status == ConversationStore.STATUS_INTERRUPTED
    controller.pendingToolApproval.value?.let { pending ->
        ToolApprovalDialog(
            pending = pending,
            onApprove = { rememberConversation ->
                controller.answerToolApproval(approved = true, rememberForConversation = rememberConversation, feedback = "")
            },
            onReject = { feedback ->
                controller.answerToolApproval(approved = false, rememberForConversation = false, feedback = feedback)
            },
        )
    }
    cropUploadUri?.let { uri ->
        ImageCropUploadDialog(
            uri = uri,
            onDismiss = { cropUploadUri = null },
            onUseOriginal = {
                controller.attachUploadedFile(uri)
                cropUploadUri = null
            },
            onCropped = { cropped ->
                controller.attachUploadedFile(cropped)
                cropUploadUri = null
            },
        )
    }
    if (attachmentMenuOpen) {
        AttachmentActionBottomSheet(
            controller = controller,
            settings = settings,
            page = attachmentMenuPage,
            search = attachmentMenuSearch,
            onPageChange = {
                attachmentMenuPage = it
                attachmentMenuSearch = ""
            },
            onSearchChange = { attachmentMenuSearch = it },
            onDismiss = {
                attachmentMenuOpen = false
                attachmentMenuPage = "root"
                attachmentMenuSearch = ""
            },
            onPickFile = {
                attachmentMenuOpen = false
                fileUploadLauncher.launch("*/*")
            },
            onPickImage = {
                attachmentMenuOpen = false
                imageUploadLauncher.launch("image/*")
            },
            onTakePhoto = {
                attachmentMenuOpen = false
                cameraLauncher.launch(null)
            },
            onFetchModels = {
                attachmentMenuOpen = false
                controller.fetchModels {
                    fetchStatus = it.fold({ uiText("已获取 ${it.size} 个模型") }, { error -> error.message.orEmpty() })
                }
            },
        )
    }
    LaunchedEffect(fetchStatus) {
        val currentStatus = fetchStatus
        if (currentStatus.isNotBlank()) {
            kotlinx.coroutines.delay(2400L)
            if (fetchStatus == currentStatus) {
                fetchStatus = ""
            }
        }
    }

    if (controller.isRoleplayMode()) {
        RoleplayChatScreen(
            controller = controller,
            settings = settings,
            input = input,
            onInputChange = { input = it },
            pendingUploads = pendingUploads,
            canSend = canSend,
            isRunning = isRunning,
            installedSkills = installedSkills,
            forcedSkillIds = forcedSkillIds,
            onForcedSkillIdsChange = { forcedSkillIds = it },
            listState = listState,
            keyboardLiftPx = keyboardLiftPx,
            keyboardLiftDp = keyboardLiftDp,
            onOpenMenu = {
                attachmentMenuPage = "root"
                attachmentMenuOpen = true
            },
            onSend = {
                val text = input
                controller.clearInputDraft()
                val skills = forcedSkillIds
                input = ""
                forcedSkillIds = emptyList()
                attachmentMenuOpen = false
                controller.send(text, skills)
            },
            onStop = { controller.stopActive() },
        )
        return
    }

    val chatBackground = remember(settings.settingsRevisionSafe()) {
        settings.chatBackgroundPath
            ?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    val chatBackgroundMaskAlpha = 1f - settings.chatBackgroundMaskOpacity.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (chatBackground != null) {
            Image(
                bitmap = chatBackground,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = chatBackgroundMaskAlpha)),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        TodoProgressPanel(settings, controller.activeConversationId.value, controller.todoItems)
        ConversationChangesPanel(settings, controller.activeConversationId.value, messageSnapshot)
        val isNearOutputEnd by remember {
            derivedStateOf {
                val total = listState.layoutInfo.totalItemsCount
                if (total == 0) {
                    true
                } else {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    val bottomDistance = if (last != null && last.index == total - 1) {
                        listState.layoutInfo.viewportEndOffset - (last.offset + last.size)
                    } else {
                        Int.MIN_VALUE
                    }
                    !listState.canScrollForward || bottomDistance >= -220
                }
            }
        }
        LaunchedEffect(keyboardLiftPx, isNearOutputEnd) {
            if (keyboardLiftPx == 0) {
                keyboardShouldLiftOutput = isNearOutputEnd
            } else if (isNearOutputEnd) {
                keyboardShouldLiftOutput = true
            }
        }
        LaunchedEffect(isRunning, isNearOutputEnd, listState.isScrollInProgress) {
            when {
                isNearOutputEnd -> autoFollowOutput = true
                isRunning && listState.isScrollInProgress -> autoFollowOutput = false
            }
        }
        LaunchedEffect(messageSnapshot.lastOrNull()?.id, messageSnapshot.lastOrNull()?.content?.length, messageSnapshot.lastOrNull()?.thinking?.length) {
            if (messageSnapshot.isNotEmpty() && (autoFollowOutput || isNearOutputEnd)) {
                listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            }
        }
        LaunchedEffect(keyboardLiftPx) {
            if (keyboardLiftPx > 0 && messageSnapshot.isNotEmpty() && keyboardShouldLiftOutput) {
                listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            }
        }
        val blankTapInteraction = remember { MutableInteractionSource() }
        val activeProcessKey = if (isRunning) {
            renderItems.lastOrNull { it.process.isNotEmpty() }?.key
        } else {
            null
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = blankTapInteraction,
                    indication = null,
                    onClick = { selectionResetKey++ },
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (keyboardShouldLiftOutput) keyboardLiftDp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(renderItems, key = { it.key }) { item ->
                    if (item.process.isNotEmpty()) {
                        AgentProcessSummary(
                            messages = item.process,
                            selectionResetKey = selectionResetKey,
                            active = item.key == activeProcessKey,
                        )
                    } else if (item.message != null) {
                        MessageCard(
                            message = item.message,
                            selectionResetKey = selectionResetKey,
                            streamingAnimationMode = settings.streamingAnimationMode,
                            isStreaming = isRunning && item.message.id == messageSnapshot.lastOrNull { it.role == "assistant" }?.id,
                            onEditAndRegenerate = controller::editAndRegenerateUserMessage,
                        )
                    }
                }
                if (isInterrupted && messageSnapshot.isNotEmpty()) {
                    item(key = "continue-interrupted") {
                        ContinueInterruptedRow(onContinue = { controller.continueActive() })
                    }
                }
                item(key = "bottom-anchor") {
                    Spacer(Modifier.height(1.dp))
                }
            }
            if (messageSnapshot.isNotEmpty() && !isNearOutputEnd && !listState.isScrollInProgress) {
                IconButton(
                    onClick = { scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = uiText("回到底部"))
                }
            }
        }
        val statusLine = listOf(controller.status.value, controller.uploadingStatus.value, fetchStatus)
            .filter { it.isNotBlank() && it != uiText("完成") }
            .joinToString(" ")
        if (statusLine.isNotBlank()) {
            Text(statusLine, color = KimiMuted, style = MaterialTheme.typography.labelMedium)
        }
        Card(
            Modifier.fillMaxWidth().keyboardAwareInputOffset(keyboardLiftPx),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ForcedSkillControls(
                    input = input,
                    installedSkills = installedSkills,
                    forcedSkillIds = forcedSkillIds,
                    enabled = !isRunning,
                    onSkillIdsChange = { forcedSkillIds = it },
                    onInputChange = { input = it },
                )
                WorkspaceFileMentionPicker(
                    input = input,
                    matches = workspaceFileMatches,
                    selected = selectedWorkspaceFiles,
                    enabled = !isRunning,
                    hasWorkspace = hasWorkspace,
                    loading = workspaceFileSearchLoading,
                    onToggle = { file ->
                        selectedWorkspaceFiles = if (selectedWorkspaceFiles.any { it.relativePath == file.relativePath }) {
                            selectedWorkspaceFiles.filterNot { it.relativePath == file.relativePath }
                        } else {
                            (selectedWorkspaceFiles + file).distinctBy { it.relativePath }.take(24)
                        }
                    },
                    onRemove = { file ->
                        selectedWorkspaceFiles = selectedWorkspaceFiles.filterNot { it.relativePath == file.relativePath }
                    },
                    onDone = {
                        input = removeWorkspaceMentionPrefix(input)
                        workspaceFileMatches = emptyList()
                    },
                )
                if (pendingUploads.isNotEmpty()) {
                    PendingUploadStrip(pendingUploads, onRemove = controller::removePendingUpload)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        TextButton(
                            onClick = {
                                attachmentMenuPage = "root"
                                attachmentMenuOpen = !attachmentMenuOpen
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = uiText("添加附件"))
                        }
                    }
                    CapsuleTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 4,
                        placeholder = uiText("输入消息"),
                        enabled = !isRunning,
                    )
                    AnimatedVisibility(isRunning) {
                        Button(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            onClick = { controller.stopActive() },
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = uiText("停止"), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    AnimatedVisibility(canSend && !isRunning) {
                        Button(
                            modifier = Modifier.size(42.dp),
                            enabled = canSend,
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            onClick = {
                                val text = input
                                val skills = forcedSkillIds
                                val workspaceFiles = selectedWorkspaceFiles
                                controller.clearInputDraft()
                                input = ""
                                forcedSkillIds = emptyList()
                                selectedWorkspaceFiles = emptyList()
                                workspaceFileMatches = emptyList()
                                attachmentMenuOpen = false
                                controller.send(text, skills, workspaceFiles)
                            },
                        ) {
                            Icon(Icons.Default.Send, contentDescription = uiText("发送"), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun WorkspaceFileMentionPicker(
    input: String,
    matches: List<WorkspaceFileReference>,
    selected: List<WorkspaceFileReference>,
    enabled: Boolean,
    hasWorkspace: Boolean,
    loading: Boolean,
    onToggle: (WorkspaceFileReference) -> Unit,
    onRemove: (WorkspaceFileReference) -> Unit,
    onDone: () -> Unit,
) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (fadeIn() + expandIn()) togetherWith (fadeOut() + shrinkOut())
        },
        label = "workspaceFileSelection",
    ) { visibleSelection ->
        if (visibleSelection.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleSelection.forEach { file ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(file.name, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = { onRemove(file) }, enabled = enabled, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.workspace_file_remove), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    val showPicker = shouldShowWorkspaceFilePicker(input, enabled, hasWorkspace)
    AnimatedVisibility(showPicker) {
    Card(
        Modifier.fillMaxWidth().heightIn(max = 280.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.workspace_file_picker_title), style = MaterialTheme.typography.labelMedium)
                    if (selected.isNotEmpty()) {
                        Text(uiText("已选择") + " ${selected.size}/24", color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onDone, enabled = enabled) { Text(uiText("完成")) }
            }
            if (loading) {
                Text(
                    stringResource(R.string.workspace_file_indexing),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (matches.isEmpty()) {
                Text(
                    stringResource(R.string.workspace_file_no_matches),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 210.dp)) {
                items(matches, key = { it.relativePath }) { file ->
                    val isSelected = selected.any { it.relativePath == file.relativePath }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable(enabled = enabled) { onToggle(file) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(file.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis, color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(
                            if (isSelected) Icons.Default.CheckCircle else Icons.Default.Add,
                            contentDescription = stringResource(if (isSelected) R.string.workspace_file_remove else R.string.workspace_file_select),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
    }
}

internal fun shouldShowWorkspaceFilePicker(input: String, enabled: Boolean, hasWorkspace: Boolean): Boolean =
    enabled && hasWorkspace && input.startsWith("@")

private fun removeWorkspaceMentionPrefix(input: String): String {
    if (!input.startsWith("@")) return input
    val body = input.drop(1)
    val firstWhitespace = body.indexOfFirst { it.isWhitespace() }
    if (firstWhitespace < 0) return ""
    return body.drop(firstWhitespace + 1).trimStart()
}

@Composable
private fun ForcedSkillControls(
    input: String,
    installedSkills: List<SkillPack>,
    forcedSkillIds: List<String>,
    enabled: Boolean,
    onSkillIdsChange: (List<String>) -> Unit,
    onInputChange: (String) -> Unit,
) {
    val forcedSkills = forcedSkillIds.mapNotNull { id -> installedSkills.firstOrNull { it.id == id } }
    val slashBody = input.takeIf { it.startsWith("/") }?.drop(1).orEmpty()
    val query = slashBody.substringBefore(' ').trim()
    val showPicker = enabled && input.startsWith("/") && installedSkills.isNotEmpty()
    val matches = installedSkills
        .filterNot { it.id in forcedSkillIds }
        .filter { skill ->
            query.isBlank() ||
                skill.name.contains(query, ignoreCase = true) ||
                skill.description.contains(query, ignoreCase = true) ||
                skill.id.contains(query, ignoreCase = true)
        }
        .take(8)

    if (forcedSkills.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(uiText("本轮强制使用"), color = KimiMuted, style = MaterialTheme.typography.labelSmall)
            forcedSkills.forEach { skill ->
                TextButton(
                    onClick = { onSkillIdsChange(forcedSkillIds - skill.id) },
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = uiText("点击移除 Skill"), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(skill.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    AnimatedVisibility(showPicker) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(uiText("选择技能"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(uiText("输入 / 选择本轮要强制使用的 Skill"), style = MaterialTheme.typography.labelSmall, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (matches.isEmpty()) {
                    Text(
                        uiText("没有匹配的 Skill"),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    matches.forEach { skill ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onSkillIdsChange(forcedSkillIds + skill.id)
                                    onInputChange(removeSkillSlashPrefix(input))
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(skill.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    if (!skill.enabled) {
                                        Text(uiText("已禁用"), color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (skill.description.isNotBlank()) {
                                    Text(skill.description, maxLines = 1, overflow = TextOverflow.Ellipsis, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun removeSkillSlashPrefix(input: String): String {
    if (!input.startsWith("/")) return input
    val body = input.drop(1)
    val firstWhitespace = body.indexOfFirst { it.isWhitespace() }
    if (firstWhitespace < 0) return ""
    return body.drop(firstWhitespace + 1).trimStart()
}
@Composable
internal fun RoleplayChatScreen(
    controller: ChatController,
    settings: AppSettings,
    input: String,
    onInputChange: (String) -> Unit,
    pendingUploads: List<UploadedFile>,
    canSend: Boolean,
    isRunning: Boolean,
    installedSkills: List<SkillPack>,
    forcedSkillIds: List<String>,
    onForcedSkillIdsChange: (List<String>) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    keyboardLiftPx: Int,
    keyboardLiftDp: androidx.compose.ui.unit.Dp,
    onOpenMenu: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val roleplayId = controller.currentRoleplayId()
    val scenario = remember(settings.settingsRevisionSafe(), roleplayId) {
        settings.roleplayScenario(roleplayId)
    }
    val stickers = remember(settings.settingsRevisionSafe(), roleplayId) {
        settings.roleplayStickers(roleplayId)
    }
    val background = remember(scenario?.backgroundPath) {
        scenario?.backgroundPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    val messageSnapshot = controller.messages.value
    val visibleMessages = remember(messageSnapshot, isRunning) {
        val roleMessages = messageSnapshot
            .filter { it.role == "user" || it.role == "assistant" }
            .filterNot { it.role == "assistant" && it.content.isBlank() }
        val streamingAssistantId = if (isRunning) roleMessages.lastOrNull { it.role == "assistant" }?.id else null
        roleMessages.filterNot { it.id == streamingAssistantId && it.role == "assistant" }
    }
    val scope = rememberCoroutineScope()
    var keyboardShouldLiftOutput by remember(controller.activeConversationId.value) { mutableStateOf(false) }
    val isNearOutputEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total == 0) {
                true
            } else {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                val bottomDistance = if (last != null && last.index == total - 1) {
                    listState.layoutInfo.viewportEndOffset - (last.offset + last.size)
                } else {
                    Int.MIN_VALUE
                }
                !listState.canScrollForward || bottomDistance >= -220
            }
        }
    }
    LaunchedEffect(keyboardLiftPx, isNearOutputEnd) {
        if (keyboardLiftPx == 0) {
            keyboardShouldLiftOutput = isNearOutputEnd
        } else if (isNearOutputEnd) {
            keyboardShouldLiftOutput = true
        }
    }
    val affection = settings.roleplayAffection(roleplayId)
    var affectionDelta by remember { mutableStateOf("") }
    val lastRoleplayTool = messageSnapshot.lastOrNull { it.role == "tool" && it.content.contains("lyra_roleplay_state_v1") }
    LaunchedEffect(lastRoleplayTool?.id, lastRoleplayTool?.content) {
        val content = lastRoleplayTool?.content ?: return@LaunchedEffect
        runCatching {
            val jsonText = content.substringAfter("lyra_roleplay_state_v1", "").trim()
            val json = JSONObject(jsonText)
            val delta = json.optInt("affectionDelta", 0)
            if (delta != 0) {
                affectionDelta = if (delta > 0) "+$delta" else delta.toString()
                kotlinx.coroutines.delay(2600L)
                affectionDelta = ""
            }
        }
    }
    LaunchedEffect(visibleMessages.size, visibleMessages.lastOrNull()?.content?.length, isRunning) {
        if (visibleMessages.isNotEmpty() && !listState.isScrollInProgress && isNearOutputEnd) {
            listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(keyboardLiftPx) {
        if (keyboardLiftPx > 0 && visibleMessages.isNotEmpty() && keyboardShouldLiftOutput) {
            listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (background != null) {
            Image(
                bitmap = background,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.56f,
            )
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.28f)))
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp + (if (keyboardShouldLiftOutput) keyboardLiftDp else 0.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (visibleMessages.isEmpty()) {
                        item("roleplay-empty") {
                            Text(
                                uiText("好感度 $affection"),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    items(visibleMessages, key = { it.id }) { message ->
                        RoleplayMessageBubble(
                            message = message,
                            settings = settings,
                            aiAvatarPath = scenario?.aiAvatarPath,
                            stickers = stickers,
                        )
                    }
                    item("roleplay-bottom-anchor") { Spacer(Modifier.height(1.dp)) }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(uiText("好感度 $affection"), style = MaterialTheme.typography.labelMedium)
                    AnimatedVisibility(affectionDelta.isNotBlank()) {
                        Text(
                            affectionDelta,
                            color = if (affectionDelta.startsWith("-")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Card(
                Modifier.fillMaxWidth().keyboardAwareInputOffset(keyboardLiftPx),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ForcedSkillControls(
                        input = input,
                        installedSkills = installedSkills,
                        forcedSkillIds = forcedSkillIds,
                        enabled = !isRunning,
                        onSkillIdsChange = onForcedSkillIdsChange,
                        onInputChange = onInputChange,
                    )
                    if (pendingUploads.isNotEmpty()) {
                        PendingUploadStrip(pendingUploads, onRemove = controller::removePendingUpload)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onOpenMenu,
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = uiText("添加"))
                        }
                        CapsuleTextField(
                            value = input,
                            onValueChange = onInputChange,
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 4,
                            placeholder = uiText("输入消息"),
                            enabled = !isRunning,
                        )
                        AnimatedVisibility(isRunning) {
                            Button(
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                onClick = onStop,
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = uiText("停止"), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        AnimatedVisibility(canSend && !isRunning) {
                            Button(
                                modifier = Modifier.size(42.dp),
                                enabled = canSend,
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                onClick = onSend,
                            ) {
                                Icon(Icons.Default.Send, contentDescription = uiText("发送"), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RoleplayMessageBubble(
    message: ChatRecord,
    settings: AppSettings,
    aiAvatarPath: String?,
    stickers: List<RoleplaySticker>,
) {
    val isUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            UserAvatar(aiAvatarPath, "A", Modifier.size(38.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 292.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RoleplayStickerAwareContent(
                text = message.content,
                stickers = stickers,
                isUser = isUser,
            )
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            UserAvatar(settings.userAvatarPath, settings.userNickname.take(1).ifBlank { uiText("你") }, Modifier.size(38.dp))
        }
    }
}

@Composable
internal fun RoleplayStickerAwareContent(
    text: String,
    stickers: List<RoleplaySticker>,
    isUser: Boolean,
) {
    val stickerMap = remember(stickers) { stickers.associateBy { it.code } }
    val regex = remember(stickerMap.keys) {
        if (stickerMap.isEmpty()) null else Regex(stickerMap.keys.joinToString("|") { Regex.escape(it) })
    }
    val parts = remember(text, regex) {
        if (regex == null) {
            listOf<Pair<String, RoleplaySticker?>>(text to null)
        } else buildList<Pair<String, RoleplaySticker?>> {
            var cursor = 0
            regex.findAll(text).forEach { match ->
                if (match.range.first > cursor) add(text.substring(cursor, match.range.first) to null)
                add(match.value to stickerMap[match.value])
                cursor = match.range.last + 1
            }
            if (cursor < text.length) add(text.substring(cursor) to null)
        }
    }
    parts.forEach { (part, sticker) ->
        if (sticker != null) {
            val bitmap = remember(sticker.path) { BitmapFactory.decodeFile(sticker.path)?.asImageBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = sticker.name,
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        } else if (part.isNotBlank()) {
            Text(
                part.trim(),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun AppSettings.settingsRevisionSafe(): Int {
    return roleplayScenarios().hashCode() * 31 +
        selectedRoleplayId.hashCode() +
        immersiveRoleplayEnabled.hashCode() +
        chatBackgroundPath.hashCode() +
        chatBackgroundMaskOpacity.hashCode()
}

internal fun requestTermuxRunCommandPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    if (context.checkSelfPermission(MainActivity.TERMUX_RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED) return
    (context as? Activity)?.requestPermissions(arrayOf(MainActivity.TERMUX_RUN_COMMAND_PERMISSION), 1001)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentActionBottomSheet(
    controller: ChatController,
    settings: AppSettings,
    page: String,
    search: String,
    onPageChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onFetchModels: () -> Unit,
) {
    val profiles = controller.profiles.toList()
    val activeProfile = profiles.firstOrNull { it.id == controller.activeProfileId.value } ?: profiles.firstOrNull()
    val prompts = remember(controller.settingsRevision.intValue) {
        settings.systemPromptPresets().filterNot { it.id == "roleplay" }
    }
    val activePrompt = prompts.firstOrNull { it.id == settings.selectedSystemPromptId } ?: prompts.firstOrNull()
    var autoApproveConfirmOpen by rememberSaveable { mutableStateOf(false) }
    val autoApprovalEnabled = controller.isAutoApprovalEnabledForActiveSession()
    if (autoApproveConfirmOpen) {
        AlertDialog(
            onDismissRequest = { autoApproveConfirmOpen = false },
            title = { Text(uiText(stringResource(R.string.title_enable_auto_approve))) },
            text = { Text(uiText(stringResource(R.string.auto_approve_warning))) },
            confirmButton = {
                TextButton(onClick = {
                    controller.setAutoApprovalForActiveSession(true)
                    autoApproveConfirmOpen = false
                }) { Text(uiText(stringResource(R.string.action_enable_auto_approve))) }
            },
            dismissButton = { TextButton(onClick = { autoApproveConfirmOpen = false }) { Text(uiText(stringResource(R.string.action_cancel))) } },
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 54.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "attachment-action-sheet-page",
            ) { targetPage ->
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (targetPage) {
                        "providers" -> {
                            SheetBackTitle(uiText(stringResource(R.string.label_choose_provider))) { onPageChange("root") }
                            CapsuleTextField(
                                value = search,
                                onValueChange = onSearchChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = uiText(stringResource(R.string.search_provider_placeholder)),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                            )
                            val filteredProfiles = profiles.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
                            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                filteredProfiles.forEach { profile ->
                                    ActionSheetRow(
                                        icon = Icons.Default.Cloud,
                                        title = profile.name,
                                        subtitle = "${profile.apiFormat} · ${profile.baseUrl}",
                                        trailing = if (profile.id == controller.activeProfileId.value) Icons.Default.Check else null,
                                        onClick = {
                                            controller.selectProfile(profile.id)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                        "models" -> {
                            SheetBackTitle(uiText(stringResource(R.string.label_choose_model))) { onPageChange("root") }
                            CapsuleTextField(
                                value = search,
                                onValueChange = onSearchChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = uiText(stringResource(R.string.search_model_placeholder)),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                            )
                            val filteredModels = activeProfile?.savedModels.orEmpty()
                                .filter { search.isBlank() || it.contains(search, ignoreCase = true) }
                            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                filteredModels.forEach { modelName ->
                                    ActionSheetRow(
                                        icon = Icons.Default.SmartToy,
                                        title = modelName,
                                        subtitle = activeProfile?.name.orEmpty(),
                                        trailing = if (modelName == controller.activeModel.value) Icons.Default.Check else null,
                                        onClick = {
                                            controller.selectModel(modelName)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                        "prompts" -> {
                            SheetBackTitle(uiText(stringResource(R.string.label_switch_prompt))) { onPageChange("root") }
                            CapsuleTextField(
                                value = search,
                                onValueChange = onSearchChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = uiText(stringResource(R.string.search_prompt_placeholder)),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                            )
                            val filteredPrompts = prompts.filter {
                                search.isBlank() ||
                                    it.name.contains(search, ignoreCase = true) ||
                                    it.prompt.contains(search, ignoreCase = true)
                            }
                            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                filteredPrompts.forEach { prompt ->
                                    ActionSheetRow(
                                        icon = Icons.Default.EditNote,
                                        title = prompt.name,
                                        subtitle = prompt.prompt.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                                        trailing = if (prompt.id == settings.selectedSystemPromptId) Icons.Default.Check else null,
                                        onClick = {
                                            controller.selectSystemPrompt(prompt.id)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                        "reasoning" -> {
                            SheetBackTitle(uiText(stringResource(R.string.label_reasoning_depth))) { onPageChange("root") }
                            val values = AppSettings.reasoningDepthValues
                            val current = settings.reasoningDepth.takeIf { it in values } ?: AppSettings.REASONING_AUTO
                            var sliderPosition by remember(current) { mutableStateOf(values.indexOf(current).coerceAtLeast(0).toFloat()) }
                            val selected = values.getOrElse(sliderPosition.toInt().coerceIn(0, values.lastIndex)) { AppSettings.REASONING_AUTO }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(reasoningDepthLabel(selected), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    uiText(stringResource(R.string.reasoning_depth_hint)),
                                    color = KimiMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Slider(
                                    value = sliderPosition,
                                    onValueChange = { sliderPosition = it },
                                    onValueChangeFinished = {
                                        controller.selectReasoningDepth(values.getOrElse(sliderPosition.toInt().coerceIn(0, values.lastIndex)) { AppSettings.REASONING_AUTO })
                                    },
                                    valueRange = 0f..(values.size - 1).toFloat(),
                                    steps = values.size - 2,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    values.forEach { value ->
                                        Text(reasoningDepthLabel(value), color = if (value == selected) MaterialTheme.colorScheme.primary else KimiMuted, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        else -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ActionSheetTile(Icons.Default.PhotoLibrary, uiText(stringResource(R.string.action_album)), Modifier.weight(1f), onPickImage)
                                ActionSheetTile(Icons.Default.PhotoCamera, uiText(stringResource(R.string.action_camera)), Modifier.weight(1f), onTakePhoto)
                                ActionSheetTile(Icons.Default.AttachFile, uiText(stringResource(R.string.action_file)), Modifier.weight(1f), onPickFile)
                            }
                            ActionSheetSwitchRow(
                                icon = Icons.Default.AdminPanelSettings,
                                title = uiText(stringResource(R.string.label_auto_approve)),
                                subtitle = uiText(stringResource(if (autoApprovalEnabled) R.string.subtitle_auto_approve_on else R.string.subtitle_auto_approve_off)),
                                checked = autoApprovalEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) autoApproveConfirmOpen = true else controller.setAutoApprovalForActiveSession(false)
                                },
                            )
                            if (controller.isRoleplayMode()) {
                                KimiDivider()
                                ActionSheetRow(
                                    icon = Icons.Default.AddComment,
                                    title = uiText(stringResource(R.string.title_new_immersive_chat)),
                                    subtitle = uiText(stringResource(R.string.subtitle_new_immersive_chat)),
                                    onClick = {
                                        controller.newConversation()
                                        onDismiss()
                                    },
                                )
                            }
                            KimiDivider()
                            ActionSheetRow(
                                icon = Icons.Default.Cloud,
                                title = uiText(stringResource(R.string.label_provider)),
                                subtitle = activeProfile?.name ?: uiText(stringResource(R.string.label_not_configured)),
                                trailing = Icons.Default.ChevronRight,
                                onClick = { onPageChange("providers") },
                            )
                            ActionSheetRow(
                                icon = Icons.Default.SmartToy,
                                title = uiText(stringResource(R.string.label_model)),
                                subtitle = controller.activeModel.value.ifBlank { activeProfile?.selectedModel.orEmpty().ifBlank { uiText(stringResource(R.string.label_not_selected)) } },
                                trailing = Icons.Default.ChevronRight,
                                onClick = { onPageChange("models") },
                            )
                            ActionSheetRow(
                                icon = Icons.Default.Sync,
                                title = uiText(stringResource(R.string.action_fetch_models)),
                                subtitle = uiText(stringResource(R.string.subtitle_fetch_models)),
                                onClick = onFetchModels,
                            )
                            ActionSheetRow(
                                icon = Icons.Default.EditNote,
                                title = uiText(stringResource(R.string.label_prompt)),
                                subtitle = activePrompt?.name ?: uiText(stringResource(R.string.label_default_assistant)),
                                trailing = Icons.Default.ChevronRight,
                                onClick = { onPageChange("prompts") },
                            )
                            ActionSheetRow(
                                icon = Icons.Default.Tune,
                                title = uiText(stringResource(R.string.label_reasoning)),
                                subtitle = reasoningDepthLabel(settings.reasoningDepth),
                                trailing = Icons.Default.ChevronRight,
                                onClick = { onPageChange("reasoning") },
                            )
                            val hasSubAgents = settings.enabledSubAgents().isNotEmpty()
                            ActionSheetSwitchRow(
                                icon = Icons.Default.AccountTree,
                                title = uiText(stringResource(R.string.label_sub_agent_orchestration)),
                                subtitle = if (hasSubAgents) {
                                    uiText(stringResource(R.string.subtitle_sub_agent_orchestration))
                                } else {
                                    uiText(stringResource(R.string.subtitle_sub_agent_no_models))
                                },
                                checked = settings.subAgentOrchestrationEnabled && hasSubAgents,
                                enabled = hasSubAgents,
                                onCheckedChange = { enabled ->
                                    settings.subAgentOrchestrationEnabled = enabled
                                    controller.settingsRevision.intValue++
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun reasoningDepthLabel(value: String): String = when (value) {
    AppSettings.REASONING_LOW -> uiText(stringResource(R.string.reasoning_low))
    AppSettings.REASONING_MEDIUM -> uiText(stringResource(R.string.reasoning_medium))
    AppSettings.REASONING_HIGH -> uiText(stringResource(R.string.reasoning_high))
    AppSettings.REASONING_ULTRA -> uiText(stringResource(R.string.reasoning_ultra))
    else -> uiText(stringResource(R.string.reasoning_auto))
}

@Composable
internal fun SheetBackTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ActionSheetTile(icon: ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(108.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun ActionSheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
internal fun ActionSheetSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val alpha = if (enabled) 1f else 0.48f
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = KimiMuted.copy(alpha = alpha), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun DropdownSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        CapsuleTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
            placeholder = placeholder,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
        )
    }
}

@Composable
internal fun PendingUploadStrip(files: List<UploadedFile>, onRemove: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        files.forEachIndexed { index, file ->
            if (file.mediaKind == "image") {
                MediaThumb(file.name, file.content.ifBlank { file.uri }, "image", onRemove = { onRemove(index) })
            } else if (file.mediaKind == "video" || file.mediaKind == "audio") {
                MediaPlaceholder(file.name, file.mediaKind, source = file.content.ifBlank { file.uri }, onRemove = { onRemove(index) })
            } else {
                KimiChip("${file.name} ×", onClick = { onRemove(index) })
            }
        }
    }
}

@Composable
internal fun MediaThumb(name: String, uri: String, kind: String, onRemove: (() -> Unit)? = null) {
    val context = LocalContext.current
    var previewOpen by remember { mutableStateOf(false) }
    val bitmap = remember(uri) {
        runCatching {
            decodeMediaPayload(uri)?.let { decoded ->
                BitmapFactory.decodeByteArray(decoded.bytes, 0, decoded.bytes.size)
            } ?: context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    Box(
        Modifier
            .size(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { previewOpen = true },
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                kind,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable(enabled = kind == "video" || kind == "audio") { previewOpen = true },
                color = KimiMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (onRemove != null) {
            TextButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = uiText("移除"), modifier = Modifier.size(16.dp))
            }
        }
    }
    if (previewOpen) {
        FullscreenMediaPreviewDialog(
            title = name,
            source = uri,
            kind = kind,
            bitmap = bitmap,
            onDismiss = { previewOpen = false },
        )
    }
}

@Composable
internal fun MediaPlaceholder(name: String, kind: String, source: String = "", onRemove: (() -> Unit)? = null) {
    var previewOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(width = 118.dp, height = 78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = source.isNotBlank() && (kind == "video" || kind == "audio")) { previewOpen = true }
            .padding(8.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text(if (kind == "video") uiText("视频") else uiText("音频"), style = MaterialTheme.typography.labelMedium)
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = KimiMuted, style = MaterialTheme.typography.labelSmall)
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(28.dp), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Close, contentDescription = uiText("移除"), modifier = Modifier.size(16.dp))
            }
        }
    }
    if (previewOpen) {
        FullscreenMediaPreviewDialog(
            title = name,
            source = source,
            kind = kind,
            onDismiss = { previewOpen = false },
        )
    }
}

internal fun isInternalProcessMessage(message: ChatRecord): Boolean {
    return message.role == "tool" || (message.role == "assistant" && message.content.isBlank())
}

internal data class ChatRenderItem(
    val key: String,
    val message: ChatRecord? = null,
    val process: List<ChatRecord> = emptyList(),
)

internal fun chatRenderItems(messages: List<ChatRecord>): List<ChatRenderItem> {
    val result = mutableListOf<ChatRenderItem>()
    val processBuffer = mutableListOf<ChatRecord>()
    fun flushProcess() {
        if (processBuffer.isNotEmpty()) {
            val group = processBuffer.toList()
            result += ChatRenderItem("process-${group.first().id}", process = group)
            processBuffer.clear()
        }
    }
    messages.forEach { message ->
        if (isInternalProcessMessage(message)) {
            processBuffer += message
        } else {
            if (message.role == "assistant") {
                if (message.thinking.isNotBlank()) {
                    processBuffer += message.copy(id = -message.id, content = "")
                }
                flushProcess()
                result += ChatRenderItem("message-${message.id}", message = message.copy(thinking = ""))
            } else {
                if (message.role == "user") flushProcess()
                result += ChatRenderItem("message-${message.id}", message = message)
            }
        }
    }
    flushProcess()
    return result
}

@Composable
internal fun AgentProcessSummary(
    messages: List<ChatRecord>,
    selectionResetKey: Int,
    active: Boolean = false,
) {
    var expanded by rememberSaveable(messages.firstOrNull()?.id ?: 0L) { mutableStateOf(false) }
    val toolCount = messages.count { it.role == "tool" }
    val thinkingCount = messages.count { it.thinking.isNotBlank() || it.role == "assistant" }
    val processKey = messages.firstOrNull()?.id ?: 0L
    val fallbackNow = remember(processKey) { System.currentTimeMillis() }
    var wasActive by rememberSaveable(processKey) { mutableStateOf(false) }
    var completedAt by rememberSaveable(processKey) { mutableStateOf<Long?>(null) }
    LaunchedEffect(active, processKey) {
        if (active) {
            wasActive = true
            completedAt = null
        } else if (wasActive && completedAt == null) {
            completedAt = System.currentTimeMillis()
        }
    }
    val startedAt = messages.minOfOrNull { it.createdAt } ?: fallbackNow
    val finishedAt = completedAt ?: messages.maxOfOrNull { it.createdAt } ?: fallbackNow
    val collapsedText = if (expanded) {
        uiText("过程记录已展开")
    } else {
        uiText("过程记录已收起 · thinking $thinkingCount / 工具 $toolCount")
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProcessDurationHeader(
                startedAt = startedAt,
                finishedAt = finishedAt,
                active = active,
            )
            CollapsedStatusLine(
                text = collapsedText,
                expanded = expanded,
                onClick = { expanded = !expanded },
            )
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    messages.forEach { MessageCard(it, selectionResetKey, inProcessRecord = true) }
                }
            }
        }
    }
}

@Composable
internal fun ProcessDurationHeader(
    startedAt: Long,
    finishedAt: Long?,
    active: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (active) uiText("任务处理中 · ") else uiText("任务耗时 · "),
            color = KimiMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        ProcessDurationText(
            startedAt = startedAt,
            finishedAt = finishedAt,
            active = active,
        )
    }
}

@Composable
internal fun ProcessDurationText(
    startedAt: Long,
    finishedAt: Long?,
    active: Boolean,
) {
    var now by remember(startedAt, finishedAt, active) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt, finishedAt, active) {
        if (active) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }
    val endAt = if (active) now else (finishedAt ?: now)
    Text(
        text = formatProcessDuration((endAt - startedAt).coerceAtLeast(0L)),
        color = KimiMuted,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
internal fun ToolApprovalDialog(
    pending: PendingToolApproval,
    onApprove: (rememberConversation: Boolean) -> Unit,
    onReject: (feedback: String) -> Unit,
) {
    var feedback by rememberSaveable(pending.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(uiText("确认工具调用")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pending.request.summary, style = MaterialTheme.typography.titleSmall)
                Text(pending.request.risk, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                SelectionContainer {
                    Text(
                        pending.request.arguments,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text(uiText("拒绝时给 AI 的修改要求")) },
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onApprove(true) }) { Text(uiText("本会话无需确认")) }
                Button(onClick = { onApprove(false) }) { Text(uiText("同意")) }
            }
        },
        dismissButton = {
            TextButton(onClick = { onReject(feedback) }) { Text(uiText("不同意")) }
        },
    )
}

@Composable
internal fun TodoProgressPanel(settings: AppSettings, conversationId: Long, items: List<TodoItem>) {
    if (items.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    val signature = remember(items) { items.joinToString("|") { "${it.id}:${it.status}:${it.text}:${it.note}" } }
    var hiddenSignature by remember(conversationId) { mutableStateOf(settings.hiddenTodoSignature(conversationId)) }
    var dragX by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val animatedDragX by animateFloatAsState(targetValue = dragX, label = "todo-panel-drag")
    val panelDragX = if (isDragging) dragX else animatedDragX
    val completed = items.count { it.status == "completed" }
    AnimatedVisibility(visible = hiddenSignature != signature, enter = fadeIn(), exit = fadeOut()) {
        Card(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = panelDragX.coerceAtMost(0f)
                    alpha = 1f - ((-translationX) / 420f).coerceIn(0f, 0.45f)
                }
                .pointerInput(signature) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            if (dragX < -120f) {
                                hiddenSignature = signature
                                settings.setHiddenTodoSignature(conversationId, signature)
                            }
                            dragX = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragX = 0f
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragX = (dragX + dragAmount.x).coerceIn(-420f, 0f)
                    }
                },
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TODO $completed/${items.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) uiText("收纳") else uiText("展开"))
                    }
                }
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items.forEach { item ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(todoStatusMark(item.status), color = todoStatusColor(item.status), style = MaterialTheme.typography.bodyMedium)
                                Column(Modifier.weight(1f)) {
                                    Text(item.text, style = MaterialTheme.typography.bodyMedium)
                                    if (item.note.isNotBlank()) {
                                        Text(item.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun todoStatusMark(status: String): String = when (status) {
    "running" -> "..."
    "completed" -> "✓"
    "blocked" -> "!"
    else -> "○"
}

@Composable
internal fun todoStatusColor(status: String): Color = when (status) {
    "running" -> MaterialTheme.colorScheme.primary
    "completed" -> Color(0xFF188038)
    "blocked" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal data class ConversationFileChange(
    val messageId: Long,
    val index: Int,
    val change: FileChangeView,
) {
    val key: String = "$messageId:$index:${change.path}"
}

@Composable
internal fun ConversationChangesPanel(settings: AppSettings, conversationId: Long, messages: List<ChatRecord>) {
    val events = remember(messages) {
        messages.flatMap { message ->
            parseFileChanges(message.content).mapIndexed { index, change ->
                ConversationFileChange(message.id, index, change)
            }
        }.takeLast(20)
      }
      if (events.isEmpty()) return
      val signature = remember(events) { events.joinToString("|") { it.key } }
      var hiddenSignature by remember(conversationId) { mutableStateOf(settings.hiddenFileChangesSignature(conversationId)) }
      var dragX by remember { mutableStateOf(0f) }
      var isDragging by remember { mutableStateOf(false) }
      val animatedDragX by animateFloatAsState(targetValue = dragX, label = "changes-panel-drag")
      val panelDragX = if (isDragging) dragX else animatedDragX

      var expanded by rememberSaveable { mutableStateOf(true) }
    var openedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val totalAdded = events.sumOf { it.change.added }
    val totalRemoved = events.sumOf { it.change.removed }

      AnimatedVisibility(visible = hiddenSignature != signature, enter = fadeIn(), exit = fadeOut()) {
      Card(
          Modifier
              .fillMaxWidth()
              .graphicsLayer {
                  translationX = panelDragX.coerceAtMost(0f)
                  alpha = 1f - ((-translationX) / 420f).coerceIn(0f, 0.45f)
              }
              .pointerInput(signature) {
                  detectDragGestures(
                      onDragStart = { isDragging = true },
                      onDragEnd = {
                          isDragging = false
                          if (dragX < -120f) {
                              hiddenSignature = signature
                              settings.setHiddenFileChangesSignature(conversationId, signature)
                          }
                          dragX = 0f
                      },
                      onDragCancel = {
                          isDragging = false
                          dragX = 0f
                      },
                  ) { change, dragAmount ->
                      change.consume()
                      dragX = (dragX + dragAmount.x).coerceIn(-420f, 0f)
                  }
              },
      ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("文件变更 ${events.size}"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("+$totalAdded", color = Color(0xFF188038), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Text("-$totalRemoved", color = Color(0xFFD93025), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) uiText("收纳") else uiText("展开"))
                }
            }
            AnimatedVisibility(expanded) {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(events.asReversed(), key = { it.key }) { event ->
                        val change = event.change
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openedKey = if (openedKey == event.key) null else event.key
                                }
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(fileNameForDisplay(change.path), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(change.path, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                }
                                Text("+${change.added}", color = Color(0xFF188038), style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(8.dp))
                                Text("-${change.removed}", color = Color(0xFFD93025), style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                if (openedKey == event.key) uiText("收起变更详情") else uiText("点击审视变更前后代码"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            AnimatedVisibility(openedKey == event.key) {
                                FileChangeDetail(change)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun ModelToolbar(controller: ChatController) {
    var profileExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val profiles = controller.profiles.toList()
    val profile = profiles.firstOrNull { it.id == controller.activeProfileId.value } ?: profiles.firstOrNull()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(0.9f)) {
            OutlinedButton(onClick = { profileExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(profile?.name ?: uiText("平台"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                profiles.forEach {
                    DropdownMenuItem(text = { Text(it.name) }, onClick = {
                        profileExpanded = false
                        controller.selectProfile(it.id)
                    })
                }
            }
        }
        Box(Modifier.weight(1.1f)) {
            OutlinedButton(onClick = { modelExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(controller.activeModel.value.ifBlank { uiText("模型") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                profile?.savedModels.orEmpty().forEach { model ->
                    DropdownMenuItem(text = { Text(model) }, onClick = {
                        modelExpanded = false
                        controller.selectModel(model)
                    })
                }
            }
        }
    }
}


internal fun formatTokensPerSecond(value: Double): String {
    if (value <= 0.0 || value.isNaN() || value.isInfinite()) return ""
    val text = if (value >= 10.0) "%.0f".format(Locale.US, value) else "%.1f".format(Locale.US, value)
    return "$text tok/s"
}
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MessageCard(
    message: ChatRecord,
    selectionResetKey: Int = 0,
    inProcessRecord: Boolean = false,
    streamingAnimationMode: String = AppSettings.STREAMING_ANIMATION_TYPEWRITER,
    isStreaming: Boolean = false,
    onEditAndRegenerate: ((Long, String) -> Unit)? = null,
) {
    val visibleContent = displayMessageContent(message)
    val mediaPreviews = remember(message.content) { uploadedMediaPreviews(message.content) }
    val filePreviews = remember(message.content) { uploadedFilePreviews(message.content) }
    val workspaceReferences = remember(message.content) { workspaceReferencePreviews(message.content) }
    val container = when (message.role) {
        "user" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        "tool" -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        else -> Color.Transparent
    }
    val contentColor = when (message.role) {
        "user" -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val clipboard = LocalClipboardManager.current
    var showThinking by rememberSaveable(message.id) { mutableStateOf(false) }
    var showToolResult by rememberSaveable(message.id) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var selectable by rememberSaveable(message.id) { mutableStateOf(false) }
    var editDialogOpen by rememberSaveable(message.id) { mutableStateOf(false) }
    var editText by rememberSaveable(message.id) { mutableStateOf(message.content) }
    val isUser = message.role == "user"
    val shouldRenderBubble = !isUser ||
        visibleContent.isNotBlank() ||
        message.thinking.isNotBlank()
    if (editDialogOpen) {
        AlertDialog(
            onDismissRequest = { editDialogOpen = false },
            title = { Text(uiText("编辑并重新生成")) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    minLines = 5,
                    maxLines = 12,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editDialogOpen = false
                        onEditAndRegenerate?.invoke(message.id, editText)
                    },
                ) {
                    Text(uiText("重新生成"))
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialogOpen = false }) {
                    Text(uiText("取消"))
                }
            },
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isUser && workspaceReferences.isNotEmpty()) {
                WorkspaceReferenceCardColumn(workspaceReferences)
            }
            if (isUser && mediaPreviews.isNotEmpty()) {
                UploadedMediaGrid(mediaPreviews)
            }
            if (isUser && filePreviews.isNotEmpty()) {
                UploadedFileCardColumn(filePreviews)
            }
            if (shouldRenderBubble) {
                Box {
                    val cardModifier = if (isUser) {
                        Modifier
                            .widthIn(max = 320.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    editText = message.content
                                    menuExpanded = true
                                },
                            )
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = container),
                        shape = if (isUser) RoundedCornerShape(22.dp) else RoundedCornerShape(18.dp),
                        border = if (message.role == "assistant") null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                        modifier = cardModifier,
                    ) {
                        val compactToolResult = inProcessRecord && message.role == "tool"
                        Column(
                            Modifier.padding(
                                horizontal = when {
                                    isUser -> 16.dp
                                    compactToolResult -> 2.dp
                                    else -> 6.dp
                                },
                                vertical = when {
                                    isUser -> 9.dp
                                    compactToolResult -> 2.dp
                                    else -> 6.dp
                                },
                            ),
                            verticalArrangement = Arrangement.spacedBy(if (compactToolResult) 2.dp else 8.dp),
                        ) {
                            if (!isUser && message.role != "assistant" && !compactToolResult) {
                                Text(uiText("工具结果"), color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                            }
                            if (message.thinking.isNotBlank()) {
                                CollapsedStatusLine(
                                    text = if (showThinking) uiText("思考详情已展开") else if (message.content.isBlank()) "thinking..." else uiText("思考完毕"),
                                    expanded = showThinking,
                                    onClick = { showThinking = !showThinking },
                                )
                                AnimatedVisibility(showThinking) {
                                    key(selectionResetKey) {
                                        SelectionContainer {
                                            Text(
                                                message.thinking,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = contentColor,
                                            )
                                        }
                                    }
                                }
                            }
                            if (message.role == "tool") {
                                ToolResultContent(
                                    content = message.content,
                                    toolName = message.toolName,
                                    toolInput = message.toolInput,
                                    expanded = showToolResult,
                                    onToggle = { showToolResult = !showToolResult },
                                    compact = compactToolResult,
                                )
                            } else {
                                if (visibleContent.isNotBlank()) {
                                    key(selectionResetKey) {
                                        if (isUser) {
                                            if (selectable) {
                                                SelectionContainer {
                                                    Text(visibleContent, color = contentColor, style = MaterialTheme.typography.bodyLarge)
                                                }
                                            } else {
                                                Text(visibleContent, color = contentColor, style = MaterialTheme.typography.bodyLarge)
                                            }
                                        } else {
                                            SelectionContainer {
                                                StreamingAssistantContent(visibleContent, isStreaming, streamingAnimationMode)
                                            }
                                        }
                                    }
                                    if (message.role == "assistant" && !inProcessRecord) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                formatTokensPerSecond(message.tokensPerSecond),
                                                color = KimiMuted,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Spacer(Modifier.weight(1f))
                                            IconButton(
                                                onClick = { clipboard.setText(AnnotatedString(message.content)) },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = uiText("复制"),
                                                    tint = KimiMuted,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                } else if (message.role == "assistant" && !inProcessRecord) {
                                    Text(uiText("正在组织输出..."), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (isUser) DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(uiText("复制")) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                clipboard.setText(AnnotatedString(message.content))
                                menuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(uiText("选择文本")) },
                            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                            onClick = {
                                selectable = true
                                menuExpanded = false
                            },
                        )
                        if (isUser && onEditAndRegenerate != null) {
                            DropdownMenuItem(
                                text = { Text(uiText("修改并重新生成")) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    editText = message.content
                                    menuExpanded = false
                                    editDialogOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(uiText("重新生成")) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEditAndRegenerate(message.id, message.content)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StreamingAssistantContent(content: String, isStreaming: Boolean, mode: String) {
    val normalizedMode = AppSettings.normalizeStreamingAnimationMode(mode)
    val latestContent by rememberUpdatedState(content)
    val fadeMode = normalizedMode == AppSettings.STREAMING_ANIMATION_FADE
    var renderedContent by remember {
        mutableStateOf(if (isStreaming && !fadeMode) "" else content)
    }
    val opaquePosition = remember {
        Animatable(if (isStreaming && fadeMode) 0f else content.length.toFloat())
    }

    LaunchedEffect(content, isStreaming, normalizedMode) {
        if (!fadeMode) return@LaunchedEffect

        val targetPosition = content.length.toFloat()
        val contentWasReplaced = !content.startsWith(renderedContent)
        renderedContent = content
        when {
            contentWasReplaced -> opaquePosition.snapTo((targetPosition - 72f).coerceAtLeast(0f))
            opaquePosition.value > targetPosition -> opaquePosition.snapTo(targetPosition)
        }
        // Render every received character immediately; only the newest tail fades in.
        opaquePosition.animateTo(
            targetValue = targetPosition,
            animationSpec = tween(durationMillis = 500),
        )
    }

    LaunchedEffect(isStreaming, normalizedMode) {
        if (fadeMode) return@LaunchedEffect
        if (!isStreaming) {
            renderedContent = latestContent
            return@LaunchedEffect
        }

        while (true) {
            val target = latestContent
            if (!target.startsWith(renderedContent)) {
                renderedContent = ""
            }
            val pending = target.length - renderedContent.length
            if (pending > 0) {
                renderedContent = target.take(renderedContent.length + 1)
            }
            delay(12L)
        }
    }
    val streamingFade = if (
        fadeMode && opaquePosition.value < renderedContent.length
    ) {
        StreamingTextFade(renderedContent.length, opaquePosition.value)
    } else {
        null
    }
    RichMarkdownContent(
        renderedContent,
        streamingFade = streamingFade,
    )
}
internal fun formatProcessDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> uiText("${hours}小时${minutes}分${seconds}秒")
        minutes > 0L -> uiText("${minutes}分${seconds}秒")
        else -> uiText("${seconds}秒")
    }
}

@Composable
internal fun CollapsedStatusLine(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KimiPillShape)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), color = KimiMuted, style = MaterialTheme.typography.labelMedium)
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) uiText("收起") else uiText("展开"),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun ContinueInterruptedRow(onContinue: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)))
        KimiChip(uiText("继续对话"), onClick = onContinue)
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)))
    }
}

internal data class UploadedMediaPreview(
    val name: String,
    val kind: String,
    val mimeType: String,
    val uri: String,
)

internal data class UploadedFilePreview(
    val name: String,
    val sizeBytes: Long?,
    val type: String,
)

@Composable
internal fun UploadedFileCardColumn(files: List<UploadedFilePreview>) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        files.forEach { file ->
            UploadedFileCard(file)
        }
    }
}

@Composable
internal fun UploadedFileCard(file: UploadedFilePreview) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(Modifier.widthIn(min = 150.dp, max = 220.dp)) {
                Text(
                    file.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf(file.type, formatUploadedFileSize(file.sizeBytes))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                Text(
                    meta,
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun UploadedMediaGrid(media: List<UploadedMediaPreview>) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        media.forEach { item ->
            if (item.kind == "image") {
                MediaThumb(item.name, item.uri, item.kind)
            } else {
                MediaPlaceholder(item.name, item.kind, source = item.uri)
            }
        }
    }
}

internal fun uploadedMediaPreviews(content: String): List<UploadedMediaPreview> {
    val markerPreviews = uploadedAttachmentPayloads(content)
        .filter { payload -> payload.optString("kind") in setOf("image", "video", "audio") }
        .map { payload ->
            UploadedMediaPreview(
                name = payload.optString("name").ifBlank { uiText("未命名文件") },
                kind = payload.optString("kind"),
                mimeType = payload.optString("mime_type"),
                uri = payload.optString("data_url").ifBlank { payload.optString("uri") },
            )
        }
    val legacyRegex = Regex("用户上传媒体：([^\\n]+)\\n类型：([^\\n]+)\\nMIME：([^\\n]*)\\n(?:DATA_URL：([^\\n]*)\\n)?URI：([^\\n]*)", RegexOption.MULTILINE)
    val legacyPreviews = legacyRegex.findAll(content).map {
        UploadedMediaPreview(
            name = it.groupValues[1].trim(),
            kind = it.groupValues[2].trim(),
            mimeType = it.groupValues[3].trim(),
            uri = it.groupValues[4].trim().ifBlank { it.groupValues[5].trim() },
        )
    }.toList()
    return markerPreviews + legacyPreviews
}

internal fun uploadedFilePreviews(content: String): List<UploadedFilePreview> {
    val markerPreviews = uploadedAttachmentPayloads(content)
        .filter { payload -> payload.optString("kind").ifBlank { "text" } == "text" }
        .map { payload ->
            val name = payload.optString("name").ifBlank { uiText("未命名文件") }
            UploadedFilePreview(
                name = name,
                sizeBytes = payload.optLong("size", -1L).takeIf { it >= 0L },
                type = uploadedFileTypeLabel(name),
            )
        }
    val legacyRegex = Regex("用户上传文件：([^\\n]+)\\n大小：(\\d+) bytes", RegexOption.MULTILINE)
    val legacyPreviews = legacyRegex.findAll(content).map {
        val name = it.groupValues[1].trim().ifBlank { uiText("未命名文件") }
        UploadedFilePreview(
            name = name,
            sizeBytes = it.groupValues[2].toLongOrNull(),
            type = uploadedFileTypeLabel(name),
        )
    }.toList()
    return markerPreviews + legacyPreviews
}

internal data class WorkspaceReferencePreview(val name: String, val path: String)

internal fun workspaceReferencePreviews(content: String): List<WorkspaceReferencePreview> {
    val regex = Regex("<lyra_workspace_refs_v1>([\\s\\S]*?)</lyra_workspace_refs_v1>")
    return regex.findAll(content).flatMap { match ->
        val files = runCatching { JSONObject(match.groupValues[1]).optJSONArray("files") }.getOrNull() ?: JSONArray()
        buildList {
            for (index in 0 until files.length()) {
                val item = files.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank()) add(WorkspaceReferencePreview(item.optString("name").ifBlank { fileNameForDisplay(path) }, path))
            }
        }.asSequence()
    }.toList()
}

@Composable
internal fun WorkspaceReferenceCardColumn(files: List<WorkspaceReferencePreview>) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEach { file ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            ) {
                Row(
                    Modifier.widthIn(max = 320.dp).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        Text(file.path, maxLines = 1, overflow = TextOverflow.Ellipsis, color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

internal fun stripWorkspaceReferenceBlocks(content: String): String = content.replace(
    Regex("\\n*<lyra_workspace_refs_v1>[\\s\\S]*?</lyra_workspace_refs_v1>\\n*"),
    "\n",
).trim()
internal fun uploadedAttachmentPayloads(content: String): List<JSONObject> {
    val regex = Regex("<lyra_attachment_v1>([\\s\\S]*?)</lyra_attachment_v1>")
    return regex.findAll(content).mapNotNull { match ->
        runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
    }.toList()
}

internal fun displayMessageContent(message: ChatRecord): String {
    if (message.role != "user") return message.content
    return stripWorkspaceReferenceBlocks(stripUploadedFileBlocks(stripUploadedMediaBlocks(stripUploadedAttachmentBlocks(message.content)))).trim()
}

internal fun stripUploadedAttachmentBlocks(content: String): String {
    return content.replace(
        Regex("\\n*<lyra_attachment_v1>[\\s\\S]*?</lyra_attachment_v1>\\n*"),
        "\n",
    ).trim()
}

internal fun stripUploadedMediaBlocks(content: String): String {
    return content.replace(
        Regex("\\n*用户上传媒体：[^\\n]+\\n类型：[^\\n]+\\nMIME：[^\\n]*\\n(?:DATA_URL：[^\\n]*\\n)?URI：[^\\n]*\\n大小：[^\\n]*(\\n)?"),
        "\n",
    ).trim()
}
@Composable
internal fun ToolResultContent(
    content: String,
    toolName: String = "",
    toolInput: String = "{}",
    expanded: Boolean,
    onToggle: () -> Unit,
    compact: Boolean = false,
) {
    val summary = if (toolName.isNotBlank()) {
        uiText("调用工具") + " $toolName · " + uiText("查看详情")
    } else {
        uiText("工具调用") + " · " + uiText("查看详情")
    }
    ToolCallSummaryButton(text = summary, compact = compact, onClick = onToggle)
    if (expanded) {
        ToolCallDetailPage(
            toolName = toolName,
            toolInput = toolInput.ifBlank { "{}" },
            toolOutput = content.ifBlank { uiText("空结果") },
            onClose = onToggle,
        )
    }
}

@Composable
private fun ToolCallSummaryButton(text: String, compact: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KimiPillShape)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f))
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 5.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Build,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(if (compact) 15.dp else 19.dp),
        )
        Spacer(Modifier.width(if (compact) 6.dp else 9.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = uiText("查看详情"),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(if (compact) 17.dp else 24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolCallDetailPage(
    toolName: String,
    toolInput: String,
    toolOutput: String,
    onClose: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(uiText("工具调用详情"))
                            Text(
                                toolName.ifBlank { uiText("未知工具") },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = uiText("返回"), tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    ToolJsonSection(
                        title = uiText("工具输入"),
                        content = toolInput,
                        onCopy = { clipboard.setText(AnnotatedString(toolInput)) },
                    )
                }
                item {
                    ToolJsonSection(
                        title = uiText("调用结果"),
                        content = toolOutput,
                        onCopy = { clipboard.setText(AnnotatedString(toolOutput)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolJsonSection(title: String, content: String, onCopy: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val highlightedJson = remember(content, colorScheme) {
        highlightedJsonForDisplay(
            value = content,
            keyColor = colorScheme.primary,
            stringColor = colorScheme.tertiary,
            numberColor = colorScheme.secondary,
            literalColor = colorScheme.error,
            punctuationColor = colorScheme.onSurfaceVariant,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("json", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = uiText("复制"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                KimiDivider()
                SelectionContainer {
                    Text(
                        highlightedJson,
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

internal fun prettyJsonForDisplay(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return "{}"
    return runCatching { JSONObject(trimmed).toString(2) }
        .recoverCatching { JSONArray(trimmed).toString(2) }
        .getOrDefault(value)
}

private val jsonSyntaxTokenRegex = Regex(
    """\"(?:\\.|[^\"\\])*\"|(?<![\w.])-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?(?![\w.])|\b(?:true|false|null)\b|[{}\[\],:]""",
)

internal fun highlightedJsonForDisplay(
    value: String,
    keyColor: Color,
    stringColor: Color,
    numberColor: Color,
    literalColor: Color,
    punctuationColor: Color,
): AnnotatedString {
    val formatted = prettyJsonForDisplay(value)
    return buildAnnotatedString {
        var cursor = 0
        jsonSyntaxTokenRegex.findAll(formatted).forEach { match ->
            if (cursor < match.range.first) append(formatted.substring(cursor, match.range.first))
            val token = match.value
            val color = when {
                token.startsWith('"') -> {
                    var next = match.range.last + 1
                    while (next < formatted.length && formatted[next].isWhitespace()) next++
                    if (formatted.getOrNull(next) == ':') keyColor else stringColor
                }
                token == "true" || token == "false" || token == "null" -> literalColor
                token.firstOrNull()?.let { it == '-' || it.isDigit() } == true -> numberColor
                else -> punctuationColor
            }
            withStyle(SpanStyle(color = color)) { append(token) }
            cursor = match.range.last + 1
        }
        if (cursor < formatted.length) append(formatted.substring(cursor))
    }
}

internal data class FileChangeView(
    val path: String,
    val added: Int,
    val removed: Int,
    val diff: String,
    val before: String,
    val after: String,
)

internal fun fileNameForDisplay(path: String): String {
    return path.trim().replace('\\', '/').substringAfterLast('/').ifBlank { path.ifBlank { uiText("未命名文件") } }
}

internal fun stripUploadedFileBlocks(content: String): String {
    return content
        .replace(
            Regex("\\n*用户上传文件：[^\\n]+\\n大小：\\d+ bytes\\n\\n```text\\n[\\s\\S]*?\\n```\\n?"),
            "\n",
        )
        .replace(Regex("\\n*用户上传文件：[^\\n]+\\n大小：\\d+ bytes\\n?"), "\n")
        .trim()
}

internal fun uploadedFileTypeLabel(name: String): String {
    val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (ext) {
        "" -> uiText("文件")
        "txt", "md", "json", "xml", "csv", "log" -> ext.uppercase() + uiText(" 文本")
        "kt", "java", "py", "js", "ts", "html", "css", "go", "rs", "cpp", "c", "h" -> ext.uppercase() + uiText(" 代码")
        "zip", "7z", "rar", "tar", "gz" -> ext.uppercase() + uiText(" 压缩包")
        "pdf" -> uiText("PDF 文档")
        "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> ext.uppercase() + uiText(" 文档")
        else -> ext.uppercase() + uiText(" 文件")
    }
}

internal fun formatUploadedFileSize(bytes: Long?): String {
    val value = bytes ?: return ""
    if (value < 1024L) return "${value}B"
    val units = listOf("KB", "MB", "GB", "TB")
    var size = value.toDouble()
    var unitIndex = -1
    do {
        size /= 1024.0
        unitIndex++
    } while (size >= 1024.0 && unitIndex < units.lastIndex)
    val text = if (size >= 10.0) "%.0f".format(size) else "%.1f".format(size)
    return "$text${units[unitIndex]}"
}

@Composable
internal fun FileChangeDetail(change: FileChangeView) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(uiText("差异"), style = MaterialTheme.typography.labelMedium)
        DiffView(change.diff.ifBlank { uiText("(无行级差异)") })
        CodeSnapshot(
            title = uiText("变更前"),
            content = change.before,
            color = Color(0xFFD93025),
            modifier = Modifier.fillMaxWidth(),
        )
        CodeSnapshot(
            title = uiText("变更后"),
            content = change.after,
            color = Color(0xFF188038),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun CodeSnapshot(title: String, content: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = color, style = MaterialTheme.typography.labelMedium)
        SelectionContainer {
            Text(
                content.ifBlank { uiText("(空)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
internal fun DiffView(diff: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        diff.lineSequence().forEach { line ->
            val color = when {
                line.startsWith("+ ") -> Color(0xFF188038)
                line.startsWith("- ") -> Color(0xFFD93025)
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                line,
                color = color,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

internal fun parseFileChanges(content: String): List<FileChangeView> {
    parseJsonFileChanges(content).takeIf { it.isNotEmpty() }?.let { return it }
    val regex = Regex("LYRA_FILE_CHANGE_BEGIN\\n(.*?)\\nLYRA_FILE_CHANGE_END", RegexOption.DOT_MATCHES_ALL)
    return regex.findAll(content).mapNotNull { match ->
        val body = match.groupValues[1]
        val path = body.lineSequence().firstOrNull { it.startsWith("path: ") }?.removePrefix("path: ")?.trim().orEmpty()
        val added = body.lineSequence().firstOrNull { it.startsWith("added: ") }?.removePrefix("added: ")?.trim()?.toIntOrNull() ?: 0
        val removed = body.lineSequence().firstOrNull { it.startsWith("removed: ") }?.removePrefix("removed: ")?.trim()?.toIntOrNull() ?: 0
        val diffAndSnapshots = body.substringAfter("diff:\n", "")
        val diff = diffAndSnapshots.substringBefore("\nLYRA_FILE_BEFORE_BEGIN", diffAndSnapshots).trimEnd()
        val before = extractMarkedSection(body, "LYRA_FILE_BEFORE_BEGIN", "LYRA_FILE_BEFORE_END")
        val after = extractMarkedSection(body, "LYRA_FILE_AFTER_BEGIN", "LYRA_FILE_AFTER_END")
        if (path.isBlank()) null else FileChangeView(path, added, removed, diff, before, after)
    }.toList()
}

internal fun parseJsonFileChanges(content: String): List<FileChangeView> {
    val root = runCatching { JSONObject(content) }.getOrNull() ?: return emptyList()
    val changes = root.optJSONArray("file_changes") ?: JSONArray()
    return buildList {
        for (index in 0 until changes.length()) {
            val change = changes.optJSONObject(index) ?: continue
            val path = change.optString("path")
            if (path.isBlank()) continue
            add(
                FileChangeView(
                    path = path,
                    added = change.optInt("added", 0),
                    removed = change.optInt("removed", 0),
                    diff = change.optString("diff"),
                    before = change.optString("before"),
                    after = change.optString("after"),
                ),
            )
        }
    }
}

internal fun extractMarkedSection(text: String, start: String, end: String): String {
    val afterStart = text.substringAfter("$start\n", missingDelimiterValue = "")
    if (afterStart.isBlank()) return ""
    return afterStart.substringBefore("\n$end", afterStart).trimEnd()
}

internal sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class Numbered(val number: String, val text: String) : MarkdownBlock()
    data class ListItems(val ordered: Boolean, val items: List<MarkdownListItem>) : MarkdownBlock()
    data class Table(val rows: List<List<String>>) : MarkdownBlock()
    data class Math(val formula: String, val display: Boolean) : MarkdownBlock()
    object Spacer : MarkdownBlock()
}







