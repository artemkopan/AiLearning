package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.SelectAgentCommand
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.repository.AgentRepository

class SelectAgentUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(userId: String, command: SelectAgentCommand): Result<AgentState> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val id = command.agentId.trim()
        if (id.isEmpty()) {
            return Result.failure(AppError.Validation("Agent id must not be blank."))
        }
        return repository.selectAgent(domainUserId, AgentId(id))
    }
}
