package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.*
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

internal class SwitchBranchOperation(
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
        val now = runtime.value.nowMillis()
        val targetBranchId = branchId.value.takeIf { it != MAIN_BRANCH_ID }

        if (targetBranchId != null) {
            val exists = ScopedAgentBranchesTable.selectAll().where {
                (ScopedAgentBranchesTable.userId eq user.value) and
                    (ScopedAgentBranchesTable.agentId eq agent.value) and
                    (ScopedAgentBranchesTable.branchId eq targetBranchId)
            }.any()
            require(exists) { "Branch not found: ${branchId.value}" }
        }

        ScopedAgentsTable.update({
            (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
        }) { row ->
            row[ScopedAgentsTable.activeBranchId] = targetBranchId
            row[updatedAt] = now
        }

        stateHelpers.value.bumpVersionTx(user, now)
        stateHelpers.value.readStateTx(user)
    }
}
