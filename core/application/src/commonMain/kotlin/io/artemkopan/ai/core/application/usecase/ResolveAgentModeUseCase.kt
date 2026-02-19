package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.error.AppError
import io.artemkopan.ai.core.domain.model.SystemInstruction

class ResolveAgentModeUseCase {

    fun execute(mode: String?): Result<SystemInstruction?> {
        if (mode.isNullOrBlank() || mode == "default") return Result.success(null)

        val prompt = PROMPTS[mode]
            ?: return Result.failure(AppError.Validation("Unknown agent mode: $mode"))

        return Result.success(SystemInstruction(prompt))
    }

    private companion object {
        val PROMPTS = mapOf(
            "engineer" to
                "You are a senior software engineer. Provide technical, implementation-focused responses. " +
                "Prioritize working code examples, best practices, and practical solutions. " +
                "Be precise, use correct terminology, and consider edge cases.",
            "philosophic" to
                "You are a thoughtful philosopher. Analyze questions from multiple perspectives. " +
                "Explore underlying assumptions, consider different schools of thought, and provide " +
                "reflective, nuanced responses that encourage deeper thinking.",
            "critic" to
                "You are a rigorous critic. Analyze every claim carefully, identify weaknesses, " +
                "logical fallacies, and unsupported assumptions. Challenge ideas constructively " +
                "and suggest improvements. Be thorough but fair in your assessment.",
        )
    }
}
