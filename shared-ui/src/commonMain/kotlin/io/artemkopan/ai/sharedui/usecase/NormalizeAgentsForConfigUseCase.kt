package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedui.state.AgentId
import io.artemkopan.ai.sharedui.state.AgentState

data class NormalizedAgentsForConfigResult(
    val agents: Map<AgentId, AgentState>,
    val draftUpdates: List<AgentState>,
)

class NormalizeAgentsForConfigUseCase(
    private val normalizeModelUseCase: NormalizeModelUseCase,
) {
    operator fun invoke(
        agents: Map<AgentId, AgentState>,
        config: AgentConfigDto,
    ): NormalizedAgentsForConfigResult {
        val updates = mutableListOf<AgentState>()
        val normalizedAgents = agents.mapValues { (_, agent) ->
            val normalizedModel = normalizeModelUseCase(agent.model, config)
            if (normalizedModel != agent.model && normalizedModel.isNotBlank()) {
                val updated = agent.copy(model = normalizedModel)
                updates += updated
                updated
            } else {
                agent
            }
        }

        return NormalizedAgentsForConfigResult(
            agents = normalizedAgents,
            draftUpdates = updates,
        )
    }
}
