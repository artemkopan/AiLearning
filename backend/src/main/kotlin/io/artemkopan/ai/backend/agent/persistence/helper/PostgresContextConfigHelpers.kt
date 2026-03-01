package io.artemkopan.ai.backend.agent.persistence.helper

import io.artemkopan.ai.core.domain.model.*

internal fun contextStrategyValue(config: AgentContextConfig): String {
    return when (config) {
        is FullHistoryAgentContextConfig -> CONTEXT_STRATEGY_FULL_HISTORY
        is RollingSummaryAgentContextConfig -> CONTEXT_STRATEGY_ROLLING_SUMMARY
        is SlidingWindowAgentContextConfig -> CONTEXT_STRATEGY_SLIDING_WINDOW
        is StickyFactsAgentContextConfig -> CONTEXT_STRATEGY_STICKY_FACTS
        is BranchingAgentContextConfig -> CONTEXT_STRATEGY_BRANCHING
    }
}

internal fun contextRecentMessagesNValue(config: AgentContextConfig): Int {
    return when (config) {
        is FullHistoryAgentContextConfig -> DEFAULT_RECENT_MESSAGES_N
        is RollingSummaryAgentContextConfig -> config.recentMessagesN
        is SlidingWindowAgentContextConfig -> config.windowSize
        is StickyFactsAgentContextConfig -> config.recentMessagesN
        is BranchingAgentContextConfig -> config.recentMessagesN
    }
}

internal fun contextSummarizeEveryKValue(config: AgentContextConfig): Int {
    return when (config) {
        is FullHistoryAgentContextConfig -> DEFAULT_SUMMARIZE_EVERY_K
        is RollingSummaryAgentContextConfig -> config.summarizeEveryK
        is SlidingWindowAgentContextConfig -> DEFAULT_SUMMARIZE_EVERY_K
        is StickyFactsAgentContextConfig -> DEFAULT_SUMMARIZE_EVERY_K
        is BranchingAgentContextConfig -> DEFAULT_SUMMARIZE_EVERY_K
    }
}
