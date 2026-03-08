package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.repository.TaskRepository

class UpdateTaskUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(userScope: String, task: AgentTask): Result<Unit> {
        val userId = parseUserIdOrError(userScope).getOrElse { return Result.failure(it) }
        return repository.upsertTask(userId, task)
    }
}
