package io.artemkopan.ai.core.application.usecase

class EstimatePromptTokensUseCase {
    fun execute(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
    }
}

private const val CHARS_PER_TOKEN = 4
