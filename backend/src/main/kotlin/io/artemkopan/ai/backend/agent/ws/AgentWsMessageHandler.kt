package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.application.model.*
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.sharedcontract.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.slf4j.Logger

class AgentWsMessageHandler(
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val createAgentUseCase: CreateAgentUseCase,
    private val selectAgentUseCase: SelectAgentUseCase,
    private val updateAgentDraftUseCase: UpdateAgentDraftUseCase,
    private val closeAgentUseCase: CloseAgentUseCase,
    private val startAgentMessageUseCase: StartAgentMessageUseCase,
    private val completeAgentMessageUseCase: CompleteAgentMessageUseCase,
    private val failAgentMessageUseCase: FailAgentMessageUseCase,
    private val stopAgentMessageUseCase: StopAgentMessageUseCase,
    private val generateTextUseCase: GenerateTextUseCase,
    private val mapFailureToUserMessageUseCase: MapFailureToUserMessageUseCase,
    private val sessionRegistry: AgentWsSessionRegistry,
    private val mapper: AgentWsMapper,
    private val json: Json,
    private val logger: Logger,
) {

    private val wsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val processingJobs = mutableMapOf<String, ProcessingJob>()
    private val jobsMutex = Mutex()

    suspend fun onConnected(userScope: String, session: DefaultWebSocketServerSession) {
        sessionRegistry.register(userScope, session)
        getAgentStateUseCase.execute(userScope)
            .onSuccess { state -> sendSnapshot(session, state) }
            .onFailure { throwable -> sendError(session, throwable.message ?: "Failed to load state", null) }
    }

    suspend fun onDisconnected(session: DefaultWebSocketServerSession) {
        sessionRegistry.unregister(session)
    }

    suspend fun onTextMessage(userScope: String, session: DefaultWebSocketServerSession, text: String) {
        val parsed = runCatching {
            json.decodeFromString(AgentWsClientMessageDto.serializer(), text)
        }.getOrElse { throwable ->
            sendError(session, "Invalid message payload", null)
            logger.warn("WS invalid payload: {}", throwable.message)
            return
        }

        when (parsed) {
            is SubscribeAgentsDto -> {
                getAgentStateUseCase.execute(userScope)
                    .onSuccess { state -> sendSnapshot(session, state) }
                    .onFailure { throwable -> sendError(session, throwable.message ?: "Failed to load state", parsed.requestId) }
            }

            is CreateAgentCommandDto -> {
                createAgentUseCase.execute(userScope)
                    .onSuccess { state -> broadcastSnapshot(userScope, state) }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is SelectAgentCommandDto -> {
                selectAgentUseCase.execute(userScope, SelectAgentCommand(parsed.agentId))
                    .onSuccess { state -> broadcastSnapshot(userScope, state) }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is UpdateAgentDraftCommandDto -> {
                updateAgentDraftUseCase.execute(
                    userScope,
                    UpdateAgentDraftCommand(
                        agentId = parsed.agentId,
                        model = parsed.model,
                        maxOutputTokens = parsed.maxOutputTokens,
                        temperature = parsed.temperature,
                        stopSequences = parsed.stopSequences,
                        agentMode = parsed.agentMode.name.lowercase(),
                        contextConfig = parsed.contextConfig.toDomain(),
                    )
                )
                    .onSuccess { state -> broadcastSnapshot(userScope, state) }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is CloseAgentCommandDto -> {
                closeAgentUseCase.execute(userScope, CloseAgentCommand(parsed.agentId))
                    .onSuccess { state -> broadcastSnapshot(userScope, state) }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is SendAgentMessageCommandDto -> {
                if (isAgentBusy(userScope, parsed.agentId)) {
                    sendError(session, "This agent already has a processing message.", parsed.requestId)
                    return
                }

                startAgentMessageUseCase.execute(
                    userScope,
                    SendAgentMessageCommand(
                        agentId = parsed.agentId,
                        text = parsed.text,
                    )
                )
                    .onSuccess { started ->
                        broadcastSnapshot(userScope, started.state)

                        val job = wsScope.launch {
                            try {
                                val startedAt = System.currentTimeMillis()
                                val generation = generateTextUseCase.execute(started.generateCommand)
                                val latencyMs = System.currentTimeMillis() - startedAt
                                generation
                                    .onSuccess { output ->
                                        completeAgentMessageUseCase.execute(
                                            userScope,
                                            CompleteAgentMessageCommand(
                                                agentId = started.agentId.value,
                                                messageId = started.messageId.value,
                                                output = output,
                                                latencyMs = latencyMs,
                                            )
                                        )
                                            .onSuccess { state -> broadcastSnapshot(userScope, state) }
                                            .onFailure { throwable ->
                                                markProcessingFailed(
                                                    userScope = userScope,
                                                    agentId = started.agentId.value,
                                                    messageId = started.messageId.value,
                                                    requestId = parsed.requestId,
                                                )
                                                sendOperationFailure(session, throwable, parsed.requestId)
                                            }
                                    }
                                    .onFailure { throwable ->
                                        if (!isStopRequested(userScope, started.agentId.value, started.messageId.value)) {
                                            markProcessingFailed(
                                                userScope = userScope,
                                                agentId = started.agentId.value,
                                                messageId = started.messageId.value,
                                                requestId = parsed.requestId,
                                            )
                                            sendOperationFailure(session, throwable, parsed.requestId)
                                        }
                                    }
                            } catch (throwable: Throwable) {
                                val stopRequested = isStopRequested(
                                    userScope = userScope,
                                    agentId = started.agentId.value,
                                    messageId = started.messageId.value,
                                )
                                if (throwable is CancellationException && stopRequested) {
                                    throw throwable
                                }
                                markProcessingFailed(
                                    userScope = userScope,
                                    agentId = started.agentId.value,
                                    messageId = started.messageId.value,
                                    requestId = parsed.requestId,
                                )
                                sendOperationFailure(session, throwable, parsed.requestId)
                                if (throwable is CancellationException) {
                                    throw throwable
                                }
                            } finally {
                                clearProcessing(userScope, started.agentId.value, started.messageId.value)
                            }
                        }

                        registerProcessing(
                            userScope = userScope,
                            agentId = started.agentId.value,
                            messageId = started.messageId.value,
                            job = job,
                        )
                    }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is StopAgentMessageCommandDto -> {
                requestStop(userScope, parsed.agentId, parsed.messageId)
                stopAgentMessageUseCase.execute(
                    userScope,
                    StopAgentMessageCommand(
                        agentId = parsed.agentId,
                        messageId = parsed.messageId,
                    )
                )
                    .onSuccess { state -> broadcastSnapshot(userScope, state) }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is SubmitAgentCommandDto -> {
                sendError(session, "submit_agent is deprecated. Use send_agent_message.", parsed.requestId)
            }
        }
    }

    suspend fun close() {
        wsScope.cancel()
    }

    private suspend fun sendSnapshot(
        session: DefaultWebSocketServerSession,
        state: AgentState,
    ) {
        val payload = mapper.toSnapshotMessage(state)
        session.send(
            Frame.Text(
                json.encodeToString(AgentWsServerMessageDto.serializer(), payload)
            )
        )
    }

    private suspend fun broadcastSnapshot(userScope: String, state: AgentState) {
        val payload = mapper.toSnapshotMessage(state)
        sessionRegistry.broadcast(
            userScope = userScope,
            text = json.encodeToString(AgentWsServerMessageDto.serializer(), payload),
        )
    }

    private suspend fun sendOperationFailure(
        session: DefaultWebSocketServerSession,
        throwable: Throwable,
        requestId: String?,
    ) {
        val message = mapFailureToUserMessageUseCase.execute(throwable)
            .getOrDefault(throwable.message ?: "Unexpected error")
        sendError(session, message, requestId)
    }

    private suspend fun sendError(
        session: DefaultWebSocketServerSession,
        message: String,
        requestId: String?,
    ) {
        val payload = AgentOperationFailedDto(
            code = "operation_failed",
            message = message,
            requestId = requestId,
        )
        session.send(
            Frame.Text(
                json.encodeToString(AgentWsServerMessageDto.serializer(), payload)
            )
        )
    }

    private suspend fun markProcessingFailed(
        userScope: String,
        agentId: String,
        messageId: String,
        requestId: String?,
    ) {
        failAgentMessageUseCase.execute(
            userScope,
            FailAgentMessageCommand(
                agentId = agentId,
                messageId = messageId,
            )
        ).onSuccess { state ->
            broadcastSnapshot(userScope, state)
        }.onFailure { throwable ->
            logger.warn(
                "Failed to mark processing as failed userScope={} agentId={} messageId={} requestId={} reason={}",
                userScope,
                agentId,
                messageId,
                requestId,
                throwable.message,
            )
        }
    }

    private suspend fun isAgentBusy(userScope: String, agentId: String): Boolean {
        return jobsMutex.withLock { processingJobs[jobKey(userScope, agentId)] != null }
    }

    private suspend fun registerProcessing(userScope: String, agentId: String, messageId: String, job: Job) {
        jobsMutex.withLock {
            processingJobs[jobKey(userScope, agentId)] = ProcessingJob(
                messageId = messageId,
                job = job,
                stopRequested = false,
            )
        }
    }

    private suspend fun requestStop(userScope: String, agentId: String, messageId: String) {
        val job = jobsMutex.withLock {
            val current = processingJobs[jobKey(userScope, agentId)] ?: return@withLock null
            if (current.messageId != messageId) return@withLock null
            current.stopRequested = true
            current.job
        }
        job?.cancel()
    }

    private suspend fun isStopRequested(userScope: String, agentId: String, messageId: String): Boolean {
        return jobsMutex.withLock {
            val current = processingJobs[jobKey(userScope, agentId)] ?: return@withLock false
            current.messageId == messageId && current.stopRequested
        }
    }

    private suspend fun clearProcessing(userScope: String, agentId: String, messageId: String) {
        jobsMutex.withLock {
            val current = processingJobs[jobKey(userScope, agentId)] ?: return@withLock
            if (current.messageId == messageId) {
                processingJobs.remove(jobKey(userScope, agentId))
            }
        }
    }

    private fun jobKey(userScope: String, agentId: String): String = "$userScope::$agentId"
}

private data class ProcessingJob(
    val messageId: String,
    val job: Job,
    var stopRequested: Boolean,
)
