package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.AgentStateSnapshotDto
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentMessageState
import io.artemkopan.ai.sharedui.core.session.AgentState
import io.artemkopan.ai.sharedui.core.session.UsageResult
import org.koin.core.annotation.Factory

data class SnapshotUiStateResult(
    val agents: Map<AgentId, AgentState>,
    val agentOrder: List<AgentId>,
    val activeAgentId: AgentId?,
    val draftUpdates: List<AgentState>,
)

@Factory
class MapSnapshotToUiStateUseCase(
    private val normalizeModelUseCase: NormalizeModelUseCase,
) {
    operator fun invoke(
        snapshot: AgentStateSnapshotDto,
        currentAgents: Map<AgentId, AgentState>,
        config: AgentConfigDto?,
    ): SnapshotUiStateResult {
        val updates = mutableListOf<AgentState>()
        val order = snapshot.agents.map { AgentId(it.id) }
        val mapped = snapshot.agents.associate { dto ->
            val id = AgentId(dto.id)
            val preservedDraft = currentAgents[id]?.draftMessage.orEmpty()
            val normalizedModel = config?.let { normalizeModelUseCase(dto.model, it) } ?: dto.model
            val mappedAgent = AgentState(
                id = id,
                title = dto.title,
                model = normalizedModel,
                maxOutputTokens = dto.maxOutputTokens,
                temperature = dto.temperature,
                stopSequences = dto.stopSequences,
                status = dto.status,
                messages = dto.messages.map { message ->
                    AgentMessageState(
                        id = message.id,
                        role = message.role,
                        text = message.text,
                        status = message.status,
                        createdAt = message.createdAt,
                        provider = message.provider,
                        model = message.model,
                        usage = message.usage?.let {
                            UsageResult(
                                inputTokens = it.inputTokens,
                                outputTokens = it.outputTokens,
                                totalTokens = it.totalTokens,
                            )
                        },
                        latencyMs = message.latencyMs,
                        messageType = message.messageType,
                    )
                },
                draftMessage = preservedDraft,
            )
            if (config != null && dto.model != normalizedModel && normalizedModel.isNotBlank()) {
                updates += mappedAgent
            }
            id to mappedAgent
        }

        return SnapshotUiStateResult(
            agents = mapped,
            agentOrder = order,
            activeAgentId = snapshot.activeAgentId?.let(::AgentId),
            draftUpdates = updates,
        )
    }
}
