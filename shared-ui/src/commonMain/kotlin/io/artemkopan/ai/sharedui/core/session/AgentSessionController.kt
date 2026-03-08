package io.artemkopan.ai.sharedui.core.session

import co.touchlab.kermit.Logger
import io.artemkopan.ai.sharedcontract.*
import io.artemkopan.ai.sharedui.gateway.AgentGateway
import io.artemkopan.ai.sharedui.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentSessionController(
    private val gateway: AgentGateway,
    private val mapSnapshotToUiStateUseCase: MapSnapshotToUiStateUseCase,
    private val normalizeAgentsForConfigUseCase: NormalizeAgentsForConfigUseCase,
    private val observeActiveModelSelectionUseCase: ObserveActiveModelSelectionUseCase,
    private val buildUpdatedConfigWithModelMetadataUseCase: BuildUpdatedConfigWithModelMetadataUseCase,
    private val enrichRuntimeStateUseCase: EnrichRuntimeStateUseCase,
) {

    private val log = Logger.withTag("AgentSessionController")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startMutex = Mutex()

    private val _sessionState = MutableStateFlow(SessionState())
    private var started = false

    private val queueManager = MessageQueueManager(
        scope = scope,
        sessionState = _sessionState,
        getState = ::getState,
        updateState = ::updateState,
        sendGateway = { gateway.send(it) },
        showError = ::showError,
    )

    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    fun getState(): SessionState = _sessionState.value

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

    fun updateState(transform: (SessionState) -> SessionState) {
        _sessionState.update { current ->
            enrichRuntimeStateUseCase(transform(current))
        }
    }

    fun updateAgent(agentId: AgentId, block: (AgentState) -> AgentState) {
        updateState { current ->
            val agent = current.agents[agentId] ?: return@updateState current
            current.copy(agents = current.agents + (agentId to block(agent)))
        }
    }

    fun sendCommand(message: () -> AgentWsClientMessageDto) {
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

    fun sendDraftUpdate(agentId: AgentId) {
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

    fun showError(title: String, message: String) {
        updateState {
            it.copy(errorDialog = ErrorDialogModel(title = title, message = message))
        }
    }

    fun enqueueMessage(agentId: AgentId, message: QueuedMessageState) {
        queueManager.enqueue(agentId, message)
    }

    fun clearQueue(agentId: AgentId) {
        queueManager.clearQueue(agentId)
    }

    fun stopQueue(agentId: AgentId) {
        queueManager.stopQueue(agentId)
    }

    fun nextQueuedMessageId(): String = queueManager.nextQueuedMessageId()

    fun nextQueuedMessageCreatedAt(): Long = queueManager.nextQueuedMessageCreatedAt()

    fun dispose() {
        queueManager.dispose()
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
                    is UserProfileSnapshotDto -> applyUserProfile(event)
                    is TaskStateSnapshotDto -> applyTaskStateSnapshot(event)
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
                    queueManager.triggerDrainForQueuedAgents()
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

    private fun applySnapshot(snapshot: AgentStateSnapshotDto) {
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

        queueManager.cancelDrainsForMissingAgents(mapped.agents.keys)
        mapped.draftUpdates.forEach { sendDraftUpdate(it.id) }
        queueManager.triggerDrainForQueuedAgents()
    }

    private fun applyTaskStateSnapshot(snapshot: TaskStateSnapshotDto) {
        val agentId = AgentId(snapshot.agentId)
        updateState { current ->
            val task = snapshot.task
            if (task == null) {
                current.copy(taskByAgent = current.taskByAgent - agentId)
            } else {
                val taskState = TaskState(
                    id = task.id,
                    title = task.title,
                    currentPhase = task.currentPhase,
                    steps = task.steps.map { step ->
                        TaskStepState(
                            index = step.index,
                            phase = step.phase,
                            description = step.description,
                            expectedAction = step.expectedAction,
                            status = step.status,
                            result = step.result,
                        )
                    },
                    currentStepIndex = task.currentStepIndex,
                    planSteps = task.planSteps,
                    questionForUser = task.questionForUser,
                    validationChecks = task.validationChecks.map { check ->
                        TaskValidationCheckState(name = check.name, passed = check.passed)
                    },
                )
                current.copy(taskByAgent = current.taskByAgent + (agentId to taskState))
            }
        }
    }

    private fun applyUserProfile(snapshot: UserProfileSnapshotDto) {
        updateState { current ->
            current.copy(
                userProfile = UserProfileState(
                    communicationStyle = snapshot.communicationStyle,
                    responseFormat = snapshot.responseFormat,
                    restrictions = snapshot.restrictions,
                    customInstructions = snapshot.customInstructions,
                ),
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
}
