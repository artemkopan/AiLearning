package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.AgentMessageType
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.sharedcontract.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Serializable
private data class PlanJsonDto(
    val plan: List<String> = emptyList(),
    @SerialName("question_for_user")
    val questionForUser: String = "",
)

@Serializable
private data class ValidationJsonDto(
    val checks: List<ValidationCheckDto> = emptyList(),
)

@Single
class AgentWsMapper(
    private val json: Json,
) {
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
                        status = agent.status.value,
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
                                messageType = message.messageType.toDto(),
                            )
                        },
                    )
                },
                activeAgentId = state.activeAgentId?.value,
                version = state.version,
            )
        )
    }

    fun toTaskStateSnapshot(task: AgentTask): TaskStateSnapshotDto {
        val (planSteps, questionForUser) = parsePlanJson(task.planJson)
        val validationChecks = parseValidationJson(task.validationJson)

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
                planSteps = planSteps,
                questionForUser = questionForUser,
                validationChecks = validationChecks,
            ),
        )
    }

    private fun parsePlanJson(planJson: String): Pair<List<String>, String> {
        if (planJson.isBlank()) return emptyList<String>() to ""
        return runCatching {
            val dto = json.decodeFromString<PlanJsonDto>(planJson)
            dto.plan to dto.questionForUser
        }.getOrElse { _ -> emptyList<String>() to "" }
    }

    private fun parseValidationJson(validationJson: String): List<ValidationCheckDto> {
        if (validationJson.isBlank()) return emptyList()
        return runCatching {
            val dto = json.decodeFromString<ValidationJsonDto>(validationJson)
            dto.checks
        }.getOrElse { _ -> emptyList<ValidationCheckDto>() }
    }

    private fun AgentMessageType.toDto(): AgentMessageTypeDto = when (this) {
        AgentMessageType.TEXT -> AgentMessageTypeDto.TEXT
        AgentMessageType.REVIEW -> AgentMessageTypeDto.REVIEW
        AgentMessageType.EXECUTION_CONFIRMATION -> AgentMessageTypeDto.EXECUTION_CONFIRMATION
    }

}
