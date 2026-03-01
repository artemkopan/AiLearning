package io.artemkopan.ai.core.application.usecase.context

class BuildFactsExtractionPromptUseCase {
    fun execute(existingFactsJson: String, newUserMessage: String): String {
        return buildString {
            appendLine("You are a fact extraction assistant.")
            appendLine("Given the existing facts and a new user message, update the facts.")
            appendLine("Return ONLY a valid JSON object of key-value pairs (string keys and string values).")
            appendLine("Keep facts concise. Remove outdated facts. Add new relevant facts.")
            appendLine("Do not include conversational text, only the JSON object.")
            appendLine()
            appendLine("Existing facts:")
            appendLine(existingFactsJson.ifBlank { "{}" })
            appendLine()
            appendLine("New user message:")
            appendLine(newUserMessage)
        }
    }
}
