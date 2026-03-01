package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresMappingHelpers
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentContextMemoryTable
import io.artemkopan.ai.core.domain.model.AgentContextMemory
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

internal class GetContextMemoryOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val mapping: Lazy<PostgresMappingHelpers>,
) {
    suspend fun execute(userId: Lazy<UserId>, agentId: Lazy<AgentId>): Result<AgentContextMemory?> = runtime.value.runDb {
        ScopedAgentContextMemoryTable.selectAll()
            .where {
                (ScopedAgentContextMemoryTable.userId eq userId.value.value) and
                    (ScopedAgentContextMemoryTable.agentId eq agentId.value.value)
            }
            .singleOrNull()
            ?.let(mapping.value::toContextMemory)
    }
}
