package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.repository.AgentRepository

class SetAgentProcessingUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(userScope: String, agentId: String): Result<AgentState> {
        val userId = parseUserIdOrError(userScope).getOrElse { return Result.failure(it) }
        val aid = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        return repository.updateAgentStatus(userId, aid, AgentStatus.PROCESSING)
    }
}
