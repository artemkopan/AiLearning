package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentTasksTable
import io.artemkopan.ai.core.domain.model.TaskId
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update
import org.koin.core.annotation.Single

@Single
internal class UpdateTaskPhaseOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
) {
    suspend fun execute(
        userId: Lazy<UserId>,
        taskId: Lazy<TaskId>,
        phase: Lazy<TaskPhase>,
        updatedAt: Lazy<Long>,
    ): Result<Unit> = runtime.value.runDb {
        ScopedAgentTasksTable.update(
            where = {
                (ScopedAgentTasksTable.userId eq userId.value.value) and
                    (ScopedAgentTasksTable.taskId eq taskId.value.value)
            }
        ) {
            it[currentPhase] = phase.value.name.lowercase()
            it[this.updatedAt] = updatedAt.value
        }
    }
}
