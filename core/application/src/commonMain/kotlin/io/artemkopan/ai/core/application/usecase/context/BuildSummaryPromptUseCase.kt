package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageRole

class BuildSummaryPromptUseCase {
    fun execute(existingSummary: String, messages: List<AgentMessage>): String {
        val turns = messages.joinToString(separator = "\n") { message ->
            val role = when (message.role) {
                AgentMessageRole.USER -> "USER"
                AgentMessageRole.ASSISTANT -> "ASSISTANT"
            }
            "$role: ${message.text}"
        }

        return buildString {
            appendLine("You maintain compressed memory for an assistant conversation.")
            appendLine("Write an updated summary using only facts from the provided data.")
            appendLine("Keep constraints, decisions, requirements, open questions, and pending tasks.")
            appendLine("Remove repetition and chit-chat. Keep it concise.")
            appendLine()
            appendLine("EXISTING SUMMARY:")
            appendLine(existingSummary.ifBlank { "(none)" })
            appendLine()
            appendLine("NEW TURNS TO COMPRESS:")
            appendLine(turns)
            appendLine()
            appendLine("Return only the updated summary.")
        }
    }
}
