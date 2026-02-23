package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.application.model.CloseAgentCommand
import io.artemkopan.ai.core.application.model.SelectAgentCommand
import io.artemkopan.ai.core.application.model.SetAgentStatusCommand
import io.artemkopan.ai.core.application.model.SubmitAgentCommand
import io.artemkopan.ai.core.application.model.UpdateAgentDraftCommand
import io.artemkopan.ai.core.application.usecase.CloseAgentUseCase
import io.artemkopan.ai.core.application.usecase.CreateAgentUseCase
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.core.application.usecase.MapFailureToUserMessageUseCase
import io.artemkopan.ai.core.application.usecase.SelectAgentUseCase
import io.artemkopan.ai.core.application.usecase.SetAgentStatusUseCase
import io.artemkopan.ai.core.application.usecase.SubmitAgentUseCase
import io.artemkopan.ai.core.application.usecase.UpdateAgentDraftUseCase
import io.artemkopan.ai.sharedcontract.AgentOperationFailedDto
import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.artemkopan.ai.sharedcontract.AgentWsServerMessageDto
import io.artemkopan.ai.sharedcontract.CloseAgentCommandDto
import io.artemkopan.ai.sharedcontract.CreateAgentCommandDto
import io.artemkopan.ai.sharedcontract.SelectAgentCommandDto
import io.artemkopan.ai.sharedcontract.SubmitAgentCommandDto
import io.artemkopan.ai.sharedcontract.SubscribeAgentsDto
import io.artemkopan.ai.sharedcontract.UpdateAgentDraftCommandDto
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.serialization.json.Json
import org.slf4j.Logger

class AgentWsMessageHandler(
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val createAgentUseCase: CreateAgentUseCase,
    private val selectAgentUseCase: SelectAgentUseCase,
    private val updateAgentDraftUseCase: UpdateAgentDraftUseCase,
    private val closeAgentUseCase: CloseAgentUseCase,
    private val setAgentStatusUseCase: SetAgentStatusUseCase,
    private val submitAgentUseCase: SubmitAgentUseCase,
    private val mapFailureToUserMessageUseCase: MapFailureToUserMessageUseCase,
    private val sessionRegistry: AgentWsSessionRegistry,
    private val mapper: AgentWsMapper,
    private val json: Json,
    private val logger: Logger,
) {

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
                        prompt = parsed.prompt,
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

            is SubmitAgentCommandDto -> {
                setAgentStatusUseCase.execute(SetAgentStatusCommand(parsed.agentId, "Running"))
                    .onSuccess { state -> broadcastSnapshot(state) }
                    .onFailure { throwable ->
                        sendOperationFailure(session, throwable, parsed.requestId)
                        return
                    }

                submitAgentUseCase.execute(SubmitAgentCommand(parsed.agentId))
                    .onSuccess { state -> broadcastSnapshot(state) }
                    .onFailure { throwable ->
                        getAgentStateUseCase.execute().onSuccess { state -> broadcastSnapshot(state) }
                        sendOperationFailure(session, throwable, parsed.requestId)
                    }
            }
        }
    }

    private suspend fun sendSnapshot(
        session: DefaultWebSocketServerSession,
        state: io.artemkopan.ai.core.domain.model.AgentState,
    ) {
        val payload = mapper.toSnapshotMessage(state)
        session.send(
            Frame.Text(
                json.encodeToString(AgentWsServerMessageDto.serializer(), payload)
            )
        )
    }

    private suspend fun broadcastSnapshot(state: io.artemkopan.ai.core.domain.model.AgentState) {
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
}
