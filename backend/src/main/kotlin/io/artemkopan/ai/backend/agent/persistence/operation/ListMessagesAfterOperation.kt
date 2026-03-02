package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.PostgresMappingHelpers
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentMessagesTable
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.annotation.Single

@Single
internal class ListMessagesAfterOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val mapping: Lazy<PostgresMappingHelpers>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        agentId: Lazy<AgentId>,
        createdAtExclusive: Lazy<Long>,
    ): Result<List<AgentMessage>> = runtime.value.runDb {
        ScopedAgentMessagesTable.selectAll()
            .where {
                (ScopedAgentMessagesTable.userId eq userId.value.value) and
                    (ScopedAgentMessagesTable.agentId eq agentId.value.value)
            }
            .filter { it[ScopedAgentMessagesTable.createdAt] > createdAtExclusive.value }
            .sortedWith(
                compareBy<ResultRow>(
                    { it[ScopedAgentMessagesTable.createdAt] },
                    { it[ScopedAgentMessagesTable.id] },
                )
            )
            .map(mapping.value::toMessage)
    }
}
