package io.artemkopan.ai.core.application.usecase.stats

import io.artemkopan.ai.core.domain.model.*

class BuildAgentStatsSnippetUseCase {
    fun execute(stats: AgentStats): String {
        val strategy = strategyDetails(stats.contextConfig)

        return buildString {
            appendLine("agent name: ${stats.title}")
            appendLine("strategy: ${strategy.name}")
            appendLine("strategy parameters: ${strategy.parameters}")
            appendLine(
                "tokens used: total=${stats.tokenStats.cumulativeTotalTokens}, " +
                    "input=${stats.tokenStats.cumulativeInputTokens}, output=${stats.tokenStats.cumulativeOutputTokens}"
            )
        }.trimEnd()
    }
}

private data class StrategyDetails(
    val name: String,
    val parameters: String,
)

private fun strategyDetails(config: AgentContextConfig): StrategyDetails = when (config) {
    is FullHistoryAgentContextConfig -> StrategyDetails(
        name = "full_history",
        parameters = "none",
    )
    is RollingSummaryAgentContextConfig -> StrategyDetails(
        name = "rolling_summary",
        parameters = "recentMessagesN=${config.recentMessagesN}, summarizeEveryK=${config.summarizeEveryK}",
    )
    is SlidingWindowAgentContextConfig -> StrategyDetails(
        name = "sliding_window",
        parameters = "windowSize=${config.windowSize}",
    )
    is StickyFactsAgentContextConfig -> StrategyDetails(
        name = "sticky_facts",
        parameters = "recentMessagesN=${config.recentMessagesN}",
    )
    is BranchingAgentContextConfig -> StrategyDetails(
        name = "branching",
        parameters = "recentMessagesN=${config.recentMessagesN}",
    )
}
