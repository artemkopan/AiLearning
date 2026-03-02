package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.*
import io.artemkopan.ai.core.domain.model.AgentDraft
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.koin.core.annotation.Single

@Single
internal class UpdateAgentDraftOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        agentId: Lazy<AgentId>,
        draft: Lazy<AgentDraft>,
    ): Result<AgentState> = runtime.value.runDb {
        val user = userId.value
        val agent = agentId.value
        val agentDraft = draft.value
        val exists = ScopedAgentsTable.selectAll().where {
            (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
        }.singleOrNull()
        require(exists != null) { "Agent not found: ${agent.value}" }

        val now = runtime.value.nowMillis()
        val contextStrategy = contextStrategyValue(agentDraft.contextConfig)
        val contextRecentN = contextRecentMessagesNValue(agentDraft.contextConfig)
        val contextSummarizeEveryK = contextSummarizeEveryKValue(agentDraft.contextConfig)
        ScopedAgentsTable.update({
            (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
        }) { row ->
            row[model] = agentDraft.model
            row[maxOutputTokens] = agentDraft.maxOutputTokens
            row[temperature] = agentDraft.temperature
            row[stopSequences] = agentDraft.stopSequences
            row[agentMode] = agentDraft.agentMode.ifBlank { "default" }
            row[ScopedAgentsTable.contextStrategy] = contextStrategy
            row[ScopedAgentsTable.contextRecentMessagesN] = contextRecentN
            row[ScopedAgentsTable.contextSummarizeEveryK] = contextSummarizeEveryK
            row[updatedAt] = now
        }

        stateHelpers.value.bumpVersionTx(user, now)
        stateHelpers.value.readStateTx(user)
    }
}
