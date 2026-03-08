package io.artemkopan.ai.sharedui.core.session

import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedcontract.AgentMessageTypeDto
import io.artemkopan.ai.sharedcontract.TaskPhaseDto

data class UsageResult(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

data class GenerationResult(
    val text: String,
    val provider: String,
    val model: String,
    val usage: UsageResult? = null,
    val latencyMs: Long = 0,
)

data class AgentMessageState(
    val id: String,
    val role: AgentMessageRoleDto,
    val text: String,
    val status: String,
    val createdAt: Long,
    val provider: String? = null,
    val model: String? = null,
    val usage: UsageResult? = null,
    val latencyMs: Long? = null,
    val messageType: AgentMessageTypeDto = AgentMessageTypeDto.TEXT,
)

data class ErrorDialogModel(
    val title: String,
    val message: String,
)

data class AgentId(val value: String)

enum class QueuedMessageStatus {
    QUEUED,
    SENDING,
}

data class QueuedDraftSnapshot(
    val model: String,
    val maxOutputTokens: String,
    val temperature: String,
    val stopSequences: String,
)

data class QueuedMessageState(
    val id: String,
    val text: String,
    val status: QueuedMessageStatus,
    val createdAt: Long,
    val draftSnapshot: QueuedDraftSnapshot,
)

data class AgentState(
    val id: AgentId,
    val title: String,
    val model: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val stopSequences: String = "",
    val status: String = STATUS_DONE,
    val messages: List<AgentMessageState> = emptyList(),
    val draftMessage: String = "",
) {
    val isLoading: Boolean
        get() = messages.any { it.status.equals(STATUS_PROCESSING, ignoreCase = true) }
}

data class SessionState(
    val agents: Map<AgentId, AgentState> = emptyMap(),
    val agentOrder: List<AgentId> = emptyList(),
    val activeAgentId: AgentId? = null,
    val errorDialog: ErrorDialogModel? = null,
    val agentConfig: AgentConfigDto? = null,
    val queuedByAgent: Map<AgentId, List<QueuedMessageState>> = emptyMap(),
    val isConnected: Boolean = false,
    val taskByAgent: Map<AgentId, TaskState> = emptyMap(),
)

data class AgentSessionSlice(
    val agent: AgentState,
    val queuedMessages: List<QueuedMessageState>,
    val agentConfig: AgentConfigDto?,
)

data class TaskStepState(
    val index: Int,
    val phase: TaskPhaseDto,
    val description: String,
    val expectedAction: String,
    val status: String,
    val result: String = "",
)

data class TaskValidationCheckState(
    val name: String,
    val passed: Boolean,
)

data class TaskState(
    val id: String,
    val title: String,
    val currentPhase: TaskPhaseDto,
    val steps: List<TaskStepState>,
    val currentStepIndex: Int,
    val planSteps: List<String> = emptyList(),
    val questionForUser: String = "",
    val goal: String = "",
    val validationChecks: List<TaskValidationCheckState> = emptyList(),
)

const val STATUS_PROCESSING = "processing"
const val STATUS_DONE = "done"
const val STATUS_STOPPED = "stopped"
