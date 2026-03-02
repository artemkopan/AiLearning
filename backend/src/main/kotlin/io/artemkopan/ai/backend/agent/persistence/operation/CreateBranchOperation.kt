package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.*
import io.artemkopan.ai.core.domain.model.AgentBranch
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.*
import org.koin.core.annotation.Single

@Single
internal class CreateBranchOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        agentId: Lazy<AgentId>,
        branch: Lazy<AgentBranch>,
    ): Result<AgentState> = runtime.value.runDb {
        val user = userId.value
        val agent = agentId.value
        val targetBranch = branch.value
        val now = runtime.value.nowMillis()
        val activeBranch = ScopedAgentsTable.selectAll().where {
            (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
        }.singleOrNull()?.get(ScopedAgentsTable.activeBranchId)

        val sourceMessages = ScopedAgentMessagesTable.selectAll()
            .where {
                (ScopedAgentMessagesTable.userId eq user.value) and
                    (ScopedAgentMessagesTable.agentId eq agent.value) and
                    if (activeBranch != null) {
                        ScopedAgentMessagesTable.branchId eq activeBranch
                    } else {
                        ScopedAgentMessagesTable.branchId.isNull()
                    }
            }
            .orderBy(ScopedAgentMessagesTable.createdAt, SortOrder.ASC)
            .toList()

        val checkpoint = sourceMessages.indexOfFirst {
            it[ScopedAgentMessagesTable.id] == targetBranch.checkpointMessageId.value
        }
        require(checkpoint >= 0) { "Checkpoint message not found: ${targetBranch.checkpointMessageId.value}" }
        val messagesToCopy = sourceMessages.subList(0, checkpoint + 1)

        ScopedAgentBranchesTable.insert { row ->
            row[ScopedAgentBranchesTable.userId] = user.value
            row[ScopedAgentBranchesTable.agentId] = agent.value
            row[branchId] = targetBranch.id
            row[name] = targetBranch.name
            row[checkpointMessageId] = targetBranch.checkpointMessageId.value
            row[createdAt] = targetBranch.createdAt.takeIf { it > 0 } ?: now
        }

        var copyIndex = 0
        for (msg in messagesToCopy) {
            copyIndex++
            val newMsgId = "${targetBranch.id}-$copyIndex"
            ScopedAgentMessagesTable.insert { row ->
                row[ScopedAgentMessagesTable.userId] = user.value
                row[id] = newMsgId
                row[ScopedAgentMessagesTable.agentId] = agent.value
                row[branchId] = targetBranch.id
                row[role] = msg[ScopedAgentMessagesTable.role]
                row[text] = msg[ScopedAgentMessagesTable.text]
                row[status] = msg[ScopedAgentMessagesTable.status]
                row[ScopedAgentMessagesTable.createdAt] = msg[ScopedAgentMessagesTable.createdAt]
                row[provider] = msg[ScopedAgentMessagesTable.provider]
                row[model] = msg[ScopedAgentMessagesTable.model]
                row[usageInputTokens] = msg[ScopedAgentMessagesTable.usageInputTokens]
                row[usageOutputTokens] = msg[ScopedAgentMessagesTable.usageOutputTokens]
                row[usageTotalTokens] = msg[ScopedAgentMessagesTable.usageTotalTokens]
                row[latencyMs] = msg[ScopedAgentMessagesTable.latencyMs]
            }
        }

        ScopedAgentsTable.update({
            (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
        }) { row ->
            row[ScopedAgentsTable.activeBranchId] = targetBranch.id
            row[updatedAt] = now
        }

        stateHelpers.value.bumpVersionTx(user, now)
        stateHelpers.value.readStateTx(user)
    }
}
