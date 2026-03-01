package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.sharedcontract.*

internal fun AgentContextConfig.toDto(): AgentContextConfigDto {
    return when (this) {
        is FullHistoryAgentContextConfig -> FullHistoryContextConfigDto(locked = locked)
        is RollingSummaryAgentContextConfig -> RollingSummaryContextConfigDto(
            recentMessagesN = recentMessagesN,
            summarizeEveryK = summarizeEveryK,
            locked = locked,
        )
        is SlidingWindowAgentContextConfig -> SlidingWindowContextConfigDto(
            windowSize = windowSize,
            locked = locked,
        )
        is StickyFactsAgentContextConfig -> StickyFactsContextConfigDto(
            recentMessagesN = recentMessagesN,
            locked = locked,
        )
        is BranchingAgentContextConfig -> BranchingContextConfigDto(
            recentMessagesN = recentMessagesN,
            locked = locked,
        )
    }
}

internal fun AgentContextConfigDto.toDomain(): AgentContextConfig {
    return when (this) {
        is FullHistoryContextConfigDto -> FullHistoryAgentContextConfig(locked = locked)
        is RollingSummaryContextConfigDto -> RollingSummaryAgentContextConfig(
            recentMessagesN = recentMessagesN,
            summarizeEveryK = summarizeEveryK,
            locked = locked,
        )
        is SlidingWindowContextConfigDto -> SlidingWindowAgentContextConfig(
            windowSize = windowSize,
            locked = locked,
        )
        is StickyFactsContextConfigDto -> StickyFactsAgentContextConfig(
            recentMessagesN = recentMessagesN,
            locked = locked,
        )
        is BranchingContextConfigDto -> BranchingAgentContextConfig(
            recentMessagesN = recentMessagesN,
            locked = locked,
        )
    }
}
