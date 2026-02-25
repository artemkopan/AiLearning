package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.SetAgentStatusCommand
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.repository.AgentRepository

class SetAgentStatusUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(userId: String, command: SetAgentStatusCommand): Result<AgentState> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val id = command.agentId.trim()
        if (id.isEmpty()) {
            return Result.failure(AppError.Validation("Agent id must not be blank."))
        }

        val status = command.status.trim()
        if (status.isEmpty()) {
            return Result.failure(AppError.Validation("Agent status must not be blank."))
        }

        return repository.updateAgentStatus(
            userId = domainUserId,
            agentId = AgentId(id),
            status = AgentStatus(status),
        )
    }
}
