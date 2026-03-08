package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.*

internal suspend fun PostgresDbRuntime.readState(userId: UserId): AgentState {
    val runtime = this
    return runDb {
        val meta = loadMetaTx(runtime, userId)
        val allMessageRows = ScopedAgentMessagesTable.selectAll()
            .where { ScopedAgentMessagesTable.userId eq userId.value }
            .orderBy(ScopedAgentMessagesTable.createdAt, SortOrder.ASC)
            .orderBy(ScopedAgentMessagesTable.id, SortOrder.ASC)
            .toList()
            .groupBy { it[ScopedAgentMessagesTable.agentId] }

        val agents = ScopedAgentsTable.selectAll()
            .where { ScopedAgentsTable.userId eq userId.value }
            .orderBy(ScopedAgentsTable.position, SortOrder.ASC)
            .map { row ->
                val agentIdStr = row[ScopedAgentsTable.id]
                val agentMessages = allMessageRows[agentIdStr].orEmpty()
                    .filter { it[ScopedAgentMessagesTable.branchId] == row[ScopedAgentsTable.activeBranchId] }
                row.toAgent(agentMessages.map { it.toMessage() })
            }

        val activeIdRaw = meta[ScopedAgentStateTable.activeAgentId]
        val activeExists = activeIdRaw != null && agents.any { it.id.value == activeIdRaw }
        val activeId = when {
            activeExists -> AgentId(activeIdRaw!!)
            agents.isEmpty() -> null
            else -> {
                ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) {
                    it[ScopedAgentStateTable.activeAgentId] = agents.first().id.value
                }
                AgentId(agents.first().id.value)
            }
        }

        AgentState(agents = agents, activeAgentId = activeId, version = meta[ScopedAgentStateTable.version])
    }.getOrThrow()
}

internal fun loadMetaTx(runtime: PostgresDbRuntime, userId: UserId): ResultRow {
    val existing = ScopedAgentStateTable.selectAll()
        .where { ScopedAgentStateTable.userId eq userId.value }
        .singleOrNull()
    if (existing != null) return existing

    ScopedAgentStateTable.insert {
        it[ScopedAgentStateTable.userId] = userId.value
        it[ScopedAgentStateTable.activeAgentId] = null
        it[ScopedAgentStateTable.nextAgentCounter] = 1
        it[ScopedAgentStateTable.version] = 1
        it[ScopedAgentStateTable.updatedAt] = runtime.nowMillis()
    }
    return ScopedAgentStateTable.selectAll()
        .where { ScopedAgentStateTable.userId eq userId.value }
        .single()
}
