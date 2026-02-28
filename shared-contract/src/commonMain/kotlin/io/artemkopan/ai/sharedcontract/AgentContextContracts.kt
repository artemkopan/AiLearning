package io.artemkopan.ai.sharedcontract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AgentContextConfigDto {
    val locked: Boolean
}

@Serializable
@SerialName("full_history")
data class FullHistoryContextConfigDto(
    override val locked: Boolean = false,
) : AgentContextConfigDto

@Serializable
@SerialName("rolling_summary_recent_n")
data class RollingSummaryContextConfigDto(
    val recentMessagesN: Int = DEFAULT_RECENT_MESSAGES_N,
    val summarizeEveryK: Int = DEFAULT_SUMMARIZE_EVERY_K,
    override val locked: Boolean = false,
) : AgentContextConfigDto

@Serializable
data class AgentStatsResponseDto(
    val agents: List<AgentStatsDto>,
)

@Serializable
data class AgentStatsDto(
    val agentId: String,
    val title: String,
    val model: String,
    val agentMode: AgentMode = AgentMode.DEFAULT,
    val contextConfig: AgentContextConfigDto,
    val contextSummary: String = "",
    val summarizedUntilCreatedAt: Long = 0,
    val contextSummaryUpdatedAt: Long = 0,
    val systemInstruction: String = "",
    val latestAssistant: AgentLatestAssistantStatsDto? = null,
    val tokenStats: AgentTokenStatsDto,
    val recentTurns: List<AgentRecentTurnStatsDto> = emptyList(),
)

@Serializable
data class AgentLatestAssistantStatsDto(
    val messageId: String,
    val text: String,
    val status: String,
    val provider: String? = null,
    val model: String? = null,
    val usage: TokenUsageDto? = null,
    val latencyMs: Long? = null,
)

@Serializable
data class AgentTokenStatsDto(
    val latestInputTokens: Int = 0,
    val latestOutputTokens: Int = 0,
    val latestTotalTokens: Int = 0,
    val cumulativeInputTokens: Int = 0,
    val cumulativeOutputTokens: Int = 0,
    val cumulativeTotalTokens: Int = 0,
    val estimatedContextRawTokens: Int = 0,
    val estimatedContextCompressedTokens: Int = 0,
)

@Serializable
data class AgentRecentTurnStatsDto(
    val role: AgentMessageRoleDto,
    val text: String,
    val status: String,
    val createdAt: Long,
)

const val DEFAULT_RECENT_MESSAGES_N = 12
const val DEFAULT_SUMMARIZE_EVERY_K = 10
