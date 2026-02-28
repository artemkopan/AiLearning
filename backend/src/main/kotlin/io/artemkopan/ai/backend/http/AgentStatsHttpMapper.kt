package io.artemkopan.ai.backend.http

import io.artemkopan.ai.core.application.usecase.stats.AgentStats
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.FullHistoryAgentContextConfig
import io.artemkopan.ai.core.domain.model.RollingSummaryAgentContextConfig
import io.artemkopan.ai.sharedcontract.*

class AgentStatsHttpMapper {
    fun toDto(stats: AgentStats): AgentStatsDto {
        return AgentStatsDto(
            agentId = stats.agentId,
            title = stats.title,
            model = stats.model,
            agentMode = parseAgentMode(stats.agentMode),
            contextConfig = when (val config = stats.contextConfig) {
                is FullHistoryAgentContextConfig -> FullHistoryContextConfigDto(locked = config.locked)
                is RollingSummaryAgentContextConfig -> RollingSummaryContextConfigDto(
                    recentMessagesN = config.recentMessagesN,
                    summarizeEveryK = config.summarizeEveryK,
                    locked = config.locked,
                )
            },
            contextSummary = stats.contextSummary,
            summarizedUntilCreatedAt = stats.summarizedUntilCreatedAt,
            contextSummaryUpdatedAt = stats.contextSummaryUpdatedAt,
            systemInstruction = stats.systemInstruction,
            latestAssistant = stats.latestAssistant?.let { latest ->
                AgentLatestAssistantStatsDto(
                    messageId = latest.messageId,
                    text = latest.text,
                    status = latest.status,
                    provider = latest.provider,
                    model = latest.model,
                    usage = if (latest.totalTokens != null || latest.inputTokens != null || latest.outputTokens != null) {
                        TokenUsageDto(
                            inputTokens = latest.inputTokens ?: 0,
                            outputTokens = latest.outputTokens ?: 0,
                            totalTokens = latest.totalTokens ?: 0,
                        )
                    } else {
                        null
                    },
                    latencyMs = latest.latencyMs,
                )
            },
            tokenStats = AgentTokenStatsDto(
                latestInputTokens = stats.tokenStats.latestInputTokens,
                latestOutputTokens = stats.tokenStats.latestOutputTokens,
                latestTotalTokens = stats.tokenStats.latestTotalTokens,
                cumulativeInputTokens = stats.tokenStats.cumulativeInputTokens,
                cumulativeOutputTokens = stats.tokenStats.cumulativeOutputTokens,
                cumulativeTotalTokens = stats.tokenStats.cumulativeTotalTokens,
                estimatedContextRawTokens = stats.tokenStats.estimatedContextRawTokens,
                estimatedContextCompressedTokens = stats.tokenStats.estimatedContextCompressedTokens,
            ),
            recentTurns = stats.recentTurns.map { turn ->
                AgentRecentTurnStatsDto(
                    role = when (turn.role) {
                        AgentMessageRole.USER -> AgentMessageRoleDto.USER
                        AgentMessageRole.ASSISTANT -> AgentMessageRoleDto.ASSISTANT
                    },
                    text = turn.text,
                    status = turn.status,
                    createdAt = turn.createdAt,
                )
            },
        )
    }

    private fun parseAgentMode(value: String): AgentMode {
        return when (value.lowercase()) {
            "engineer" -> AgentMode.ENGINEER
            "philosophic" -> AgentMode.PHILOSOPHIC
            "critic" -> AgentMode.CRITIC
            else -> AgentMode.DEFAULT
        }
    }
}
