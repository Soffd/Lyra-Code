package com.yukisoffd.lyracode.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxCommandPreparationTest {
    @Test
    fun detectsDetachedServerCommandWithoutConfusingRedirections() {
        val command = "cd /storage/emulated/0/Ubuntu/Gobang && nohup python3 gobang_server.py > server.log 2>&1 & echo ${'$'}! > server.pid"

        assertTrue(shouldDetachTermuxCommand(command, backgroundRequested = false))
        assertFalse(containsStandaloneBackgroundOperator("python3 script.py > server.log 2>&1"))
        assertFalse(containsStandaloneBackgroundOperator("first && second"))
        assertFalse(containsStandaloneBackgroundOperator("printf '&'"))
        assertFalse(containsStandaloneBackgroundOperator("command &> combined.log"))
    }

    @Test
    fun explicitBackgroundAlwaysDetachesButWaitKeepsForegroundSemantics() {
        assertTrue(shouldDetachTermuxCommand("python3 server.py", backgroundRequested = true))
        assertFalse(shouldDetachTermuxCommand("worker & wait ${'$'}!", backgroundRequested = false))
    }

    @Test
    fun detachedWrapperClosesDescriptorsAndReturnsTraceableMetadata() {
        val wrapper = buildDetachedTermuxCommand(
            command = "printf '%s' \"it's running\"",
            executionId = 1234,
            bashPath = "/data/data/com.termux/files/usr/bin/bash",
        )

        assertTrue(wrapper.contains("</dev/null"))
        assertTrue(wrapper.contains(">\"${'$'}lyra_output_file\" 2>&1 &"))
        assertTrue(wrapper.contains("lyracode-run-1234-${'$'}${'$'}.log"))
        assertTrue(wrapper.contains("background_started: true"))
        assertTrue(wrapper.contains("launcher_pid: %s"))
        assertTrue(wrapper.contains("'\"'\"'"))
    }
}
