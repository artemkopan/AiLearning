# Stage 2: Task State Machine

## Status: IMPLEMENTED

## Goal
Implement task execution as a formal state machine with phases: planning → execution → validation → done. Support pausing at any stage and resuming without repeating explanations.

## Result
An agent with formalized task state. The assistant knows which phase it's in, what step to do next, and what's already been completed.

## Architecture Notes (corrections from original spec)
1. **Domain layer has no serialization dependency** — `@Serializable` annotations removed from domain models. Backend uses `TaskStepSerializer.kt` helper for JSON column storage.
2. **Operations use `Lazy<T>` parameters** — following `GetUserProfileOperation`/`UpsertUserProfileOperation` pattern.
3. **No `sendToSession()` on outbound service** — WS use cases send directly via `context.session.send(Frame.Text(...))`.
4. **`nowMillis()`** — operations use `runtime.value.nowMillis()`, not `System.currentTimeMillis()`.
5. **KSP annotations** — WS use cases use `@Factory(binds = [...])`, operations use `@Single`.
6. **UI layer added** — `TaskStateSnapshotDto` handled in `AgentSessionController`, displayed in conversation column with `TaskStatePanel`.

---

## Step 1: Domain Models

**Create** `core/domain/src/commonMain/kotlin/io/artemkopan/ai/core/domain/model/TaskStateModels.kt`

```kotlin
package io.artemkopan.ai.core.domain.model

data class TaskId(val value: String)

data class AgentTask(
    val id: TaskId,
    val agentId: AgentId,
    val title: String,
    val currentPhase: TaskPhase,
    val steps: List<TaskStep>,
    val currentStepIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class TaskPhase {
    PLANNING,
    EXECUTION,
    VALIDATION,
    DONE,
    PAUSED,
}

data class TaskStep(
    val index: Int,
    val phase: TaskPhase,
    val description: String,
    val expectedAction: String,   // what the assistant should do
    val status: TaskStepStatus,
    val result: String = "",      // output once completed
)

enum class TaskStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
}

data class TaskPhaseTransition(
    val taskId: TaskId,
    val fromPhase: TaskPhase,
    val toPhase: TaskPhase,
    val reason: String,
    val timestamp: Long,
)
```

**State machine transitions:**
```
PLANNING ──→ EXECUTION ──→ VALIDATION ──→ DONE
    │             │              │
    └─────────────┴──────────────┘
              ↓        ↑
            PAUSED ────┘ (resume to previous phase)
```

---

## Step 2: Repository Interface

**Create** `core/domain/src/commonMain/kotlin/io/artemkopan/ai/core/domain/repository/TaskRepository.kt`

```kotlin
package io.artemkopan.ai.core.domain.repository

import io.artemkopan.ai.core.domain.model.*

interface TaskRepository {
    suspend fun getActiveTask(userId: UserId, agentId: AgentId): Result<AgentTask?>
    suspend fun upsertTask(userId: UserId, task: AgentTask): Result<Unit>
    suspend fun updateTaskPhase(userId: UserId, taskId: TaskId, phase: TaskPhase, updatedAt: Long): Result<Unit>
    suspend fun updateTaskStep(
        userId: UserId,
        taskId: TaskId,
        stepIndex: Int,
        status: TaskStepStatus,
        result: String,
    ): Result<Unit>
    suspend fun appendTransition(userId: UserId, transition: TaskPhaseTransition): Result<Unit>
}
```

---

## Step 3: Database Schema

**Modify** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/helper/PostgresSchema.kt`

Add two tables:

```kotlin
internal object ScopedAgentTasksTable : Table("scoped_agent_tasks") {
    val userId = varchar("user_id", 128)
    val taskId = varchar("task_id", 64)
    val agentId = varchar("agent_id", 64)
    val title = varchar("title", 512)
    val currentPhase = varchar("current_phase", 32)
    val currentStepIndex = integer("current_step_index").default(0)
    val stepsJson = text("steps_json")      // JSON array of TaskStep
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId, taskId)
}

internal object ScopedAgentTaskTransitionsTable : Table("scoped_agent_task_transitions") {
    val userId = varchar("user_id", 128)
    val taskId = varchar("task_id", 64)
    val fromPhase = varchar("from_phase", 32)
    val toPhase = varchar("to_phase", 32)
    val reason = text("reason")
    val timestamp = long("timestamp")
}
```

**Modify** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/helper/PostgresDbRuntime.kt`

Add both tables to `SchemaUtils.createMissingTablesAndColumns(...)`.

---

## Step 4: Database Operations

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/operation/GetActiveTaskOperation.kt`

```kotlin
package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentTasksTable
import io.artemkopan.ai.core.domain.model.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.annotation.Single

@Single
internal class GetActiveTaskOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(userId: UserId, agentId: AgentId): Result<AgentTask?> = runtime.value.runDb {
        ScopedAgentTasksTable.selectAll()
            .where {
                (ScopedAgentTasksTable.userId eq userId.value) and
                    (ScopedAgentTasksTable.agentId eq agentId.value) and
                    (ScopedAgentTasksTable.currentPhase neq TaskPhase.DONE.name.lowercase())
            }
            .orderBy(ScopedAgentTasksTable.updatedAt)
            .lastOrNull()
            ?.let { row ->
                AgentTask(
                    id = TaskId(row[ScopedAgentTasksTable.taskId]),
                    agentId = agentId,
                    title = row[ScopedAgentTasksTable.title],
                    currentPhase = TaskPhase.valueOf(row[ScopedAgentTasksTable.currentPhase].uppercase()),
                    steps = json.value.decodeFromString<List<TaskStep>>(row[ScopedAgentTasksTable.stepsJson]),
                    currentStepIndex = row[ScopedAgentTasksTable.currentStepIndex],
                    createdAt = row[ScopedAgentTasksTable.createdAt],
                    updatedAt = row[ScopedAgentTasksTable.updatedAt],
                )
            }
    }
}
```

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/operation/UpsertTaskOperation.kt`

```kotlin
package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentTasksTable
import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.model.UserId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.koin.core.annotation.Single

@Single
internal class UpsertTaskOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(userId: UserId, task: AgentTask): Result<Unit> = runtime.value.runDb {
        val now = System.currentTimeMillis()
        val exists = ScopedAgentTasksTable.selectAll()
            .where {
                (ScopedAgentTasksTable.userId eq userId.value) and
                    (ScopedAgentTasksTable.taskId eq task.id.value)
            }
            .count() > 0

        if (exists) {
            ScopedAgentTasksTable.update(
                where = {
                    (ScopedAgentTasksTable.userId eq userId.value) and
                        (ScopedAgentTasksTable.taskId eq task.id.value)
                }
            ) {
                it[title] = task.title
                it[currentPhase] = task.currentPhase.name.lowercase()
                it[currentStepIndex] = task.currentStepIndex
                it[stepsJson] = json.value.encodeToString(task.steps)
                it[updatedAt] = now
            }
        } else {
            ScopedAgentTasksTable.insert {
                it[this.userId] = userId.value
                it[taskId] = task.id.value
                it[agentId] = task.agentId.value
                it[title] = task.title
                it[currentPhase] = task.currentPhase.name.lowercase()
                it[currentStepIndex] = task.currentStepIndex
                it[stepsJson] = json.value.encodeToString(task.steps)
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }
}
```

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/operation/UpdateTaskPhaseOperation.kt`

```kotlin
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
    suspend fun execute(userId: UserId, taskId: TaskId, phase: TaskPhase, updatedAt: Long): Result<Unit> =
        runtime.value.runDb {
            ScopedAgentTasksTable.update(
                where = {
                    (ScopedAgentTasksTable.userId eq userId.value) and
                        (ScopedAgentTasksTable.taskId eq taskId.value)
                }
            ) {
                it[currentPhase] = phase.name.lowercase()
                it[this.updatedAt] = updatedAt
            }
        }
}
```

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/operation/UpdateTaskStepOperation.kt`

```kotlin
package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentTasksTable
import io.artemkopan.ai.core.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.koin.core.annotation.Single

@Single
internal class UpdateTaskStepOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
    private val json: Lazy<Json>,
) {
    suspend fun execute(
        userId: UserId,
        taskId: TaskId,
        stepIndex: Int,
        status: TaskStepStatus,
        result: String,
    ): Result<Unit> = runtime.value.runDb {
        val now = System.currentTimeMillis()

        // Read current steps, modify the target step, write back
        val row = ScopedAgentTasksTable.selectAll()
            .where {
                (ScopedAgentTasksTable.userId eq userId.value) and
                    (ScopedAgentTasksTable.taskId eq taskId.value)
            }
            .singleOrNull() ?: return@runDb

        val steps = json.value.decodeFromString<List<TaskStep>>(row[ScopedAgentTasksTable.stepsJson])
            .toMutableList()

        if (stepIndex in steps.indices) {
            steps[stepIndex] = steps[stepIndex].copy(status = status, result = result)
        }

        // Advance currentStepIndex to next non-completed step
        val nextIndex = steps.indexOfFirst {
            it.status == TaskStepStatus.PENDING || it.status == TaskStepStatus.IN_PROGRESS
        }.takeIf { it >= 0 } ?: steps.size

        ScopedAgentTasksTable.update(
            where = {
                (ScopedAgentTasksTable.userId eq userId.value) and
                    (ScopedAgentTasksTable.taskId eq taskId.value)
            }
        ) {
            it[stepsJson] = json.value.encodeToString(steps.toList())
            it[currentStepIndex] = nextIndex
            it[updatedAt] = now
        }
    }
}
```

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/operation/AppendTaskTransitionOperation.kt`

```kotlin
package io.artemkopan.ai.backend.agent.persistence.operation

import io.artemkopan.ai.backend.agent.persistence.helper.PostgresDbRuntime
import io.artemkopan.ai.backend.agent.persistence.helper.ScopedAgentTaskTransitionsTable
import io.artemkopan.ai.core.domain.model.TaskPhaseTransition
import io.artemkopan.ai.core.domain.model.UserId
import org.jetbrains.exposed.sql.insert
import org.koin.core.annotation.Single

@Single
internal class AppendTaskTransitionOperation(
    private val runtime: Lazy<PostgresDbRuntime>,
) {
    suspend fun execute(userId: UserId, transition: TaskPhaseTransition): Result<Unit> =
        runtime.value.runDb {
            ScopedAgentTaskTransitionsTable.insert {
                it[this.userId] = userId.value
                it[taskId] = transition.taskId.value
                it[fromPhase] = transition.fromPhase.name.lowercase()
                it[toPhase] = transition.toPhase.name.lowercase()
                it[reason] = transition.reason
                it[timestamp] = transition.timestamp
            }
        }
}
```

---

## Step 5: Repository Implementation

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/persistence/PostgresTaskRepository.kt`

```kotlin
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
        getActiveTaskOp.value.execute(userId, agentId)

    override suspend fun upsertTask(userId: UserId, task: AgentTask): Result<Unit> =
        upsertTaskOp.value.execute(userId, task)

    override suspend fun updateTaskPhase(userId: UserId, taskId: TaskId, phase: TaskPhase, updatedAt: Long): Result<Unit> =
        updateTaskPhaseOp.value.execute(userId, taskId, phase, updatedAt)

    override suspend fun updateTaskStep(
        userId: UserId,
        taskId: TaskId,
        stepIndex: Int,
        status: TaskStepStatus,
        result: String,
    ): Result<Unit> = updateTaskStepOp.value.execute(userId, taskId, stepIndex, status, result)

    override suspend fun appendTransition(userId: UserId, transition: TaskPhaseTransition): Result<Unit> =
        appendTransitionOp.value.execute(userId, transition)
}
```

---

## Step 6: Application Use Cases

**Create** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/task/GetActiveTaskUseCase.kt`

```kotlin
package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.application.usecase.parseAgentIdOrError
import io.artemkopan.ai.core.application.usecase.parseUserIdOrError
import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.repository.TaskRepository

class GetActiveTaskUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(userId: String, agentId: String): Result<AgentTask?> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val domainAgentId = parseAgentIdOrError(agentId).getOrElse { return Result.failure(it) }
        return repository.getActiveTask(domainUserId, domainAgentId)
    }
}
```

**Create** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/task/CreateTaskUseCase.kt`

```kotlin
package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.application.usecase.parseAgentIdOrError
import io.artemkopan.ai.core.application.usecase.parseUserIdOrError
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.TaskRepository
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

        val now = 0L // DB sets actual timestamp
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
```

**Create** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/task/TransitionTaskPhaseUseCase.kt`

```kotlin
package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.application.usecase.parseUserIdOrError
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.TaskRepository

class TransitionTaskPhaseUseCase(
    private val repository: TaskRepository,
) {
    suspend fun execute(
        userId: String,
        taskId: String,
        targetPhase: TaskPhase,
        reason: String = "",
    ): Result<Unit> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val domainTaskId = TaskId(taskId)

        // Validate transition is allowed
        // Valid transitions:
        //   PLANNING → EXECUTION
        //   EXECUTION → VALIDATION
        //   VALIDATION → DONE
        //   Any (except DONE) → PAUSED
        //   PAUSED → PLANNING, EXECUTION, or VALIDATION (resume)
        // Invalid:
        //   DONE → anything
        //   Skip phases (PLANNING → VALIDATION)

        val now = System.currentTimeMillis()

        repository.appendTransition(
            domainUserId,
            TaskPhaseTransition(
                taskId = domainTaskId,
                fromPhase = targetPhase, // caller should pass current; simplified here
                toPhase = targetPhase,
                reason = reason,
                timestamp = now,
            ),
        ).getOrElse { return Result.failure(it) }

        return repository.updateTaskPhase(domainUserId, domainTaskId, targetPhase, now)
    }
}
```

> Note: Full transition validation logic should check current phase from DB before allowing transition. Expand the validation as needed.

**Create** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/task/UpdateTaskStepUseCase.kt`

```kotlin
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
```

**Create** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/task/BuildTaskStatePromptSnippetUseCase.kt`

```kotlin
package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.model.TaskStepStatus

class BuildTaskStatePromptSnippetUseCase {
    fun execute(task: AgentTask?): String {
        if (task == null) return ""
        return buildString {
            appendLine("ACTIVE TASK: ${task.title}")
            appendLine("Current phase: ${task.currentPhase.name}")
            appendLine("Progress: step ${task.currentStepIndex + 1} of ${task.steps.size}")

            val currentStep = task.steps.getOrNull(task.currentStepIndex)
            if (currentStep != null) {
                appendLine("Current step: ${currentStep.description}")
                appendLine("Expected action: ${currentStep.expectedAction}")
            }

            val completedSteps = task.steps.filter { it.status == TaskStepStatus.COMPLETED }
            if (completedSteps.isNotEmpty()) {
                appendLine()
                appendLine("Completed steps:")
                completedSteps.forEach { step ->
                    appendLine("  - [${step.phase.name}] ${step.description}: ${step.result}")
                }
            }

            appendLine()
            appendLine("IMPORTANT: Continue from the current step. Do NOT repeat completed steps.")
        }
    }
}
```

---

## Step 7: Modify AssistantMemoryModel

**Modify** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/model/AssistantMemoryModel.kt`

Add field to `WorkingMemoryLayer`:

```kotlin
data class WorkingMemoryLayer(
    val taskDataSummary: String,
    val taskStateSnippet: String = "",    // <-- ADD THIS
)
```

---

## Step 8: Modify BuildContextPromptUseCase

**Modify** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/BuildContextPromptUseCase.kt`

In `execute(memory: AssistantMemoryModel)`, add after the "WORKING MEMORY (CURRENT TASK DATA)" block (after line 53):

```kotlin
val taskState = memory.working.taskStateSnippet.trim()
if (taskState.isNotBlank()) {
    appendLine()
    appendLine("WORKING MEMORY (TASK STATE MACHINE):")
    appendLine(taskState)
}
```

---

## Step 9: Modify StartAgentMessageUseCase

**Modify** `core/application/src/commonMain/kotlin/io/artemkopan/ai/core/application/usecase/StartAgentMessageUseCase.kt`

Add constructor parameters:

```kotlin
class StartAgentMessageUseCase(
    // ... existing params ...
    private val getActiveTaskUseCase: GetActiveTaskUseCase,                        // <-- ADD
    private val buildTaskStatePromptSnippetUseCase: BuildTaskStatePromptSnippetUseCase, // <-- ADD
)
```

In `execute()`, before building `AssistantMemoryModel` (before line 124), add:

```kotlin
val activeTask = getActiveTaskUseCase.execute(userId, command.agentId).getOrNull()
val taskStateSnippet = buildTaskStatePromptSnippetUseCase.execute(activeTask)
```

Update `WorkingMemoryLayer` construction:

```kotlin
working = WorkingMemoryLayer(
    taskDataSummary = preparedContext.summaryText,
    taskStateSnippet = taskStateSnippet,
),
```

---

## Step 10: WebSocket Contract DTOs

**Modify** `shared-contract/src/commonMain/kotlin/io/artemkopan/ai/sharedcontract/AgentWsContracts.kt`

Add shared data classes:

```kotlin
@Serializable
data class TaskDto(
    val id: String,
    val agentId: String,
    val title: String,
    val currentPhase: String,
    val steps: List<TaskStepDto>,
    val currentStepIndex: Int,
)

@Serializable
data class TaskStepDto(
    val index: Int,
    val phase: String,
    val description: String,
    val expectedAction: String,
    val status: String,
    val result: String = "",
)
```

Add client messages:

```kotlin
@Serializable
@SerialName("create_task")
data class CreateTaskCommandDto(
    val agentId: String,
    val title: String,
    val steps: List<TaskStepDto>,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("transition_task_phase")
data class TransitionTaskPhaseCommandDto(
    val agentId: String,
    val taskId: String,
    val targetPhase: String,      // "planning", "execution", "validation", "done", "paused"
    val reason: String = "",
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("update_task_step")
data class UpdateTaskStepCommandDto(
    val agentId: String,
    val taskId: String,
    val stepIndex: Int,
    val status: String,           // "completed", "skipped", "in_progress"
    val result: String = "",
    val requestId: String? = null,
) : AgentWsClientMessageDto
```

Add server message:

```kotlin
@Serializable
@SerialName("task_state_snapshot")
data class TaskStateSnapshotDto(
    val agentId: String,
    val task: TaskDto?,
) : AgentWsServerMessageDto
```

---

## Step 11: WebSocket Handlers

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/ws/usecase/CreateTaskWsUseCase.kt`

```kotlin
package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.task.CreateTaskUseCase
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.model.TaskStep
import io.artemkopan.ai.core.domain.model.TaskStepStatus
import io.artemkopan.ai.sharedcontract.CreateTaskCommandDto
import io.artemkopan.ai.sharedcontract.TaskDto
import io.artemkopan.ai.sharedcontract.TaskStateSnapshotDto
import io.artemkopan.ai.sharedcontract.TaskStepDto
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class CreateTaskWsUseCase(
    private val createTaskUseCase: CreateTaskUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<CreateTaskCommandDto> {

    override val messageType: KClass<CreateTaskCommandDto> = CreateTaskCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: CreateTaskCommandDto,
    ): Result<Unit> {
        val domainSteps = message.steps.mapIndexed { index, dto ->
            TaskStep(
                index = index,
                phase = TaskPhase.valueOf(dto.phase.uppercase()),
                description = dto.description,
                expectedAction = dto.expectedAction,
                status = TaskStepStatus.PENDING,
                result = "",
            )
        }

        val task = createTaskUseCase.execute(
            userId = context.userScope,
            agentId = message.agentId,
            title = message.title,
            steps = domainSteps,
        ).getOrElse { return Result.failure(it) }

        // Send task snapshot back
        outboundService.sendToSession(
            context.session,
            TaskStateSnapshotDto(
                agentId = message.agentId,
                task = TaskDto(
                    id = task.id.value,
                    agentId = task.agentId.value,
                    title = task.title,
                    currentPhase = task.currentPhase.name.lowercase(),
                    steps = task.steps.map { step ->
                        TaskStepDto(
                            index = step.index,
                            phase = step.phase.name.lowercase(),
                            description = step.description,
                            expectedAction = step.expectedAction,
                            status = step.status.name.lowercase(),
                            result = step.result,
                        )
                    },
                    currentStepIndex = task.currentStepIndex,
                ),
            ),
        )
        return Result.success(Unit)
    }
}
```

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/ws/usecase/TransitionTaskPhaseWsUseCase.kt`

```kotlin
package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.core.application.usecase.task.TransitionTaskPhaseUseCase
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.TransitionTaskPhaseCommandDto
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class TransitionTaskPhaseWsUseCase(
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
) : AgentWsMessageUseCase<TransitionTaskPhaseCommandDto> {

    override val messageType: KClass<TransitionTaskPhaseCommandDto> = TransitionTaskPhaseCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: TransitionTaskPhaseCommandDto,
    ): Result<Unit> {
        return transitionTaskPhaseUseCase.execute(
            userId = context.userScope,
            taskId = message.taskId,
            targetPhase = TaskPhase.valueOf(message.targetPhase.uppercase()),
            reason = message.reason,
        )
    }
}
```

**Create** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/ws/usecase/UpdateTaskStepWsUseCase.kt`

```kotlin
package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.core.application.usecase.task.UpdateTaskStepUseCase
import io.artemkopan.ai.core.domain.model.TaskStepStatus
import io.artemkopan.ai.sharedcontract.UpdateTaskStepCommandDto
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class UpdateTaskStepWsUseCase(
    private val updateTaskStepUseCase: UpdateTaskStepUseCase,
) : AgentWsMessageUseCase<UpdateTaskStepCommandDto> {

    override val messageType: KClass<UpdateTaskStepCommandDto> = UpdateTaskStepCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: UpdateTaskStepCommandDto,
    ): Result<Unit> {
        return updateTaskStepUseCase.execute(
            userId = context.userScope,
            taskId = message.taskId,
            stepIndex = message.stepIndex,
            status = TaskStepStatus.valueOf(message.status.uppercase()),
            result = message.result,
        )
    }
}
```

---

## Step 12: Update requestIdOrNull

**Modify** `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/ws/AgentWsMessageHandler.kt`

Add branches in `requestIdOrNull()`:

```kotlin
is CreateTaskCommandDto -> requestId
is TransitionTaskPhaseCommandDto -> requestId
is UpdateTaskStepCommandDto -> requestId
```

---

## Step 13: DI Registration

**Modify** `backend/src/main/kotlin/io/artemkopan/ai/backend/di/KoinModules.kt`

In `applicationModule`, add:

```kotlin
factory { GetActiveTaskUseCase(repository = get()) }
factory { CreateTaskUseCase(repository = get()) }
factory { TransitionTaskPhaseUseCase(repository = get()) }
factory { UpdateTaskStepUseCase(repository = get()) }
factory { BuildTaskStatePromptSnippetUseCase() }
```

Update `StartAgentMessageUseCase` factory:

```kotlin
factory {
    StartAgentMessageUseCase(
        repository = get(),
        prepareAgentContextUseCase = get(),
        buildContextPromptUseCase = get(),
        retrieveRelevantContextUseCase = get(),
        indexMessageEmbeddingsUseCase = get(),
        expandStatsShortcutsInPromptUseCase = get(),
        extractAndPersistFactsUseCase = get(),
        getUserProfileUseCase = get(),                        // from Stage 1
        buildUserProfilePromptSnippetUseCase = get(),          // from Stage 1
        getActiveTaskUseCase = get(),                          // <-- ADD
        buildTaskStatePromptSnippetUseCase = get(),             // <-- ADD
    )
}
```

---

## Verification Checklist

- [ ] `./gradlew build` compiles all modules
- [ ] Start backend, verify `scoped_agent_tasks` and `scoped_agent_task_transitions` tables created
- [ ] Send `create_task` WS message with title + steps → receive `task_state_snapshot` response
- [ ] Send chat message → verify prompt contains "TASK STATE MACHINE" with phase and current step
- [ ] Transition to EXECUTION phase → send message → verify phase updated in prompt
- [ ] Pause task (transition to PAUSED) → send message → verify "PAUSED" in prompt
- [ ] Resume task → verify it continues from the same step, no repetition
- [ ] Complete all steps → transition to DONE → verify task no longer appears in prompt
- [ ] Agent without active task → verify no error, empty snippet skipped
- [ ] Mark step as completed → verify currentStepIndex advances
