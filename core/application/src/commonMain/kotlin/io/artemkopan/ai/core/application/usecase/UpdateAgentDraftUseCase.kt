package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.UpdateAgentDraftCommand
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.AgentRepository

class UpdateAgentDraftUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(userId: String, command: UpdateAgentDraftCommand): Result<AgentState> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val id = command.agentId.trim()
        if (id.isEmpty()) {
            return Result.failure(AppError.Validation("Agent id must not be blank."))
        }

        val state = repository.getState(domainUserId).getOrElse { return Result.failure(it) }
        val currentAgent = state.agents.firstOrNull { it.id.value == id }
            ?: return Result.failure(AppError.Validation("Agent not found: $id"))

        val requestedContextConfig = normalizeContextConfig(command.contextConfig).getOrElse {
            return Result.failure(it)
        }
        val hasUserMessages = currentAgent.messages.any { it.role == AgentMessageRole.USER }
        val currentUnlocked = currentAgent.contextConfig.withLocked(false)
        val requestedUnlocked = requestedContextConfig.withLocked(false)
        if (hasUserMessages && requestedUnlocked != currentUnlocked) {
            return Result.failure(AppError.Validation("Context strategy is locked after the first user message."))
        }

        val mode = command.agentMode.trim().lowercase().ifEmpty { "default" }
        return repository.updateAgentDraft(
            userId = domainUserId,
            agentId = AgentId(id),
            draft = AgentDraft(
                model = command.model,
                maxOutputTokens = command.maxOutputTokens,
                temperature = command.temperature,
                stopSequences = command.stopSequences,
                agentMode = mode,
                contextConfig = if (hasUserMessages) currentUnlocked.withLocked(true) else requestedUnlocked,
            ),
        )
    }

    private fun normalizeContextConfig(config: AgentContextConfig): Result<AgentContextConfig> {
        return when (config) {
            is FullHistoryAgentContextConfig -> Result.success(FullHistoryAgentContextConfig(locked = config.locked))
            is RollingSummaryAgentContextConfig -> {
                if (config.recentMessagesN <= 0) {
                    return Result.failure(AppError.Validation("Recent messages N must be greater than 0."))
                }
                if (config.summarizeEveryK <= 0) {
                    return Result.failure(AppError.Validation("Summarize every K must be greater than 0."))
                }
                Result.success(
                    RollingSummaryAgentContextConfig(
                        recentMessagesN = config.recentMessagesN,
                        summarizeEveryK = config.summarizeEveryK,
                        locked = config.locked,
                    )
                )
            }
            is SlidingWindowAgentContextConfig -> {
                if (config.windowSize <= 0) {
                    return Result.failure(AppError.Validation("Window size must be greater than 0."))
                }
                Result.success(config)
            }
            is StickyFactsAgentContextConfig -> {
                if (config.recentMessagesN <= 0) {
                    return Result.failure(AppError.Validation("Recent messages N must be greater than 0."))
                }
                Result.success(config)
            }
            is BranchingAgentContextConfig -> {
                if (config.recentMessagesN <= 0) {
                    return Result.failure(AppError.Validation("Recent messages N must be greater than 0."))
                }
                Result.success(config)
            }
        }
    }
}

private fun AgentContextConfig.withLocked(locked: Boolean): AgentContextConfig {
    return when (this) {
        is FullHistoryAgentContextConfig -> copy(locked = locked)
        is RollingSummaryAgentContextConfig -> copy(locked = locked)
        is SlidingWindowAgentContextConfig -> copy(locked = locked)
        is StickyFactsAgentContextConfig -> copy(locked = locked)
        is BranchingAgentContextConfig -> copy(locked = locked)
    }
}
