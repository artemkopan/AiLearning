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
    val planJson: String = "",
    val validationJson: String = "",
)

sealed class TaskPhase(val name: String) {
    data object Planning : TaskPhase("PLANNING")
    data object WaitingForApproval : TaskPhase("WAITING_FOR_APPROVAL")
    data object WaitingForUserInput : TaskPhase("WAITING_FOR_USER_INPUT")
    data object Execution : TaskPhase("EXECUTION")
    data object Validation : TaskPhase("VALIDATION")
    data object Paused : TaskPhase("PAUSED")
    data object Done : TaskPhase("DONE")
    data object Failed : TaskPhase("FAILED")
    data object Stopped : TaskPhase("STOPPED")

    companion object {
        private val byName by lazy {
            listOf(
                Planning, WaitingForApproval, WaitingForUserInput,
                Execution, Validation, Paused, Done, Failed, Stopped,
            ).associateBy { it.name }
        }

        fun fromName(name: String): TaskPhase =
            byName[name.uppercase()] ?: error("Unknown TaskPhase: $name")
    }

    override fun toString(): String = name
}

object TaskStateMachine {
    private val transitions: Map<TaskPhase, Set<TaskPhase>> = mapOf(
        TaskPhase.Planning to setOf(TaskPhase.WaitingForApproval, TaskPhase.Failed, TaskPhase.Stopped),
        TaskPhase.WaitingForApproval to setOf(TaskPhase.Execution, TaskPhase.WaitingForUserInput, TaskPhase.Failed, TaskPhase.Stopped),
        TaskPhase.WaitingForUserInput to setOf(TaskPhase.WaitingForApproval, TaskPhase.Failed, TaskPhase.Stopped),
        TaskPhase.Execution to setOf(TaskPhase.Validation, TaskPhase.Paused, TaskPhase.Failed, TaskPhase.Stopped),
        TaskPhase.Validation to setOf(TaskPhase.Done, TaskPhase.Failed, TaskPhase.Stopped),
        TaskPhase.Paused to setOf(TaskPhase.Execution, TaskPhase.Stopped),
        TaskPhase.Done to emptySet(),
        TaskPhase.Failed to setOf(TaskPhase.Planning),
        TaskPhase.Stopped to emptySet(),
    )

    fun canTransition(from: TaskPhase, to: TaskPhase): Boolean =
        transitions[from]?.contains(to) == true

    fun transition(from: TaskPhase, to: TaskPhase): TaskPhase {
        require(canTransition(from, to)) { "Invalid transition: ${from.name} -> ${to.name}" }
        return to
    }
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
