package io.artemkopan.ai.sharedcontract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TaskPhaseDto {
    @SerialName("PLANNING") PLANNING,
    @SerialName("WAITING_FOR_APPROVAL") WAITING_FOR_APPROVAL,
    @SerialName("WAITING_FOR_USER_INPUT") WAITING_FOR_USER_INPUT,
    @SerialName("EXECUTION") EXECUTION,
    @SerialName("VALIDATION") VALIDATION,
    @SerialName("PAUSED") PAUSED,
    @SerialName("DONE") DONE,
    @SerialName("FAILED") FAILED,
    @SerialName("STOPPED") STOPPED,
}

@Serializable
sealed interface AgentWsClientMessageDto

@Serializable
@SerialName("subscribe_agents")
data class SubscribeAgentsDto(
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("create_agent")
data class CreateAgentCommandDto(
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("select_agent")
data class SelectAgentCommandDto(
    val agentId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("close_agent")
data class CloseAgentCommandDto(
    val agentId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("send_agent_message")
data class SendAgentMessageCommandDto(
    val agentId: String,
    val text: String,
    val skipGeneration: Boolean = false,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("stop_agent_message")
data class StopAgentMessageCommandDto(
    val agentId: String,
    val messageId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("accept_plan")
data class AcceptPlanCommandDto(
    val agentId: String,
    val taskId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("reject_plan")
data class RejectPlanCommandDto(
    val agentId: String,
    val taskId: String,
    val reason: String = "",
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("pause_task")
data class PauseTaskCommandDto(
    val agentId: String,
    val taskId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("resume_task")
data class ResumeTaskCommandDto(
    val agentId: String,
    val taskId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("update_agent_invariants")
data class UpdateAgentInvariantsCommandDto(
    val agentId: String,
    val invariants: List<String>,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("stop_task")
data class StopTaskCommandDto(
    val agentId: String,
    val taskId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
data class TaskDto(
    val id: String,
    val agentId: String,
    val title: String,
    val currentPhase: TaskPhaseDto,
    val steps: List<TaskStepDto>,
    val currentStepIndex: Int,
    val planSteps: List<String> = emptyList(),
    val questionForUser: String = "",
    val goal: String = "",
    val validationChecks: List<ValidationCheckDto> = emptyList(),
)

@Serializable
data class ValidationCheckDto(
    val name: String = "",
    val passed: Boolean = false,
)

@Serializable
data class TaskStepDto(
    val index: Int,
    val phase: TaskPhaseDto,
    val description: String,
    val expectedAction: String,
    val status: String,
    val result: String = "",
)

@Serializable
data class AgentStateSnapshotDto(
    val agents: List<AgentDto>,
    val activeAgentId: String? = null,
    val version: Long,
)

@Serializable
sealed interface AgentWsServerMessageDto

@Serializable
@SerialName("agent_state_snapshot")
data class AgentStateSnapshotMessageDto(
    val state: AgentStateSnapshotDto,
) : AgentWsServerMessageDto

@Serializable
@SerialName("task_state_snapshot")
data class TaskStateSnapshotDto(
    val agentId: String,
    val task: TaskDto?,
) : AgentWsServerMessageDto

@Serializable
@SerialName("task_phase_changed")
data class TaskPhaseChangedDto(
    val agentId: String,
    val taskId: String,
    val fromPhase: TaskPhaseDto,
    val toPhase: TaskPhaseDto,
    val reason: String = "",
) : AgentWsServerMessageDto

@Serializable
@SerialName("agent_operation_failed")
data class AgentOperationFailedDto(
    val code: String,
    val message: String,
    val requestId: String? = null,
) : AgentWsServerMessageDto
