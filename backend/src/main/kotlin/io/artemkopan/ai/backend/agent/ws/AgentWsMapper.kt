package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.domain.model.*
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
    val goal: String = "",
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
                        invariants = agent.invariants,
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
        val parsedPlan = parsePlanJson(task.planJson)
        val validationChecks = parseValidationJson(task.validationJson)

        return TaskStateSnapshotDto(
            agentId = task.agentId.value,
            task = TaskDto(
                id = task.id.value,
                agentId = task.agentId.value,
                title = task.title,
                currentPhase = task.currentPhase.toDto(),
                steps = task.steps.map { step ->
                    TaskStepDto(
                        index = step.index,
                        phase = step.phase.toDto(),
                        description = step.description,
                        expectedAction = step.expectedAction,
                        status = step.status.name.lowercase(),
                        result = step.result,
                    )
                },
                currentStepIndex = task.currentStepIndex,
                planSteps = parsedPlan.planSteps,
                questionForUser = parsedPlan.questionForUser,
                goal = parsedPlan.goal,
                validationChecks = validationChecks,
            ),
        )
    }

    fun toPhaseChangedDto(
        agentId: String,
        taskId: String,
        fromPhase: TaskPhase,
        toPhase: TaskPhase,
        reason: String,
    ): TaskPhaseChangedDto = TaskPhaseChangedDto(
        agentId = agentId,
        taskId = taskId,
        fromPhase = fromPhase.toDto(),
        toPhase = toPhase.toDto(),
        reason = reason,
    )

    private fun parsePlanJson(planJson: String): ParsedPlan {
        if (planJson.isBlank()) return ParsedPlan()
        return runCatching {
            val dto = json.decodeFromString<PlanJsonDto>(planJson)
            ParsedPlan(planSteps = dto.plan, questionForUser = dto.questionForUser, goal = dto.goal)
        }.getOrElse { _ -> ParsedPlan() }
    }

    private data class ParsedPlan(
        val planSteps: List<String> = emptyList(),
        val questionForUser: String = "",
        val goal: String = "",
    )

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

fun TaskPhase.toDto(): TaskPhaseDto = when (this) {
    TaskPhase.Planning -> TaskPhaseDto.PLANNING
    TaskPhase.WaitingForApproval -> TaskPhaseDto.WAITING_FOR_APPROVAL
    TaskPhase.WaitingForUserInput -> TaskPhaseDto.WAITING_FOR_USER_INPUT
    TaskPhase.Execution -> TaskPhaseDto.EXECUTION
    TaskPhase.Validation -> TaskPhaseDto.VALIDATION
    TaskPhase.Paused -> TaskPhaseDto.PAUSED
    TaskPhase.Done -> TaskPhaseDto.DONE
    TaskPhase.Failed -> TaskPhaseDto.FAILED
    TaskPhase.Stopped -> TaskPhaseDto.STOPPED
}
