package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.application.model.CloseAgentCommand
import io.artemkopan.ai.core.application.model.SelectAgentCommand
import io.artemkopan.ai.core.application.model.SendAgentMessageCommand
import io.artemkopan.ai.core.application.model.StopAgentMessageCommand
import io.artemkopan.ai.core.application.model.UpdateAgentDraftCommand
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.application.usecase.CloseAgentUseCase
import io.artemkopan.ai.core.application.usecase.CompleteAgentMessageCommand
import io.artemkopan.ai.core.application.usecase.CompleteAgentMessageUseCase
import io.artemkopan.ai.core.application.usecase.CreateAgentUseCase
import io.artemkopan.ai.core.application.usecase.GenerateTextUseCase
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.core.application.usecase.MapFailureToUserMessageUseCase
import io.artemkopan.ai.core.application.usecase.SelectAgentUseCase
import io.artemkopan.ai.core.application.usecase.StartAgentMessageUseCase
import io.artemkopan.ai.core.application.usecase.StopAgentMessageUseCase
import io.artemkopan.ai.core.application.usecase.UpdateAgentDraftUseCase
import io.artemkopan.ai.sharedcontract.AgentOperationFailedDto
import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.artemkopan.ai.sharedcontract.AgentWsServerMessageDto
import io.artemkopan.ai.sharedcontract.CloseAgentCommandDto
import io.artemkopan.ai.sharedcontract.CreateAgentCommandDto
import io.artemkopan.ai.sharedcontract.SelectAgentCommandDto
import io.artemkopan.ai.sharedcontract.SendAgentMessageCommandDto
import io.artemkopan.ai.sharedcontract.StopAgentMessageCommandDto
import io.artemkopan.ai.sharedcontract.SubmitAgentCommandDto
import io.artemkopan.ai.sharedcontract.SubscribeAgentsDto
import io.artemkopan.ai.sharedcontract.UpdateAgentDraftCommandDto
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    suspend fun onConnected(session: DefaultWebSocketServerSession) {
        sessionRegistry.register(session)
        getAgentStateUseCase.execute()
            .onSuccess { state -> sendSnapshot(session, state) }
            .onFailure { throwable -> sendError(session, throwable.message ?: "Failed to load state", null) }
    }

    suspend fun onDisconnected(session: DefaultWebSocketServerSession) {
        sessionRegistry.unregister(session)
    }

    suspend fun onTextMessage(session: DefaultWebSocketServerSession, text: String) {
        val parsed = runCatching {
            json.decodeFromString(AgentWsClientMessageDto.serializer(), text)
        }.getOrElse { throwable ->
            sendError(session, "Invalid message payload", null)
            logger.warn("WS invalid payload: {}", throwable.message)
            return
        }

        when (parsed) {
            is SubscribeAgentsDto -> {
                getAgentStateUseCase.execute()
                    .onSuccess { state -> sendSnapshot(session, state) }
                    .onFailure { throwable -> sendError(session, throwable.message ?: "Failed to load state", parsed.requestId) }
            }

            is CreateAgentCommandDto -> {
                createAgentUseCase.execute()
                    .onSuccess { state -> broadcastSnapshot(state) }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is SelectAgentCommandDto -> {
                selectAgentUseCase.execute(SelectAgentCommand(parsed.agentId))
                    .onSuccess { state -> broadcastSnapshot(state) }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is UpdateAgentDraftCommandDto -> {
                updateAgentDraftUseCase.execute(
                    UpdateAgentDraftCommand(
                        agentId = parsed.agentId,
                        model = parsed.model,
                        maxOutputTokens = parsed.maxOutputTokens,
                        temperature = parsed.temperature,
                        stopSequences = parsed.stopSequences,
                        agentMode = parsed.agentMode.name.lowercase(),
                    )
                )
                    .onSuccess { state -> broadcastSnapshot(state) }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is CloseAgentCommandDto -> {
                closeAgentUseCase.execute(CloseAgentCommand(parsed.agentId))
                    .onSuccess { state -> broadcastSnapshot(state) }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is SendAgentMessageCommandDto -> {
                if (isAgentBusy(parsed.agentId)) {
                    sendError(session, "This agent already has a processing message.", parsed.requestId)
                    return
                }

                startAgentMessageUseCase.execute(
                    SendAgentMessageCommand(
                        agentId = parsed.agentId,
                        text = parsed.text,
                    )
                )
                    .onSuccess { started ->
                        broadcastSnapshot(started.state)

                        val job = wsScope.launch {
                            try {
                                val generation = generateTextUseCase.execute(started.generateCommand)
                                generation
                                    .onSuccess { output ->
                                        completeAgentMessageUseCase.execute(
                                            CompleteAgentMessageCommand(
                                                agentId = started.agentId.value,
                                                messageId = started.messageId.value,
                                                output = output,
                                            )
                                        )
                                            .onSuccess { state -> broadcastSnapshot(state) }
                                            .onFailure { throwable ->
                                                sendOperationFailure(session, throwable, parsed.requestId)
                                            }
                                    }
                                    .onFailure { throwable ->
                                        if (!isStopRequested(started.agentId.value, started.messageId.value)) {
                                            stopAgentMessageUseCase.execute(
                                                StopAgentMessageCommand(
                                                    agentId = started.agentId.value,
                                                    messageId = started.messageId.value,
                                                )
                                            ).onSuccess { state -> broadcastSnapshot(state) }
                                            sendOperationFailure(session, throwable, parsed.requestId)
                                        }
                                    }
                            } finally {
                                clearProcessing(started.agentId.value, started.messageId.value)
                            }
                        }

                        registerProcessing(
                            agentId = started.agentId.value,
                            messageId = started.messageId.value,
                            job = job,
                        )
                    }
                    .onFailure { throwable -> sendOperationFailure(session, throwable, parsed.requestId) }
            }

            is StopAgentMessageCommandDto -> {
                requestStop(parsed.agentId, parsed.messageId)
                stopAgentMessageUseCase.execute(
                    StopAgentMessageCommand(
                        agentId = parsed.agentId,
                        messageId = parsed.messageId,
                    )
                )
                    .onSuccess { state -> broadcastSnapshot(state) }
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

    private suspend fun broadcastSnapshot(state: AgentState) {
        val payload = mapper.toSnapshotMessage(state)
        sessionRegistry.broadcast(json.encodeToString(AgentWsServerMessageDto.serializer(), payload))
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

    private suspend fun isAgentBusy(agentId: String): Boolean {
        return jobsMutex.withLock { processingJobs[agentId] != null }
    }

    private suspend fun registerProcessing(agentId: String, messageId: String, job: Job) {
        jobsMutex.withLock {
            processingJobs[agentId] = ProcessingJob(
                messageId = messageId,
                job = job,
                stopRequested = false,
            )
        }
    }

    private suspend fun requestStop(agentId: String, messageId: String) {
        val job = jobsMutex.withLock {
            val current = processingJobs[agentId] ?: return@withLock null
            if (current.messageId != messageId) return@withLock null
            current.stopRequested = true
            current.job
        }
        job?.cancel()
    }

    private suspend fun isStopRequested(agentId: String, messageId: String): Boolean {
        return jobsMutex.withLock {
            val current = processingJobs[agentId] ?: return@withLock false
            current.messageId == messageId && current.stopRequested
        }
    }

    private suspend fun clearProcessing(agentId: String, messageId: String) {
        jobsMutex.withLock {
            val current = processingJobs[agentId] ?: return@withLock
            if (current.messageId == messageId) {
                processingJobs.remove(agentId)
            }
        }
    }
}

private data class ProcessingJob(
    val messageId: String,
    val job: Job,
    var stopRequested: Boolean,
)
