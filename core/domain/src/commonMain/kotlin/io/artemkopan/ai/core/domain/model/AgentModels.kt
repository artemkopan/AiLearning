package io.artemkopan.ai.core.domain.model

data class AgentId(val value: String)

data class UserId(val value: String)

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

data class AgentContextMemory(
    val agentId: AgentId,
    val summaryText: String,
    val summarizedUntilCreatedAt: Long,
    val updatedAt: Long,
)

data class RetrievedContextChunk(
    val messageId: AgentMessageId,
    val text: String,
    val score: Double,
    val createdAt: Long,
)

sealed interface AgentContextConfig {
    val locked: Boolean
}

data class FullHistoryAgentContextConfig(
    override val locked: Boolean = false,
) : AgentContextConfig

data class RollingSummaryAgentContextConfig(
    val recentMessagesN: Int = DEFAULT_RECENT_MESSAGES_N,
    val summarizeEveryK: Int = DEFAULT_SUMMARIZE_EVERY_K,
    override val locked: Boolean = false,
) : AgentContextConfig

data class SlidingWindowAgentContextConfig(
    val windowSize: Int = DEFAULT_SLIDING_WINDOW_SIZE,
    override val locked: Boolean = false,
) : AgentContextConfig

data class StickyFactsAgentContextConfig(
    val recentMessagesN: Int = DEFAULT_RECENT_MESSAGES_N,
    override val locked: Boolean = false,
) : AgentContextConfig

data class BranchingAgentContextConfig(
    val recentMessagesN: Int = DEFAULT_RECENT_MESSAGES_N,
    override val locked: Boolean = false,
) : AgentContextConfig

data class AgentFacts(
    val agentId: AgentId,
    val factsJson: String,
    val updatedAt: Long,
)

data class AgentBranch(
    val id: String,
    val name: String,
    val checkpointMessageId: AgentMessageId,
    val createdAt: Long,
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
    val contextConfig: AgentContextConfig = RollingSummaryAgentContextConfig(),
    val contextSummary: String = "",
    val summarizedUntilCreatedAt: Long = 0,
    val contextSummaryUpdatedAt: Long = 0,
    val messages: List<AgentMessage> = emptyList(),
    val branches: List<AgentBranch> = emptyList(),
    val activeBranchId: String? = null,
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
    val contextConfig: AgentContextConfig,
)

const val DEFAULT_RECENT_MESSAGES_N = 12
const val DEFAULT_SUMMARIZE_EVERY_K = 10
const val DEFAULT_SLIDING_WINDOW_SIZE = 20
