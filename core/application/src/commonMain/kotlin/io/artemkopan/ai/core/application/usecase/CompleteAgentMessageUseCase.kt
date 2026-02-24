package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.GenerateOutput
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.repository.AgentRepository

data class CompleteAgentMessageCommand(
    val agentId: String,
    val messageId: String,
    val output: GenerateOutput,
)

class CompleteAgentMessageUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(command: CompleteAgentMessageCommand): Result<AgentState> {
        val domainAgentId = parseAgentIdOrError(command.agentId).getOrElse { return Result.failure(it) }
        val domainMessageId = parseMessageIdOrError(command.messageId).getOrElse { return Result.failure(it) }
        val currentMessage = repository.findMessage(
            agentId = domainAgentId,
            messageId = domainMessageId,
        ).getOrElse { return Result.failure(it) }
            ?: return Result.failure(AppError.Validation("Message not found."))

        if (!currentMessage.status.equals(STATUS_PROCESSING, ignoreCase = true)) {
            return repository.getState()
        }

        repository.updateMessage(
            agentId = domainAgentId,
            messageId = domainMessageId,
            text = command.output.text,
            status = STATUS_DONE,
            provider = command.output.provider,
            model = command.output.model,
            usageInputTokens = command.output.usage?.inputTokens,
            usageOutputTokens = command.output.usage?.outputTokens,
            usageTotalTokens = command.output.usage?.totalTokens,
            latencyMs = null,
        ).getOrElse { return Result.failure(it) }

        return repository.updateAgentStatus(
            domainAgentId,
            AgentStatus(STATUS_DONE),
        )
    }
}

private const val STATUS_PROCESSING = "processing"
private const val STATUS_DONE = "done"
