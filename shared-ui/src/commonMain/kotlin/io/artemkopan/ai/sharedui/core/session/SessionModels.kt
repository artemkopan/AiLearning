package io.artemkopan.ai.sharedui.core.session

import io.artemkopan.ai.sharedcontract.*

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
    val agentMode: AgentMode,
    val contextConfig: AgentContextConfigDto,
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
    val agentMode: AgentMode = AgentMode.DEFAULT,
    val status: String = STATUS_DONE,
    val contextConfig: AgentContextConfigDto = RollingSummaryContextConfigDto(),
    val contextSummary: String = "",
    val summarizedUntilCreatedAt: Long = 0,
    val contextSummaryUpdatedAt: Long = 0,
    val messages: List<AgentMessageState> = emptyList(),
    val branches: List<BranchDto> = emptyList(),
    val activeBranchId: String? = null,
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
    val contextTotalTokensByAgent: Map<AgentId, String> = emptyMap(),
    val contextLeftByAgent: Map<AgentId, String> = emptyMap(),
    val queuedByAgent: Map<AgentId, List<QueuedMessageState>> = emptyMap(),
    val isConnected: Boolean = false,
)

data class AgentSessionSlice(
    val agent: AgentState,
    val queuedMessages: List<QueuedMessageState>,
    val contextTotalTokensLabel: String,
    val contextLeftLabel: String,
    val agentConfig: AgentConfigDto?,
)

const val STATUS_PROCESSING = "processing"
const val STATUS_DONE = "done"
const val STATUS_STOPPED = "stopped"
