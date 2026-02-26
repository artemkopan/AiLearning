package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.repository.AgentRepository

data class FailAgentMessageCommand(
    val agentId: String,
    val messageId: String,
)

class FailAgentMessageUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(userId: String, command: FailAgentMessageCommand): Result<AgentState> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val domainAgentId = parseAgentIdOrError(command.agentId).getOrElse { return Result.failure(it) }
        val domainMessageId = parseMessageIdOrError(command.messageId).getOrElse { return Result.failure(it) }
        val currentMessage = repository.findMessage(
            userId = domainUserId,
            agentId = domainAgentId,
            messageId = domainMessageId,
        ).getOrElse { return Result.failure(it) }
            ?: return Result.failure(AppError.Validation("Message not found."))

        if (currentMessage.status.equals(STATUS_PROCESSING, ignoreCase = true)) {
            repository.updateMessage(
                userId = domainUserId,
                agentId = domainAgentId,
                messageId = domainMessageId,
                status = STATUS_FAILED,
            ).getOrElse { return Result.failure(it) }
        }

        return repository.updateAgentStatus(
            userId = domainUserId,
            agentId = domainAgentId,
            status = AgentStatus(STATUS_FAILED),
        )
    }
}

private const val STATUS_PROCESSING = "processing"
private const val STATUS_FAILED = "failed"
