package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.STATUS_PROCESSING
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentMessagesTable
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.annotation.Single

@Single
internal class HasProcessingMessageOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
) {
    suspend fun execute(userId: Lazy<UserId>, agentId: Lazy<AgentId>): Result<Boolean> = runtime.value.runDb {
        ScopedAgentMessagesTable.selectAll().where {
            (ScopedAgentMessagesTable.userId eq userId.value.value) and
                (ScopedAgentMessagesTable.agentId eq agentId.value.value) and
                (ScopedAgentMessagesTable.status eq STATUS_PROCESSING)
        }.any()
    }
}
