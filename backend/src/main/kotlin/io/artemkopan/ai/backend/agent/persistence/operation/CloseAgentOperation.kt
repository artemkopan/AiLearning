package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.*
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.koin.core.annotation.Single

@Single
internal class CloseAgentOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(userId: Lazy<UserId>, agentId: Lazy<AgentId>): Result<AgentState> = runtime.value.runDb {
        val user = userId.value
        val agent = agentId.value
        val allBefore = ScopedAgentsTable.selectAll()
            .where { ScopedAgentsTable.userId eq user.value }
            .orderBy(ScopedAgentsTable.position, SortOrder.ASC)
            .map { it[ScopedAgentsTable.id] }
        val closedIndex = allBefore.indexOf(agent.value)
        require(closedIndex >= 0) { "Agent not found: ${agent.value}" }

        ScopedAgentMessagesTable.deleteWhere {
            (ScopedAgentMessagesTable.userId eq user.value) and (ScopedAgentMessagesTable.agentId eq agent.value)
        }
        ScopedAgentContextMemoryTable.deleteWhere {
            (ScopedAgentContextMemoryTable.userId eq user.value) and
                (ScopedAgentContextMemoryTable.agentId eq agent.value)
        }
        ScopedAgentMessageEmbeddingsTable.deleteWhere {
            (ScopedAgentMessageEmbeddingsTable.userId eq user.value) and
                (ScopedAgentMessageEmbeddingsTable.agentId eq agent.value)
        }
        ScopedAgentFactsTable.deleteWhere {
            (ScopedAgentFactsTable.userId eq user.value) and
                (ScopedAgentFactsTable.agentId eq agent.value)
        }
        ScopedAgentBranchesTable.deleteWhere {
            (ScopedAgentBranchesTable.userId eq user.value) and
                (ScopedAgentBranchesTable.agentId eq agent.value)
        }
        ScopedAgentsTable.deleteWhere {
            (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
        }

        val remaining = ScopedAgentsTable.selectAll()
            .where { ScopedAgentsTable.userId eq user.value }
            .orderBy(ScopedAgentsTable.position, SortOrder.ASC)
            .map { it[ScopedAgentsTable.id] }

        val meta = stateHelpers.value.loadMetaTx(user)
        val currentActive = meta[ScopedAgentStateTable.activeAgentId]
        val newActive = when {
            remaining.isEmpty() -> null
            currentActive == agent.value -> {
                if (closedIndex >= remaining.size) remaining.last() else remaining[closedIndex]
            }

            currentActive != null && remaining.contains(currentActive) -> currentActive
            else -> remaining.first()
        }

        val now = runtime.value.nowMillis()
        ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq user.value }) { row ->
            row[activeAgentId] = newActive
            row[version] = meta[ScopedAgentStateTable.version] + 1
            row[updatedAt] = now
        }

        stateHelpers.value.readStateTx(user)
    }
}
