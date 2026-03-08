package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.model.TaskId
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.repository.TaskRepository
import kotlinx.datetime.Clock

class CreateTaskUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(userScope: String, agentId: String, title: String): Result<AgentTask> {
        val userId = parseUserIdOrError(userScope).getOrElse { return Result.failure(it) }
        val aid = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        val now = Clock.System.now().toEpochMilliseconds()
        val task = AgentTask(
            id = TaskId("task-${now}-${aid.value.take(8)}"),
            agentId = aid,
            title = title.ifBlank { "Task" },
            currentPhase = TaskPhase.Planning,
            steps = emptyList(),
            currentStepIndex = 0,
            createdAt = now,
            updatedAt = now,
        )
        return repository.upsertTask(userId, task).map { task }
    }
}
