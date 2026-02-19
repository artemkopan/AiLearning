package io.artemkopan.ai.sharedui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedui.gateway.PromptGateway
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsageResult(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

data class GenerationResult(
    val text: String,
    val provider: String,
    val model: String,
    val usage: UsageResult? = null,
)

data class ErrorPopupState(
    val title: String,
    val message: String,
)

data class UiState(
    val prompt: String = "",
    val maxOutputTokens: String = "",
    val stopSequences: String = "",
    val agentMode: AgentMode = AgentMode.DEFAULT,
    val isLoading: Boolean = false,
    val response: GenerationResult? = null,
    val errorPopup: ErrorPopupState? = null,
)

sealed interface UiAction {
    data class PromptChanged(val value: String) : UiAction
    data class MaxOutputTokensChanged(val value: String) : UiAction
    data class StopSequencesChanged(val value: String) : UiAction
    data class AgentModeChanged(val value: AgentMode) : UiAction
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
            is UiAction.MaxOutputTokensChanged -> handleMaxOutputTokensChanged(action.value)
            is UiAction.StopSequencesChanged -> handleStopSequencesChanged(action.value)
            is UiAction.AgentModeChanged -> handleAgentModeChanged(action.value)
            is UiAction.Submit -> handleSubmit()
            is UiAction.DismissError -> handleDismissError()
        }
    }

    private fun handlePromptChanged(value: String) {
        _state.update { it.copy(prompt = value) }
    }

    private fun handleMaxOutputTokensChanged(value: String) {
        _state.update { it.copy(maxOutputTokens = value.filter { ch -> ch.isDigit() }) }
    }

    private fun handleStopSequencesChanged(value: String) {
        _state.update { it.copy(stopSequences = value) }
    }

    private fun handleAgentModeChanged(value: AgentMode) {
        _state.update { it.copy(agentMode = value) }
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

        val maxTokens = _state.value.maxOutputTokens.toIntOrNull()
        val stopSeqs = _state.value.stopSequences
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }

        generationJob = viewModelScope.launch {
            gateway.generate(
                GenerateRequestDto(
                    prompt = currentPrompt,
                    maxOutputTokens = maxTokens,
                    stopSequences = stopSeqs,
                    agentMode = _state.value.agentMode.takeIf { it != AgentMode.DEFAULT },
                )
            )
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
                                usage = dto.usage?.let { usage ->
                                    UsageResult(
                                        inputTokens = usage.inputTokens,
                                        outputTokens = usage.outputTokens,
                                        totalTokens = usage.totalTokens,
                                    )
                                },
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
