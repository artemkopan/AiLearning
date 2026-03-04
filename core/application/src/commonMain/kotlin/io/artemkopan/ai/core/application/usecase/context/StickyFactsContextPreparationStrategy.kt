package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.StickyFactsAgentContextConfig
import io.artemkopan.ai.core.domain.model.UserId

class StickyFactsContextPreparationStrategy(
) : ContextPreparationStrategy {

    override suspend fun prepare(userId: UserId, agent: Agent): Result<PreparedContextWindow> {
        val config = agent.contextConfig as? StickyFactsAgentContextConfig
            ?: return Result.failure(IllegalArgumentException("Invalid context config for sticky facts strategy."))

        val recentMessages = agent.messages
            .filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
            .takeLast(config.recentMessagesN)

        return Result.success(
            PreparedContextWindow(
                summaryText = "",
                recentMessages = recentMessages,
            )
        )
    }
}

private const val STATUS_STOPPED = "stopped"
