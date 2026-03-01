package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.*
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

internal class DeleteBranchOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        agentId: Lazy<AgentId>,
        branchId: Lazy<String>,
    ): Result<AgentState> = runtime.value.runDb {
        val user = userId.value
        val agent = agentId.value
        val branch = branchId.value
        val now = runtime.value.nowMillis()

        ScopedAgentMessagesTable.deleteWhere {
            (ScopedAgentMessagesTable.userId eq user.value) and
                (ScopedAgentMessagesTable.agentId eq agent.value) and
                (ScopedAgentMessagesTable.branchId eq branch)
        }
        ScopedAgentBranchesTable.deleteWhere {
            (ScopedAgentBranchesTable.userId eq user.value) and
                (ScopedAgentBranchesTable.agentId eq agent.value) and
                (ScopedAgentBranchesTable.branchId eq branch)
        }

        val currentActive = ScopedAgentsTable.selectAll().where {
            (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
        }.singleOrNull()?.get(ScopedAgentsTable.activeBranchId)

        if (currentActive == branch) {
            ScopedAgentsTable.update({
                (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
            }) { row ->
                row[ScopedAgentsTable.activeBranchId] = null
                row[updatedAt] = now
            }
        }

        stateHelpers.value.bumpVersionTx(user, now)
        stateHelpers.value.readStateTx(user)
    }
}
