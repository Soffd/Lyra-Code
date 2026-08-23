package com.yukisoffd.lyracode

internal const val UPDATE_MANIFEST_EASTER_EGG_TIMEOUT_MS = 3_000L
internal const val UPDATE_MANIFEST_EASTER_EGG_RESUME_COUNT = 40
internal const val UPDATE_MANIFEST_EASTER_EGG_TARGET_COUNT = 60

internal enum class UpdateManifestEasterEggEvent {
    NONE,
    SHOW_FIRST_WARNING,
    SHOW_CHOICE,
    CRASH_ONCE,
    OPEN_RECOVERY,
}

/**
 * In-memory state for one visit to the About page. Only the deliberate crash checkpoint is
 * persisted; every other abandoned or timed-out attempt starts from zero.
 */
internal class UpdateManifestEasterEggSession(
    initialClickCount: Int = 0,
) {
    var clickCount: Int = initialClickCount.coerceIn(0, UPDATE_MANIFEST_EASTER_EGG_TARGET_COUNT - 1)
        private set

    var paused: Boolean = false
        private set

    var locked: Boolean = false
        private set

    private var crashOnNextClick = false
    private var lastClickAtMs: Long? = null

    val hasActiveSequence: Boolean
        get() = clickCount > 0 && !paused && !locked && lastClickAtMs != null

    fun click(nowMs: Long): UpdateManifestEasterEggEvent {
        if (locked || paused) return UpdateManifestEasterEggEvent.NONE

        if (hasTimedOut(nowMs)) reset()
        if (crashOnNextClick) {
            crashOnNextClick = false
            return UpdateManifestEasterEggEvent.CRASH_ONCE
        }

        clickCount++
        lastClickAtMs = nowMs
        return when (clickCount) {
            10 -> pauseWith(UpdateManifestEasterEggEvent.SHOW_FIRST_WARNING)
            UPDATE_MANIFEST_EASTER_EGG_RESUME_COUNT -> pauseWith(UpdateManifestEasterEggEvent.SHOW_CHOICE)
            UPDATE_MANIFEST_EASTER_EGG_TARGET_COUNT -> pauseWith(UpdateManifestEasterEggEvent.OPEN_RECOVERY)
            else -> UpdateManifestEasterEggEvent.NONE
        }
    }

    fun resumeAfterPrompt(nowMs: Long) {
        if (!paused || locked) return
        paused = false
        lastClickAtMs = nowMs
    }

    fun chooseStop() {
        paused = false
        locked = true
        crashOnNextClick = false
        clickCount = 0
        lastClickAtMs = null
    }

    fun chooseDefiant(nowMs: Long) {
        if (clickCount != UPDATE_MANIFEST_EASTER_EGG_RESUME_COUNT) return
        paused = false
        crashOnNextClick = true
        lastClickAtMs = nowMs
    }

    fun expire(nowMs: Long): Boolean {
        if (!hasTimedOut(nowMs)) return false
        reset()
        return true
    }

    fun remainingTimeoutMs(nowMs: Long): Long? {
        val lastClick = lastClickAtMs ?: return null
        if (paused || locked || clickCount == 0) return null
        return (UPDATE_MANIFEST_EASTER_EGG_TIMEOUT_MS - (nowMs - lastClick)).coerceAtLeast(0L)
    }

    fun reset() {
        clickCount = 0
        paused = false
        locked = false
        crashOnNextClick = false
        lastClickAtMs = null
    }

    private fun hasTimedOut(nowMs: Long): Boolean {
        val lastClick = lastClickAtMs ?: return false
        return !paused && !locked && nowMs - lastClick >= UPDATE_MANIFEST_EASTER_EGG_TIMEOUT_MS
    }

    private fun pauseWith(event: UpdateManifestEasterEggEvent): UpdateManifestEasterEggEvent {
        paused = true
        lastClickAtMs = null
        return event
    }
}

internal class FallingPaddlePhysics(
    initialPosition: Float,
    private val targetPosition: Float,
    private val gravity: Float,
    private val minimumBounceVelocity: Float,
) {
    var position: Float = initialPosition
        private set
    var velocity: Float = 0f
        private set
    var bounceCount: Int = 0
        private set
    var settled: Boolean = false
        private set

    fun step(deltaSeconds: Float) {
        if (settled || deltaSeconds <= 0f) return
        velocity += gravity * deltaSeconds
        position += velocity * deltaSeconds
        if (position < targetPosition) return

        position = targetPosition
        if (bounceCount < 2 && kotlin.math.abs(velocity) > minimumBounceVelocity) {
            val restitution = if (bounceCount == 0) 0.30f else 0.18f
            velocity = -velocity * restitution
            bounceCount++
        } else {
            velocity = 0f
            settled = true
        }
    }
}

internal data class BreakoutVelocity(
    val x: Float,
    val y: Float,
    val nextKickDirection: Float,
)

internal fun ensureBreakoutHorizontalVelocity(
    velocityX: Float,
    velocityY: Float,
    speed: Float,
    kickDirection: Float,
    minimumHorizontalFraction: Float = 0.26f,
): BreakoutVelocity {
    val minimumHorizontalSpeed = speed * minimumHorizontalFraction
    if (kotlin.math.abs(velocityX) >= minimumHorizontalSpeed) {
        return BreakoutVelocity(velocityX, velocityY, kickDirection)
    }
    val verticalDirection = if (velocityY < 0f) -1f else 1f
    val horizontal = if (kickDirection < 0f) -minimumHorizontalSpeed else minimumHorizontalSpeed
    val vertical = verticalDirection * kotlin.math.sqrt(
        (speed * speed - horizontal * horizontal).coerceAtLeast(1f),
    )
    return BreakoutVelocity(horizontal, vertical, -kickDirection)
}
