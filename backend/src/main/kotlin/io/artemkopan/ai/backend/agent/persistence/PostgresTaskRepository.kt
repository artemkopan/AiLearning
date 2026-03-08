package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.TaskRepository
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.koin.core.annotation.Single

@Single(binds = [io.artemkopan.ai.core.domain.repository.TaskRepository::class])
internal class PostgresTaskRepository(
    private val runtime: PostgresDbRuntime,
    private val json: Json,
) : TaskRepository {

    override suspend fun getActiveTask(userId: UserId, agentId: io.artemkopan.ai.core.domain.model.AgentId): Result<AgentTask?> =
        runtime.runDb {
            ScopedAgentTasksTable.selectAll()
                .where {
                    (ScopedAgentTasksTable.userId eq userId.value) and
                        (ScopedAgentTasksTable.agentId eq agentId.value)
                }
                .orderBy(ScopedAgentTasksTable.updatedAt, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.toTask()
        }

    override suspend fun upsertTask(userId: UserId, task: AgentTask): Result<Unit> = runtime.runDb {
        val now = runtime.nowMillis()
        val exists = ScopedAgentTasksTable.selectAll()
            .where {
                (ScopedAgentTasksTable.userId eq userId.value) and
                    (ScopedAgentTasksTable.taskId eq task.id.value)
            }
            .count() > 0

        if (exists) {
            ScopedAgentTasksTable.update({
                (ScopedAgentTasksTable.userId eq userId.value) and (ScopedAgentTasksTable.taskId eq task.id.value)
            }) { row ->
                row[ScopedAgentTasksTable.title] = task.title
                row[ScopedAgentTasksTable.currentPhase] = task.currentPhase.name.lowercase()
                row[ScopedAgentTasksTable.currentStepIndex] = task.currentStepIndex
                row[ScopedAgentTasksTable.stepsJson] = task.steps.toTaskStepsJson(json)
                row[ScopedAgentTasksTable.planJson] = task.planJson
                row[ScopedAgentTasksTable.validationJson] = task.validationJson
                row[ScopedAgentTasksTable.updatedAt] = now
            }
        } else {
            ScopedAgentTasksTable.insert {
                it[ScopedAgentTasksTable.userId] = userId.value
                it[ScopedAgentTasksTable.taskId] = task.id.value
                it[ScopedAgentTasksTable.agentId] = task.agentId.value
                it[ScopedAgentTasksTable.title] = task.title
                it[ScopedAgentTasksTable.currentPhase] = task.currentPhase.name.lowercase()
                it[ScopedAgentTasksTable.currentStepIndex] = task.currentStepIndex
                it[ScopedAgentTasksTable.stepsJson] = task.steps.toTaskStepsJson(json)
                it[ScopedAgentTasksTable.planJson] = task.planJson
                it[ScopedAgentTasksTable.validationJson] = task.validationJson
                it[ScopedAgentTasksTable.createdAt] = now
                it[ScopedAgentTasksTable.updatedAt] = now
            }
        }
        Unit
    }

    override suspend fun updateTaskPhase(userId: UserId, taskId: TaskId, phase: TaskPhase, updatedAt: Long, stepIndex: Int?): Result<Unit> =
        runtime.runDb {
            ScopedAgentTasksTable.update({
                (ScopedAgentTasksTable.userId eq userId.value) and (ScopedAgentTasksTable.taskId eq taskId.value)
            }) { row ->
                row[ScopedAgentTasksTable.currentPhase] = phase.name.lowercase()
                row[ScopedAgentTasksTable.updatedAt] = updatedAt
                stepIndex?.let { row[ScopedAgentTasksTable.currentStepIndex] = it }
            }
            Unit
        }

    override suspend fun updateTaskStep(
        userId: UserId,
        taskId: TaskId,
        stepIndex: Int,
        status: TaskStepStatus,
        result: String,
    ): Result<Unit> = runtime.runDb {
        val taskObj = ScopedAgentTasksTable.selectAll()
            .where {
                (ScopedAgentTasksTable.userId eq userId.value) and (ScopedAgentTasksTable.taskId eq taskId.value)
            }
            .singleOrNull() ?: throw IllegalStateException("Task not found")
        if (stepIndex > 100) throw IllegalArgumentException("stepIndex too large: $stepIndex")
        val steps = (taskObj[ScopedAgentTasksTable.stepsJson] ?: "").toTaskSteps(json).toMutableList()
        while (steps.size <= stepIndex) {
            steps.add(TaskStep(
                index = steps.size,
                phase = TaskPhase.PLANNING,
                description = "",
                expectedAction = "",
                status = TaskStepStatus.PENDING,
                result = "",
            ))
        }
        steps[stepIndex] = steps[stepIndex].copy(status = status, result = result)
        ScopedAgentTasksTable.update({
            (ScopedAgentTasksTable.userId eq userId.value) and (ScopedAgentTasksTable.taskId eq taskId.value)
        }) { row ->
            row[ScopedAgentTasksTable.stepsJson] = steps.toTaskStepsJson(json)
            row[ScopedAgentTasksTable.updatedAt] = runtime.nowMillis()
        }
        Unit
    }

    override suspend fun appendTransition(userId: UserId, transition: TaskPhaseTransition): Result<Unit> = runtime.runDb {
        ScopedAgentTaskTransitionsTable.insert {
            it[ScopedAgentTaskTransitionsTable.userId] = userId.value
            it[ScopedAgentTaskTransitionsTable.taskId] = transition.taskId.value
            it[ScopedAgentTaskTransitionsTable.fromPhase] = transition.fromPhase.name.lowercase()
            it[ScopedAgentTaskTransitionsTable.toPhase] = transition.toPhase.name.lowercase()
            it[ScopedAgentTaskTransitionsTable.reason] = transition.reason
            it[ScopedAgentTaskTransitionsTable.timestamp] = transition.timestamp
        }
        Unit
    }

    private fun ResultRow.toTask(): AgentTask = AgentTask(
        id = TaskId(this[ScopedAgentTasksTable.taskId]),
        agentId = io.artemkopan.ai.core.domain.model.AgentId(this[ScopedAgentTasksTable.agentId]),
        title = this[ScopedAgentTasksTable.title],
        currentPhase = TaskPhase.valueOf(this[ScopedAgentTasksTable.currentPhase].uppercase()),
        steps = (this[ScopedAgentTasksTable.stepsJson] ?: "").toTaskSteps(json),
        currentStepIndex = this[ScopedAgentTasksTable.currentStepIndex],
        createdAt = this[ScopedAgentTasksTable.createdAt],
        updatedAt = this[ScopedAgentTasksTable.updatedAt],
        planJson = this[ScopedAgentTasksTable.planJson] ?: "",
        validationJson = this[ScopedAgentTasksTable.validationJson] ?: "",
    )
}
