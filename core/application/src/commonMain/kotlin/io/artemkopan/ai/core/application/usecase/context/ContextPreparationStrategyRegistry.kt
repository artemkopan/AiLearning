package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.AgentContextConfig
import io.artemkopan.ai.core.domain.model.FullHistoryAgentContextConfig
import io.artemkopan.ai.core.domain.model.RollingSummaryAgentContextConfig

class ContextPreparationStrategyRegistry(
    private val fullHistoryStrategy: FullHistoryContextPreparationStrategy,
    private val rollingSummaryStrategy: RollingSummaryContextPreparationStrategy,
) {
    fun resolve(config: AgentContextConfig): ContextPreparationStrategy {
        return when (config) {
            is FullHistoryAgentContextConfig -> fullHistoryStrategy
            is RollingSummaryAgentContextConfig -> rollingSummaryStrategy
        }
    }
}
