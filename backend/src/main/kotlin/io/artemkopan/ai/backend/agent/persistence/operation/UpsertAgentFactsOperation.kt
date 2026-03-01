package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentFactsTable
import io.artemkopan.ai.core.domain.model.AgentFacts
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

internal class UpsertAgentFactsOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
) {
    suspend fun execute(userId: Lazy<UserId>, facts: Lazy<AgentFacts>): Result<Unit> = runtime.value.runDb {
        val user = userId.value
        val agentFacts = facts.value
        val now = runtime.value.nowMillis()
        val existing = ScopedAgentFactsTable.selectAll()
            .where {
                (ScopedAgentFactsTable.userId eq user.value) and
                    (ScopedAgentFactsTable.agentId eq agentFacts.agentId.value)
            }
            .singleOrNull()

        if (existing == null) {
            ScopedAgentFactsTable.insert { row ->
                row[ScopedAgentFactsTable.userId] = user.value
                row[agentId] = agentFacts.agentId.value
                row[factsJson] = agentFacts.factsJson
                row[updatedAt] = agentFacts.updatedAt.takeIf { it > 0 } ?: now
            }
        } else {
            ScopedAgentFactsTable.update({
                (ScopedAgentFactsTable.userId eq user.value) and
                    (ScopedAgentFactsTable.agentId eq agentFacts.agentId.value)
            }) { row ->
                row[factsJson] = agentFacts.factsJson
                row[updatedAt] = agentFacts.updatedAt.takeIf { it > 0 } ?: now
            }
        }
    }
}
