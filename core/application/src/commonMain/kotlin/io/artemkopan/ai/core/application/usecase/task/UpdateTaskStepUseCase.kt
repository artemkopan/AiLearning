package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.application.usecase.parseUserIdOrError
import io.artemkopan.ai.core.domain.model.TaskId
import io.artemkopan.ai.core.domain.model.TaskStepStatus
import io.artemkopan.ai.core.domain.repository.TaskRepository

class UpdateTaskStepUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(
        userId: String,
        taskId: String,
        stepIndex: Int,
        status: TaskStepStatus,
        result: String = "",
    ): Result<Unit> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        return repository.updateTaskStep(domainUserId, TaskId(taskId), stepIndex, status, result)
    }
}
