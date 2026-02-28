package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.UserId

class PrepareAgentContextUseCase(
    private val strategyRegistry: ContextPreparationStrategyRegistry,
) {
    suspend fun execute(userId: UserId, agent: Agent): Result<PreparedContextWindow> {
        return strategyRegistry.resolve(agent.contextConfig).prepare(userId, agent)
    }
}
