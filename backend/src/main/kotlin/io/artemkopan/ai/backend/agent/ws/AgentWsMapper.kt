package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.sharedcontract.*
import org.koin.core.annotation.Single

@Single
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
                        contextConfig = agent.contextConfig.toDto(),
                        contextSummary = agent.contextSummary,
                        summarizedUntilCreatedAt = agent.summarizedUntilCreatedAt,
                        contextSummaryUpdatedAt = agent.contextSummaryUpdatedAt,
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
                        branches = agent.branches.map { branch ->
                            BranchDto(
                                id = branch.id,
                                name = branch.name,
                                checkpointMessageId = branch.checkpointMessageId.value,
                                createdAt = branch.createdAt,
                            )
                        },
                        activeBranchId = agent.activeBranchId,
                    )
                },
                activeAgentId = state.activeAgentId?.value,
                version = state.version,
            )
        )
    }

    fun toTaskStateSnapshot(task: AgentTask): TaskStateSnapshotDto {
        return TaskStateSnapshotDto(
            agentId = task.agentId.value,
            task = TaskDto(
                id = task.id.value,
                agentId = task.agentId.value,
                title = task.title,
                currentPhase = task.currentPhase.name.lowercase(),
                steps = task.steps.map { step ->
                    TaskStepDto(
                        index = step.index,
                        phase = step.phase.name.lowercase(),
                        description = step.description,
                        expectedAction = step.expectedAction,
                        status = step.status.name.lowercase(),
                        result = step.result,
                    )
                },
                currentStepIndex = task.currentStepIndex,
            ),
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
