package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.repository.AgentRepository

class GetAgentStateUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(): Result<AgentState> = repository.getState()
}
