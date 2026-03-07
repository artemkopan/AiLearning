package io.artemkopan.ai.sharedui.feature.root.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutAction
import io.artemkopan.ai.sharedui.feature.root.model.RootShortcutEvent
import io.artemkopan.ai.sharedui.feature.root.model.RootUiModel
import io.artemkopan.ai.sharedui.usecase.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RootViewModel(
    private val observeSessionStateUseCase: ObserveSessionStateUseCase,
    private val disposeSessionUseCase: DisposeSessionUseCase,
    private val resolveRootShortcutActionUseCase: ResolveRootShortcutActionUseCase,
    private val submitFromActiveAgentActionUseCase: SubmitFromActiveAgentActionUseCase,
    private val createAgentActionUseCase: CreateAgentActionUseCase,
    private val selectNextAgentActionUseCase: SelectNextAgentActionUseCase,
    private val selectPreviousAgentActionUseCase: SelectPreviousAgentActionUseCase,
) : ViewModel() {

    val state: StateFlow<RootUiModel> = observeSessionStateUseCase()
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

    fun onShortcut(event: RootShortcutEvent): Boolean {
        return when (resolveRootShortcutActionUseCase(event)) {
            RootShortcutAction.SUBMIT -> {
                submitFromActiveAgentActionUseCase()
                true
            }
            RootShortcutAction.CREATE_AGENT -> {
                createAgentActionUseCase()
                true
            }
            RootShortcutAction.SELECT_NEXT_AGENT -> {
                selectNextAgentActionUseCase()
                true
            }
            RootShortcutAction.SELECT_PREVIOUS_AGENT -> {
                selectPreviousAgentActionUseCase()
                true
            }
            null -> false
        }
    }

    override fun onCleared() {
        disposeSessionUseCase()
        super.onCleared()
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
