package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.domain.model.Prompt

class ValidatePromptUseCase(
    private val estimatePromptTokensUseCase: EstimatePromptTokensUseCase,
) {
    fun execute(rawPrompt: String, inputTokenLimit: Int): Result<Prompt> {
        val normalized = rawPrompt.trim()
        if (normalized.isBlank()) {
            return Result.failure(AppError.Validation("Prompt must not be blank."))
        }
        if (inputTokenLimit <= 0) {
            return Result.failure(AppError.Validation("Model input token limit must be greater than 0."))
        }

        val estimatedPromptTokens = estimatePromptTokensUseCase.execute(normalized)
        if (estimatedPromptTokens > inputTokenLimit) {
            return Result.failure(
                AppError.Validation(
                    "Prompt exceeds model input limit: estimated=$estimatedPromptTokens, limit=$inputTokenLimit.",
                )
            )
        }
        return Result.success(Prompt(normalized))
    }
}
