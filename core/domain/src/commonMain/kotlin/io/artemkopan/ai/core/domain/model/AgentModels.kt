package io.artemkopan.ai.core.domain.model

data class AgentId(val value: String)

data class AgentMessageId(val value: String)

data class AgentStatus(val value: String)

data class AgentUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

enum class AgentMessageRole {
    USER,
    ASSISTANT,
}

data class AgentMessage(
    val id: AgentMessageId,
    val role: AgentMessageRole,
    val text: String,
    val status: String,
    val createdAt: Long,
    val provider: String? = null,
    val model: String? = null,
    val usage: AgentUsage? = null,
    val latencyMs: Long? = null,
)

data class Agent(
    val id: AgentId,
    val title: String,
    val model: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val stopSequences: String = "",
    val agentMode: String = "default",
    val status: AgentStatus = AgentStatus("done"),
    val messages: List<AgentMessage> = emptyList(),
)

data class AgentState(
    val agents: List<Agent>,
    val activeAgentId: AgentId? = null,
    val version: Long = 0,
)

data class AgentDraft(
    val model: String,
    val maxOutputTokens: String,
    val temperature: String,
    val stopSequences: String,
    val agentMode: String,
)
