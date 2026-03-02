package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentBranchesTable
import io.artemkopan.ai.core.domain.model.AgentBranch
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.annotation.Single

@Single
internal class GetBranchesOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
) {
    suspend fun execute(userId: Lazy<UserId>, agentId: Lazy<AgentId>): Result<List<AgentBranch>> = runtime.value.runDb {
        ScopedAgentBranchesTable.selectAll()
            .where {
                (ScopedAgentBranchesTable.userId eq userId.value.value) and
                    (ScopedAgentBranchesTable.agentId eq agentId.value.value)
            }
            .orderBy(ScopedAgentBranchesTable.createdAt, SortOrder.ASC)
            .map { row ->
                AgentBranch(
                    id = row[ScopedAgentBranchesTable.branchId],
                    name = row[ScopedAgentBranchesTable.name],
                    checkpointMessageId = AgentMessageId(row[ScopedAgentBranchesTable.checkpointMessageId]),
                    createdAt = row[ScopedAgentBranchesTable.createdAt],
                )
            }
    }
}
