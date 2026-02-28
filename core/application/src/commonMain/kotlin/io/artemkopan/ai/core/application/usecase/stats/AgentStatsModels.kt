package io.artemkopan.ai.core.application.usecase.stats

import io.artemkopan.ai.core.domain.model.AgentContextConfig
import io.artemkopan.ai.core.domain.model.AgentMessageRole

data class AgentLatestAssistantStats(
    val messageId: String,
    val text: String,
    val status: String,
    val provider: String? = null,
    val model: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val latencyMs: Long? = null,
)

data class AgentTokenStats(
    val latestInputTokens: Int = 0,
    val latestOutputTokens: Int = 0,
    val latestTotalTokens: Int = 0,
    val cumulativeInputTokens: Int = 0,
    val cumulativeOutputTokens: Int = 0,
    val cumulativeTotalTokens: Int = 0,
    val estimatedContextRawTokens: Int = 0,
    val estimatedContextCompressedTokens: Int = 0,
)

data class AgentRecentTurnStats(
    val role: AgentMessageRole,
    val text: String,
    val status: String,
    val createdAt: Long,
)

data class AgentStats(
    val agentId: String,
    val title: String,
    val model: String,
    val agentMode: String,
    val contextConfig: AgentContextConfig,
    val contextSummary: String,
    val summarizedUntilCreatedAt: Long,
    val contextSummaryUpdatedAt: Long,
    val systemInstruction: String,
    val latestAssistant: AgentLatestAssistantStats?,
    val tokenStats: AgentTokenStats,
    val recentTurns: List<AgentRecentTurnStats>,
)
