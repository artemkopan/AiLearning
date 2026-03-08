package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.TaskPhaseDto
import io.artemkopan.ai.sharedui.core.session.AgentState
import io.artemkopan.ai.sharedui.core.session.TaskState
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.PhaseProgressStyle
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.PhaseProgressUiState
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.TaskUiState
import org.koin.core.annotation.Factory

@Factory
class BuildTaskUiStateUseCase {

    operator fun invoke(agent: AgentState?, activeTask: TaskState?): TaskUiState {
        if (activeTask == null) return TaskUiState()

        val phase = activeTask.currentPhase
        val isWaitingForUserInput = phase == TaskPhaseDto.WAITING_FOR_USER_INPUT
        val showPlanActions = phase == TaskPhaseDto.WAITING_FOR_APPROVAL

        return TaskUiState(
            showPlanActions = showPlanActions,
            isWaitingForUserInput = isWaitingForUserInput,
            inputLabel = if (isWaitingForUserInput) "// ANSWER" else "// MESSAGE",
            answerPrompt = if (isWaitingForUserInput) activeTask.questionForUser.takeIf { it.isNotBlank() } else null,
            phaseProgress = buildPhaseProgress(phase),
            showSpinner = agent?.isLoading == true || phase.isActivePhase(),
        )
    }

    private fun buildPhaseProgress(phase: TaskPhaseDto): PhaseProgressUiState? {
        val label = when (phase) {
            TaskPhaseDto.PLANNING -> "PLANNING..."
            TaskPhaseDto.EXECUTION -> "EXECUTING..."
            TaskPhaseDto.VALIDATION -> "VALIDATING..."
            TaskPhaseDto.WAITING_FOR_USER_INPUT -> "WAITING FOR YOUR ANSWER..."
            TaskPhaseDto.PAUSED -> "PAUSED"
            else -> return null
        }
        val style = when (phase) {
            TaskPhaseDto.WAITING_FOR_USER_INPUT -> PhaseProgressStyle.AWAITING_INPUT
            TaskPhaseDto.PAUSED -> PhaseProgressStyle.PAUSED
            else -> PhaseProgressStyle.ACTIVE
        }
        return PhaseProgressUiState(label = label, style = style)
    }

    private fun TaskPhaseDto.isActivePhase(): Boolean = when (this) {
        TaskPhaseDto.PLANNING, TaskPhaseDto.EXECUTION, TaskPhaseDto.VALIDATION -> true
        else -> false
    }
}
