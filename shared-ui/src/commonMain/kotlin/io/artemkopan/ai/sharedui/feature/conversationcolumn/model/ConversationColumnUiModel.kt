package io.artemkopan.ai.sharedui.feature.conversationcolumn.model

import io.artemkopan.ai.sharedui.core.session.AgentMessageState
import io.artemkopan.ai.sharedui.core.session.AgentState
import io.artemkopan.ai.sharedui.core.session.QueuedMessageState

data class ConversationColumnUiModel(
    val agent: AgentState? = null,
    val allAgents: List<AgentState> = emptyList(),
    val queuedMessages: List<QueuedMessageState> = emptyList(),
)

data class ConversationDisplayMessage(
    val id: String,
    val message: AgentMessageState,
    val isQueuedLocal: Boolean,
)
