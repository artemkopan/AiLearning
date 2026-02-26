package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.state.UiState

class EnrichRuntimeStateUseCase(
    private val computeContextLeftLabelUseCase: ComputeContextLeftLabelUseCase,
) {
    operator fun invoke(state: UiState): UiState {
        val contextTotalTokensByAgent = state.agents.mapValues { (_, agent) ->
            computeContextLeftLabelUseCase.computeContextUsedTokens(agent).toString()
        }
        val contextLeftByAgent = state.agents.mapValues { (_, agent) ->
            computeContextLeftLabelUseCase(
                agent = agent,
                config = state.agentConfig,
            )
        }
        return state.copy(
            contextTotalTokensByAgent = contextTotalTokensByAgent,
            contextLeftByAgent = contextLeftByAgent,
        )
    }
}
