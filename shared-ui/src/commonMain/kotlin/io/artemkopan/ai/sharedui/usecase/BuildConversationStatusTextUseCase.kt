package io.artemkopan.ai.sharedui.usecase

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
            val stepLabel = activeTask.steps.getOrNull(activeTask.currentStepIndex)?.description ?: ""
            val progress = "step ${activeTask.currentStepIndex + 1}/${activeTask.steps.size}"
            val taskLine = "task: ${activeTask.currentPhase} $progress"
            parts += if (stepLabel.isNotBlank()) "$taskLine — $stepLabel" else taskLine
        }

        return parts.joinToString(" | ").takeIf { it.isNotBlank() }
    }
}
