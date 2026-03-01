package io.artemkopan.ai.sharedui.core.session

import co.touchlab.kermit.Logger
import io.artemkopan.ai.sharedcontract.*
import io.artemkopan.ai.sharedui.gateway.AgentGateway
import io.artemkopan.ai.sharedui.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentSessionStore(
    private val gateway: AgentGateway,
    private val normalizeModelUseCase: NormalizeModelUseCase,
    private val filterTemperatureInputUseCase: FilterTemperatureInputUseCase,
    private val normalizeAgentsForConfigUseCase: NormalizeAgentsForConfigUseCase,
    private val mapSnapshotToUiStateUseCase: MapSnapshotToUiStateUseCase,
    private val observeActiveModelSelectionUseCase: ObserveActiveModelSelectionUseCase,
    private val buildUpdatedConfigWithModelMetadataUseCase: BuildUpdatedConfigWithModelMetadataUseCase,
    private val enrichRuntimeStateUseCase: EnrichRuntimeStateUseCase,
) {

    private val log = Logger.withTag("AgentSessionStore")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startMutex = Mutex()

    private val _sessionState = MutableStateFlow(SessionState())
    private val drainJobsByAgent = mutableMapOf<AgentId, Job>()
    private var queuedMessageCounter: Long = 0
    private var queuedMessageCreatedAtCounter: Long = 0
    private var started = false

    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    fun start() {
        scope.launch {
            startMutex.withLock {
                if (started) return@withLock
                started = true
                observeGatewayEvents()
                observeActiveModelMetadata()
                connectGateway()
                loadConfig()
            }
        }
    }

    fun observeAgent(agentId: AgentId): Flow<AgentSessionSlice?> {
        return sessionState
            .map { current ->
                val agent = current.agents[agentId] ?: return@map null
                AgentSessionSlice(
                    agent = agent,
                    queuedMessages = current.queuedByAgent[agentId].orEmpty(),
                    contextTotalTokensLabel = current.contextTotalTokensByAgent[agentId] ?: "n/a",
                    contextLeftLabel = current.contextLeftByAgent[agentId] ?: "n/a",
                    agentConfig = current.agentConfig,
                )
            }
            .distinctUntilChanged()
    }

    fun observeActiveAgentId(): Flow<AgentId?> =
        sessionState.map { it.activeAgentId }.distinctUntilChanged()

    fun observeError(): Flow<ErrorDialogModel?> =
        sessionState.map { it.errorDialog }.distinctUntilChanged()

    fun dismissError() {
        updateState { it.copy(errorDialog = null) }
    }

    fun createAgent() {
        sendCommand { CreateAgentCommandDto() }
    }

    fun selectAgent(agentId: AgentId) {
        sendCommand { SelectAgentCommandDto(agentId = agentId.value) }
    }

    fun closeAgent(agentId: AgentId) {
        clearQueuedMessages(agentId)
        drainJobsByAgent.remove(agentId)?.cancel()
        sendCommand { CloseAgentCommandDto(agentId = agentId.value) }
    }

    fun selectNextAgent() {
        val current = _sessionState.value
        nextAgentId(current.agentOrder, current.activeAgentId)?.let(::selectAgent)
    }

    fun selectPreviousAgent() {
        val current = _sessionState.value
        previousAgentId(current.agentOrder, current.activeAgentId)?.let(::selectAgent)
    }

    fun submitFromActiveAgent() {
        _sessionState.value.activeAgentId?.let(::submitMessage)
    }

    fun updateDraftMessage(agentId: AgentId, value: String) {
        updateAgent(agentId) { current ->
            current.copy(draftMessage = value)
        }
    }

    fun updateModel(agentId: AgentId, value: String) {
        val normalized = normalizeModelUseCase(value, _sessionState.value.agentConfig)
        updateAgent(agentId) { current -> current.copy(model = normalized) }
        sendDraftUpdate(agentId)
    }

    fun updateMaxOutputTokens(agentId: AgentId, value: String) {
        updateAgent(agentId) { current -> current.copy(maxOutputTokens = value.filter { it.isDigit() }) }
        sendDraftUpdate(agentId)
    }

    fun updateTemperature(agentId: AgentId, value: String) {
        val filtered = filterTemperatureInputUseCase(value)
        updateAgent(agentId) { current -> current.copy(temperature = filtered) }
        sendDraftUpdate(agentId)
    }

    fun updateStopSequences(agentId: AgentId, value: String) {
        updateAgent(agentId) { current -> current.copy(stopSequences = value) }
        sendDraftUpdate(agentId)
    }

    fun updateAgentMode(agentId: AgentId, value: AgentMode) {
        updateAgent(agentId) { current -> current.copy(agentMode = value) }
        sendDraftUpdate(agentId)
    }

    fun updateContextStrategy(agentId: AgentId, strategy: String) {
        updateAgent(agentId) { current ->
            if (current.contextConfig.locked) return@updateAgent current
            when (strategy) {
                STRATEGY_FULL_HISTORY -> current.copy(
                    contextConfig = FullHistoryContextConfigDto(locked = false)
                )

                STRATEGY_SLIDING_WINDOW -> current.copy(
                    contextConfig = SlidingWindowContextConfigDto(
                        windowSize = (current.contextConfig as? SlidingWindowContextConfigDto)?.windowSize
                            ?: DEFAULT_SLIDING_WINDOW_SIZE,
                        locked = false,
                    )
                )

                STRATEGY_STICKY_FACTS -> current.copy(
                    contextConfig = StickyFactsContextConfigDto(
                        recentMessagesN = (current.contextConfig as? StickyFactsContextConfigDto)?.recentMessagesN ?: 12,
                        locked = false,
                    )
                )

                STRATEGY_BRANCHING -> current.copy(
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
        sendDraftUpdate(agentId)
    }

    fun updateContextRecentMessages(agentId: AgentId, value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        updateAgent(agentId) { current ->
            if (current.contextConfig.locked) return@updateAgent current
            val updated = when (val config = current.contextConfig) {
                is RollingSummaryContextConfigDto -> config.copy(recentMessagesN = parsed)
                is StickyFactsContextConfigDto -> config.copy(recentMessagesN = parsed)
                is BranchingContextConfigDto -> config.copy(recentMessagesN = parsed)
                else -> return@updateAgent current
            }
            current.copy(contextConfig = updated)
        }
        sendDraftUpdate(agentId)
    }

    fun updateContextSummarizeEvery(agentId: AgentId, value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        updateAgent(agentId) { current ->
            val config = current.contextConfig as? RollingSummaryContextConfigDto ?: return@updateAgent current
            if (config.locked) return@updateAgent current
            current.copy(contextConfig = config.copy(summarizeEveryK = parsed))
        }
        sendDraftUpdate(agentId)
    }

    fun updateContextWindowSize(agentId: AgentId, value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        updateAgent(agentId) { current ->
            val config = current.contextConfig as? SlidingWindowContextConfigDto ?: return@updateAgent current
            if (config.locked) return@updateAgent current
            current.copy(contextConfig = config.copy(windowSize = parsed))
        }
        sendDraftUpdate(agentId)
    }

    fun createBranch(agentId: AgentId, checkpointMessageId: String, name: String) {
        sendCommand {
            CreateBranchCommandDto(
                agentId = agentId.value,
                checkpointMessageId = checkpointMessageId,
                branchName = name,
            )
        }
    }

    fun switchBranch(agentId: AgentId, branchId: String) {
        sendCommand {
            SwitchBranchCommandDto(
                agentId = agentId.value,
                branchId = branchId,
            )
        }
    }

    fun deleteBranch(agentId: AgentId, branchId: String) {
        sendCommand {
            DeleteBranchCommandDto(
                agentId = agentId.value,
                branchId = branchId,
            )
        }
    }

    fun submitMessage(agentId: AgentId) {
        val agent = _sessionState.value.agents[agentId] ?: return
        val text = agent.draftMessage.trim()
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
                model = agent.model,
                maxOutputTokens = agent.maxOutputTokens,
                temperature = agent.temperature,
                stopSequences = agent.stopSequences,
                agentMode = agent.agentMode,
                contextConfig = agent.contextConfig,
            ),
        )

        updateState { current ->
            current.copy(queuedByAgent = current.queuedByAgent.append(agentId, queuedMessage))
        }
        updateAgent(agentId) { it.copy(draftMessage = "") }
        triggerQueueDrain(agentId)
    }

    fun stopQueue(agentId: AgentId) {
        clearQueuedMessages(agentId)
        drainJobsByAgent.remove(agentId)?.cancel()

        val processingMessage = _sessionState.value
            .agents[agentId]
            ?.messages
            ?.firstOrNull {
                it.role == AgentMessageRoleDto.ASSISTANT &&
                    it.status.equals(STATUS_PROCESSING, ignoreCase = true)
            } ?: return

        sendCommand {
            StopAgentMessageCommandDto(
                agentId = agentId.value,
                messageId = processingMessage.id,
            )
        }
    }

    fun dispose() {
        drainJobsByAgent.values.forEach { it.cancel() }
        drainJobsByAgent.clear()
        gateway.disconnect()
        scope.cancel()
    }

    private fun observeGatewayEvents() {
        scope.launch {
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
        scope.launch {
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
        scope.launch {
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
                    updates.forEach { sendDraftUpdate(it.id) }
                }
                .onFailure { throwable ->
                    log.e(throwable) { "Failed to load config" }
                }
        }
    }

    private fun applySnapshot(snapshot: io.artemkopan.ai.sharedcontract.AgentStateSnapshotDto) {
        val mapped = mapSnapshotToUiStateUseCase(
            snapshot = snapshot,
            currentAgents = _sessionState.value.agents,
            config = _sessionState.value.agentConfig,
        )

        updateState {
            val prunedQueue = it.queuedByAgent.filterKeys { id -> mapped.agents.containsKey(id) }
            it.copy(
                agents = mapped.agents,
                agentOrder = mapped.agentOrder,
                activeAgentId = mapped.activeAgentId,
                errorDialog = null,
                queuedByAgent = prunedQueue,
            )
        }

        cancelDrainsForMissingAgents(mapped.agents.keys)
        mapped.draftUpdates.forEach { sendDraftUpdate(it.id) }
        triggerDrainForQueuedAgents()
    }

    private fun sendDraftUpdate(agentId: AgentId) {
        val agent = _sessionState.value.agents[agentId] ?: return
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

    private fun observeActiveModelMetadata() {
        scope.launch {
            observeActiveModelSelectionUseCase(sessionState)
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

    private fun sendCommand(message: () -> AgentWsClientMessageDto) {
        scope.launch {
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
        if (_sessionState.value.queuedByAgent[agentId].isNullOrEmpty()) return
        if (drainJobsByAgent[agentId]?.isActive == true) return

        val job = scope.launch {
            drainQueue(agentId)
        }
        drainJobsByAgent[agentId] = job
        job.invokeOnCompletion {
            drainJobsByAgent.remove(agentId)
        }
    }

    private fun triggerDrainForQueuedAgents() {
        _sessionState.value.queuedByAgent.keys.forEach(::triggerQueueDrain)
    }

    private suspend fun drainQueue(agentId: AgentId) {
        while (currentCoroutineContext().isActive) {
            val queued = _sessionState.value.queuedByAgent[agentId].orEmpty().firstOrNull() ?: return
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
        return sessionState
            .map { current ->
                val agent = current.agents[agentId]
                if (current.isConnected && agent != null && !agent.isLoading) {
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
            sessionState
                .map { current ->
                    val agent = current.agents[agentId]
                    current.isConnected && agent != null && agent.isLoading
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
            current.copy(queuedByAgent = current.queuedByAgent + (agentId to updatedQueue))
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
            current.copy(queuedByAgent = current.queuedByAgent - agentId)
        }
    }

    private fun cancelDrainsForMissingAgents(existingIds: Set<AgentId>) {
        val missing = drainJobsByAgent.keys.filterNot { existingIds.contains(it) }
        missing.forEach { id ->
            drainJobsByAgent.remove(id)?.cancel()
        }
    }

    private fun updateAgent(agentId: AgentId, block: (AgentState) -> AgentState) {
        updateState { current ->
            val agent = current.agents[agentId] ?: return@updateState current
            current.copy(agents = current.agents + (agentId to block(agent)))
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

    private fun showError(title: String, message: String) {
        updateState {
            it.copy(errorDialog = ErrorDialogModel(title = title, message = message))
        }
    }

    private fun updateState(transform: (SessionState) -> SessionState) {
        _sessionState.update { current ->
            enrichRuntimeStateUseCase(transform(current))
        }
    }
}

private fun Map<AgentId, List<QueuedMessageState>>.append(
    agentId: AgentId,
    queuedMessage: QueuedMessageState,
): Map<AgentId, List<QueuedMessageState>> {
    val updated = get(agentId).orEmpty() + queuedMessage
    return this + (agentId to updated)
}

private fun nextAgentId(order: List<AgentId>, activeAgentId: AgentId?): AgentId? {
    if (order.isEmpty()) return null
    val currentIndex = order.indexOf(activeAgentId).takeIf { it >= 0 } ?: 0
    return order[(currentIndex + 1) % order.size]
}

private fun previousAgentId(order: List<AgentId>, activeAgentId: AgentId?): AgentId? {
    if (order.isEmpty()) return null
    val currentIndex = order.indexOf(activeAgentId).takeIf { it >= 0 } ?: 0
    return order[(currentIndex - 1 + order.size) % order.size]
}

private const val STRATEGY_FULL_HISTORY = "full_history"
private const val STRATEGY_SLIDING_WINDOW = "sliding_window"
private const val STRATEGY_STICKY_FACTS = "sticky_facts"
private const val STRATEGY_BRANCHING = "branching"
private const val LOCAL_QUEUE_MESSAGE_ID_PREFIX = "local-queue-"
