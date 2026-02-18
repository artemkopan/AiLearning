package io.artemkopan.ai.sharedui.state

import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto
import io.artemkopan.ai.sharedui.gateway.PromptGateway
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ErrorPopupState(
    val title: String,
    val message: String,
)

data class UiState(
    val prompt: String = "",
    val isLoading: Boolean = false,
    val response: GenerateResponseDto? = null,
    val errorPopup: ErrorPopupState? = null,
)

class AppState(
    private val gateway: PromptGateway,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    init {
        Napier.d(tag = TAG) { "AppState initialized" }
    }

    fun onPromptChanged(value: String) {
        mutableState.value = mutableState.value.copy(prompt = value)
    }

    fun dismissError() {
        Napier.d(tag = TAG) { "Error dismissed" }
        mutableState.value = mutableState.value.copy(errorPopup = null)
    }

    fun submit() {
        val currentPrompt = mutableState.value.prompt.trim()
        if (currentPrompt.isBlank()) {
            Napier.w(tag = TAG) { "Submit blocked: prompt is blank" }
            mutableState.value = mutableState.value.copy(
                errorPopup = ErrorPopupState(
                    title = "Validation Error",
                    message = "Prompt must not be blank.",
                )
            )
            return
        }

        Napier.i(tag = TAG) { "Submitting prompt: length=${currentPrompt.length}" }
        mutableState.value = mutableState.value.copy(isLoading = true, errorPopup = null)

        scope.launch {
            val result = gateway.generate(GenerateRequestDto(prompt = currentPrompt))
            mutableState.value = result.fold(
                onSuccess = { response ->
                    Napier.i(tag = TAG) {
                        "Generation successful: requestId=${response.requestId}, latencyMs=${response.latencyMs}"
                    }
                    mutableState.value.copy(
                        isLoading = false,
                        response = response,
                    )
                },
                onFailure = { throwable ->
                    Napier.e(tag = TAG, throwable = throwable) { "Generation failed" }
                    mutableState.value.copy(
                        isLoading = false,
                        errorPopup = ErrorPopupState(
                            title = "Request Failed",
                            message = throwable.message ?: "Unexpected error. Please try again.",
                        )
                    )
                }
            )
        }
    }

    fun close() {
        Napier.d(tag = TAG) { "AppState closing" }
        scope.cancel()
    }

    private companion object {
        const val TAG = "AppState"
    }
}
