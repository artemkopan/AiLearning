package io.artemkopan.ai.sharedui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedcontract.ChatConfigDto
import io.artemkopan.ai.sharedcontract.GenerateRequestDto
import io.artemkopan.ai.sharedui.gateway.PromptGateway
import co.touchlab.kermit.Logger
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

data class ChatId(val value: String)

data class ChatState(
    val id: ChatId,
    val title: String,
    val prompt: String = "",
    val model: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val stopSequences: String = "",
    val agentMode: AgentMode = AgentMode.DEFAULT,
    val isLoading: Boolean = false,
    val response: GenerationResult? = null,
)

data class UiState(
    val chats: Map<ChatId, ChatState> = emptyMap(),
    val chatOrder: List<ChatId> = emptyList(),
    val activeChatId: ChatId? = null,
    val errorPopup: ErrorPopupState? = null,
    val chatConfig: ChatConfigDto? = null,
)

sealed interface UiAction {
    data class PromptChanged(val value: String) : UiAction
    data class ModelChanged(val value: String) : UiAction
    data class MaxOutputTokensChanged(val value: String) : UiAction
    data class TemperatureChanged(val value: String) : UiAction
    data class StopSequencesChanged(val value: String) : UiAction
    data class AgentModeChanged(val value: AgentMode) : UiAction
    data object Submit : UiAction
    data object DismissError : UiAction
    data object CreateChat : UiAction
    data class SelectChat(val chatId: ChatId) : UiAction
    data class CloseChat(val chatId: ChatId) : UiAction
}

class AppViewModel(
    private val gateway: PromptGateway,
) : ViewModel() {

    private val log = Logger.withTag("AppViewModel")
    private val _state = MutableStateFlow(UiState())
    private val generationJobs = mutableMapOf<ChatId, Job>()
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var chatCounter = 0

    init {
        log.d { "AppViewModel initialized" }
        handleCreateChat()
        viewModelScope.launch {
            gateway.getConfig()
                .onSuccess { config ->
                    log.i { "Config loaded: ${config.models.size} models, default=${config.defaultModel}" }
                    _state.update { it.copy(chatConfig = config) }
                }
                .onFailure { throwable ->
                    log.e(throwable) { "Failed to load config" }
                }
        }
    }

    fun onAction(action: UiAction) {
        when (action) {
            is UiAction.PromptChanged -> handlePromptChanged(action.value)
            is UiAction.ModelChanged -> handleModelChanged(action.value)
            is UiAction.MaxOutputTokensChanged -> handleMaxOutputTokensChanged(action.value)
            is UiAction.TemperatureChanged -> handleTemperatureChanged(action.value)
            is UiAction.StopSequencesChanged -> handleStopSequencesChanged(action.value)
            is UiAction.AgentModeChanged -> handleAgentModeChanged(action.value)
            is UiAction.Submit -> handleSubmit()
            is UiAction.DismissError -> handleDismissError()
            is UiAction.CreateChat -> handleCreateChat()
            is UiAction.SelectChat -> handleSelectChat(action.chatId)
            is UiAction.CloseChat -> handleCloseChat(action.chatId)
        }
    }

    private fun updateActiveChat(block: ChatState.() -> ChatState) {
        _state.update { state ->
            val activeId = state.activeChatId ?: return@update state
            val chat = state.chats[activeId] ?: return@update state
            state.copy(chats = state.chats + (activeId to chat.block()))
        }
    }

    private fun handlePromptChanged(value: String) {
        updateActiveChat {
            val firstLine = value.lineSequence().firstOrNull()?.trim().orEmpty()
            val newTitle = if (firstLine.isNotEmpty()) {
                firstLine.take(MAX_TITLE_LENGTH)
            } else {
                "Chat ${id.value.substringAfter("chat-")}"
            }
            copy(prompt = value, title = newTitle)
        }
    }

    private fun handleModelChanged(value: String) {
        updateActiveChat { copy(model = value) }
    }

    private fun handleMaxOutputTokensChanged(value: String) {
        updateActiveChat { copy(maxOutputTokens = value.filter { ch -> ch.isDigit() }) }
    }

    private fun handleTemperatureChanged(value: String) {
        val filtered = buildString {
            var hasDot = false
            for (ch in value) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !hasDot -> { append(ch); hasDot = true }
                }
            }
        }
        updateActiveChat { copy(temperature = filtered) }
    }

    private fun handleStopSequencesChanged(value: String) {
        updateActiveChat { copy(stopSequences = value) }
    }

    private fun handleAgentModeChanged(value: AgentMode) {
        updateActiveChat { copy(agentMode = value) }
    }

    private fun handleDismissError() {
        log.d { "Error dismissed" }
        _state.update { it.copy(errorPopup = null) }
    }

    private fun handleSubmit() {
        val currentState = _state.value
        val activeId = currentState.activeChatId ?: return
        val chat = currentState.chats[activeId] ?: return
        val rawPrompt = chat.prompt.trim()
        val currentPrompt = resolveReferences(rawPrompt, currentState.chats)

        if (currentPrompt.isBlank()) {
            log.w { "Submit blocked: prompt is blank" }
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

        // Cancel previous request for this chat if still running
        generationJobs[activeId]?.let { job ->
            if (job.isActive) {
                log.d { "Cancelling previous generation for chat ${activeId.value}" }
                job.cancel()
            }
        }

        log.i { "Submitting prompt for chat ${activeId.value}: length=${currentPrompt.length}" }

        _state.update { state ->
            val updatedChat = state.chats[activeId]?.copy(isLoading = true) ?: return@update state
            state.copy(
                chats = state.chats + (activeId to updatedChat),
                errorPopup = null,
            )
        }

        val maxTokens = chat.maxOutputTokens.toIntOrNull()
        val temperature = chat.temperature.toDoubleOrNull()
        val stopSeqs = chat.stopSequences
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }

        val capturedId = activeId
        generationJobs[capturedId] = viewModelScope.launch {
            gateway.generate(
                GenerateRequestDto(
                    prompt = currentPrompt,
                    model = chat.model.trim().takeIf { it.isNotEmpty() },
                    maxOutputTokens = maxTokens,
                    temperature = temperature,
                    stopSequences = stopSeqs,
                    agentMode = chat.agentMode.takeIf { it != AgentMode.DEFAULT },
                )
            )
                .onSuccess { dto ->
                    log.i {
                        "Generation successful for chat ${capturedId.value}: requestId=${dto.requestId}"
                    }
                    _state.update { state ->
                        val existing = state.chats[capturedId] ?: return@update state
                        state.copy(
                            chats = state.chats + (capturedId to existing.copy(
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
                            )),
                        )
                    }
                }
                .onFailure { throwable ->
                    log.e(throwable) {
                        "Generation failed for chat ${capturedId.value}"
                    }
                    _state.update { state ->
                        val existing = state.chats[capturedId] ?: return@update state
                        state.copy(
                            chats = state.chats + (capturedId to existing.copy(isLoading = false)),
                            errorPopup = ErrorPopupState(
                                title = "Request Failed",
                                message = throwable.message ?: "Unexpected error. Please try again.",
                            ),
                        )
                    }
                }
        }
    }

    private fun resolveReferences(prompt: String, chats: Map<ChatId, ChatState>): String {
        val resolved = StringBuilder(prompt.length)
        var cursor = 0

        while (cursor < prompt.length) {
            val tokenStart = prompt.indexOf("[#", cursor)
            if (tokenStart < 0) {
                resolved.append(prompt, cursor, prompt.length)
                break
            }

            resolved.append(prompt, cursor, tokenStart)

            val tokenEnd = prompt.indexOf(']', tokenStart + 2)
            if (tokenEnd < 0) {
                resolved.append(prompt, tokenStart, prompt.length)
                break
            }

            val token = prompt.substring(tokenStart, tokenEnd + 1)
            val content = prompt.substring(tokenStart + 2, tokenEnd)
            val separator = content.indexOf(' ')
            if (separator <= 0 || separator >= content.lastIndex) {
                resolved.append(token)
                cursor = tokenEnd + 1
                continue
            }

            val chatNumber = content.substring(0, separator)
            val referenceType = content.substring(separator + 1)
            if (!chatNumber.all { it.isDigit() } || (referenceType != "prompt" && referenceType != "output")) {
                resolved.append(token)
                cursor = tokenEnd + 1
                continue
            }

            val chat = chats[ChatId("chat-$chatNumber")]
            val replacement = when (referenceType) {
                "prompt" -> chat?.prompt?.takeIf { it.isNotBlank() } ?: token
                "output" -> chat?.response?.text?.takeIf { it.isNotBlank() } ?: token
                else -> token
            }
            resolved.append(replacement)
            cursor = tokenEnd + 1
        }

        return resolved.toString()
    }

    private fun handleCreateChat() {
        val currentState = _state.value
        if (currentState.chatOrder.size >= MAX_CHATS) {
            log.w { "Cannot create chat: max $MAX_CHATS reached" }
            return
        }

        chatCounter++
        val newId = ChatId("chat-$chatCounter")
        val newChat = ChatState(id = newId, title = "Chat $chatCounter")

        _state.update { state ->
            state.copy(
                chats = state.chats + (newId to newChat),
                chatOrder = state.chatOrder + newId,
                activeChatId = newId,
            )
        }
        log.d { "Created chat ${newId.value}" }
    }

    private fun handleSelectChat(chatId: ChatId) {
        _state.update { it.copy(activeChatId = chatId) }
    }

    private fun handleCloseChat(chatId: ChatId) {
        // Cancel any running generation for this chat
        generationJobs.remove(chatId)?.cancel()

        _state.update { state ->
            val newOrder = state.chatOrder - chatId
            val newChats = state.chats - chatId

            // Select a neighbor if closing the active chat
            val newActiveId = if (state.activeChatId == chatId) {
                val oldIndex = state.chatOrder.indexOf(chatId)
                when {
                    newOrder.isEmpty() -> null
                    oldIndex >= newOrder.size -> newOrder.last()
                    else -> newOrder[oldIndex]
                }
            } else {
                state.activeChatId
            }

            state.copy(
                chats = newChats,
                chatOrder = newOrder,
                activeChatId = newActiveId,
            )
        }
        log.d { "Closed chat ${chatId.value}" }
    }

    override fun onCleared() {
        super.onCleared()
        generationJobs.values.forEach { it.cancel() }
        generationJobs.clear()
        log.d { "AppViewModel cleared" }
    }

}

private const val MAX_CHATS = 5
private const val MAX_TITLE_LENGTH = 20
