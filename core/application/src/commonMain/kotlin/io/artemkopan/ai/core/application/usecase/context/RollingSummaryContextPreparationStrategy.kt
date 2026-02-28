package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.usecase.GenerateTextUseCase
import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.RollingSummaryAgentContextConfig
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository

class RollingSummaryContextPreparationStrategy(
    private val repository: AgentRepository,
    private val generateTextUseCase: GenerateTextUseCase,
    private val shouldSummarizeUseCase: ShouldSummarizeUseCase,
    private val buildSummaryPromptUseCase: BuildSummaryPromptUseCase,
    private val persistContextSummaryUseCase: PersistContextSummaryUseCase,
    private val summaryMaxOutputTokens: Int,
    private val summaryModelOverride: String? = null,
) : ContextPreparationStrategy {

    override suspend fun prepare(userId: UserId, agent: Agent): Result<PreparedContextWindow> {
        val config = agent.contextConfig as? RollingSummaryAgentContextConfig
            ?: return Result.failure(IllegalArgumentException("Invalid context config for rolling summary strategy."))
        val memory = repository.getContextMemory(userId, agent.id).getOrElse { return Result.failure(it) }
        val existingSummary = memory?.summaryText.orEmpty()
        val initialCutoff = memory?.summarizedUntilCreatedAt ?: 0L
        val unsummarized = agent.messages
            .filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
            .filter { it.createdAt > initialCutoff }
        if (unsummarized.isEmpty()) {
            return Result.success(PreparedContextWindow(summaryText = existingSummary, recentMessages = emptyList()))
        }

        val olderMessages = unsummarized.dropLast(config.recentMessagesN)
        val shouldSummarize = shouldSummarizeUseCase.execute(
            messagesToCompressCount = olderMessages.size,
            summarizeEveryK = config.summarizeEveryK,
        )
        if (!shouldSummarize) {
            return Result.success(
                PreparedContextWindow(
                    summaryText = existingSummary,
                    recentMessages = unsummarized,
                )
            )
        }

        val summaryPrompt = buildSummaryPromptUseCase.execute(existingSummary, olderMessages)
        val summaryModel = summaryModelOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: agent.model.trim().takeIf { it.isNotEmpty() }
        val generatedSummary = generateTextUseCase.execute(
            GenerateCommand(
                prompt = summaryPrompt,
                model = summaryModel,
                temperature = SUMMARY_TEMPERATURE,
                maxOutputTokens = summaryMaxOutputTokens,
                stopSequences = null,
                agentMode = null,
            )
        ).getOrElse { return Result.failure(it) }
            .text
            .trim()
        if (generatedSummary.isBlank()) {
            return Result.success(
                PreparedContextWindow(
                    summaryText = existingSummary,
                    recentMessages = unsummarized,
                )
            )
        }

        val summarizedUntilCreatedAt = olderMessages.maxOfOrNull { it.createdAt } ?: initialCutoff
        persistContextSummaryUseCase.execute(
            userId = userId,
            agentId = agent.id,
            summaryText = generatedSummary,
            summarizedUntilCreatedAt = summarizedUntilCreatedAt,
        ).getOrElse { return Result.failure(it) }

        return Result.success(
            PreparedContextWindow(
                summaryText = generatedSummary,
                recentMessages = unsummarized
                    .filter { it.createdAt > summarizedUntilCreatedAt }
                    .takeLast(config.recentMessagesN),
            )
        )
    }
}

private const val STATUS_STOPPED = "stopped"
private const val SUMMARY_TEMPERATURE = 0.2
