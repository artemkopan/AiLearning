package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.UpdateAgentDraftCommand
import io.artemkopan.ai.core.domain.model.AgentDraft
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.repository.AgentRepository

class UpdateAgentDraftUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(userId: String, command: UpdateAgentDraftCommand): Result<AgentState> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val id = command.agentId.trim()
        if (id.isEmpty()) {
            return Result.failure(AppError.Validation("Agent id must not be blank."))
        }

        val mode = command.agentMode.trim().lowercase().ifEmpty { "default" }
        return repository.updateAgentDraft(
            userId = domainUserId,
            agentId = AgentId(id),
            draft = AgentDraft(
                model = command.model,
                maxOutputTokens = command.maxOutputTokens,
                temperature = command.temperature,
                stopSequences = command.stopSequences,
                agentMode = mode,
            ),
        )
    }
}
