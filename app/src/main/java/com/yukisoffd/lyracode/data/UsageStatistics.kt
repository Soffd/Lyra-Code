package com.yukisoffd.lyracode.data

import android.content.Context
import com.yukisoffd.lyracode.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class UsageStatsPeriod(val labelResId: Int) {
    DAY(R.string.stat_period_day),
    WEEK(R.string.stat_period_week),
    MONTH(R.string.stat_period_month),
    YEAR(R.string.stat_period_year),
    TOTAL(R.string.stat_period_total),
}

data class UsageStatsSummary(
    val period: UsageStatsPeriod,
    val startAt: Long,
    val endAt: Long,
    val conversationCount: Int,
    val userInputTokens: Long,
    val aiOutputTokens: Long,
    val userMessageCount: Int,
    val assistantMessageCount: Int,
    val toolMessageCount: Int,
    val modelRequestCount: Int,
)

class UsageStatisticsRepository(
    context: Context,
    private val conversationStore: ConversationStore,
) {
    private val tokenizer = DeepSeekV3Tokenizer.get(context)

    fun calculate(period: UsageStatsPeriod, anchorAt: Long = System.currentTimeMillis()): UsageStatsSummary {
        val range = periodRange(period, anchorAt)
        val conversationsWithUserInput = linkedSetOf<Long>()
        var userInputTokens = 0L
        var aiOutputTokens = 0L
        var userMessageCount = 0
        var assistantMessageCount = 0
        var toolMessageCount = 0
        var modelRequestCount = 0

        conversationStore.usageMessageEvents(range.first, range.second).forEach { event ->
            when (event.role.lowercase()) {
                "user" -> {
                    conversationsWithUserInput += event.conversationId
                    userMessageCount++
                }
                "tool" -> toolMessageCount++
                "assistant" -> assistantMessageCount++
            }
        }

        conversationStore.usageModelRequests(range.first, range.second).forEach { request ->
            userInputTokens += request.inputTokens
            aiOutputTokens += request.outputTokens
            modelRequestCount++
        }

        val recordedAssistantIds = conversationStore.usageModelRequestMessageIds()
        conversationStore.conversations(includeSubAgents = true).forEach { conversation ->
            var repeatedContextTokens = 0L
            conversationStore.messages(conversation.id).forEach { message ->
                when (message.role.lowercase()) {
                    "user", "tool" -> repeatedContextTokens += message.promptInputCost()
                    "assistant" -> {
                        val inRange = message.createdAt >= range.first && message.createdAt < range.second
                        if (inRange && message.id !in recordedAssistantIds) {
                            modelRequestCount++
                            userInputTokens += REQUEST_STATIC_INPUT_TOKENS + repeatedContextTokens
                            aiOutputTokens += message.assistantOutputCost()
                        }
                        repeatedContextTokens += message.promptInputCost()
                    }
                }
            }
        }

        return UsageStatsSummary(
            period = period,
            startAt = range.first,
            endAt = range.second,
            conversationCount = conversationsWithUserInput.size,
            userInputTokens = userInputTokens,
            aiOutputTokens = aiOutputTokens,
            userMessageCount = userMessageCount,
            assistantMessageCount = assistantMessageCount,
            toolMessageCount = toolMessageCount,
            modelRequestCount = modelRequestCount,
        )
    }
    private fun ChatMessage.promptInputCost(): Long {
        return when (role.lowercase()) {
            "user" -> MESSAGE_WRAPPER_TOKENS + tokenizer.count(content)
            "tool" -> MESSAGE_WRAPPER_TOKENS + tokenizer.count(content)
            "assistant" -> MESSAGE_WRAPPER_TOKENS + assistantOutputCost()
            else -> MESSAGE_WRAPPER_TOKENS + tokenizer.count(content) + tokenizer.count(thinking)
        }
    }

    private fun ChatMessage.assistantOutputCost(): Long {
        return tokenizer.count(content) +
            tokenizer.count(thinking) +
            tokenizer.count(toolCallsOutputText(rawJson))
    }

    private fun toolCallsOutputText(rawJson: String?): String {
        val raw = rawJson?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return ""
        val calls = raw.optJSONArray("tool_calls") ?: return ""
        return buildString {
            for (index in 0 until calls.length()) {
                val call = calls.optJSONObject(index) ?: continue
                appendToolCallOutput(call)
                append('\n')
            }
        }
    }

    private fun StringBuilder.appendToolCallOutput(call: JSONObject) {
        val function = call.optJSONObject("function")
        if (function != null) {
            append(function.optString("name"))
            append('\n')
            append(function.optString("arguments"))
            return
        }
        append(call.optString("name"))
        append('\n')
        val input = call.opt("input")
        when (input) {
            is JSONObject, is JSONArray -> append(input.toString())
            null -> append(call.toString())
            else -> append(input.toString())
        }
    }

    private fun periodRange(period: UsageStatsPeriod, anchorAt: Long): Pair<Long, Long> {
        if (period == UsageStatsPeriod.TOTAL) return Long.MIN_VALUE to Long.MAX_VALUE
        val start = Calendar.getInstance().apply {
            timeInMillis = anchorAt
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            UsageStatsPeriod.DAY -> Unit
            UsageStatsPeriod.WEEK -> start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            UsageStatsPeriod.MONTH -> start.set(Calendar.DAY_OF_MONTH, 1)
            UsageStatsPeriod.YEAR -> start.set(Calendar.DAY_OF_YEAR, 1)
            UsageStatsPeriod.TOTAL -> Unit
        }
        val end = start.clone() as Calendar
        when (period) {
            UsageStatsPeriod.DAY -> end.add(Calendar.DAY_OF_YEAR, 1)
            UsageStatsPeriod.WEEK -> end.add(Calendar.WEEK_OF_YEAR, 1)
            UsageStatsPeriod.MONTH -> end.add(Calendar.MONTH, 1)
            UsageStatsPeriod.YEAR -> end.add(Calendar.YEAR, 1)
            UsageStatsPeriod.TOTAL -> Unit
        }
        return start.timeInMillis to end.timeInMillis
    }

    private companion object {
        private const val REQUEST_STATIC_INPUT_TOKENS = 1024L
        private const val MESSAGE_WRAPPER_TOKENS = 8L
    }
}