package io.artemkopan.ai.sharedui.feature.conversationcolumn.command

import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentState

data class ConversationCommandContext(
    val activeAgentId: AgentId?,
    val allAgents: List<AgentState>,
)
