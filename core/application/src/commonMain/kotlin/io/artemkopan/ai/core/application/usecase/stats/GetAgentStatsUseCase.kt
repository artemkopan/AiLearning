package io.artemkopan.ai.core.application.usecase.stats

import io.artemkopan.ai.core.application.usecase.BuildContextPromptUseCase
import io.artemkopan.ai.core.application.usecase.EstimatePromptTokensUseCase
import io.artemkopan.ai.core.application.usecase.ResolveAgentModeUseCase
import io.artemkopan.ai.core.application.usecase.parseUserIdOrError
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.FullHistoryAgentContextConfig
import io.artemkopan.ai.core.domain.model.RollingSummaryAgentContextConfig
import io.artemkopan.ai.core.domain.repository.AgentRepository

class GetAgentStatsUseCase(
    private val repository: AgentRepository,
    private val buildContextPromptUseCase: BuildContextPromptUseCase,
    private val estimatePromptTokensUseCase: EstimatePromptTokensUseCase,
    private val resolveAgentModeUseCase: ResolveAgentModeUseCase,
) {
    suspend fun execute(userId: String): Result<List<AgentStats>> {
        val domainUserId = parseUserIdOrError(userId).getOrElse { return Result.failure(it) }
        val state = repository.getState(domainUserId).getOrElse { return Result.failure(it) }

        return Result.success(
            state.agents.map { agent ->
                val activeMessages = agent.messages.filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
                val rawPrompt = buildContextPromptUseCase.execute(
                    summary = "",
                    messages = activeMessages,
                    retrievedMemory = emptyList(),
                )
                val rawTokens = estimatePromptTokensUseCase.execute(rawPrompt)
                val compressedTokens = estimatePromptTokensUseCase.execute(
                    when (val config = agent.contextConfig) {
                        is FullHistoryAgentContextConfig -> rawPrompt
                        is RollingSummaryAgentContextConfig -> {
                            val recent = activeMessages
                                .filter { it.createdAt > agent.summarizedUntilCreatedAt }
                                .takeLast(config.recentMessagesN)
                            buildContextPromptUseCase.execute(
                                summary = agent.contextSummary,
                                messages = recent,
                                retrievedMemory = emptyList(),
                            )
                        }
                    }
                )

                val latestAssistant = activeMessages.lastOrNull { it.role == AgentMessageRole.ASSISTANT }
                val latestAssistantStats = latestAssistant?.let { message ->
                    AgentLatestAssistantStats(
                        messageId = message.id.value,
                        text = message.text,
                        status = message.status,
                        provider = message.provider,
                        model = message.model,
                        inputTokens = message.usage?.inputTokens,
                        outputTokens = message.usage?.outputTokens,
                        totalTokens = message.usage?.totalTokens,
                        latencyMs = message.latencyMs,
                    )
                }
                val cumulativeUsage = activeMessages
                    .filter { it.role == AgentMessageRole.ASSISTANT }
                    .mapNotNull { it.usage }

                val systemInstruction = resolveAgentModeUseCase.execute(agent.agentMode)
                    .getOrNull()
                    ?.value
                    .orEmpty()

                AgentStats(
                    agentId = agent.id.value,
                    title = agent.title,
                    model = agent.model,
                    agentMode = agent.agentMode,
                    contextConfig = agent.contextConfig,
                    contextSummary = agent.contextSummary,
                    summarizedUntilCreatedAt = agent.summarizedUntilCreatedAt,
                    contextSummaryUpdatedAt = agent.contextSummaryUpdatedAt,
                    systemInstruction = systemInstruction,
                    latestAssistant = latestAssistantStats,
                    tokenStats = AgentTokenStats(
                        latestInputTokens = latestAssistantStats?.inputTokens ?: 0,
                        latestOutputTokens = latestAssistantStats?.outputTokens ?: 0,
                        latestTotalTokens = latestAssistantStats?.totalTokens ?: 0,
                        cumulativeInputTokens = cumulativeUsage.sumOf { it.inputTokens },
                        cumulativeOutputTokens = cumulativeUsage.sumOf { it.outputTokens },
                        cumulativeTotalTokens = cumulativeUsage.sumOf { it.totalTokens },
                        estimatedContextRawTokens = rawTokens,
                        estimatedContextCompressedTokens = compressedTokens,
                    ),
                    recentTurns = activeMessages
                        .takeLast(MAX_RECENT_TURNS)
                        .map { message ->
                            AgentRecentTurnStats(
                                role = message.role,
                                text = message.text,
                                status = message.status,
                                createdAt = message.createdAt,
                            )
                        },
                )
            }
        )
    }
}

private const val STATUS_STOPPED = "stopped"
private const val MAX_RECENT_TURNS = 8
