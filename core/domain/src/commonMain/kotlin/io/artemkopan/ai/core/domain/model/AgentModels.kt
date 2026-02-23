package io.artemkopan.ai.core.domain.model

data class AgentId(val value: String)

data class AgentStatus(val value: String)

data class AgentUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

data class AgentResponse(
    val text: String,
    val provider: String,
    val model: String,
    val usage: AgentUsage? = null,
    val latencyMs: Long = 0,
)

data class Agent(
    val id: AgentId,
    val title: String,
    val prompt: String = "",
    val model: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val stopSequences: String = "",
    val agentMode: String = "default",
    val status: AgentStatus = AgentStatus("Ready"),
    val response: AgentResponse? = null,
)

data class AgentState(
    val agents: List<Agent>,
    val activeAgentId: AgentId? = null,
    val version: Long = 0,
)

data class AgentDraft(
    val prompt: String,
    val model: String,
    val maxOutputTokens: String,
    val temperature: String,
    val stopSequences: String,
    val agentMode: String,
)
