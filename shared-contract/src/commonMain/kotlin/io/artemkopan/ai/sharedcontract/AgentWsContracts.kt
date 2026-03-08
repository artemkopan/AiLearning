package io.artemkopan.ai.sharedcontract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
data class TaskDto(
    val id: String,
    val agentId: String,
    val title: String,
    val currentPhase: String,
    val steps: List<TaskStepDto>,
    val currentStepIndex: Int,
    val planSteps: List<String> = emptyList(),
    val questionForUser: String = "",
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
    val phase: String,
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
@SerialName("agent_operation_failed")
data class AgentOperationFailedDto(
    val code: String,
    val message: String,
    val requestId: String? = null,
) : AgentWsServerMessageDto
