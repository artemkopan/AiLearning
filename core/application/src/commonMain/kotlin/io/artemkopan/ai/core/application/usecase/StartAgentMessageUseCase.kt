package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.domain.error.DomainError
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.repository.AgentRepository
import kotlinx.datetime.Clock

data class StartedAgentMessage(
    val agentId: String,
    val messageId: String,
    val generateCommand: GenerateCommand,
)

class StartAgentMessageUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(userScope: String, agentId: String, text: String): Result<StartedAgentMessage> {
        val userId = parseUserIdOrError(userScope).getOrElse { return Result.failure(it) }
        val aid = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        val state = repository.getState(userId).getOrElse { return Result.failure(it) }
        val agent = state.agents.find { it.id == aid }
            ?: return Result.failure(DomainError.Validation("Agent not found: ${aid.value}"))
        if (text.isBlank()) {
            return Result.failure(DomainError.Validation("Message text must not be blank"))
        }
        val busy = repository.hasProcessingMessage(userId, aid).getOrElse { return Result.failure(it) }
        if (busy) {
            return Result.failure(DomainError.Validation("Agent already processing a message"))
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val userMsgId = AgentMessageId("msg-$now-user")
        val userMsg = AgentMessage(
            id = userMsgId,
            role = AgentMessageRole.USER,
            text = text.trim(),
            status = AgentStatus.DONE.value,
            createdAt = now,
        )
        repository.appendMessage(userId, aid, userMsg).getOrElse { return Result.failure(it) }
        val asstMsgId = AgentMessageId("msg-${now + 1}-asst")
        val asstMsg = AgentMessage(
            id = asstMsgId,
            role = AgentMessageRole.ASSISTANT,
            text = "",
            status = AgentStatus.PROCESSING.value,
            createdAt = now + 1,
        )
        repository.updateAgentStatus(userId, aid, AgentStatus.PROCESSING)
            .getOrElse { return Result.failure(it) }
        val afterAsst = repository.appendMessage(userId, aid, asstMsg).getOrElse { return Result.failure(it) }
        val messages = afterAsst.agents.find { it.id == aid }?.messages
            ?: return Result.failure(DomainError.Validation("Agent not found after message append: ${aid.value}"))
        val prompt = buildConversationPrompt(messages)
        return Result.success(
            StartedAgentMessage(
                agentId = aid.value,
                messageId = asstMsgId.value,
                generateCommand = GenerateCommand(
                    prompt = prompt,
                    model = agent.model.ifBlank { "deepseek-chat" },
                    temperature = agent.temperature.toDoubleOrNull() ?: 0.7,
                    maxOutputTokens = agent.maxOutputTokens.toIntOrNull(),
                    systemInstruction = PLANNING_SYSTEM_PROMPT +
                        InvariantsPromptBuilder.buildInvariantsBlock(agent.invariants),
                ),
            ),
        )
    }

    private fun buildConversationPrompt(messages: List<AgentMessage>): String {
        return messages.joinToString("\n\n") { msg ->
            val role = when (msg.role) {
                AgentMessageRole.USER -> "User"
                AgentMessageRole.ASSISTANT -> "Assistant"
            }
            "$role: ${msg.text.ifBlank { "..." }}"
        } + "\n\nAssistant:"
    }

}

private const val PLANNING_SYSTEM_PROMPT =
    """You are a helpful assistant. The user has sent a message. Respond with a brief plan (2-5 bullet points) for how you would address their request. Format as a JSON object: {"plan": ["step1", "step2", ...], "question_for_user": "optional clarification question"}"""
