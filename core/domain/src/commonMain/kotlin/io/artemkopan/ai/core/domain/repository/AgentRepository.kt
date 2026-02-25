package io.artemkopan.ai.core.domain.repository

import io.artemkopan.ai.core.domain.model.AgentDraft
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentContextMemory
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.RetrievedContextChunk
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.model.UserId

interface AgentRepository {
    suspend fun getState(userId: UserId): Result<AgentState>
    suspend fun createAgent(userId: UserId): Result<AgentState>
    suspend fun selectAgent(userId: UserId, agentId: AgentId): Result<AgentState>
    suspend fun updateAgentDraft(userId: UserId, agentId: AgentId, draft: AgentDraft): Result<AgentState>
    suspend fun closeAgent(userId: UserId, agentId: AgentId): Result<AgentState>
    suspend fun updateAgentStatus(userId: UserId, agentId: AgentId, status: AgentStatus): Result<AgentState>
    suspend fun appendMessage(userId: UserId, agentId: AgentId, message: AgentMessage): Result<AgentState>
    suspend fun updateMessage(
        userId: UserId,
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
    suspend fun findMessage(userId: UserId, agentId: AgentId, messageId: AgentMessageId): Result<AgentMessage?>
    suspend fun hasProcessingMessage(userId: UserId, agentId: AgentId): Result<Boolean>
    suspend fun getContextMemory(userId: UserId, agentId: AgentId): Result<AgentContextMemory?>
    suspend fun upsertContextMemory(userId: UserId, memory: AgentContextMemory): Result<Unit>
    suspend fun listMessagesAfter(userId: UserId, agentId: AgentId, createdAtExclusive: Long): Result<List<AgentMessage>>
    suspend fun upsertMessageEmbedding(
        userId: UserId,
        agentId: AgentId,
        messageId: AgentMessageId,
        chunkIndex: Int,
        textChunk: String,
        embedding: List<Double>,
        createdAt: Long,
    ): Result<Unit>
    suspend fun searchRelevantContext(
        userId: UserId,
        agentId: AgentId,
        queryEmbedding: List<Double>,
        limit: Int,
        minScore: Double,
    ): Result<List<RetrievedContextChunk>>
}
