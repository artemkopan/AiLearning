package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.CloseAgentCommand
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.repository.AgentRepository

class CloseAgentUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(command: CloseAgentCommand): Result<AgentState> {
        val id = command.agentId.trim()
        if (id.isEmpty()) {
            return Result.failure(AppError.Validation("Agent id must not be blank."))
        }

        val state = repository.getState().getOrElse { return Result.failure(it) }
        if (state.agents.size <= 1) {
            return Result.failure(AppError.Validation("At least one agent must remain open."))
        }

        return repository.closeAgent(AgentId(id))
    }
}
