package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.RetrievedContextChunk

class BuildContextPromptUseCase {
    fun execute(
        summary: String?,
        messages: List<AgentMessage>,
        retrievedMemory: List<RetrievedContextChunk> = emptyList(),
    ): String {
        val normalizedSummary = summary?.trim().orEmpty()
        val activeMessages = messages.filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
        if (normalizedSummary.isBlank() && activeMessages.isEmpty() && retrievedMemory.isEmpty()) return ""

        return buildString {
            appendLine("Continue the conversation as ASSISTANT.")
            appendLine("Keep response concise and relevant to the latest USER message.")

            if (normalizedSummary.isNotBlank()) {
                appendLine()
                appendLine("CONTEXT SUMMARY:")
                appendLine(normalizedSummary)
            }

            if (retrievedMemory.isNotEmpty()) {
                appendLine()
                appendLine("RELEVANT MEMORY:")
                retrievedMemory.forEachIndexed { index, chunk ->
                    appendLine("${index + 1}. ${chunk.text}")
                }
            }

            if (activeMessages.isNotEmpty()) {
                appendLine()
                appendLine("RECENT TURNS:")
                activeMessages.forEach { message ->
                    val prefix = when (message.role) {
                        AgentMessageRole.USER -> "USER"
                        AgentMessageRole.ASSISTANT -> "ASSISTANT"
                    }
                    appendLine("$prefix: ${message.text}")
                }
            }
        }
    }
}

private const val STATUS_STOPPED = "stopped"
