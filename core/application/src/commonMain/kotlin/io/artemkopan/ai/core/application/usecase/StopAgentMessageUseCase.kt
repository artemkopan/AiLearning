package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.repository.AgentRepository

class StopAgentMessageUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(
        userScope: String,
        agentId: String,
        messageId: String,
    ): Result<io.artemkopan.ai.core.domain.model.AgentState> {
        val userId = parseUserIdOrError(userScope).getOrElse { return Result.failure(it) }
        val aid = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        repository.updateMessage(
            userId = userId,
            agentId = aid,
            messageId = AgentMessageId(messageId),
            status = AgentStatus.STOPPED.value,
        ).getOrElse { return Result.failure(it) }
        return repository.updateAgentStatus(userId, aid, AgentStatus.DONE)
    }
}
