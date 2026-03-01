package io.artemkopan.ai.sharedui.feature.agentssidepanel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.feature.agentssidepanel.model.AgentsSidePanelItemModel
import io.artemkopan.ai.sharedui.feature.agentssidepanel.model.AgentsSidePanelUiModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AgentsSidePanelViewModel(
    private val sessionStore: AgentSessionStore,
) : ViewModel() {

    val state: StateFlow<AgentsSidePanelUiModel> = sessionStore.sessionState
        .map { session ->
            AgentsSidePanelUiModel(
                agents = session.agentOrder.mapNotNull { id ->
                    val agent = session.agents[id] ?: return@mapNotNull null
                    AgentsSidePanelItemModel(
                        id = id,
                        title = agent.title,
                        status = agent.status,
                        isLoading = agent.isLoading,
                    )
                },
                activeAgentId = session.activeAgentId,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AgentsSidePanelUiModel(),
        )

    fun onCreateAgentClicked() {
        sessionStore.createAgent()
    }

    fun onAgentSelected(agentId: AgentId) {
        sessionStore.selectAgent(agentId)
    }

    fun onAgentClosed(agentId: AgentId) {
        sessionStore.closeAgent(agentId)
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
