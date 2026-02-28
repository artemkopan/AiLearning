package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.UserId

class FullHistoryContextPreparationStrategy : ContextPreparationStrategy {
    override suspend fun prepare(userId: UserId, agent: Agent): Result<PreparedContextWindow> {
        val activeMessages = agent.messages.filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
        return Result.success(
            PreparedContextWindow(
                summaryText = "",
                recentMessages = activeMessages,
            )
        )
    }
}

private const val STATUS_STOPPED = "stopped"
