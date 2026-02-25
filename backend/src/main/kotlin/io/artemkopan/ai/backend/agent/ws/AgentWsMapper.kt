package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.sharedcontract.AgentDto
import io.artemkopan.ai.sharedcontract.AgentMessageDto
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedcontract.AgentMode
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
                        model = agent.model,
                        maxOutputTokens = agent.maxOutputTokens,
                        temperature = agent.temperature,
                        stopSequences = agent.stopSequences,
                        agentMode = parseAgentMode(agent.agentMode),
                        status = agent.status.value,
                        contextSummary = agent.contextSummary,
                        summarizedUntilCreatedAt = agent.summarizedUntilCreatedAt,
                        messages = agent.messages.map { message ->
                            AgentMessageDto(
                                id = message.id.value,
                                role = when (message.role) {
                                    AgentMessageRole.USER -> AgentMessageRoleDto.USER
                                    AgentMessageRole.ASSISTANT -> AgentMessageRoleDto.ASSISTANT
                                },
                                text = message.text,
                                status = message.status,
                                createdAt = message.createdAt,
                                provider = message.provider,
                                model = message.model,
                                usage = message.usage?.let { usage ->
                                    TokenUsageDto(
                                        inputTokens = usage.inputTokens,
                                        outputTokens = usage.outputTokens,
                                        totalTokens = usage.totalTokens,
                                    )
                                },
                                latencyMs = message.latencyMs,
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
