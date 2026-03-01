package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.application.usecase.MapFailureToUserMessageUseCase
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.sharedcontract.AgentOperationFailedDto
import io.artemkopan.ai.sharedcontract.AgentWsServerMessageDto
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

open class AgentWsOutboundService(
    private val sessionRegistry: AgentWsSessionRegistry,
    private val mapper: AgentWsMapper,
    private val json: Json,
    private val mapFailureToUserMessageUseCase: MapFailureToUserMessageUseCase,
) {
    open suspend fun sendSnapshot(
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

    open suspend fun broadcastSnapshot(userScope: String, state: AgentState) {
        val payload = mapper.toSnapshotMessage(state)
        sessionRegistry.broadcast(
            userScope = userScope,
            text = json.encodeToString(AgentWsServerMessageDto.serializer(), payload),
        )
    }

    open suspend fun sendOperationFailure(
        session: DefaultWebSocketServerSession,
        throwable: Throwable,
        requestId: String?,
    ) {
        val message = mapFailureToUserMessageUseCase.execute(throwable)
            .getOrDefault(throwable.message ?: "Unexpected error")
        sendError(session, message, requestId)
    }

    open suspend fun sendError(
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
