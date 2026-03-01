package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresStateHelpers
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentContextMemoryTable
import io.artemkopan.ai.core.domain.model.AgentContextMemory
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

internal class UpsertContextMemoryOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val stateHelpers: Lazy<PostgresStateHelpers>,
) {
    suspend fun execute(userId: Lazy<UserId>, memory: Lazy<AgentContextMemory>): Result<Unit> = runtime.value.runDb {
        val user = userId.value
        val contextMemory = memory.value
        val now = runtime.value.nowMillis()
        val existing = ScopedAgentContextMemoryTable.selectAll()
            .where {
                (ScopedAgentContextMemoryTable.userId eq user.value) and
                    (ScopedAgentContextMemoryTable.agentId eq contextMemory.agentId.value)
            }
            .singleOrNull()

        if (existing == null) {
            ScopedAgentContextMemoryTable.insert { row ->
                row[ScopedAgentContextMemoryTable.userId] = user.value
                row[agentId] = contextMemory.agentId.value
                row[summaryText] = contextMemory.summaryText
                row[summarizedUntilCreatedAt] = contextMemory.summarizedUntilCreatedAt
                row[updatedAt] = contextMemory.updatedAt.takeIf { it > 0 } ?: now
            }
        } else {
            ScopedAgentContextMemoryTable.update({
                (ScopedAgentContextMemoryTable.userId eq user.value) and
                    (ScopedAgentContextMemoryTable.agentId eq contextMemory.agentId.value)
            }) { row ->
                row[summaryText] = contextMemory.summaryText
                row[summarizedUntilCreatedAt] = contextMemory.summarizedUntilCreatedAt
                row[updatedAt] = contextMemory.updatedAt.takeIf { it > 0 } ?: now
            }
        }

        stateHelpers.value.bumpVersionTx(user, now)
    }
}
