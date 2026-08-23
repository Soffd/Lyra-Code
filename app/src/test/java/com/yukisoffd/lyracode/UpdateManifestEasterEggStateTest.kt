package com.yukisoffd.lyracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestEasterEggStateTest {
    @Test
    fun tenthTapPausesTheTimeoutUntilTheWarningIsDismissed() {
        val session = UpdateManifestEasterEggSession()

        repeat(9) { assertEquals(UpdateManifestEasterEggEvent.NONE, session.click(it * 100L)) }
        assertEquals(UpdateManifestEasterEggEvent.SHOW_FIRST_WARNING, session.click(900L))
        assertTrue(session.paused)
        assertFalse(session.expire(20_000L))

        session.resumeAfterPrompt(20_000L)
        assertEquals(UpdateManifestEasterEggEvent.NONE, session.click(22_999L))
        assertEquals(11, session.clickCount)
    }

    @Test
    fun choosingStopAtFortyLocksTheCurrentPageSession() {
        val session = sessionAtChoice()

        session.chooseStop()

        repeat(30) { assertEquals(UpdateManifestEasterEggEvent.NONE, session.click(5_000L + it)) }
        assertTrue(session.locked)
        assertEquals(0, session.clickCount)
    }

    @Test
    fun defiantChoiceCrashesOnceAndResumesWithExactlyTwentyTapsRemaining() {
        val session = sessionAtChoice()
        session.chooseDefiant(4_000L)

        assertEquals(UpdateManifestEasterEggEvent.CRASH_ONCE, session.click(4_100L))

        val resumed = UpdateManifestEasterEggSession(UPDATE_MANIFEST_EASTER_EGG_RESUME_COUNT)
        repeat(19) { assertEquals(UpdateManifestEasterEggEvent.NONE, resumed.click(10_000L + it * 100L)) }
        assertEquals(UpdateManifestEasterEggEvent.OPEN_RECOVERY, resumed.click(11_900L))
    }

    @Test
    fun threeSecondGapImmediatelyAbandonsTheAttempt() {
        val session = UpdateManifestEasterEggSession()
        repeat(7) { session.click(it * 100L) }

        assertTrue(session.expire(3_600L))
        assertEquals(0, session.clickCount)
        assertEquals(UpdateManifestEasterEggEvent.NONE, session.click(9_000L))
        assertEquals(1, session.clickCount)
    }

    @Test
    fun paddleFallsFromAboveBouncesAndSettlesAtTheBottom() {
        val physics = FallingPaddlePhysics(
            initialPosition = -20f,
            targetPosition = 500f,
            gravity = 1_400f,
            minimumBounceVelocity = 60f,
        )
        var sawDownwardMotion = false
        var sawUpwardBounce = false
        var previousPosition = physics.position

        repeat(600) {
            physics.step(1f / 120f)
            if (physics.position > previousPosition) sawDownwardMotion = true
            if (physics.velocity < 0f) sawUpwardBounce = true
            previousPosition = physics.position
        }

        assertTrue(sawDownwardMotion)
        assertTrue(sawUpwardBounce)
        assertEquals(2, physics.bounceCount)
        assertTrue(physics.settled)
        assertEquals(500f, physics.position, 0.001f)
    }

    @Test
    fun verticalBallTrajectoryReceivesAlternatingHorizontalMovement() {
        val first = ensureBreakoutHorizontalVelocity(
            velocityX = 0f,
            velocityY = -300f,
            speed = 300f,
            kickDirection = 1f,
        )
        val second = ensureBreakoutHorizontalVelocity(
            velocityX = 0f,
            velocityY = first.y,
            speed = 300f,
            kickDirection = first.nextKickDirection,
        )

        assertTrue(first.x > 0f)
        assertTrue(second.x < 0f)
        assertTrue(kotlin.math.abs(first.x) >= 78f)
        assertEquals(300f, kotlin.math.hypot(first.x, first.y), 0.01f)
    }

    private fun sessionAtChoice(): UpdateManifestEasterEggSession {
        val session = UpdateManifestEasterEggSession()
        repeat(9) { session.click(it * 100L) }
        session.click(900L)
        session.resumeAfterPrompt(1_000L)
        repeat(29) { session.click(1_100L + it * 100L) }
        assertEquals(UpdateManifestEasterEggEvent.SHOW_CHOICE, session.click(4_000L))
        return session
    }
}
