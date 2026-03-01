package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresStateHelpers
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentsTable
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentStatus
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update

internal class UpdateAgentStatusOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        agentId: Lazy<AgentId>,
        status: Lazy<AgentStatus>,
    ): Result<AgentState> = runtime.value.runDb {
        val user = userId.value
        val agent = agentId.value
        val updated = ScopedAgentsTable.update({
            (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value)
        }) { row ->
            row[ScopedAgentsTable.status] = status.value.value
            row[updatedAt] = runtime.value.nowMillis()
        }
        require(updated > 0) { "Agent not found: ${agent.value}" }

        stateHelpers.value.bumpVersionTx(user)
        stateHelpers.value.readStateTx(user)
    }
}
