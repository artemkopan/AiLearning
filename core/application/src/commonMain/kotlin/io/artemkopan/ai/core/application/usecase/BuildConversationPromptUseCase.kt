package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageRole

class BuildConversationPromptUseCase {
    fun execute(messages: List<AgentMessage>): String {
        if (messages.isEmpty()) return ""

        val conversation = messages
            .filter { it.status != "stopped" }
            .joinToString(separator = "\n") { message ->
                val prefix = when (message.role) {
                    AgentMessageRole.USER -> "USER"
                    AgentMessageRole.ASSISTANT -> "ASSISTANT"
                }
                "$prefix: ${message.text}"
            }

        return buildString {
            appendLine("Continue the conversation as ASSISTANT.")
            appendLine("Keep response concise and relevant to the latest USER message.")
            appendLine()
            append(conversation)
        }
    }
}
