package io.artemkopan.ai.sharedui.feature.taskstatemanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.feature.taskstatemanager.model.TaskStateManagerUiModel
import io.artemkopan.ai.sharedui.feature.taskstatemanager.model.TaskStepUiModel
import io.artemkopan.ai.sharedui.usecase.ObserveSessionStateUseCase
import io.artemkopan.ai.sharedui.usecase.PauseTaskActionUseCase
import io.artemkopan.ai.sharedui.usecase.ResumeTaskActionUseCase
import kotlinx.coroutines.flow.*

class TaskStateManagerViewModel(
    private val agentId: AgentId,
    private val observeSessionStateUseCase: ObserveSessionStateUseCase,
    private val pauseTaskActionUseCase: PauseTaskActionUseCase,
    private val resumeTaskActionUseCase: ResumeTaskActionUseCase,
) : ViewModel() {

    private val isExpanded = MutableStateFlow(false)
    private val isPauseRequested = MutableStateFlow(false)

    val state: StateFlow<TaskStateManagerUiModel> = combine(
        observeSessionStateUseCase()
            .map { it.taskByAgent[agentId] }
            .distinctUntilChanged(),
        isExpanded,
        isPauseRequested,
    ) { task, expanded, pauseRequested ->
        if (task == null) return@combine TaskStateManagerUiModel()

        val isPaused = task.currentPhase.equals(PHASE_PAUSED, ignoreCase = true)
        val isDone = task.currentPhase.equals(PHASE_DONE, ignoreCase = true)

        if (isPaused && pauseRequested) {
            isPauseRequested.value = false
        }

        val completedCount = task.steps.count { it.status.equals(STATUS_COMPLETED, ignoreCase = true) }

        TaskStateManagerUiModel(
            visible = true,
            expanded = expanded,
            taskTitle = task.title,
            currentPhase = task.currentPhase,
            isPaused = isPaused,
            isDone = isDone,
            isPauseRequested = pauseRequested && !isPaused,
            steps = task.steps.map { step ->
                TaskStepUiModel(
                    index = step.index,
                    phase = step.phase,
                    description = step.description,
                    expectedAction = step.expectedAction,
                    status = step.status,
                    result = step.result,
                    isCurrent = step.index == task.currentStepIndex,
                )
            },
            currentStepIndex = task.currentStepIndex,
            progressLabel = "$completedCount/${task.steps.size}",
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TaskStateManagerUiModel(),
    )

    fun onToggleExpanded() {
        isExpanded.update { !it }
    }

    fun onPauseTask() {
        isPauseRequested.value = true
        pauseTaskActionUseCase(agentId)
    }

    fun onResumeTask() {
        isPauseRequested.value = false
        resumeTaskActionUseCase(agentId)
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val PHASE_PAUSED = "paused"
private const val PHASE_DONE = "done"
private const val STATUS_COMPLETED = "completed"
