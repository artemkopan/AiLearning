package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresStateHelpers
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentStateTable
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentsTable
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

internal class SelectAgentOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(userId: Lazy<UserId>, agentId: Lazy<AgentId>): Result<AgentState> = runtime.value.runDb {
        val user = userId.value
        val agent = agentId.value
        val exists = ScopedAgentsTable.selectAll()
            .where { (ScopedAgentsTable.userId eq user.value) and (ScopedAgentsTable.id eq agent.value) }
            .any()
        require(exists) { "Agent not found: ${agent.value}" }

        val meta = stateHelpers.value.loadMetaTx(user)
        val now = runtime.value.nowMillis()
        ScopedAgentStateTable.update({ ScopedAgentStateTable.userId eq user.value }) { row ->
            row[activeAgentId] = agent.value
            row[version] = meta[ScopedAgentStateTable.version] + 1
            row[updatedAt] = now
        }

        stateHelpers.value.readStateTx(user)
    }
}
