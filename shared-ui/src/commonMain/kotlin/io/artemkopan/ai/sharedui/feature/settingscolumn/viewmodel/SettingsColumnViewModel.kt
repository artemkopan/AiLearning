package io.artemkopan.ai.sharedui.feature.settingscolumn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.feature.settingscolumn.model.SettingsColumnUiModel
import io.artemkopan.ai.sharedui.usecase.ObserveAgentSliceUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsColumnViewModel(
    private val agentId: AgentId,
    private val observeAgentSliceUseCase: ObserveAgentSliceUseCase,
) : ViewModel() {

    val state: StateFlow<SettingsColumnUiModel> = observeAgentSliceUseCase(agentId)
        .map { slice ->
            val latestAssistant = slice?.agent?.messages?.lastOrNull { message ->
                message.role == AgentMessageRoleDto.ASSISTANT &&
                    message.status.equals("done", ignoreCase = true)
            }
            SettingsColumnUiModel(
                agent = slice?.agent,
                agentConfig = slice?.agentConfig,
                runtimeOutputTokensLabel = latestAssistant?.usage?.outputTokens?.toString() ?: "n/a",
                runtimeApiDurationLabel = latestAssistant?.latencyMs?.let { "$it ms" } ?: "n/a",
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsColumnUiModel(),
        )
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
