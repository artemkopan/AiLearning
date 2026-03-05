package io.artemkopan.ai.core.application.usecase.context

class BuildFactsExtractionPromptUseCase {
    fun execute(existingFactsJson: String, newUserMessage: String): String {
        return buildString {
            appendLine("You are a fact extraction assistant.")
            appendLine("Given the existing facts and a new user message, update the facts.")
            appendLine("Return ONLY a valid JSON object of key-value pairs (string keys and string values).")
            appendLine()
            appendLine("RULES:")
            appendLine("1. PRESERVE all existing facts by default. Only remove a fact if the new message EXPLICITLY contradicts or corrects it.")
            appendLine("2. If the new message is casual, greeting, or off-topic (e.g. 'ok', 'thanks', 'hi'), return the existing facts UNCHANGED.")
            appendLine("3. Add new facts only when the user states something concrete about preferences, goals, context, or decisions.")
            appendLine("4. Never remove a fact just because the new message does not mention it.")
            appendLine("5. Keep facts concise (short key-value pairs).")
            appendLine()
            appendLine("Existing facts:")
            appendLine(existingFactsJson.ifBlank { "{}" })
            appendLine()
            appendLine("New user message:")
            appendLine(newUserMessage)
        }
    }
}
