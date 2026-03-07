package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentTasksTable
import io.artemkopan.ai.backend.agent.persistence.helper.toJson
import io.artemkopan.ai.backend.agent.persistence.helper.toTaskSteps
import io.artemkopan.ai.core.domain.model.TaskId
import io.artemkopan.ai.core.domain.model.TaskStepStatus
import io.artemkopan.ai.core.domain.model.UserId
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.koin.core.annotation.Single

@Single
internal class UpdateTaskStepOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        taskId: Lazy<TaskId>,
        stepIndex: Lazy<Int>,
        status: Lazy<TaskStepStatus>,
        result: Lazy<String>,
    ): Result<Unit> = runtime.value.runDb {
        val now = runtime.value.nowMillis()

        val row = ScopedAgentTasksTable.selectAll()
            .where {
                (ScopedAgentTasksTable.userId eq userId.value.value) and
                    (ScopedAgentTasksTable.taskId eq taskId.value.value)
            }
            .singleOrNull() ?: return@runDb

        val steps = row[ScopedAgentTasksTable.stepsJson].toTaskSteps(json.value)
            .toMutableList()

        val idx = stepIndex.value
        if (idx in steps.indices) {
            steps[idx] = steps[idx].copy(status = status.value, result = result.value)
        }

        val nextIndex = steps.indexOfFirst {
            it.status == TaskStepStatus.PENDING || it.status == TaskStepStatus.IN_PROGRESS
        }.takeIf { it >= 0 } ?: steps.size

        ScopedAgentTasksTable.update(
            where = {
                (ScopedAgentTasksTable.userId eq userId.value.value) and
                    (ScopedAgentTasksTable.taskId eq taskId.value.value)
            }
        ) {
            it[stepsJson] = steps.toList().toJson(json.value)
            it[currentStepIndex] = nextIndex
            it[updatedAt] = now
        }
    }
}
