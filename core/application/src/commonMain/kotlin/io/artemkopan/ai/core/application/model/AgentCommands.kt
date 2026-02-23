package io.artemkopan.ai.core.application.model

data class SelectAgentCommand(
    val agentId: String,
)

data class UpdateAgentDraftCommand(
    val agentId: String,
    val prompt: String,
    val model: String,
    val maxOutputTokens: String,
    val temperature: String,
    val stopSequences: String,
    val agentMode: String,
)

data class CloseAgentCommand(
    val agentId: String,
)

data class SetAgentStatusCommand(
    val agentId: String,
    val status: String,
)

data class SubmitAgentCommand(
    val agentId: String,
)
