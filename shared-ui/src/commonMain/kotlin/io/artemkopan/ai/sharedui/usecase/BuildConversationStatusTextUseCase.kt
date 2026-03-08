package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.TaskPhaseDto
import io.artemkopan.ai.sharedui.core.session.AgentState
import io.artemkopan.ai.sharedui.core.session.QueuedMessageState
import io.artemkopan.ai.sharedui.core.session.TaskState
import org.koin.core.annotation.Factory

@Factory
class BuildConversationStatusTextUseCase {
    operator fun invoke(
        agent: AgentState,
        queuedMessages: List<QueuedMessageState>,
        activeTask: TaskState? = null,
    ): String? {
        val parts = mutableListOf<String>()

        when {
            agent.isLoading && queuedMessages.isNotEmpty() -> parts += "${agent.status} / queued ${queuedMessages.size}"
            agent.isLoading -> parts += agent.status
            queuedMessages.isNotEmpty() -> parts += "queued ${queuedMessages.size}"
        }

        if (activeTask != null) {
            val phaseLabel = activeTask.currentPhase.toStatusLabel()
            val stepLabel = activeTask.steps.getOrNull(activeTask.currentStepIndex)?.description ?: ""
            val progress = "step ${activeTask.currentStepIndex + 1}/${activeTask.steps.size}"
            val taskLine = "task: $phaseLabel $progress"
            parts += if (stepLabel.isNotBlank()) "$taskLine — $stepLabel" else taskLine
        }

        return parts.joinToString(" | ").takeIf { it.isNotBlank() }
    }

    private fun TaskPhaseDto.toStatusLabel(): String = when (this) {
        TaskPhaseDto.PLANNING -> "planning"
        TaskPhaseDto.WAITING_FOR_APPROVAL -> "awaiting approval"
        TaskPhaseDto.WAITING_FOR_USER_INPUT -> "awaiting answer"
        TaskPhaseDto.EXECUTION -> "executing"
        TaskPhaseDto.VALIDATION -> "validating"
        TaskPhaseDto.PAUSED -> "paused"
        TaskPhaseDto.DONE -> "done"
        TaskPhaseDto.FAILED -> "failed"
        TaskPhaseDto.STOPPED -> "stopped"
    }
}
