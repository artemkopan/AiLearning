package io.artemkopan.ai.sharedui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedcontract.AgentOperationFailedDto
import io.artemkopan.ai.sharedcontract.AgentStateSnapshotDto
import io.artemkopan.ai.sharedcontract.AgentStateSnapshotMessageDto
import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.artemkopan.ai.sharedcontract.AgentWsServerMessageDto
import io.artemkopan.ai.sharedcontract.CloseAgentCommandDto
import io.artemkopan.ai.sharedcontract.CreateAgentCommandDto
import io.artemkopan.ai.sharedcontract.SelectAgentCommandDto
import io.artemkopan.ai.sharedcontract.SendAgentMessageCommandDto
import io.artemkopan.ai.sharedcontract.StopAgentMessageCommandDto
import io.artemkopan.ai.sharedcontract.SubscribeAgentsDto
import io.artemkopan.ai.sharedcontract.UpdateAgentDraftCommandDto
import io.artemkopan.ai.sharedui.gateway.AgentGateway
import co.touchlab.kermit.Logger
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
    val latencyMs: Long = 0,
)

data class AgentMessageState(
    val id: String,
    val role: AgentMessageRoleDto,
    val text: String,
    val status: String,
    val createdAt: Long,
    val provider: String? = null,
    val model: String? = null,
    val usage: UsageResult? = null,
    val latencyMs: Long? = null,
)

data class ErrorPopupState(
    val title: String,
    val message: String,
)

data class AgentId(val value: String)

data class AgentState(
    val id: AgentId,
    val title: String,
    val model: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val stopSequences: String = "",
    val agentMode: AgentMode = AgentMode.DEFAULT,
    val status: String = STATUS_DONE,
    val messages: List<AgentMessageState> = emptyList(),
    val draftMessage: String = "",
) {
    val isLoading: Boolean
        get() = messages.any { it.status.equals(STATUS_PROCESSING, ignoreCase = true) }
}

data class UiState(
    val agents: Map<AgentId, AgentState> = emptyMap(),
    val agentOrder: List<AgentId> = emptyList(),
    val activeAgentId: AgentId? = null,
    val errorPopup: ErrorPopupState? = null,
    val agentConfig: AgentConfigDto? = null,
    val isConnected: Boolean = false,
)

sealed interface UiAction {
    data class MessageInputChanged(val value: String) : UiAction
    data class ModelChanged(val value: String) : UiAction
    data class MaxOutputTokensChanged(val value: String) : UiAction
    data class TemperatureChanged(val value: String) : UiAction
    data class StopSequencesChanged(val value: String) : UiAction
    data class AgentModeChanged(val value: AgentMode) : UiAction
    data object Submit : UiAction
    data class StopMessage(val messageId: String) : UiAction
    data object DismissError : UiAction
    data object CreateAgent : UiAction
    data class SelectAgent(val agentId: AgentId) : UiAction
    data class CloseAgent(val agentId: AgentId) : UiAction
}

class AppViewModel(
    private val gateway: AgentGateway,
) : ViewModel() {

    private val log = Logger.withTag("AppViewModel")
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        log.d { "AppViewModel initialized" }
        observeGatewayEvents()
        connectGateway()
        loadConfig()
    }

    fun onAction(action: UiAction) {
        when (action) {
            is UiAction.MessageInputChanged -> handleMessageInputChanged(action.value)
            is UiAction.ModelChanged -> handleModelChanged(action.value)
            is UiAction.MaxOutputTokensChanged -> handleMaxOutputTokensChanged(action.value)
            is UiAction.TemperatureChanged -> handleTemperatureChanged(action.value)
            is UiAction.StopSequencesChanged -> handleStopSequencesChanged(action.value)
            is UiAction.AgentModeChanged -> handleAgentModeChanged(action.value)
            is UiAction.Submit -> handleSubmit()
            is UiAction.StopMessage -> handleStopMessage(action.messageId)
            is UiAction.DismissError -> handleDismissError()
            is UiAction.CreateAgent -> sendCommand { CreateAgentCommandDto() }
            is UiAction.SelectAgent -> sendCommand { SelectAgentCommandDto(agentId = action.agentId.value) }
            is UiAction.CloseAgent -> sendCommand { CloseAgentCommandDto(agentId = action.agentId.value) }
        }
    }

    private fun observeGatewayEvents() {
        viewModelScope.launch {
            gateway.events.collect { event ->
                when (event) {
                    is AgentStateSnapshotMessageDto -> applySnapshot(event.state)
                    is AgentOperationFailedDto -> showError(
                        title = "Request Failed",
                        message = event.message,
                    )
                }
            }
        }
    }

    private fun connectGateway() {
        viewModelScope.launch {
            gateway.connect()
                .onSuccess {
                    _state.update { it.copy(isConnected = true) }
                    sendCommand { SubscribeAgentsDto() }
                }
                .onFailure { throwable ->
                    log.e(throwable) { "Failed to connect WebSocket" }
                    showError(
                        title = "Connection Failed",
                        message = throwable.message ?: "Failed to connect to backend.",
                    )
                }
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            gateway.getConfig()
                .onSuccess { config ->
                    _state.update { it.copy(agentConfig = config) }
                }
                .onFailure { throwable ->
                    log.e(throwable) { "Failed to load config" }
                }
        }
    }

    private fun handleMessageInputChanged(value: String) {
        updateActiveAgent { current ->
            current.copy(draftMessage = value)
        }
    }

    private fun handleModelChanged(value: String) {
        updateActiveAgent { it.copy(model = value) }
        sendActiveDraftUpdate()
    }

    private fun handleMaxOutputTokensChanged(value: String) {
        updateActiveAgent { it.copy(maxOutputTokens = value.filter { ch -> ch.isDigit() }) }
        sendActiveDraftUpdate()
    }

    private fun handleTemperatureChanged(value: String) {
        val filtered = buildString {
            var hasDot = false
            for (ch in value) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !hasDot -> {
                        append(ch)
                        hasDot = true
                    }
                }
            }
        }
        updateActiveAgent { it.copy(temperature = filtered) }
        sendActiveDraftUpdate()
    }

    private fun handleStopSequencesChanged(value: String) {
        updateActiveAgent { it.copy(stopSequences = value) }
        sendActiveDraftUpdate()
    }

    private fun handleAgentModeChanged(value: AgentMode) {
        updateActiveAgent { it.copy(agentMode = value) }
        sendActiveDraftUpdate()
    }

    private fun handleSubmit() {
        val active = getActiveAgent() ?: return
        val text = active.draftMessage.trim()
        if (text.isBlank()) {
            showError(
                title = "Validation Error",
                message = "Message must not be blank.",
            )
            return
        }

        sendCommand {
            SendAgentMessageCommandDto(
                agentId = active.id.value,
                text = text,
            )
        }

        updateActiveAgent { it.copy(draftMessage = "") }
    }

    private fun handleStopMessage(messageId: String) {
        val active = getActiveAgent() ?: return
        sendCommand {
            StopAgentMessageCommandDto(
                agentId = active.id.value,
                messageId = messageId,
            )
        }
    }

    private fun handleDismissError() {
        _state.update { it.copy(errorPopup = null) }
    }

    private fun applySnapshot(snapshot: AgentStateSnapshotDto) {
        val currentAgents = _state.value.agents
        val order = snapshot.agents.map { AgentId(it.id) }
        val mapped = snapshot.agents.associate { dto ->
            val id = AgentId(dto.id)
            val preservedDraft = currentAgents[id]?.draftMessage.orEmpty()
            id to AgentState(
                id = id,
                title = dto.title,
                model = dto.model,
                maxOutputTokens = dto.maxOutputTokens,
                temperature = dto.temperature,
                stopSequences = dto.stopSequences,
                agentMode = dto.agentMode,
                status = dto.status,
                messages = dto.messages.map { message ->
                    AgentMessageState(
                        id = message.id,
                        role = message.role,
                        text = message.text,
                        status = message.status,
                        createdAt = message.createdAt,
                        provider = message.provider,
                        model = message.model,
                        usage = message.usage?.let {
                            UsageResult(
                                inputTokens = it.inputTokens,
                                outputTokens = it.outputTokens,
                                totalTokens = it.totalTokens,
                            )
                        },
                        latencyMs = message.latencyMs,
                    )
                },
                draftMessage = preservedDraft,
            )
        }

        _state.update {
            it.copy(
                agents = mapped,
                agentOrder = order,
                activeAgentId = snapshot.activeAgentId?.let(::AgentId),
                errorPopup = null,
            )
        }
    }

    private fun sendActiveDraftUpdate() {
        val active = getActiveAgent() ?: return
        sendCommand {
            UpdateAgentDraftCommandDto(
                agentId = active.id.value,
                model = active.model,
                maxOutputTokens = active.maxOutputTokens,
                temperature = active.temperature,
                stopSequences = active.stopSequences,
                agentMode = active.agentMode,
            )
        }
    }

    private fun updateActiveAgent(block: (AgentState) -> AgentState) {
        _state.update { state ->
            val activeId = state.activeAgentId ?: return@update state
            val active = state.agents[activeId] ?: return@update state
            state.copy(agents = state.agents + (activeId to block(active)))
        }
    }

    private fun getActiveAgent(): AgentState? {
        val current = _state.value
        val activeId = current.activeAgentId ?: return null
        return current.agents[activeId]
    }

    private fun sendCommand(message: () -> AgentWsClientMessageDto) {
        viewModelScope.launch {
            gateway.send(message())
                .onFailure { throwable ->
                    showError(
                        title = "Request Failed",
                        message = throwable.message ?: "Failed to send command.",
                    )
                }
        }
    }

    private fun showError(title: String, message: String) {
        _state.update {
            it.copy(
                errorPopup = ErrorPopupState(
                    title = title,
                    message = message,
                )
            )
        }
    }

    override fun onCleared() {
        gateway.disconnect()
        super.onCleared()
    }
}

private const val STATUS_PROCESSING = "processing"
private const val STATUS_DONE = "done"
