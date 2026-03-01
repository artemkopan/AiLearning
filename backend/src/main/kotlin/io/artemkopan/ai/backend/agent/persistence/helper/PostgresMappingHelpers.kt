package io.artemkopan.ai.backend.agent.persistence.helper

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.core.domain.model.*
import org.jetbrains.exposed.sql.ResultRow

internal class PostgresMappingHelpers(
    private val config: Lazy<AppConfig>,
) {
    fun toAgent(
        row: ResultRow,
        messages: List<AgentMessage>,
        contextMemory: ResultRow?,
        branches: List<AgentBranch> = emptyList(),
    ): Agent {
        val hasUserMessages = messages.any { it.role == AgentMessageRole.USER }
        return Agent(
            id = AgentId(row[ScopedAgentsTable.id]),
            title = row[ScopedAgentsTable.title],
            model = row[ScopedAgentsTable.model],
            maxOutputTokens = row[ScopedAgentsTable.maxOutputTokens],
            temperature = row[ScopedAgentsTable.temperature],
            stopSequences = row[ScopedAgentsTable.stopSequences],
            agentMode = row[ScopedAgentsTable.agentMode],
            status = AgentStatus(row[ScopedAgentsTable.status]),
            contextConfig = toContextConfig(row, hasUserMessages),
            contextSummary = contextMemory?.get(ScopedAgentContextMemoryTable.summaryText).orEmpty(),
            summarizedUntilCreatedAt = contextMemory?.get(ScopedAgentContextMemoryTable.summarizedUntilCreatedAt) ?: 0L,
            contextSummaryUpdatedAt = contextMemory?.get(ScopedAgentContextMemoryTable.updatedAt) ?: 0L,
            messages = messages,
            branches = branches,
            activeBranchId = row[ScopedAgentsTable.activeBranchId],
        )
    }

    fun toMessage(row: ResultRow): AgentMessage {
        val usage = if (row[ScopedAgentMessagesTable.usageTotalTokens] != null) {
            AgentUsage(
                inputTokens = row[ScopedAgentMessagesTable.usageInputTokens] ?: 0,
                outputTokens = row[ScopedAgentMessagesTable.usageOutputTokens] ?: 0,
                totalTokens = row[ScopedAgentMessagesTable.usageTotalTokens] ?: 0,
            )
        } else {
            null
        }

        return AgentMessage(
            id = AgentMessageId(row[ScopedAgentMessagesTable.id]),
            role = when (row[ScopedAgentMessagesTable.role]) {
                ROLE_ASSISTANT -> AgentMessageRole.ASSISTANT
                else -> AgentMessageRole.USER
            },
            text = row[ScopedAgentMessagesTable.text],
            status = row[ScopedAgentMessagesTable.status],
            createdAt = row[ScopedAgentMessagesTable.createdAt],
            provider = row[ScopedAgentMessagesTable.provider],
            model = row[ScopedAgentMessagesTable.model],
            usage = usage,
            latencyMs = row[ScopedAgentMessagesTable.latencyMs],
        )
    }

    fun toContextMemory(row: ResultRow): AgentContextMemory {
        return AgentContextMemory(
            agentId = AgentId(row[ScopedAgentContextMemoryTable.agentId]),
            summaryText = row[ScopedAgentContextMemoryTable.summaryText],
            summarizedUntilCreatedAt = row[ScopedAgentContextMemoryTable.summarizedUntilCreatedAt],
            updatedAt = row[ScopedAgentContextMemoryTable.updatedAt],
        )
    }

    fun toContextConfig(row: ResultRow, locked: Boolean): AgentContextConfig {
        val recentN = row[ScopedAgentsTable.contextRecentMessagesN]
            .takeIf { it > 0 }
            ?: config.value.contextRecentMaxMessages.takeIf { it > 0 }
            ?: DEFAULT_RECENT_MESSAGES_N
        return when (row[ScopedAgentsTable.contextStrategy]) {
            CONTEXT_STRATEGY_FULL_HISTORY -> FullHistoryAgentContextConfig(locked = locked)
            CONTEXT_STRATEGY_SLIDING_WINDOW -> SlidingWindowAgentContextConfig(
                windowSize = recentN.takeIf { it > 0 } ?: DEFAULT_SLIDING_WINDOW_SIZE,
                locked = locked,
            )

            CONTEXT_STRATEGY_STICKY_FACTS -> StickyFactsAgentContextConfig(
                recentMessagesN = recentN,
                locked = locked,
            )

            CONTEXT_STRATEGY_BRANCHING -> BranchingAgentContextConfig(
                recentMessagesN = recentN,
                locked = locked,
            )

            else -> RollingSummaryAgentContextConfig(
                recentMessagesN = recentN,
                summarizeEveryK = row[ScopedAgentsTable.contextSummarizeEveryK]
                    .takeIf { it > 0 }
                    ?: config.value.contextSummarizeEveryMessages.takeIf { it > 0 }
                    ?: DEFAULT_SUMMARIZE_EVERY_K,
                locked = locked,
            )
        }
    }
}
