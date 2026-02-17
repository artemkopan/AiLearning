package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.domain.model.Prompt

class ValidatePromptUseCase(
    private val maxPromptLength: Int = 8_000,
) {
    fun execute(rawPrompt: String): Result<Prompt> {
        val normalized = rawPrompt.trim()
        if (normalized.isBlank()) {
            return Result.failure(AppError.Validation("Prompt must not be blank."))
        }
        if (normalized.length > maxPromptLength) {
            return Result.failure(AppError.Validation("Prompt must not exceed $maxPromptLength characters."))
        }
        return Result.success(Prompt(normalized))
    }
}
