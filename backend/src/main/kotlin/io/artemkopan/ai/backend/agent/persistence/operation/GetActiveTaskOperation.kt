package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentTasksTable
import io.artemkopan.ai.backend.agent.persistence.helper.parseTaskPhaseFromDb
import io.artemkopan.ai.backend.agent.persistence.helper.toTaskSteps
import io.artemkopan.ai.core.domain.model.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.annotation.Single

@Single
internal class GetActiveTaskOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(userId: Lazy<UserId>, agentId: Lazy<AgentId>): Result<AgentTask?> = runtime.value.runDb {
        ScopedAgentTasksTable.selectAll()
            .where {
                (ScopedAgentTasksTable.userId eq userId.value.value) and
                    (ScopedAgentTasksTable.agentId eq agentId.value.value) and
                    (ScopedAgentTasksTable.currentPhase neq TaskPhase.DONE.name.lowercase())
            }
            .orderBy(ScopedAgentTasksTable.updatedAt)
            .lastOrNull()
            ?.let { row ->
                AgentTask(
                    id = TaskId(row[ScopedAgentTasksTable.taskId]),
                    agentId = agentId.value,
                    title = row[ScopedAgentTasksTable.title],
                    currentPhase = parseTaskPhaseFromDb(row[ScopedAgentTasksTable.currentPhase]),
                    steps = row[ScopedAgentTasksTable.stepsJson].toTaskSteps(json.value),
                    currentStepIndex = row[ScopedAgentTasksTable.currentStepIndex],
                    createdAt = row[ScopedAgentTasksTable.createdAt],
                    updatedAt = row[ScopedAgentTasksTable.updatedAt],
                    planJson = row.getOrNull(ScopedAgentTasksTable.planJson) ?: "",
                    validationJson = row.getOrNull(ScopedAgentTasksTable.validationJson) ?: "",
                )
            }
    }
}
