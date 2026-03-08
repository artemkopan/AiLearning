package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.application.usecase.parseUserIdOrError
import io.artemkopan.ai.core.domain.model.TaskId
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.model.TaskPhaseTransition
import io.artemkopan.ai.core.domain.repository.TaskRepository
import kotlinx.datetime.Clock

class TransitionTaskPhaseUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(
        userId: String,
        taskId: String,
        fromPhase: TaskPhase,
        targetPhase: TaskPhase,
        reason: String = "",
        newStepIndex: Int? = null,
    ): Result<Unit> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val domainTaskId = TaskId(taskId)

        val now = Clock.System.now().toEpochMilliseconds()

        repository.appendTransition(
            domainUserId,
            TaskPhaseTransition(
                taskId = domainTaskId,
                fromPhase = fromPhase,
                toPhase = targetPhase,
                reason = reason,
                timestamp = now,
            ),
        ).getOrElse { return Result.failure(it) }

        return repository.updateTaskPhase(domainUserId, domainTaskId, targetPhase, now, stepIndex = newStepIndex)
    }
}
