package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.backend.agent.persistence.operation.*
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.TaskRepository
import org.koin.core.annotation.Single

@Single(binds = [TaskRepository::class])
class PostgresTaskRepository internal constructor(
    private val getActiveTaskOp: Lazy<GetActiveTaskOperation>,
    private val upsertTaskOp: Lazy<UpsertTaskOperation>,
    private val updateTaskPhaseOp: Lazy<UpdateTaskPhaseOperation>,
    private val updateTaskStepOp: Lazy<UpdateTaskStepOperation>,
    private val appendTransitionOp: Lazy<AppendTaskTransitionOperation>,
) : TaskRepository {

    override suspend fun getActiveTask(userId: UserId, agentId: AgentId): Result<AgentTask?> =
        getActiveTaskOp.value.execute(
            userId = lazyArg { userId },
            agentId = lazyArg { agentId },
        )

    override suspend fun upsertTask(userId: UserId, task: AgentTask): Result<Unit> =
        upsertTaskOp.value.execute(
            userId = lazyArg { userId },
            task = lazyArg { task },
        )

    override suspend fun updateTaskPhase(userId: UserId, taskId: TaskId, phase: TaskPhase, updatedAt: Long): Result<Unit> =
        updateTaskPhaseOp.value.execute(
            userId = lazyArg { userId },
            taskId = lazyArg { taskId },
            phase = lazyArg { phase },
            updatedAt = lazyArg { updatedAt },
        )

    override suspend fun updateTaskStep(
        userId: UserId,
        taskId: TaskId,
        stepIndex: Int,
        status: TaskStepStatus,
        result: String,
    ): Result<Unit> = updateTaskStepOp.value.execute(
        userId = lazyArg { userId },
        taskId = lazyArg { taskId },
        stepIndex = lazyArg { stepIndex },
        status = lazyArg { status },
        result = lazyArg { result },
    )

    override suspend fun appendTransition(userId: UserId, transition: TaskPhaseTransition): Result<Unit> =
        appendTransitionOp.value.execute(
            userId = lazyArg { userId },
            transition = lazyArg { transition },
        )

    private fun <T> lazyArg(valueProvider: () -> T): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE) { valueProvider() }
}
