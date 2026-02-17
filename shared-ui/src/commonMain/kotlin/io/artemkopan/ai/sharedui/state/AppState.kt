package io.artemkopan.ai.sharedui.state

import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedcontract.GenerateResponseDto
import io.artemkopan.ai.sharedui.gateway.PromptGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    fun onPromptChanged(value: String) {
        mutableState.value = mutableState.value.copy(prompt = value)
    }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(errorPopup = null)
    }

    fun submit() {
        println("[AiAssistant][UI] Send clicked")
        val currentPrompt = mutableState.value.prompt.trim()
        if (currentPrompt.isBlank()) {
            println("[AiAssistant][UI] Validation failed: blank prompt")
            mutableState.value = mutableState.value.copy(
                errorPopup = ErrorPopupState(
                    title = "Validation Error",
                    message = "Prompt must not be blank.",
                )
            )
            return
        }

        mutableState.value = mutableState.value.copy(isLoading = true, errorPopup = null)
        println("[AiAssistant][UI] Sending /api/v1/generate request")

        scope.launch {
            val result = gateway.generate(GenerateRequestDto(prompt = currentPrompt))
            mutableState.value = result.fold(
                onSuccess = { response ->
                    println("[AiAssistant][UI] Request success")
                    mutableState.value.copy(
                        isLoading = false,
                        response = response,
                    )
                },
                onFailure = { throwable ->
                    println("[AiAssistant][UI] Request failed: ${throwable.message ?: "unknown"}")
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
}
