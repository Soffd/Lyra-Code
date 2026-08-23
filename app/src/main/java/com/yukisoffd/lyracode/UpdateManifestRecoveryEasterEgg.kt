package com.yukisoffd.lyracode

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun UpdateManifestRecoveryEasterEgg(
    onExit: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as DialogWindowProvider).window
        BackHandler(enabled = true) {}
        DisposableEffect(dialogWindow) {
            dialogWindow.configureEasterEggWindow()
            onDispose { dialogWindow.restoreAfterEasterEgg() }
        }

        var recoveryVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(650L)
            recoveryVisible = true
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (recoveryVisible) {
                RecoveryBreakoutScreen(onExit = onExit)
            }
        }
    }
}

@Composable
private fun RecoveryBreakoutScreen(
    onExit: () -> Unit,
) {
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val exitLabel = stringResource(R.string.easter_egg_recovery_exit)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val densityValue = density.density
        val recoverySources = remember {
            listOf(
                RecoveryTextSource("Android Recovery", RECOVERY_AMBER, bold = true),
                RecoveryTextSource("${Build.BRAND}/${Build.DEVICE}/${Build.PRODUCT}", RECOVERY_AMBER),
                RecoveryTextSource("${Build.VERSION.RELEASE}/${Build.DISPLAY}", RECOVERY_AMBER),
                RecoveryTextSource("user/release-keys", RECOVERY_AMBER),
                RecoveryTextSource(RECOVERY_INSTRUCTION, RECOVERY_AMBER),
                RecoveryTextSource(RECOVERY_CORRUPTION, RECOVERY_RED, bold = true, extraTop = true),
                RecoveryTextSource(
                    RECOVERY_TRY_AGAIN,
                    android.graphics.Color.WHITE,
                    highlighted = true,
                    extraTop = true,
                    wholeLineBrick = true,
                ),
                RecoveryTextSource(RECOVERY_FACTORY_RESET, RECOVERY_BLUE, wholeLineBrick = true),
            )
        }
        val game = remember(widthPx, heightPx, densityValue, recoverySources) {
            RecoveryBreakoutGame(widthPx, heightPx, densityValue, recoverySources)
        }
        var revision by remember(game) { mutableIntStateOf(0) }
        var gameStarted by remember(game) { mutableStateOf(false) }
        var completed by remember(game) { mutableStateOf(game.completed) }
        val latestGameStarted by rememberUpdatedState(gameStarted)

        LaunchedEffect(game, gameStarted, isResumed) {
            if (!gameStarted || !isResumed || game.completed) return@LaunchedEffect
            var previousFrame = withFrameNanos { it }
            while (isActive && isResumed && !game.completed) {
                withFrameNanos { frameTime ->
                    val deltaSeconds = ((frameTime - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.034f)
                    previousFrame = frameTime
                    game.step(deltaSeconds)
                    revision++
                    completed = game.completed
                }
            }
        }

        val interactionModifier = if (!completed) {
            Modifier.pointerInput(game) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!latestGameStarted) {
                        game.start()
                        gameStarted = true
                    } else {
                        game.movePaddleTo(down.position.x)
                    }
                    revision++
                    down.consume()
                    drag(down.id) { change ->
                        game.movePaddleTo(change.position.x)
                        revision++
                        change.consume()
                    }
                }
            }
        } else {
            Modifier
        }

        Box(Modifier.fillMaxSize().then(interactionModifier)) {
            Canvas(Modifier.fillMaxSize()) {
                if (revision >= 0) game.draw(drawContext.canvas.nativeCanvas)
            }
            if (!gameStarted) {
                Text(
                    text = RECOVERY_TAP_TO_START,
                    color = Color(0xFF777777),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 42.dp),
                )
            }
        }

        if (completed) {
            Surface(
                onClick = onExit,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 5.dp,
                shadowElevation = 5.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                    Text(exitLabel, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

private data class RecoveryTextSource(
    val text: String,
    val color: Int,
    val bold: Boolean = false,
    val highlighted: Boolean = false,
    val extraTop: Boolean = false,
    val wholeLineBrick: Boolean = false,
)

private data class RecoveryBrick(
    val text: String,
    val color: Int,
    val bold: Boolean,
    val highlighted: Boolean,
    val bounds: FloatBounds,
    val baseline: Float,
    val drawX: Float,
    val wholeLineBrick: Boolean,
)

private class FallingRecoveryGlyph(
    val brick: RecoveryBrick,
    var drawX: Float = brick.drawX,
    var baseline: Float = brick.baseline,
    var velocityX: Float,
    var velocityY: Float,
    var rotation: Float = 0f,
    val angularVelocity: Float,
)

private data class FloatBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

private class RecoveryBreakoutGame(
    private val width: Float,
    private val height: Float,
    private val density: Float,
    sources: List<RecoveryTextSource>,
) {
    private val normalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = (18f * density).coerceAtMost(width / 20f)
    }
    private val boldTextPaint = Paint(normalTextPaint).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bricks = layoutBricks(sources).toMutableList()
    private val fallingGlyphs = mutableListOf<FallingRecoveryGlyph>()
    private val ballRadius = 7f * density
    private val paddleWidth = min(132f * density, width * 0.42f)
    private val paddleHeight = 11f * density
    private val paddleTargetY = height * 0.87f
    private val speed = max(245f * density, height * 0.34f)
    private val gravity = max(1_450f * density, height * 1.8f)
    private val paddlePhysics = FallingPaddlePhysics(
        initialPosition = -paddleHeight,
        targetPosition = paddleTargetY,
        gravity = gravity,
        minimumBounceVelocity = 80f * density,
    )

    var started: Boolean = false
        private set
    var completed: Boolean = bricks.isEmpty()
        private set

    private var running = false
    private var paddleCenterX = width / 2f
    private val paddleY: Float get() = paddlePhysics.position
    private var ballX = width / 2f
    private var ballY = -paddleHeight - ballRadius * 2f
    private var ballVelocityX = 0f
    private var ballVelocityY = 0f
    private var horizontalKickDirection = 1f

    fun start() {
        if (started || completed) return
        started = true
    }

    fun movePaddleTo(x: Float) {
        paddleCenterX = x.coerceIn(paddleWidth / 2f, width - paddleWidth / 2f)
    }

    fun step(deltaSeconds: Float) {
        if (!started || completed || deltaSeconds <= 0f) return
        updateFallingGlyphs(deltaSeconds)
        if (bricks.isEmpty()) {
            completed = fallingGlyphs.isEmpty()
            return
        }
        updateFallingPaddle(deltaSeconds)
        if (!running) {
            ballVelocityY += gravity * 0.92f * deltaSeconds
            ballY += ballVelocityY * deltaSeconds
            val paddle = paddleBounds()
            if (ballVelocityY > 0f && circleIntersects(ballX, ballY, ballRadius, paddle)) {
                ballY = paddle.top - ballRadius
                running = true
                aimAtNextBrick()
            } else if (ballY + ballRadius >= height) {
                // Even if the player moves the falling paddle away, the ball can never be lost.
                ballY = height - ballRadius
                running = true
                aimAtNextBrick()
            }
            return
        }

        val previousY = ballY
        ballX += ballVelocityX * deltaSeconds
        ballY += ballVelocityY * deltaSeconds

        if (ballX - ballRadius <= 0f) {
            ballX = ballRadius
            ballVelocityX = abs(ballVelocityX)
        } else if (ballX + ballRadius >= width) {
            ballX = width - ballRadius
            ballVelocityX = -abs(ballVelocityX)
        }
        if (ballY - ballRadius <= 0f) {
            ballY = ballRadius
            ballVelocityY = abs(ballVelocityY)
        }

        val paddle = paddleBounds()
        if (ballVelocityY > 0f && circleIntersects(ballX, ballY, ballRadius, paddle)) {
            ballY = paddle.top - ballRadius
            aimAtNextBrick()
        }

        val hitBricks = bricks.filter { circleIntersects(ballX, ballY, ballRadius * 1.35f, it.bounds) }
        if (hitBricks.isNotEmpty()) {
            val hitBrick = hitBricks.first()
            bricks.removeAll(hitBricks.toSet())
            hitBricks.forEach(::releaseFallingGlyph)
            val hitVertically = previousY + ballRadius <= hitBrick.bounds.top ||
                previousY - ballRadius >= hitBrick.bounds.bottom
            if (hitVertically) ballVelocityY = -ballVelocityY else ballVelocityX = -ballVelocityX
            if (bricks.isEmpty()) {
                completed = fallingGlyphs.isEmpty()
                return
            }
            aimAtNextBrick()
        }

        // There is deliberately no losing state: missing the paddle rebounds from the bottom.
        if (ballY + ballRadius >= height) {
            ballY = height - ballRadius
            aimAtNextBrick()
        }

        // Avoid a nearly horizontal trajectory that could take too long to reach the text.
        if (abs(ballVelocityY) < speed * 0.35f) {
            ballVelocityY = if (ballVelocityY < 0f) -speed * 0.7f else speed * 0.7f
            normalizeVelocity()
        }
        ensureHorizontalMovement()
    }

    fun draw(canvas: android.graphics.Canvas) {
        bricks.forEach { brick ->
            if (brick.highlighted) {
                shapePaint.color = RECOVERY_HIGHLIGHT
                shapePaint.style = Paint.Style.FILL
                canvas.drawRect(
                    brick.bounds.left,
                    brick.bounds.top,
                    brick.bounds.right,
                    brick.bounds.bottom,
                    shapePaint,
                )
            }
            val textPaint = if (brick.bold) boldTextPaint else normalTextPaint
            textPaint.color = brick.color
            canvas.drawText(brick.text, brick.drawX, brick.baseline, textPaint)
        }
        fallingGlyphs.forEach { glyph ->
            val textPaint = if (glyph.brick.bold) boldTextPaint else normalTextPaint
            textPaint.color = glyph.brick.color
            val centerX = glyph.drawX + textPaint.measureText(glyph.brick.text) / 2f
            val centerY = glyph.baseline - textPaint.textSize / 2f
            canvas.save()
            canvas.rotate(glyph.rotation, centerX, centerY)
            canvas.drawText(glyph.brick.text, glyph.drawX, glyph.baseline, textPaint)
            canvas.restore()
        }

        if (started) {
            shapePaint.color = android.graphics.Color.rgb(205, 205, 205)
            shapePaint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                paddleCenterX - paddleWidth / 2f,
                paddleY,
                paddleCenterX + paddleWidth / 2f,
                paddleY + paddleHeight,
                paddleHeight / 2f,
                paddleHeight / 2f,
                shapePaint,
            )
            shapePaint.color = android.graphics.Color.WHITE
            canvas.drawCircle(ballX, ballY, ballRadius, shapePaint)
        }
    }

    private fun layoutBricks(sources: List<RecoveryTextSource>): List<RecoveryBrick> {
        val margin = 14f * density
        val lineHeight = normalTextPaint.textSize * 1.28f
        var top = height * 0.13f
        return buildList {
            sources.forEach { source ->
                if (source.extraTop) top += lineHeight * 0.28f
                val paint = if (source.bold) boldTextPaint else normalTextPaint
                wrapText(source.text, paint, width - margin * 2f - 8f * density).forEach { line ->
                    val bottom = top + lineHeight
                    val baseline = top + lineHeight * 0.78f
                    val lineStart = margin + 4f * density
                    if (source.wholeLineBrick) {
                        add(
                            RecoveryBrick(
                                text = line,
                                color = source.color,
                                bold = source.bold,
                                highlighted = source.highlighted,
                                bounds = FloatBounds(margin, top, width - margin, bottom),
                                baseline = baseline,
                                drawX = lineStart,
                                wholeLineBrick = true,
                            ),
                        )
                    } else {
                        var cursorX = lineStart
                        line.forEach { character ->
                            val glyph = character.toString()
                            val glyphWidth = paint.measureText(glyph)
                            if (!character.isWhitespace()) {
                                add(
                                    RecoveryBrick(
                                        text = glyph,
                                        color = source.color,
                                        bold = source.bold,
                                        highlighted = false,
                                        bounds = FloatBounds(
                                            cursorX - 1.5f * density,
                                            top,
                                            cursorX + glyphWidth + 1.5f * density,
                                            bottom,
                                        ),
                                        baseline = baseline,
                                        drawX = cursorX,
                                        wholeLineBrick = false,
                                    ),
                                )
                            }
                            cursorX += glyphWidth
                        }
                    }
                    top = bottom + 1.5f * density
                }
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = start + 1
            var lastWhitespace = -1
            while (end <= text.length && paint.measureText(text, start, end) <= maxWidth) {
                if (text[end - 1].isWhitespace()) lastWhitespace = end - 1
                end++
            }
            val measuredEnd = (end - 1).coerceAtLeast(start + 1)
            val breakAt = if (measuredEnd < text.length && lastWhitespace >= start) lastWhitespace else measuredEnd
            lines += text.substring(start, breakAt).trim()
            start = if (breakAt == lastWhitespace) breakAt + 1 else breakAt
            while (start < text.length && text[start].isWhitespace()) start++
        }
        return lines.filter { it.isNotEmpty() }
    }

    private fun aimAtNextBrick() {
        val bricksAbove = bricks.filter { it.bounds.centerY < ballY - normalTextPaint.textSize * 0.45f }
        val target = (bricksAbove.ifEmpty { bricks }).minByOrNull {
            hypot(it.bounds.centerX - ballX, it.bounds.centerY - ballY)
        }?.bounds
        if (target == null) {
            completed = fallingGlyphs.isEmpty()
            return
        }
        val targetX = target.centerX
        val targetY = target.centerY
        val dx = targetX - ballX
        val dy = targetY - ballY
        val length = hypot(dx, dy).coerceAtLeast(1f)
        ballVelocityX = dx / length * speed
        ballVelocityY = dy / length * speed
        if (ballVelocityY >= -speed * 0.2f) {
            ballVelocityY = -speed * 0.72f
            normalizeVelocity()
        }
        ensureHorizontalMovement()
    }

    private fun releaseFallingGlyph(brick: RecoveryBrick) {
        if (brick.wholeLineBrick) return
        val characterBias = if (brick.text.firstOrNull()?.code?.rem(2) == 0) 1f else -1f
        fallingGlyphs += FallingRecoveryGlyph(
            brick = brick,
            velocityX = ballVelocityX * 0.16f + characterBias * 28f * density,
            velocityY = max(55f * density, abs(ballVelocityY) * 0.10f),
            angularVelocity = characterBias * (95f + abs(ballVelocityX) / speed * 80f),
        )
    }

    private fun updateFallingGlyphs(deltaSeconds: Float) {
        fallingGlyphs.forEach { glyph ->
            glyph.velocityY += gravity * deltaSeconds
            glyph.drawX += glyph.velocityX * deltaSeconds
            glyph.baseline += glyph.velocityY * deltaSeconds
            glyph.rotation += glyph.angularVelocity * deltaSeconds
        }
        fallingGlyphs.removeAll { it.baseline > height + normalTextPaint.textSize * 2f }
    }

    private fun ensureHorizontalMovement() {
        val stabilized = ensureBreakoutHorizontalVelocity(
            velocityX = ballVelocityX,
            velocityY = ballVelocityY,
            speed = speed,
            kickDirection = horizontalKickDirection,
        )
        ballVelocityX = stabilized.x
        ballVelocityY = stabilized.y
        horizontalKickDirection = stabilized.nextKickDirection
    }

    private fun updateFallingPaddle(deltaSeconds: Float) {
        paddlePhysics.step(deltaSeconds)
    }

    private fun paddleBounds() = FloatBounds(
        paddleCenterX - paddleWidth / 2f,
        paddleY,
        paddleCenterX + paddleWidth / 2f,
        paddleY + paddleHeight,
    )

    private fun normalizeVelocity() {
        val length = hypot(ballVelocityX, ballVelocityY).coerceAtLeast(1f)
        ballVelocityX = ballVelocityX / length * speed
        ballVelocityY = ballVelocityY / length * speed
    }

    private fun circleIntersects(x: Float, y: Float, radius: Float, bounds: FloatBounds): Boolean {
        val closestX = x.coerceIn(bounds.left, bounds.right)
        val closestY = y.coerceIn(bounds.top, bounds.bottom)
        val dx = x - closestX
        val dy = y - closestY
        return dx * dx + dy * dy <= radius * radius
    }

}

@Suppress("DEPRECATION")
private fun Window.configureEasterEggWindow() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
    decorView.setBackgroundColor(android.graphics.Color.BLACK)
    decorView.setPadding(0, 0, 0, 0)
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    attributes = attributes.apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        dimAmount = 1f
    }
    statusBarColor = android.graphics.Color.BLACK
    navigationBarColor = android.graphics.Color.BLACK
    WindowCompat.getInsetsController(this, decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Window.restoreAfterEasterEgg() {
    WindowCompat.getInsetsController(this, decorView).show(WindowInsetsCompat.Type.systemBars())
}

private const val RECOVERY_AMBER = 0xFFFFB300.toInt()
private const val RECOVERY_RED = 0xFFFF2A18.toInt()
private const val RECOVERY_BLUE = 0xFF0088FF.toInt()
private const val RECOVERY_HIGHLIGHT = 0xFF006CFF.toInt()
private const val RECOVERY_INSTRUCTION = "Use volume up/down and power."
private const val RECOVERY_CORRUPTION =
    "Cannot load Android system. Your data may be corrupt. If you continue to get this message, " +
        "you may need to perform a factory data reset and erase all user data stored on this device."
private const val RECOVERY_TRY_AGAIN = "Try again"
private const val RECOVERY_FACTORY_RESET = "Factory data reset"
private const val RECOVERY_TAP_TO_START = "Tap the screen to drop the paddle and ball"
