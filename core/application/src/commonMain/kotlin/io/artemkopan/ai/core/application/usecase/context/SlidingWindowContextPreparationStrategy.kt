package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.SlidingWindowAgentContextConfig
import io.artemkopan.ai.core.domain.model.UserId

class SlidingWindowContextPreparationStrategy : ContextPreparationStrategy {
    override suspend fun prepare(userId: UserId, agent: Agent): Result<PreparedContextWindow> {
        val config = agent.contextConfig as? SlidingWindowAgentContextConfig
            ?: return Result.failure(IllegalArgumentException("Invalid context config for sliding window strategy."))
        val activeMessages = agent.messages
            .filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
            .takeLast(config.windowSize)
        return Result.success(
            PreparedContextWindow(
                summaryText = "",
                recentMessages = activeMessages,
            )
        )
    }
}

private const val STATUS_STOPPED = "stopped"
