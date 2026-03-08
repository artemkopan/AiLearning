package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.repository.AgentRepository

class CreateAgentUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(userScope: String): Result<AgentState> {
        val userId = parseUserIdOrError(userScope).getOrElse { return Result.failure(it) }
        return repository.createAgent(userId)
    }
}
