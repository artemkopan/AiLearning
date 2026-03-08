package io.artemkopan.ai.core.domain.repository

import io.artemkopan.ai.core.domain.model.*

interface TaskRepository {
    suspend fun getActiveTask(userId: UserId, agentId: AgentId): Result<AgentTask?>
    suspend fun upsertTask(userId: UserId, task: AgentTask): Result<Unit>
    suspend fun updateTaskPhase(userId: UserId, taskId: TaskId, phase: TaskPhase, updatedAt: Long, stepIndex: Int? = null): Result<Unit>
    suspend fun updateTaskStep(
        userId: UserId,
        taskId: TaskId,
        stepIndex: Int,
        status: TaskStepStatus,
        result: String,
    ): Result<Unit>
    suspend fun appendTransition(userId: UserId, transition: TaskPhaseTransition): Result<Unit>
}
