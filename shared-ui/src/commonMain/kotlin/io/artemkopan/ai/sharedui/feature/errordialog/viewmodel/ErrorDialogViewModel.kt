package io.artemkopan.ai.sharedui.feature.errordialog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.feature.errordialog.model.ErrorDialogUiModel
import io.artemkopan.ai.sharedui.usecase.DismissErrorActionUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ErrorDialogViewModel(
    private val sessionStore: AgentSessionStore,
    private val dismissErrorActionUseCase: DismissErrorActionUseCase,
) : ViewModel() {

    val state: StateFlow<ErrorDialogUiModel> = sessionStore.observeError()
        .map { ErrorDialogUiModel(error = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ErrorDialogUiModel(),
        )

    fun dismiss() {
        dismissErrorActionUseCase()
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
