package io.artemkopan.ai.sharedui.feature.root.model

import io.artemkopan.ai.sharedui.core.session.AgentId

data class RootUiModel(
    val agentOrder: List<AgentId> = emptyList(),
    val activeAgentId: AgentId? = null,
)
