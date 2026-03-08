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
@SerialName("update_agent_draft")
data class UpdateAgentDraftCommandDto(
    val agentId: String,
    val model: String,
    val maxOutputTokens: String,
    val temperature: String,
    val stopSequences: String,
    val agentMode: AgentMode = AgentMode.DEFAULT,
    val contextConfig: AgentContextConfigDto = RollingSummaryContextConfigDto(),
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("close_agent")
data class CloseAgentCommandDto(
    val agentId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("submit_agent")
data class SubmitAgentCommandDto(
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
@SerialName("create_branch")
data class CreateBranchCommandDto(
    val agentId: String,
    val checkpointMessageId: String,
    val branchName: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("switch_branch")
data class SwitchBranchCommandDto(
    val agentId: String,
    val branchId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("delete_branch")
data class DeleteBranchCommandDto(
    val agentId: String,
    val branchId: String,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("update_user_profile")
data class UpdateUserProfileCommandDto(
    val communicationStyle: String,
    val responseFormat: String,
    val restrictions: List<String> = emptyList(),
    val customInstructions: String = "",
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("create_task")
data class CreateTaskCommandDto(
    val agentId: String,
    val title: String,
    val steps: List<TaskStepDto>,
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("transition_task_phase")
data class TransitionTaskPhaseCommandDto(
    val agentId: String,
    val taskId: String,
    val fromPhase: String,
    val targetPhase: String,
    val reason: String = "",
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("update_task_step")
data class UpdateTaskStepCommandDto(
    val agentId: String,
    val taskId: String,
    val stepIndex: Int,
    val status: String,
    val result: String = "",
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
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("edit_plan")
data class EditPlanCommandDto(
    val agentId: String,
    val taskId: String,
    val instructions: String = "",
    val requestId: String? = null,
) : AgentWsClientMessageDto

@Serializable
@SerialName("confirm_execution")
data class ConfirmExecutionCommandDto(
    val agentId: String,
    val taskId: String,
    val text: String = "",
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
data class BranchDto(
    val id: String,
    val name: String,
    val checkpointMessageId: String,
    val createdAt: Long,
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
@SerialName("user_profile_snapshot")
data class UserProfileSnapshotDto(
    val communicationStyle: String,
    val responseFormat: String,
    val restrictions: List<String>,
    val customInstructions: String,
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
