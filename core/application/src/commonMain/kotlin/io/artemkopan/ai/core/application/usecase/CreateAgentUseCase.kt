package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.repository.AgentRepository

class CreateAgentUseCase(
    private val repository: AgentRepository,
    private val maxAgents: Int = 5,
) {
    suspend fun execute(userId: String): Result<AgentState> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val currentState = repository.getState(domainUserId).getOrElse { return Result.failure(it) }
        if (currentState.agents.size >= maxAgents) {
            return Result.failure(AppError.Validation("Cannot create more than $maxAgents agents."))
        }
        return repository.createAgent(domainUserId)
    }
}
