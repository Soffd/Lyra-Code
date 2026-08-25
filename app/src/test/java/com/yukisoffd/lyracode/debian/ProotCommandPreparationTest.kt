package com.yukisoffd.lyracode.debian

import org.junit.Assert.assertTrue
import org.junit.Test

class ProotCommandPreparationTest {
    @Test
    fun trackedCommandReportsShellCompletionSeparatelyFromProotExit() {
        val wrapper = buildTrackedProotCommand(
            command = "nohup python3 server.py > server.log 2>&1 &",
            completionGuestPath = "/root/.lyracode/commands/42.status",
            supervisorPidGuestPath = "/root/.lyracode/commands/42.pid",
            shellPath = "/bin/bash",
        )

        assertTrue(wrapper.contains("'/bin/bash' -lc"))
        assertTrue(wrapper.contains("42.status"))
        assertTrue(wrapper.contains("42.pid"))
        assertTrue(wrapper.contains("${'$'}PPID"))
        assertTrue(wrapper.contains("lyra_exit_code=${'$'}?"))
        assertTrue(wrapper.contains("exit \"${'$'}lyra_exit_code\""))
    }

    @Test
    fun trackedCommandSafelyQuotesUserShellText() {
        val wrapper = buildTrackedProotCommand(
            command = "printf '%s' \"it's alive\"",
            completionGuestPath = "/root/.lyracode/commands/43.status",
            supervisorPidGuestPath = "/root/.lyracode/commands/43.pid",
            shellPath = "/bin/sh",
        )

        assertTrue(wrapper.contains("'\"'\"'"))
        assertTrue(wrapper.contains("'/bin/sh' -lc"))
    }

    @Test
    fun explicitBackgroundClosesStreamsAndReturnsLogMetadata() {
        val wrapper = buildDetachedProotCommand(
            command = "python3 server.py",
            executionId = 44,
            shellPath = "/bin/bash",
        )

        assertTrue(wrapper.contains("</dev/null"))
        assertTrue(wrapper.contains(">\"${'$'}lyra_output_file\" 2>&1 &"))
        assertTrue(wrapper.contains("lyracode-run-44-${'$'}${'$'}.log"))
        assertTrue(wrapper.contains("background_started: true"))
        assertTrue(wrapper.contains("launcher_pid: %s"))
        assertTrue(wrapper.contains("output_file: %s"))
    }
}
