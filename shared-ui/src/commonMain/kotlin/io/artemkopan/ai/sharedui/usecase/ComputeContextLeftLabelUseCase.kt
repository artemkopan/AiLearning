package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.FullHistoryContextConfigDto
import io.artemkopan.ai.sharedui.state.AgentState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

class ComputeContextLeftLabelUseCase {
    fun computeContextUsedTokens(agent: AgentState): Int {
        val summaryTokens = if (agent.contextConfig is FullHistoryContextConfigDto) 0 else estimateTokens(agent.contextSummary)
        val contextMessages = agent.messages
            .filter { !it.status.equals(STATUS_STOPPED, ignoreCase = true) }
        val recentMessages = if (agent.contextConfig is FullHistoryContextConfigDto) {
            contextMessages
        } else {
            contextMessages.filter { it.createdAt > agent.summarizedUntilCreatedAt }
        }
        val messageTokens = recentMessages.sumOf { message ->
            estimateTokens(message.text) + 4
        }
        val draftTokens = if (agent.draftMessage.isBlank()) 0 else estimateTokens(agent.draftMessage) + 4
        return summaryTokens + messageTokens + draftTokens + 64
    }

    operator fun invoke(agent: AgentState, config: AgentConfigDto?): String {
        val contextWindow = resolveContextWindowTokens(agent, config) ?: return "n/a"
        val contextUsed = computeContextUsedTokens(agent)
        val contextLeft = max(contextWindow - contextUsed, 0)
        return formatCompactCount(contextLeft)
    }

    private fun resolveContextWindowTokens(
        agent: AgentState,
        config: AgentConfigDto?,
    ): Int? {
        if (config == null) return null

        val selectedModelId = agent.model.trim().ifBlank { config.defaultModel.trim() }
        return config.models.firstOrNull { it.id == selectedModelId }?.contextWindowTokens
            ?: config.models.firstOrNull { it.name == selectedModelId }?.contextWindowTokens
            ?: config.defaultContextWindowTokens
    }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length + 3) / 4
    }

    private fun formatCompactCount(value: Int): String {
        val absValue = abs(value.toLong())
        return when {
            absValue >= 1_000_000L -> "${formatOneDecimal(value / 1_000_000.0)}M"
            absValue >= 1_000L -> "${formatOneDecimal(value / 1_000.0)}K"
            else -> value.toString()
        }
    }

    private fun formatOneDecimal(value: Double): String {
        val rounded = round(value * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}

private const val STATUS_STOPPED = "stopped"
