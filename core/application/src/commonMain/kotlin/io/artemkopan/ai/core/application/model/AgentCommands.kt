package io.artemkopan.ai.core.application.model

import io.artemkopan.ai.core.domain.model.AgentContextConfig

data class SelectAgentCommand(
    val agentId: String,
)

data class UpdateAgentDraftCommand(
    val agentId: String,
    val model: String,
    val maxOutputTokens: String,
    val temperature: String,
    val stopSequences: String,
    val agentMode: String,
    val contextConfig: AgentContextConfig,
)

data class CloseAgentCommand(
    val agentId: String,
)

data class SetAgentStatusCommand(
    val agentId: String,
    val status: String,
)

data class SendAgentMessageCommand(
    val agentId: String,
    val text: String,
)

data class StopAgentMessageCommand(
    val agentId: String,
    val messageId: String,
)
