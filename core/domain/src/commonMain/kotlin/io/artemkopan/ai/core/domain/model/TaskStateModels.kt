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
    val expectedAction: String,
    val status: TaskStepStatus,
    val result: String = "",
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
