package io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.feature.settingscolumn.model.SettingsColumnUiModel
import io.artemkopan.ai.sharedui.usecase.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsColumnViewModel(
    private val agentId: AgentId,
    private val sessionStore: AgentSessionStore,
    private val updateAgentModeActionUseCase: UpdateAgentModeActionUseCase,
    private val updateContextStrategyActionUseCase: UpdateContextStrategyActionUseCase,
    private val updateContextRecentMessagesActionUseCase: UpdateContextRecentMessagesActionUseCase,
    private val updateContextSummarizeEveryActionUseCase: UpdateContextSummarizeEveryActionUseCase,
    private val updateContextWindowSizeActionUseCase: UpdateContextWindowSizeActionUseCase,
    private val switchBranchActionUseCase: SwitchBranchActionUseCase,
    private val deleteBranchActionUseCase: DeleteBranchActionUseCase,
    private val keepDigitsUseCase: KeepDigitsUseCase,
) : ViewModel() {

    val state: StateFlow<SettingsColumnUiModel> = sessionStore.observeAgent(agentId)
        .map { slice ->
            val latestAssistant = slice?.agent?.messages?.lastOrNull { message ->
                message.role == AgentMessageRoleDto.ASSISTANT &&
                    message.status.equals("done", ignoreCase = true)
            }
            SettingsColumnUiModel(
                agent = slice?.agent,
                agentConfig = slice?.agentConfig,
                contextTotalTokensLabel = slice?.contextTotalTokensLabel ?: "n/a",
                contextLeftLabel = slice?.contextLeftLabel ?: "n/a",
                runtimeOutputTokensLabel = latestAssistant?.usage?.outputTokens?.toString() ?: "n/a",
                runtimeApiDurationLabel = latestAssistant?.latencyMs?.let { "$it ms" } ?: "n/a",
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsColumnUiModel(),
        )

    fun onAgentModeChanged(mode: AgentMode) {
        updateAgentModeActionUseCase(agentId, mode)
    }

    fun onContextStrategyChanged(value: String) {
        updateContextStrategyActionUseCase(agentId, value)
    }

    fun onContextRecentMessagesChanged(value: String) {
        updateContextRecentMessagesActionUseCase(agentId, keepDigitsUseCase(value))
    }

    fun onContextSummarizeEveryChanged(value: String) {
        updateContextSummarizeEveryActionUseCase(agentId, keepDigitsUseCase(value))
    }

    fun onContextWindowSizeChanged(value: String) {
        updateContextWindowSizeActionUseCase(agentId, keepDigitsUseCase(value))
    }

    fun onSwitchBranch(branchId: String) {
        switchBranchActionUseCase(agentId, branchId)
    }

    fun onDeleteBranch(branchId: String) {
        deleteBranchActionUseCase(agentId, branchId)
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
