package io.artemkopan.ai.core.domain.repository

import io.artemkopan.ai.core.domain.model.AgentDraft
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentResponse
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus

interface AgentRepository {
    suspend fun getState(): Result<AgentState>
    suspend fun createAgent(): Result<AgentState>
    suspend fun selectAgent(agentId: AgentId): Result<AgentState>
    suspend fun updateAgentDraft(agentId: AgentId, draft: AgentDraft): Result<AgentState>
    suspend fun closeAgent(agentId: AgentId): Result<AgentState>
    suspend fun updateAgentStatus(agentId: AgentId, status: AgentStatus): Result<AgentState>
    suspend fun saveGenerationResult(agentId: AgentId, response: AgentResponse, status: AgentStatus): Result<AgentState>
}
