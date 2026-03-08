package io.artemkopan.ai.backend.agent.persistence

import io.artemkopan.ai.core.domain.model.*
import org.jetbrains.exposed.sql.ResultRow

internal fun ResultRow.toAgent(messages: List<AgentMessage>): Agent = Agent(
    id = io.artemkopan.ai.core.domain.model.AgentId(this[ScopedAgentsTable.id]),
    title = this[ScopedAgentsTable.title],
    model = this[ScopedAgentsTable.model],
    maxOutputTokens = this[ScopedAgentsTable.maxOutputTokens],
    temperature = this[ScopedAgentsTable.temperature],
    stopSequences = this[ScopedAgentsTable.stopSequences],
    agentMode = this[ScopedAgentsTable.agentMode],
    status = AgentStatus(this[ScopedAgentsTable.status]),
    contextConfig = RollingSummaryAgentContextConfig(
        recentMessagesN = this[ScopedAgentsTable.contextRecentMessagesN].takeIf { it > 0 } ?: DEFAULT_RECENT_MESSAGES_N,
        summarizeEveryK = this[ScopedAgentsTable.contextSummarizeEveryK].takeIf { it > 0 } ?: DEFAULT_SUMMARIZE_EVERY_K,
    ),
    contextSummary = "",
    summarizedUntilCreatedAt = 0,
    contextSummaryUpdatedAt = 0,
    messages = messages,
    branches = emptyList(),
    activeBranchId = this[ScopedAgentsTable.activeBranchId],
)

internal fun ResultRow.toMessage(): AgentMessage {
    val usage = this[ScopedAgentMessagesTable.usageTotalTokens]?.let {
        AgentUsage(
            inputTokens = this[ScopedAgentMessagesTable.usageInputTokens] ?: 0,
            outputTokens = this[ScopedAgentMessagesTable.usageOutputTokens] ?: 0,
            totalTokens = it,
        )
    }
    val messageType = try {
        AgentMessageType.valueOf(this[ScopedAgentMessagesTable.messageType].uppercase())
    } catch (_: IllegalArgumentException) {
        AgentMessageType.TEXT
    }
    return AgentMessage(
        id = AgentMessageId(this[ScopedAgentMessagesTable.id]),
        role = when (this[ScopedAgentMessagesTable.role]) {
            ROLE_ASSISTANT -> AgentMessageRole.ASSISTANT
            else -> AgentMessageRole.USER
        },
        text = this[ScopedAgentMessagesTable.text],
        status = this[ScopedAgentMessagesTable.status],
        createdAt = this[ScopedAgentMessagesTable.createdAt],
        provider = this[ScopedAgentMessagesTable.provider],
        model = this[ScopedAgentMessagesTable.model],
        usage = usage,
        latencyMs = this[ScopedAgentMessagesTable.latencyMs],
        messageType = messageType,
    )
}
