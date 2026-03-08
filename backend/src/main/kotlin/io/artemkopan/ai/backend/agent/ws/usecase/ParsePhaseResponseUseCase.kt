package io.artemkopan.ai.backend.agent.ws.usecase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Factory

data class PlanningResponse(
    val stage: String,
    val goal: String,
    val plan: List<String>,
    val requiresUserConfirmation: Boolean,
    val questionForUser: String,
)

data class ExecutionResponse(
    val stage: String,
    val completedSteps: List<String>,
    val output: String,
    val notes: String?,
    val needsValidation: Boolean,
)

data class ValidationCheck(
    val name: String,
    val passed: Boolean,
)

data class ValidationResponse(
    val stage: String,
    val success: Boolean,
    val checks: List<ValidationCheck>,
    val issues: List<String>,
    val finalStatus: String,
)

@Factory
class ParsePhaseResponseUseCase(
    private val json: Json,
) {
    fun parsePlanningResponse(text: String): Result<PlanningResponse> = runCatching {
        val dto = json.decodeFromString<PlanningResponseDto>(text.trim())
        PlanningResponse(
            stage = dto.stage,
            goal = dto.goal,
            plan = dto.plan,
            requiresUserConfirmation = dto.requiresUserConfirmation,
            questionForUser = dto.questionForUser,
        )
    }

    fun parseExecutionResponse(text: String): Result<ExecutionResponse> = runCatching {
        val dto = json.decodeFromString<ExecutionResponseDto>(text.trim())
        ExecutionResponse(
            stage = dto.stage,
            completedSteps = dto.completedSteps,
            output = dto.output,
            notes = dto.notes,
            needsValidation = dto.needsValidation,
        )
    }

    fun parseValidationResponse(text: String): Result<ValidationResponse> = runCatching {
        val dto = json.decodeFromString<ValidationResponseDto>(text.trim())
        ValidationResponse(
            stage = dto.stage,
            success = dto.success,
            checks = dto.checks.map { ValidationCheck(name = it.name, passed = it.passed) },
            issues = dto.issues,
            finalStatus = dto.finalStatus,
        )
    }

    fun formatPlanningForDisplay(response: PlanningResponse): String = buildString {
        if (response.goal.isNotBlank()) {
            appendLine("Goal: ${response.goal}")
            appendLine()
        }
        if (response.plan.isNotEmpty()) {
            appendLine("Plan:")
            response.plan.forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
        }
        if (response.questionForUser.isNotBlank()) {
            appendLine()
            append(response.questionForUser)
        }
    }

    fun formatExecutionForDisplay(response: ExecutionResponse): String = buildString {
        if (response.completedSteps.isNotEmpty()) {
            appendLine("Completed steps:")
            response.completedSteps.forEach { step ->
                appendLine("- $step")
            }
        }
        if (response.output.isNotBlank()) {
            appendLine()
            appendLine("Output:")
            appendLine(response.output)
        }
        if (!response.notes.isNullOrBlank()) {
            appendLine()
            appendLine("Notes: ${response.notes}")
        }
    }

    fun formatValidationForDisplay(response: ValidationResponse): String = buildString {
        appendLine("Validation ${if (response.success) "PASSED" else "FAILED"}:")
        response.checks.forEach { check ->
            val marker = if (check.passed) "PASS" else "FAIL"
            appendLine("[$marker] ${check.name}")
        }
        if (response.issues.isNotEmpty()) {
            appendLine()
            appendLine("Issues:")
            response.issues.forEach { issue ->
                appendLine("- $issue")
            }
        }
        appendLine()
        append("Status: ${response.finalStatus}")
    }
}

@Serializable
private data class PlanningResponseDto(
    val stage: String = "",
    val goal: String = "",
    val plan: List<String> = emptyList(),
    @SerialName("requires_user_confirmation")
    val requiresUserConfirmation: Boolean = true,
    @SerialName("question_for_user")
    val questionForUser: String = "",
)

@Serializable
private data class ExecutionResponseDto(
    val stage: String = "",
    @SerialName("completed_steps")
    val completedSteps: List<String> = emptyList(),
    val output: String = "",
    val notes: String? = null,
    @SerialName("needs_validation")
    val needsValidation: Boolean = true,
)

@Serializable
private data class ValidationResponseDto(
    val stage: String = "",
    val success: Boolean = false,
    val checks: List<ValidationCheckDto> = emptyList(),
    val issues: List<String> = emptyList(),
    @SerialName("final_status")
    val finalStatus: String = "done",
)

@Serializable
private data class ValidationCheckDto(
    val name: String = "",
    val passed: Boolean = false,
)
