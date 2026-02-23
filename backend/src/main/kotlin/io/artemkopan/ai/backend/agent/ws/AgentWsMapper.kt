package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.sharedcontract.AgentDto
import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedcontract.AgentResponseDto
import io.artemkopan.ai.sharedcontract.AgentStateSnapshotDto
import io.artemkopan.ai.sharedcontract.AgentStateSnapshotMessageDto
import io.artemkopan.ai.sharedcontract.TokenUsageDto

class AgentWsMapper {
    fun toSnapshotMessage(state: AgentState): AgentStateSnapshotMessageDto {
        return AgentStateSnapshotMessageDto(
            state = AgentStateSnapshotDto(
                agents = state.agents.map { agent ->
                    AgentDto(
                        id = agent.id.value,
                        title = agent.title,
                        prompt = agent.prompt,
                        model = agent.model,
                        maxOutputTokens = agent.maxOutputTokens,
                        temperature = agent.temperature,
                        stopSequences = agent.stopSequences,
                        agentMode = parseAgentMode(agent.agentMode),
                        status = agent.status.value,
                        response = agent.response?.let { response ->
                            AgentResponseDto(
                                text = response.text,
                                provider = response.provider,
                                model = response.model,
                                usage = response.usage?.let { usage ->
                                    TokenUsageDto(
                                        inputTokens = usage.inputTokens,
                                        outputTokens = usage.outputTokens,
                                        totalTokens = usage.totalTokens,
                                    )
                                },
                                latencyMs = response.latencyMs,
                            )
                        },
                    )
                },
                activeAgentId = state.activeAgentId?.value,
                version = state.version,
            )
        )
    }

    private fun parseAgentMode(value: String): AgentMode {
        return when (value.lowercase()) {
            "engineer" -> AgentMode.ENGINEER
            "philosophic" -> AgentMode.PHILOSOPHIC
            "critic" -> AgentMode.CRITIC
            else -> AgentMode.DEFAULT
        }
    }
}
