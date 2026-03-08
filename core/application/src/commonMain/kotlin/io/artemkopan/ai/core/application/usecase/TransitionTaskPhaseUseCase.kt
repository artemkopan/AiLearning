package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.TaskId
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.model.TaskPhaseTransition
import io.artemkopan.ai.core.domain.model.TaskStateMachine
import io.artemkopan.ai.core.domain.repository.TaskRepository
import kotlinx.datetime.Clock

class TransitionTaskPhaseUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(
        userScope: String,
        agentId: String,
        taskId: String,
        targetPhase: TaskPhase,
        reason: String = "",
        stepIndex: Int? = null,
    ): Result<TaskPhase> {
        val userId = parseUserIdOrError(userScope).getOrElse { return Result.failure(it) }
        val aid = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        val domainTaskId = TaskId(taskId)
        val now = Clock.System.now().toEpochMilliseconds()
        val task = repository.getActiveTask(userId, aid).getOrElse { return Result.failure(it) }
        if (task != null && task.id != domainTaskId) {
            return Result.failure(IllegalStateException("Active task ${task.id.value} does not match requested task $taskId"))
        }
        val fromPhase = task?.currentPhase ?: TaskPhase.Planning
        if (!TaskStateMachine.canTransition(fromPhase, targetPhase)) {
            return Result.failure(
                IllegalStateException("Invalid phase transition: ${fromPhase.name} -> ${targetPhase.name}")
            )
        }
        repository.appendTransition(
            userId,
            TaskPhaseTransition(
                taskId = domainTaskId,
                fromPhase = fromPhase,
                toPhase = targetPhase,
                reason = reason,
                timestamp = now,
            ),
        ).getOrElse { return Result.failure(it) }
        repository.updateTaskPhase(userId, domainTaskId, targetPhase, now, stepIndex)
            .getOrElse { return Result.failure(it) }
        return Result.success(fromPhase)
    }
}
