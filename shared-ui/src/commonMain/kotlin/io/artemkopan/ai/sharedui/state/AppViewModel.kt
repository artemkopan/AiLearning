package io.artemkopan.ai.sharedui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedui.gateway.PromptGateway
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GenerationResult(
    val text: String,
    val provider: String,
    val model: String,
)

data class ErrorPopupState(
    val title: String,
    val message: String,
)

data class UiState(
    val prompt: String = "",
    val isLoading: Boolean = false,
    val response: GenerationResult? = null,
    val errorPopup: ErrorPopupState? = null,
)

sealed interface UiAction {
    data class PromptChanged(val value: String) : UiAction
    data object Submit : UiAction
    data object DismissError : UiAction
}

class AppViewModel(
    private val gateway: PromptGateway,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    private var generationJob: Job? = null
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        Napier.d(tag = TAG) { "AppViewModel initialized" }
    }

    fun onAction(action: UiAction) {
        when (action) {
            is UiAction.PromptChanged -> handlePromptChanged(action.value)
            is UiAction.Submit -> handleSubmit()
            is UiAction.DismissError -> handleDismissError()
        }
    }

    private fun handlePromptChanged(value: String) {
        _state.update { it.copy(prompt = value) }
    }

    private fun handleDismissError() {
        Napier.d(tag = TAG) { "Error dismissed" }
        _state.update { it.copy(errorPopup = null) }
    }

    private fun handleSubmit() {
        val currentPrompt = _state.value.prompt.trim()
        if (currentPrompt.isBlank()) {
            Napier.w(tag = TAG) { "Submit blocked: prompt is blank" }
            _state.update {
                it.copy(
                    errorPopup = ErrorPopupState(
                        title = "Validation Error",
                        message = "Prompt must not be blank.",
                    )
                )
            }
            return
        }

        // Cancel previous request if still running
        generationJob?.let { job ->
            if (job.isActive) {
                Napier.d(tag = TAG) { "Cancelling previous generation request" }
                job.cancel()
            }
        }

        Napier.i(tag = TAG) { "Submitting prompt: length=${currentPrompt.length}" }
        _state.update { it.copy(isLoading = true, errorPopup = null) }

        generationJob = viewModelScope.launch {
            gateway.generate(GenerateRequestDto(prompt = currentPrompt))
                .onSuccess { dto ->
                    Napier.i(tag = TAG) {
                        "Generation successful: requestId=${dto.requestId}, latencyMs=${dto.latencyMs}"
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            response = GenerationResult(
                                text = dto.text,
                                provider = dto.provider,
                                model = dto.model,
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    Napier.e(tag = TAG, throwable = throwable) { "Generation failed" }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorPopup = ErrorPopupState(
                                title = "Request Failed",
                                message = throwable.message ?: "Unexpected error. Please try again.",
                            )
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Napier.d(tag = TAG) { "AppViewModel cleared" }
    }

    private companion object {
        const val TAG = "AppViewModel"
    }
}
