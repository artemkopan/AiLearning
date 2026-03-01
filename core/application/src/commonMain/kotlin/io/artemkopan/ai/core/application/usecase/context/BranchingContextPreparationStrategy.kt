package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.BranchingAgentContextConfig
import io.artemkopan.ai.core.domain.model.UserId

class BranchingContextPreparationStrategy : ContextPreparationStrategy {
    override suspend fun prepare(userId: UserId, agent: Agent): Result<PreparedContextWindow> {
        val config = agent.contextConfig as? BranchingAgentContextConfig
            ?: return Result.failure(IllegalArgumentException("Invalid context config for branching strategy."))
        val activeMessages = agent.messages
            .filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
            .takeLast(config.recentMessagesN)
        return Result.success(
            PreparedContextWindow(
                summaryText = "",
                recentMessages = activeMessages,
            )
        )
    }
}

private const val STATUS_STOPPED = "stopped"
