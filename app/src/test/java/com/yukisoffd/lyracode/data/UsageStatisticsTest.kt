package com.yukisoffd.lyracode.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatisticsTest {
    @Test
    fun aggregatesRequestsByModelNameAcrossProviders() {
        val result = aggregateModelUsage(
            listOf(
                ModelUsageSample("gpt-4o-mini", inputTokens = 1_000L, outputTokens = 200L),
                ModelUsageSample("GPT-4O-MINI", inputTokens = 800L, outputTokens = 300L),
                ModelUsageSample("deepseek-chat", inputTokens = 400L, outputTokens = 100L),
            ),
        )

        assertEquals(2, result.size)
        assertEquals("gpt-4o-mini", result[0].modelName)
        assertEquals(2, result[0].requestCount)
        assertEquals(1_800L, result[0].inputTokens)
        assertEquals(500L, result[0].outputTokens)
        assertEquals("deepseek-chat", result[1].modelName)
    }

    @Test
    fun stripsProviderPathsBeforeAggregatingModelUsage() {
        val result = aggregateModelUsage(
            listOf(
                ModelUsageSample("deepseek-v4-pro", inputTokens = 100L, outputTokens = 20L),
                ModelUsageSample("deepseek-ai/deepseek-v4-pro", inputTokens = 200L, outputTokens = 30L),
                ModelUsageSample("catalog/deepseek-ai/DEEPSEEK-V4-PRO", inputTokens = 300L, outputTokens = 40L),
            ),
        )

        assertEquals(1, result.size)
        assertEquals("deepseek-v4-pro", result.single().modelName)
        assertEquals(3, result.single().requestCount)
        assertEquals(600L, result.single().inputTokens)
        assertEquals(90L, result.single().outputTokens)
    }
}
