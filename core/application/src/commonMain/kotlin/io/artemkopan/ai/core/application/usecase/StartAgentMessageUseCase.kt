package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.model.SendAgentMessageCommand
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.repository.AgentRepository
import kotlin.random.Random

data class StartedAgentMessage(
    val state: AgentState,
    val agentId: AgentId,
    val messageId: AgentMessageId,
    val generateCommand: GenerateCommand,
)

class StartAgentMessageUseCase(
    private val repository: AgentRepository,
    private val maybeSummarizeContextUseCase: MaybeSummarizeContextUseCase,
    private val buildContextPromptUseCase: BuildContextPromptUseCase,
    private val retrieveRelevantContextUseCase: RetrieveRelevantContextUseCase,
    private val indexMessageEmbeddingsUseCase: IndexMessageEmbeddingsUseCase,
) {
    suspend fun execute(userId: String, command: SendAgentMessageCommand): Result<StartedAgentMessage> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val agentId = parseAgentIdOrError(command.agentId).getOrElse { return Result.failure(it) }

        val text = command.text.trim()
        if (text.isEmpty()) {
            return Result.failure(AppError.Validation("Message text must not be blank."))
        }

        val hasProcessing = repository.hasProcessingMessage(domainUserId, agentId).getOrElse { return Result.failure(it) }
        if (hasProcessing) {
            return Result.failure(AppError.Validation("This agent already has a processing message."))
        }

        val state = repository.getState(domainUserId).getOrElse { return Result.failure(it) }
        val agent = state.agents.firstOrNull { it.id == agentId }
            ?: return Result.failure(AppError.Validation("Agent not found: ${command.agentId}"))

        val userMessage = AgentMessage(
            id = AgentMessageId(createMessageId()),
            role = AgentMessageRole.USER,
            text = text,
            status = STATUS_DONE,
            createdAt = 0L,
        )
        val assistantPlaceholderId = AgentMessageId(createMessageId())
        val assistantPlaceholder = AgentMessage(
            id = assistantPlaceholderId,
            role = AgentMessageRole.ASSISTANT,
            text = "",
            status = STATUS_PROCESSING,
            createdAt = 0L,
        )

        val afterUser = repository.appendMessage(domainUserId, agentId, userMessage).getOrElse { return Result.failure(it) }
        repository.appendMessage(domainUserId, agentId, assistantPlaceholder).getOrElse { return Result.failure(it) }
        repository.updateAgentStatus(domainUserId, agentId, AgentStatus(STATUS_PROCESSING))
            .getOrElse { return Result.failure(it) }

        val latestAgent = afterUser.agents.firstOrNull { it.id == agentId } ?: agent
        val persistedUserMessage = latestAgent.messages.firstOrNull { it.id == userMessage.id } ?: userMessage
        val preparedContext = maybeSummarizeContextUseCase.execute(domainUserId, latestAgent).getOrElse {
            return Result.failure(it)
        }
        val retrievedMemory = retrieveRelevantContextUseCase.execute(
            userId = domainUserId,
            agentId = agentId,
            queryText = text,
        ).getOrElse {
            return Result.failure(it)
        }
        val conversationPrompt = buildContextPromptUseCase.execute(
            summary = preparedContext.summaryText,
            messages = preparedContext.recentMessages,
            retrievedMemory = retrievedMemory,
        )
        if (conversationPrompt.isBlank()) {
            return Result.failure(AppError.Validation("Conversation context is empty."))
        }

        indexMessageEmbeddingsUseCase.execute(
            userId = domainUserId,
            agentId = agentId,
            messageId = persistedUserMessage.id,
            text = persistedUserMessage.text,
            createdAt = persistedUserMessage.createdAt,
        ).getOrElse { return Result.failure(it) }

        val finalState = repository.getState(domainUserId).getOrElse { return Result.failure(it) }

        return Result.success(
            StartedAgentMessage(
                state = finalState,
                agentId = agentId,
                messageId = assistantPlaceholderId,
                generateCommand = GenerateCommand(
                    prompt = conversationPrompt,
                    model = latestAgent.model.trim().takeIf { it.isNotEmpty() },
                    temperature = latestAgent.temperature.toDoubleOrNull(),
                    maxOutputTokens = latestAgent.maxOutputTokens.toIntOrNull(),
                    stopSequences = latestAgent.stopSequences
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .takeIf { it.isNotEmpty() },
                    agentMode = latestAgent.agentMode.takeIf { it != "default" },
                ),
            )
        )
    }
    private fun createMessageId(): String {
        val a = Random.nextLong().toULong().toString(16)
        val b = Random.nextLong().toULong().toString(16)
        return "$a$b"
    }
}

private const val STATUS_PROCESSING = "processing"
private const val STATUS_DONE = "done"
