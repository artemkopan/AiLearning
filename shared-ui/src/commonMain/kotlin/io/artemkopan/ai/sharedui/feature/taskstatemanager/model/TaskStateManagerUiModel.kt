package io.artemkopan.ai.sharedui.feature.taskstatemanager.model

data class TaskStateManagerUiModel(
    val visible: Boolean = false,
    val expanded: Boolean = false,
    val taskTitle: String = "",
    val currentPhase: String = "",
    val isPaused: Boolean = false,
    val isDone: Boolean = false,
    val isPauseRequested: Boolean = false,
    val steps: List<TaskStepUiModel> = emptyList(),
    val currentStepIndex: Int = 0,
    val progressLabel: String = "",
)

data class TaskStepUiModel(
    val index: Int,
    val phase: String,
    val description: String,
    val expectedAction: String,
    val status: String,
    val result: String,
    val isCurrent: Boolean,
)
