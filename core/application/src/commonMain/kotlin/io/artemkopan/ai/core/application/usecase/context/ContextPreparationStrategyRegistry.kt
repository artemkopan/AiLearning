package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.*

class ContextPreparationStrategyRegistry(
    private val fullHistoryStrategy: FullHistoryContextPreparationStrategy,
    private val rollingSummaryStrategy: RollingSummaryContextPreparationStrategy,
    private val slidingWindowStrategy: SlidingWindowContextPreparationStrategy,
    private val stickyFactsStrategy: StickyFactsContextPreparationStrategy,
    private val branchingStrategy: BranchingContextPreparationStrategy,
) {
    fun resolve(config: AgentContextConfig): ContextPreparationStrategy {
        return when (config) {
            is FullHistoryAgentContextConfig -> fullHistoryStrategy
            is RollingSummaryAgentContextConfig -> rollingSummaryStrategy
            is SlidingWindowAgentContextConfig -> slidingWindowStrategy
            is StickyFactsAgentContextConfig -> stickyFactsStrategy
            is BranchingAgentContextConfig -> branchingStrategy
        }
    }
}
