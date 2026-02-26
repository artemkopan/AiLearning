package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.state.AgentId
import io.artemkopan.ai.sharedui.state.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class ActiveModelSelection(
    val agentId: AgentId,
    val modelId: String,
)

class ObserveActiveModelSelectionUseCase(
    private val normalizeModelUseCase: NormalizeModelUseCase,
) {
    operator fun invoke(state: Flow<UiState>): Flow<ActiveModelSelection?> {
        return state
            .map { current ->
                val activeAgentId = current.activeAgentId ?: return@map null
                val activeAgent = current.agents[activeAgentId] ?: return@map null
                val resolvedModelId = normalizeModelUseCase(activeAgent.model, current.agentConfig)
                if (resolvedModelId.isBlank()) return@map null
                ActiveModelSelection(
                    agentId = activeAgentId,
                    modelId = resolvedModelId,
                )
            }
            .distinctUntilChanged()
    }
}
