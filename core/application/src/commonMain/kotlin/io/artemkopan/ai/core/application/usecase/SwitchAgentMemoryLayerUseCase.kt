package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.usecase.shortcut.MemoryLayerType
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.AgentRepository

class SwitchAgentMemoryLayerUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(
        userId: String,
        agentId: String,
        layer: MemoryLayerType,
    ): Result<AgentState> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val domainAgentId = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        val state = repository.getState(domainUserId).getOrElse { return Result.failure(it) }
        val agent = state.agents.firstOrNull { it.id == domainAgentId }
            ?: return Result.failure(AppError.Validation("Agent not found: $agentId"))

        val nextContextConfig = when (layer) {
            MemoryLayerType.SHORT_TERM -> SlidingWindowAgentContextConfig(
                windowSize = agent.contextConfig.recentMessagesHint(),
                locked = agent.contextConfig.locked,
            )

            MemoryLayerType.WORKING -> RollingSummaryAgentContextConfig(
                recentMessagesN = agent.contextConfig.recentMessagesHint(),
                summarizeEveryK = agent.contextConfig.summarizeEveryHint(),
                locked = agent.contextConfig.locked,
            )

            MemoryLayerType.LONG_TERM -> StickyFactsAgentContextConfig(
                recentMessagesN = agent.contextConfig.recentMessagesHint(),
                locked = agent.contextConfig.locked,
            )
        }

        return repository.updateAgentDraft(
            userId = domainUserId,
            agentId = domainAgentId,
            draft = AgentDraft(
                model = agent.model,
                maxOutputTokens = agent.maxOutputTokens,
                temperature = agent.temperature,
                stopSequences = agent.stopSequences,
                agentMode = agent.agentMode,
                contextConfig = nextContextConfig,
            )
        )
    }
}

private fun AgentContextConfig.recentMessagesHint(): Int {
    return when (this) {
        is SlidingWindowAgentContextConfig -> windowSize
        is RollingSummaryAgentContextConfig -> recentMessagesN
        is StickyFactsAgentContextConfig -> recentMessagesN
        is BranchingAgentContextConfig -> recentMessagesN
        is FullHistoryAgentContextConfig -> DEFAULT_RECENT_MESSAGES_N
    }
}

private fun AgentContextConfig.summarizeEveryHint(): Int {
    return when (this) {
        is RollingSummaryAgentContextConfig -> summarizeEveryK
        else -> DEFAULT_SUMMARIZE_EVERY_K
    }
}
