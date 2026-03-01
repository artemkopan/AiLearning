package io.artemkopan.ai.sharedui.feature.agentssidepanel.model

import io.artemkopan.ai.sharedui.core.session.AgentId

data class AgentsSidePanelItemModel(
    val id: AgentId,
    val title: String,
    val status: String,
    val isLoading: Boolean,
)

data class AgentsSidePanelUiModel(
    val agents: List<AgentsSidePanelItemModel> = emptyList(),
    val activeAgentId: AgentId? = null,
)
