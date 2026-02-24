package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.StopAgentMessageCommand
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.repository.AgentRepository

class StopAgentMessageUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(command: StopAgentMessageCommand): Result<AgentState> {
        val agentId = parseAgentIdOrError(command.agentId).getOrElse { return Result.failure(it) }
        val messageId = parseMessageIdOrError(command.messageId).getOrElse { return Result.failure(it) }
        val message = repository.findMessage(
            agentId = agentId,
            messageId = messageId,
        ).getOrElse { return Result.failure(it) }
            ?: return Result.failure(AppError.Validation("Message not found."))

        if (!message.status.equals(STATUS_PROCESSING, ignoreCase = true)) {
            return Result.failure(AppError.Validation("Only processing messages can be stopped."))
        }

        repository.updateMessage(
            agentId = agentId,
            messageId = message.id,
            status = STATUS_STOPPED,
        ).getOrElse { return Result.failure(it) }

        return repository.updateAgentStatus(agentId, AgentStatus(STATUS_STOPPED))
    }
}

private const val STATUS_PROCESSING = "processing"
private const val STATUS_STOPPED = "stopped"
