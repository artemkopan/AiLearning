package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentFactsTable
import io.artemkopan.ai.core.domain.model.AgentFacts
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

internal class GetAgentFactsOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
) {
    suspend fun execute(userId: Lazy<UserId>, agentId: Lazy<AgentId>): Result<AgentFacts?> = runtime.value.runDb {
        ScopedAgentFactsTable.selectAll()
            .where {
                (ScopedAgentFactsTable.userId eq userId.value.value) and
                    (ScopedAgentFactsTable.agentId eq agentId.value.value)
            }
            .singleOrNull()
            ?.let {
                AgentFacts(
                    agentId = agentId.value,
                    factsJson = it[ScopedAgentFactsTable.factsJson],
                    updatedAt = it[ScopedAgentFactsTable.updatedAt],
                )
            }
    }
}
