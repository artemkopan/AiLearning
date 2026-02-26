package io.artemkopan.ai.core.application.usecase

import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.domain.model.Agent
import io.artemkopan.ai.core.domain.model.AgentContextMemory
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository

data class PreparedContextWindow(
    val summaryText: String,
    val recentMessages: List<AgentMessage>,
)

class MaybeSummarizeContextUseCase(
    private val repository: AgentRepository,
    private val generateTextUseCase: GenerateTextUseCase,
    private val buildContextPromptUseCase: BuildContextPromptUseCase,
    private val estimatePromptTokensUseCase: EstimatePromptTokensUseCase,
    private val summaryTriggerTokens: Int,
    private val recentMessagesMax: Int,
    private val summaryMaxOutputTokens: Int,
    private val summaryModelOverride: String? = null,
) {
    suspend fun execute(userId: UserId, agent: Agent): Result<PreparedContextWindow> {
        val memory = repository.getContextMemory(userId, agent.id).getOrElse { return Result.failure(it) }
        val initialSummary = memory?.summaryText.orEmpty()
        val initialCutoff = memory?.summarizedUntilCreatedAt ?: 0L
        val unsummarized = agent.messages
            .filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
            .filter { it.createdAt > initialCutoff }

        if (unsummarized.isEmpty()) {
            return Result.success(
                PreparedContextWindow(
                    summaryText = initialSummary,
                    recentMessages = emptyList(),
                )
            )
        }

        val promptBeforeSummary = buildContextPromptUseCase.execute(
            summary = initialSummary,
            messages = unsummarized,
        )
        val estimatedTokens = estimatePromptTokensUseCase.execute(promptBeforeSummary)
        val olderMessages = unsummarized.dropLast(recentMessagesMax)
        if (estimatedTokens <= summaryTriggerTokens || olderMessages.isEmpty()) {
            return Result.success(
                PreparedContextWindow(
                    summaryText = initialSummary,
                    recentMessages = unsummarized,
                )
            )
        }

        val summaryPrompt = buildSummaryPrompt(
            existingSummary = initialSummary,
            messages = olderMessages,
        )
        val summaryModel = summaryModelOverride
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: agent.model.trim().takeIf { it.isNotEmpty() }
        val summaryResult = generateTextUseCase.execute(
            GenerateCommand(
                prompt = summaryPrompt,
                model = summaryModel,
                temperature = SUMMARY_TEMPERATURE,
                maxOutputTokens = summaryMaxOutputTokens,
                stopSequences = null,
                agentMode = null,
            )
        )
        val summaryText = summaryResult.getOrNull()?.text?.trim().orEmpty()
        if (summaryText.isBlank()) {
            return Result.success(
                PreparedContextWindow(
                    summaryText = initialSummary,
                    recentMessages = unsummarized,
                )
            )
        }

        val summarizedUntilCreatedAt = olderMessages.maxOfOrNull { it.createdAt } ?: initialCutoff
        repository.upsertContextMemory(
            userId = userId,
            memory = AgentContextMemory(
                agentId = agent.id,
                summaryText = summaryText,
                summarizedUntilCreatedAt = summarizedUntilCreatedAt,
                updatedAt = System.currentTimeMillis(),
            )
        ).getOrElse { return Result.failure(it) }

        return Result.success(
            PreparedContextWindow(
                summaryText = summaryText,
                recentMessages = unsummarized
                    .filter { it.createdAt > summarizedUntilCreatedAt }
                    .takeLast(recentMessagesMax),
            )
        )
    }

    private fun buildSummaryPrompt(
        existingSummary: String,
        messages: List<AgentMessage>,
    ): String {
        val turns = messages.joinToString(separator = "\n") { message ->
            val role = when (message.role) {
                AgentMessageRole.USER -> "USER"
                AgentMessageRole.ASSISTANT -> "ASSISTANT"
            }
            "$role: ${message.text}"
        }

        return buildString {
            appendLine("You maintain compressed memory for an assistant conversation.")
            appendLine("Write an updated summary using only facts from the provided data.")
            appendLine("Keep constraints, decisions, requirements, open questions, and pending tasks.")
            appendLine("Remove repetition and chit-chat. Keep it concise.")
            appendLine()
            appendLine("EXISTING SUMMARY:")
            appendLine(existingSummary.ifBlank { "(none)" })
            appendLine()
            appendLine("NEW TURNS TO COMPRESS:")
            appendLine(turns)
            appendLine()
            appendLine("Return only the updated summary.")
        }
    }
}

private const val STATUS_STOPPED = "stopped"
private const val SUMMARY_TEMPERATURE = 0.2
