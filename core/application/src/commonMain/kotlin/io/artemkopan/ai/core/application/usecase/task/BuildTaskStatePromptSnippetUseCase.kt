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
