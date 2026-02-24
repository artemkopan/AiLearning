package io.artemkopan.ai.core.domain.repository

import io.artemkopan.ai.core.domain.model.AgentDraft
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus

interface AgentRepository {
    suspend fun getState(): Result<AgentState>
    suspend fun createAgent(): Result<AgentState>
    suspend fun selectAgent(agentId: AgentId): Result<AgentState>
    suspend fun updateAgentDraft(agentId: AgentId, draft: AgentDraft): Result<AgentState>
    suspend fun closeAgent(agentId: AgentId): Result<AgentState>
    suspend fun updateAgentStatus(agentId: AgentId, status: AgentStatus): Result<AgentState>
    suspend fun appendMessage(agentId: AgentId, message: AgentMessage): Result<AgentState>
    suspend fun updateMessage(
        agentId: AgentId,
        messageId: AgentMessageId,
        text: String? = null,
        status: String? = null,
        provider: String? = null,
        model: String? = null,
        usageInputTokens: Int? = null,
        usageOutputTokens: Int? = null,
        usageTotalTokens: Int? = null,
        latencyMs: Long? = null,
    ): Result<AgentState>
    suspend fun findMessage(agentId: AgentId, messageId: AgentMessageId): Result<AgentMessage?>
    suspend fun hasProcessingMessage(agentId: AgentId): Result<Boolean>
}
