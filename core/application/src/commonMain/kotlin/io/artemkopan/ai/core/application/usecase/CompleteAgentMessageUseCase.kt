package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.repository.AgentRepository

class CompleteAgentMessageUseCase(
    private val repository: AgentRepository,
) {
    suspend fun execute(
        userScope: String,
        agentId: String,
        messageId: String,
        text: String,
        provider: String? = null,
        model: String? = null,
        usageInputTokens: Int? = null,
        usageOutputTokens: Int? = null,
        usageTotalTokens: Int? = null,
        latencyMs: Long? = null,
    ): Result<io.artemkopan.ai.core.domain.model.AgentState> {
        val userId = parseUserIdOrError(userScope).getOrElse { return Result.failure(it) }
        val aid = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        repository.updateMessage(
            userId = userId,
            agentId = aid,
            messageId = AgentMessageId(messageId),
            text = text,
            status = AgentStatus.DONE.value,
            provider = provider,
            model = model,
            usageInputTokens = usageInputTokens,
            usageOutputTokens = usageOutputTokens,
            usageTotalTokens = usageTotalTokens,
            latencyMs = latencyMs,
        ).getOrElse { return Result.failure(it) }
        return repository.updateAgentStatus(userId, aid, AgentStatus.DONE)
    }
}
