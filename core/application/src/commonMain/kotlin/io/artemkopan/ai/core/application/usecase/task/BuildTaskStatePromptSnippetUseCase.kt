package io.artemkopan.ai.core.application.usecase.task

import io.artemkopan.ai.core.application.model.TaskPromptResult
import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.model.LlmResponseFormat
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.model.TaskStepStatus

class BuildTaskStatePromptSnippetUseCase {
    fun execute(task: AgentTask?): TaskPromptResult {
        if (task == null) {
            return TaskPromptResult(
                taskStateSnippet = "",
                phaseSystemInstruction = null,
                responseFormat = LlmResponseFormat.TEXT,
            )
        }

        val taskStateSnippet = buildString {
            appendLine("ACTIVE TASK: ${task.title}")
            appendLine("Current phase: ${task.currentPhase.name}")
            appendLine("Progress: step ${task.currentStepIndex + 1} of ${task.steps.size}")

            val currentStep = task.steps.getOrNull(task.currentStepIndex)
            if (currentStep != null) {
                appendLine("Current step: ${currentStep.description}")
                appendLine("Expected action: ${currentStep.expectedAction}")
            }

            val phaseDirective = when (task.currentPhase) {
                TaskPhase.PLANNING ->
                    "PHASE DIRECTIVE: Create a clear, actionable step-by-step plan. " +
                        "Do NOT execute yet -- only plan. Structure the plan with numbered steps."
                TaskPhase.EXECUTION ->
                    "PHASE DIRECTIVE: Execute the plan from earlier in this conversation. " +
                        "Produce concrete results. Follow the plan steps in order."
                TaskPhase.VALIDATION ->
                    "PHASE DIRECTIVE: Review the execution output against the original request. " +
                        "Verify correctness. Report: PASS if resolved, or list remaining issues."
                TaskPhase.WAITING_FOR_APPROVAL ->
                    "PHASE DIRECTIVE: Task is paused for review. Respond to user feedback."
                TaskPhase.DONE, TaskPhase.FAILED -> ""
            }
            if (phaseDirective.isNotEmpty()) {
                appendLine()
                appendLine(phaseDirective)
            }

            if (task.planJson.isNotBlank() && task.currentPhase in setOf(TaskPhase.EXECUTION, TaskPhase.VALIDATION)) {
                appendLine()
                appendLine("APPROVED PLAN (JSON):")
                appendLine(task.planJson)
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

        val (phaseSystemInstruction, responseFormat) = when (task.currentPhase) {
            TaskPhase.PLANNING ->
                (
                    "You are a task planner. Your job is only to create a plan. Do not execute the task. " +
                        "Do not claim the task is complete. Return JSON only with: stage, goal, plan (array of strings), " +
                        "requires_user_confirmation, question_for_user."
                    ) to LlmResponseFormat.JSON
            TaskPhase.EXECUTION ->
                (
                    "You are a task executor. Execute only the approved plan. Do not re-plan unless the plan is impossible. " +
                        "Return JSON only with: stage, completed_steps (array of strings), output, notes, needs_validation."
                    ) to LlmResponseFormat.JSON
            TaskPhase.VALIDATION ->
                (
                    "You are a validator. Check whether execution matches the approved plan and user request. " +
                        "Return JSON only with: stage, success, checks (array of {name, passed}), issues (array of strings), final_status (done or failed)."
                    ) to LlmResponseFormat.JSON
            TaskPhase.WAITING_FOR_APPROVAL, TaskPhase.DONE, TaskPhase.FAILED ->
                null to LlmResponseFormat.TEXT
        }

        return TaskPromptResult(
            taskStateSnippet = taskStateSnippet,
            phaseSystemInstruction = phaseSystemInstruction,
            responseFormat = responseFormat,
        )
    }
}
