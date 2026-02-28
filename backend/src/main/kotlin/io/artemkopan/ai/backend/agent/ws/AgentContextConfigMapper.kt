package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.domain.model.AgentContextConfig
import io.artemkopan.ai.core.domain.model.FullHistoryAgentContextConfig
import io.artemkopan.ai.core.domain.model.RollingSummaryAgentContextConfig
import io.artemkopan.ai.sharedcontract.AgentContextConfigDto
import io.artemkopan.ai.sharedcontract.FullHistoryContextConfigDto
import io.artemkopan.ai.sharedcontract.RollingSummaryContextConfigDto

internal fun AgentContextConfig.toDto(): AgentContextConfigDto {
    return when (this) {
        is FullHistoryAgentContextConfig -> FullHistoryContextConfigDto(locked = locked)
        is RollingSummaryAgentContextConfig -> RollingSummaryContextConfigDto(
            recentMessagesN = recentMessagesN,
            summarizeEveryK = summarizeEveryK,
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
    }
}
