package io.artemkopan.ai.sharedui.feature.root.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.feature.root.model.RootUiModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RootViewModel(
    private val sessionStore: AgentSessionStore,
) : ViewModel() {

    val state: StateFlow<RootUiModel> = sessionStore.sessionState
        .map { session ->
            RootUiModel(
                agentOrder = session.agentOrder,
                activeAgentId = session.activeAgentId,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = RootUiModel(),
        )

    fun onSubmitShortcut() {
        sessionStore.submitFromActiveAgent()
    }

    fun onCreateAgentShortcut() {
        sessionStore.createAgent()
    }

    fun onSelectNextAgentShortcut() {
        sessionStore.selectNextAgent()
    }

    fun onSelectPreviousAgentShortcut() {
        sessionStore.selectPreviousAgent()
    }

    override fun onCleared() {
        sessionStore.dispose()
        super.onCleared()
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
