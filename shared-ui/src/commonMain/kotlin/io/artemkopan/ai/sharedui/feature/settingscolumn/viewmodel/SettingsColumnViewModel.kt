package io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.feature.settingscolumn.model.SettingsColumnUiModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsColumnViewModel(
    private val agentId: AgentId,
    private val sessionStore: AgentSessionStore,
) : ViewModel() {

    val state: StateFlow<SettingsColumnUiModel> = sessionStore.observeAgent(agentId)
        .map { slice ->
            SettingsColumnUiModel(
                agent = slice?.agent,
                agentConfig = slice?.agentConfig,
                contextTotalTokensLabel = slice?.contextTotalTokensLabel ?: "n/a",
                contextLeftLabel = slice?.contextLeftLabel ?: "n/a",
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsColumnUiModel(),
        )

    fun onAgentModeChanged(mode: AgentMode) {
        sessionStore.updateAgentMode(agentId, mode)
    }

    fun onContextStrategyChanged(value: String) {
        sessionStore.updateContextStrategy(agentId, value)
    }

    fun onContextRecentMessagesChanged(value: String) {
        sessionStore.updateContextRecentMessages(agentId, value)
    }

    fun onContextSummarizeEveryChanged(value: String) {
        sessionStore.updateContextSummarizeEvery(agentId, value)
    }

    fun onContextWindowSizeChanged(value: String) {
        sessionStore.updateContextWindowSize(agentId, value)
    }

    fun onSwitchBranch(branchId: String) {
        sessionStore.switchBranch(agentId, branchId)
    }

    fun onDeleteBranch(branchId: String) {
        sessionStore.deleteBranch(agentId, branchId)
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
