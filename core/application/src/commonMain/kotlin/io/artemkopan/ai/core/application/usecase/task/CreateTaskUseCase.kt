package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.application.usecase.parseAgentIdOrError
import io.artemkopan.ai.core.application.usecase.parseUserIdOrError
import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.model.TaskId
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.model.TaskStep
import io.artemkopan.ai.core.domain.repository.TaskRepository
import kotlinx.datetime.Clock
import kotlin.random.Random

class CreateTaskUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(
        userId: String,
        agentId: String,
        title: String,
        steps: List<TaskStep>,
    ): Result<AgentTask> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val domainAgentId = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }

        val now = Clock.System.now().toEpochMilliseconds()
        val task = AgentTask(
            id = TaskId(generateTaskId()),
            agentId = domainAgentId,
            title = title,
            currentPhase = TaskPhase.PLANNING,
            steps = steps.mapIndexed { index, step -> step.copy(index = index) },
            currentStepIndex = 0,
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertTask(domainUserId, task).getOrElse { return Result.failure(it) }
        return Result.success(task)
    }

    private fun generateTaskId(): String {
        val a = Random.nextLong().toULong().toString(16)
        val b = Random.nextLong().toULong().toString(16)
        return "task-$a$b"
    }
}
