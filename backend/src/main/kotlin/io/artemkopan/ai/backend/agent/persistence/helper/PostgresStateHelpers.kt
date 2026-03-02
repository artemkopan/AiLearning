package io.artemkopan.ai.backend.agent.persistence.helper

import io.artemkopan.ai.core.domain.model.*
import org.jetbrains.exposed.sql.*
import org.koin.core.annotation.Single

@Single
internal class PostgresStateHelpers(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val mapping: Lazy<PostgresMappingHelpers>,
) {
    fun readStateTx(userId: UserId): AgentState {
        val meta = loadMetaTx(userId)
        val contextMemoryByAgent = ScopedAgentContextMemoryTable.selectAll()
            .where { ScopedAgentContextMemoryTable.userId eq userId.value }
            .associateBy { it[ScopedAgentContextMemoryTable.agentId] }

        val allMessageRows = ScopedAgentMessagesTable.selectAll()
            .where { ScopedAgentMessagesTable.userId eq userId.value }
            .orderBy(ScopedAgentMessagesTable.createdAt, SortOrder.ASC)
            .orderBy(ScopedAgentMessagesTable.id, SortOrder.ASC)
            .toList()
            .groupBy { it[ScopedAgentMessagesTable.agentId] }

        val branchesByAgent = ScopedAgentBranchesTable.selectAll()
            .where { ScopedAgentBranchesTable.userId eq userId.value }
            .orderBy(ScopedAgentBranchesTable.createdAt, SortOrder.ASC)
            .toList()
            .groupBy { it[ScopedAgentBranchesTable.agentId] }

        val agents = ScopedAgentsTable.selectAll()
            .where { ScopedAgentsTable.userId eq userId.value }
            .orderBy(ScopedAgentsTable.position, SortOrder.ASC)
            .map { row ->
                val agentIdStr = row[ScopedAgentsTable.id]
                val activeBranch = row[ScopedAgentsTable.activeBranchId]
                val agentMessages = allMessageRows[agentIdStr].orEmpty()
                val filteredMessages = if (activeBranch != null) {
                    agentMessages.filter { it[ScopedAgentMessagesTable.branchId] == activeBranch }
                } else {
                    agentMessages.filter { it[ScopedAgentMessagesTable.branchId] == null }
                }
                val branches = branchesByAgent[agentIdStr].orEmpty().map { branchRow ->
                    AgentBranch(
                        id = branchRow[ScopedAgentBranchesTable.branchId],
                        name = branchRow[ScopedAgentBranchesTable.name],
                        checkpointMessageId = AgentMessageId(branchRow[ScopedAgentBranchesTable.checkpointMessageId]),
                        createdAt = branchRow[ScopedAgentBranchesTable.createdAt],
                    )
                }
                mapping.value.toAgent(
                    row = row,
                    messages = filteredMessages.map(mapping.value::toMessage),
                    contextMemory = contextMemoryByAgent[agentIdStr],
                    branches = branches,
                )
            }

        val activeIdRaw = meta[ScopedAgentStateTable.activeAgentId]
        val activeExists = activeIdRaw != null && agents.any { it.id.value == activeIdRaw }
        val activeId = when {
            activeExists -> AgentId(activeIdRaw)
            agents.isEmpty() -> null
            else -> {
                val fallback = agents.first().id.value
                ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) { row ->
                    row[activeAgentId] = fallback
                }
                AgentId(fallback)
            }
        }

        return AgentState(
            agents = agents,
            activeAgentId = activeId,
            version = meta[ScopedAgentStateTable.version],
        )
    }

    fun loadMetaTx(userId: UserId): ResultRow {
        val existing = ScopedAgentStateTable.selectAll()
            .where { ScopedAgentStateTable.userId eq userId.value }
            .singleOrNull()
        if (existing != null) return existing

        val now = runtime.value.nowMillis()
        ScopedAgentStateTable.insert { row ->
            row[ScopedAgentStateTable.userId] = userId.value
            row[activeAgentId] = null
            row[nextAgentCounter] = 1
            row[version] = 1
            row[updatedAt] = now
        }

        return ScopedAgentStateTable.selectAll()
            .where { ScopedAgentStateTable.userId eq userId.value }
            .singleOrNull()
            ?: error("Scoped agent state metadata row is missing")
    }

    fun bumpVersionTx(userId: UserId, now: Long = runtime.value.nowMillis()) {
        val meta = loadMetaTx(userId)
        ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq userId.value }) { row ->
            row[version] = meta[ScopedAgentStateTable.version] + 1
            row[updatedAt] = now
        }
    }
}
