package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.repository.AgentRepository

class GetAgentStateUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(userId: String): Result<AgentState> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        return repository.getState(domainUserId)
    }
}
