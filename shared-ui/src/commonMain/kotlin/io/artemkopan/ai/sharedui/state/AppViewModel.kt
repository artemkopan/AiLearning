package io.artemkopan.ai.sharedui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.artemkopan.ai.sharedcontract.*
import io.artemkopan.ai.sharedui.gateway.AgentGateway
import io.artemkopan.ai.sharedui.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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

enum class QueuedMessageStatus {
    QUEUED,
    SENDING,
}

data class QueuedDraftSnapshot(
    val model: String,
    val maxOutputTokens: String,
    val temperature: String,
    val stopSequences: String,
    val agentMode: AgentMode,
    val contextConfig: AgentContextConfigDto,
)

data class QueuedMessageState(
    val id: String,
    val text: String,
    val status: QueuedMessageStatus,
    val createdAt: Long,
    val draftSnapshot: QueuedDraftSnapshot,
)

data class AgentState(
    val id: AgentId,
    val title: String,
    val model: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val stopSequences: String = "",
    val agentMode: AgentMode = AgentMode.DEFAULT,
    val status: String = STATUS_DONE,
    val contextConfig: AgentContextConfigDto = RollingSummaryContextConfigDto(),
    val contextSummary: String = "",
    val summarizedUntilCreatedAt: Long = 0,
    val contextSummaryUpdatedAt: Long = 0,
    val messages: List<AgentMessageState> = emptyList(),
    val branches: List<BranchDto> = emptyList(),
    val activeBranchId: String? = null,
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
    val contextTotalTokensByAgent: Map<AgentId, String> = emptyMap(),
    val contextLeftByAgent: Map<AgentId, String> = emptyMap(),
    val queuedByAgent: Map<AgentId, List<QueuedMessageState>> = emptyMap(),
    val isConnected: Boolean = false,
)

sealed interface UiAction {
    data class MessageInputChanged(val value: String) : UiAction
    data class ModelChanged(val value: String) : UiAction
    data class MaxOutputTokensChanged(val value: String) : UiAction
    data class TemperatureChanged(val value: String) : UiAction
    data class StopSequencesChanged(val value: String) : UiAction
    data class AgentModeChanged(val value: AgentMode) : UiAction
    data class ContextStrategyChanged(val value: String) : UiAction
    data class ContextRecentMessagesChanged(val value: String) : UiAction
    data class ContextSummarizeEveryChanged(val value: String) : UiAction
    data class ContextWindowSizeChanged(val value: String) : UiAction
    data class CreateBranch(val checkpointMessageId: String, val name: String) : UiAction
    data class SwitchBranch(val branchId: String) : UiAction
    data class DeleteBranch(val branchId: String) : UiAction
    data object Submit : UiAction
    data object StopQueue : UiAction
    data object DismissError : UiAction
    data object CreateAgent : UiAction
    data class SelectAgent(val agentId: AgentId) : UiAction
    data class CloseAgent(val agentId: AgentId) : UiAction
}

class AppViewModel(
    private val gateway: AgentGateway,
    private val normalizeModelUseCase: NormalizeModelUseCase,
    private val filterTemperatureInputUseCase: FilterTemperatureInputUseCase,
    private val normalizeAgentsForConfigUseCase: NormalizeAgentsForConfigUseCase,
    private val mapSnapshotToUiStateUseCase: MapSnapshotToUiStateUseCase,
    private val observeActiveModelSelectionUseCase: ObserveActiveModelSelectionUseCase,
    private val buildUpdatedConfigWithModelMetadataUseCase: BuildUpdatedConfigWithModelMetadataUseCase,
    private val enrichRuntimeStateUseCase: EnrichRuntimeStateUseCase,
) : ViewModel() {

    private val log = Logger.withTag("AppViewModel")
    private val _state = MutableStateFlow(UiState())
    private val drainJobsByAgent = mutableMapOf<AgentId, Job>()
    private var queuedMessageCounter: Long = 0
    private var queuedMessageCreatedAtCounter: Long = 0
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        log.d { "AppViewModel initialized" }
        observeGatewayEvents()
        observeActiveModelMetadata()
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
            is UiAction.ContextStrategyChanged -> handleContextStrategyChanged(action.value)
            is UiAction.ContextRecentMessagesChanged -> handleContextRecentMessagesChanged(action.value)
            is UiAction.ContextSummarizeEveryChanged -> handleContextSummarizeEveryChanged(action.value)
            is UiAction.ContextWindowSizeChanged -> handleContextWindowSizeChanged(action.value)
            is UiAction.CreateBranch -> handleCreateBranch(action.checkpointMessageId, action.name)
            is UiAction.SwitchBranch -> handleSwitchBranch(action.branchId)
            is UiAction.DeleteBranch -> handleDeleteBranch(action.branchId)
            is UiAction.Submit -> handleSubmit()
            is UiAction.StopQueue -> handleStopQueue()
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
                    updateState { it.copy(isConnected = true) }
                    sendCommand { SubscribeAgentsDto() }
                    triggerDrainForQueuedAgents()
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
                    val updates = mutableListOf<AgentState>()
                    updateState { current ->
                        val normalized = normalizeAgentsForConfigUseCase(current.agents, config)
                        updates += normalized.draftUpdates
                        current.copy(
                            agents = normalized.agents,
                            agentConfig = config,
                        )
                    }
                    updates.forEach(::sendDraftUpdate)
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
        val normalized = normalizeModelUseCase(value, _state.value.agentConfig)
        updateActiveAgent { it.copy(model = normalized) }
        sendActiveDraftUpdate()
    }

    private fun handleMaxOutputTokensChanged(value: String) {
        updateActiveAgent { it.copy(maxOutputTokens = value.filter { ch -> ch.isDigit() }) }
        sendActiveDraftUpdate()
    }

    private fun handleTemperatureChanged(value: String) {
        val filtered = filterTemperatureInputUseCase(value)
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

    private fun handleContextStrategyChanged(value: String) {
        updateActiveAgent { current ->
            if (current.contextConfig.locked) return@updateActiveAgent current
            when (value) {
                "full_history" -> current.copy(
                    contextConfig = FullHistoryContextConfigDto(locked = false)
                )
                "sliding_window" -> current.copy(
                    contextConfig = SlidingWindowContextConfigDto(
                        windowSize = (current.contextConfig as? SlidingWindowContextConfigDto)?.windowSize ?: DEFAULT_SLIDING_WINDOW_SIZE,
                        locked = false,
                    )
                )
                "sticky_facts" -> current.copy(
                    contextConfig = StickyFactsContextConfigDto(
                        recentMessagesN = (current.contextConfig as? StickyFactsContextConfigDto)?.recentMessagesN ?: 12,
                        locked = false,
                    )
                )
                "branching" -> current.copy(
                    contextConfig = BranchingContextConfigDto(
                        recentMessagesN = (current.contextConfig as? BranchingContextConfigDto)?.recentMessagesN ?: 12,
                        locked = false,
                    )
                )
                else -> current.copy(
                    contextConfig = RollingSummaryContextConfigDto(
                        recentMessagesN = (current.contextConfig as? RollingSummaryContextConfigDto)?.recentMessagesN ?: 12,
                        summarizeEveryK = (current.contextConfig as? RollingSummaryContextConfigDto)?.summarizeEveryK ?: 10,
                        locked = false,
                    )
                )
            }
        }
        sendActiveDraftUpdate()
    }

    private fun handleContextRecentMessagesChanged(value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        updateActiveAgent { current ->
            if (current.contextConfig.locked) return@updateActiveAgent current
            val updated = when (val config = current.contextConfig) {
                is RollingSummaryContextConfigDto -> config.copy(recentMessagesN = parsed)
                is StickyFactsContextConfigDto -> config.copy(recentMessagesN = parsed)
                is BranchingContextConfigDto -> config.copy(recentMessagesN = parsed)
                else -> return@updateActiveAgent current
            }
            current.copy(contextConfig = updated)
        }
        sendActiveDraftUpdate()
    }

    private fun handleContextSummarizeEveryChanged(value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        updateActiveAgent { current ->
            val config = current.contextConfig as? RollingSummaryContextConfigDto ?: return@updateActiveAgent current
            if (config.locked) return@updateActiveAgent current
            current.copy(
                contextConfig = config.copy(summarizeEveryK = parsed)
            )
        }
        sendActiveDraftUpdate()
    }

    private fun handleContextWindowSizeChanged(value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        updateActiveAgent { current ->
            val config = current.contextConfig as? SlidingWindowContextConfigDto ?: return@updateActiveAgent current
            if (config.locked) return@updateActiveAgent current
            current.copy(
                contextConfig = config.copy(windowSize = parsed)
            )
        }
        sendActiveDraftUpdate()
    }

    private fun handleCreateBranch(checkpointMessageId: String, name: String) {
        val active = getActiveAgent() ?: return
        sendCommand {
            CreateBranchCommandDto(
                agentId = active.id.value,
                checkpointMessageId = checkpointMessageId,
                branchName = name,
            )
        }
    }

    private fun handleSwitchBranch(branchId: String) {
        val active = getActiveAgent() ?: return
        sendCommand {
            SwitchBranchCommandDto(
                agentId = active.id.value,
                branchId = branchId,
            )
        }
    }

    private fun handleDeleteBranch(branchId: String) {
        val active = getActiveAgent() ?: return
        sendCommand {
            DeleteBranchCommandDto(
                agentId = active.id.value,
                branchId = branchId,
            )
        }
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

        val queuedMessage = QueuedMessageState(
            id = nextQueuedMessageId(),
            text = text,
            status = QueuedMessageStatus.QUEUED,
            createdAt = nextQueuedMessageCreatedAt(),
            draftSnapshot = QueuedDraftSnapshot(
                model = active.model,
                maxOutputTokens = active.maxOutputTokens,
                temperature = active.temperature,
                stopSequences = active.stopSequences,
                agentMode = active.agentMode,
                contextConfig = active.contextConfig,
            ),
        )

        updateState { current ->
            current.copy(
                queuedByAgent = current.queuedByAgent.append(active.id, queuedMessage)
            )
        }
        updateActiveAgent { it.copy(draftMessage = "") }
        triggerQueueDrain(active.id)
    }

    private fun handleStopQueue() {
        val active = getActiveAgent() ?: return
        clearQueuedMessages(active.id)
        drainJobsByAgent.remove(active.id)?.cancel()

        val processingMessage = active.messages.firstOrNull {
            it.role == AgentMessageRoleDto.ASSISTANT &&
                it.status.equals(STATUS_PROCESSING, ignoreCase = true)
        } ?: return

        sendCommand {
            StopAgentMessageCommandDto(
                agentId = active.id.value,
                messageId = processingMessage.id,
            )
        }
    }

    private fun handleDismissError() {
        updateState { it.copy(errorPopup = null) }
    }

    private fun applySnapshot(snapshot: AgentStateSnapshotDto) {
        val mapped = mapSnapshotToUiStateUseCase(
            snapshot = snapshot,
            currentAgents = _state.value.agents,
            config = _state.value.agentConfig,
        )

        updateState {
            val prunedQueue = it.queuedByAgent.filterKeys { id -> mapped.agents.containsKey(id) }
            it.copy(
                agents = mapped.agents,
                agentOrder = mapped.agentOrder,
                activeAgentId = mapped.activeAgentId,
                errorPopup = null,
                queuedByAgent = prunedQueue,
            )
        }

        cancelDrainsForMissingAgents(mapped.agents.keys)
        mapped.draftUpdates.forEach(::sendDraftUpdate)
        triggerDrainForQueuedAgents()
    }

    private fun sendActiveDraftUpdate() {
        val active = getActiveAgent() ?: return
        sendDraftUpdate(active)
    }

    private fun sendDraftUpdate(agent: AgentState) {
        sendCommand {
            UpdateAgentDraftCommandDto(
                agentId = agent.id.value,
                model = agent.model,
                maxOutputTokens = agent.maxOutputTokens,
                temperature = agent.temperature,
                stopSequences = agent.stopSequences,
                agentMode = agent.agentMode,
                contextConfig = agent.contextConfig,
            )
        }
    }

    private fun updateActiveAgent(block: (AgentState) -> AgentState) {
        updateState { state ->
            val activeId = state.activeAgentId ?: return@updateState state
            val active = state.agents[activeId] ?: return@updateState state
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

    private fun triggerQueueDrain(agentId: AgentId) {
        if (_state.value.queuedByAgent[agentId].isNullOrEmpty()) return
        if (drainJobsByAgent[agentId]?.isActive == true) return

        val job = viewModelScope.launch {
            drainQueue(agentId)
        }
        drainJobsByAgent[agentId] = job
        job.invokeOnCompletion {
            drainJobsByAgent.remove(agentId)
        }
    }

    private fun triggerDrainForQueuedAgents() {
        _state.value.queuedByAgent.keys.forEach(::triggerQueueDrain)
    }

    private suspend fun drainQueue(agentId: AgentId) {
        while (currentCoroutineContext().isActive) {
            val queued = _state.value.queuedByAgent[agentId].orEmpty().firstOrNull() ?: return
            waitUntilAgentReady(agentId) ?: return
            updateQueuedStatus(agentId, queued.id, QueuedMessageStatus.SENDING)
            sendQueuedMessage(agentId, queued)
                .onSuccess {
                    removeQueuedMessage(agentId, queued.id)
                    waitForProcessingCycle(agentId)
                }
                .onFailure { throwable ->
                    removeQueuedMessage(agentId, queued.id)
                    showError(
                        title = "Request Failed",
                        message = throwable.message ?: "Failed to send queued message.",
                    )
                }
        }
    }

    private suspend fun waitUntilAgentReady(agentId: AgentId): AgentState? {
        return state
            .map { uiState ->
                val agent = uiState.agents[agentId]
                if (uiState.isConnected && agent != null && !agent.isLoading) {
                    agent
                } else {
                    null
                }
            }
            .distinctUntilChanged()
            .filterNotNull()
            .first()
    }

    private suspend fun waitForProcessingCycle(agentId: AgentId) {
        val enteredProcessing = withTimeoutOrNull(5_000L) {
            state
                .map { uiState ->
                    val agent = uiState.agents[agentId]
                    uiState.isConnected && agent != null && agent.isLoading
                }
                .distinctUntilChanged()
                .first { it }
        } ?: false

        if (enteredProcessing) {
            waitUntilAgentReady(agentId)
        }
    }

    private suspend fun sendQueuedMessage(agentId: AgentId, queued: QueuedMessageState): Result<Unit> {
        val snapshot = queued.draftSnapshot
        val updateDraftResult = gateway.send(
            UpdateAgentDraftCommandDto(
                agentId = agentId.value,
                model = snapshot.model,
                maxOutputTokens = snapshot.maxOutputTokens,
                temperature = snapshot.temperature,
                stopSequences = snapshot.stopSequences,
                agentMode = snapshot.agentMode,
                contextConfig = snapshot.contextConfig,
            )
        )
        if (updateDraftResult.isFailure) return updateDraftResult

        return gateway.send(
            SendAgentMessageCommandDto(
                agentId = agentId.value,
                text = queued.text,
            )
        )
    }

    private fun updateQueuedStatus(agentId: AgentId, queuedId: String, status: QueuedMessageStatus) {
        updateState { current ->
            val queue = current.queuedByAgent[agentId].orEmpty()
            if (queue.isEmpty()) return@updateState current
            val updatedQueue = queue.map { queued ->
                if (queued.id == queuedId) queued.copy(status = status) else queued
            }
            current.copy(
                queuedByAgent = current.queuedByAgent + (agentId to updatedQueue)
            )
        }
    }

    private fun removeQueuedMessage(agentId: AgentId, queuedId: String) {
        updateState { current ->
            val queue = current.queuedByAgent[agentId].orEmpty()
            if (queue.isEmpty()) return@updateState current
            val updatedQueue = queue.filterNot { queued -> queued.id == queuedId }
            val updatedMap = if (updatedQueue.isEmpty()) {
                current.queuedByAgent - agentId
            } else {
                current.queuedByAgent + (agentId to updatedQueue)
            }
            current.copy(queuedByAgent = updatedMap)
        }
    }

    private fun clearQueuedMessages(agentId: AgentId) {
        updateState { current ->
            if (!current.queuedByAgent.containsKey(agentId)) return@updateState current
            current.copy(
                queuedByAgent = current.queuedByAgent - agentId
            )
        }
    }

    private fun cancelDrainsForMissingAgents(existingIds: Set<AgentId>) {
        val missing = drainJobsByAgent.keys.filterNot { existingIds.contains(it) }
        missing.forEach { id ->
            drainJobsByAgent.remove(id)?.cancel()
        }
    }

    private fun nextQueuedMessageId(): String {
        queuedMessageCounter += 1
        return "$LOCAL_QUEUE_MESSAGE_ID_PREFIX$queuedMessageCounter"
    }

    private fun nextQueuedMessageCreatedAt(): Long {
        queuedMessageCreatedAtCounter += 1
        return queuedMessageCreatedAtCounter
    }

    private fun observeActiveModelMetadata() {
        viewModelScope.launch {
            observeActiveModelSelectionUseCase(state)
                .collectLatest { selection ->
                    if (selection == null) return@collectLatest
                    requestModelMetadata(selection.modelId)
                }
        }
    }

    private suspend fun requestModelMetadata(modelId: String) {
        val normalized = modelId.trim()
        if (normalized.isEmpty()) return

        gateway.getModelMetadata(normalized)
            .onSuccess { metadata ->
                updateState { current ->
                    val config = current.agentConfig ?: return@updateState current
                    current.copy(
                        agentConfig = buildUpdatedConfigWithModelMetadataUseCase(config, metadata),
                    )
                }
            }
            .onFailure { throwable ->
                log.w(throwable) { "Failed to refresh model metadata: model=$normalized" }
            }
    }

    private fun showError(title: String, message: String) {
        updateState {
            it.copy(
                errorPopup = ErrorPopupState(
                    title = title,
                    message = message,
                )
            )
        }
    }

    private fun updateState(transform: (UiState) -> UiState) {
        _state.update { current ->
            enrichRuntimeStateUseCase(transform(current))
        }
    }

    override fun onCleared() {
        drainJobsByAgent.values.forEach { it.cancel() }
        drainJobsByAgent.clear()
        gateway.disconnect()
        super.onCleared()
    }
}

private fun Map<AgentId, List<QueuedMessageState>>.append(
    agentId: AgentId,
    queuedMessage: QueuedMessageState,
): Map<AgentId, List<QueuedMessageState>> {
    val updated = get(agentId).orEmpty() + queuedMessage
    return this + (agentId to updated)
}

private const val LOCAL_QUEUE_MESSAGE_ID_PREFIX = "local-queue-"
private const val STATUS_PROCESSING = "processing"
private const val STATUS_DONE = "done"
