package io.artemkopan.ai.core.application.usecase.stats

import io.artemkopan.ai.core.domain.model.*

class BuildAgentStatsSnippetUseCase {
    fun execute(stats: AgentStats): String {
        val strategy = when (val config = stats.contextConfig) {
            is FullHistoryAgentContextConfig -> "full_history"
            is RollingSummaryAgentContextConfig -> {
                "rolling_summary_recent_n(recentN=${config.recentMessagesN}, summarizeEveryK=${config.summarizeEveryK})"
            }
            is SlidingWindowAgentContextConfig -> "sliding_window(windowSize=${config.windowSize})"
            is StickyFactsAgentContextConfig -> "sticky_facts(recentN=${config.recentMessagesN})"
            is BranchingAgentContextConfig -> "branching(recentN=${config.recentMessagesN})"
        }
        val latest = stats.latestAssistant
        val recentTurns = stats.recentTurns.joinToString(separator = "\n") { turn ->
            "- ${turn.role.name}: ${truncate(turn.text, MAX_TURN_TEXT)}"
        }

        return buildString {
            appendLine("AGENT STATS")
            appendLine("id: ${stats.agentId}")
            appendLine("title: ${stats.title}")
            appendLine("model: ${stats.model}")
            appendLine("mode: ${stats.agentMode}")
            appendLine("context_strategy: $strategy")
            appendLine("context_summary: ${truncate(stats.contextSummary, MAX_SUMMARY_TEXT)}")
            appendLine(
                "tokens: latest(in=${stats.tokenStats.latestInputTokens}, out=${stats.tokenStats.latestOutputTokens}, total=${stats.tokenStats.latestTotalTokens}), " +
                    "cumulative(in=${stats.tokenStats.cumulativeInputTokens}, out=${stats.tokenStats.cumulativeOutputTokens}, total=${stats.tokenStats.cumulativeTotalTokens}), " +
                    "context(raw=${stats.tokenStats.estimatedContextRawTokens}, compressed=${stats.tokenStats.estimatedContextCompressedTokens})"
            )
            appendLine("system_instruction: ${truncate(stats.systemInstruction, MAX_SYSTEM_PROMPT_TEXT)}")
            if (latest != null) {
                appendLine("latest_response: ${truncate(latest.text, MAX_RESPONSE_TEXT)}")
            }
            if (recentTurns.isNotBlank()) {
                appendLine("recent_turns:")
                appendLine(recentTurns)
            }
        }.trimEnd()
    }

    private fun truncate(value: String, maxLength: Int): String {
        val normalized = value.replace('\n', ' ').trim()
        if (normalized.length <= maxLength) return normalized
        return normalized.take(maxLength) + "..."
    }
}

private const val MAX_SUMMARY_TEXT = 700
private const val MAX_SYSTEM_PROMPT_TEXT = 500
private const val MAX_RESPONSE_TEXT = 500
private const val MAX_TURN_TEXT = 180
