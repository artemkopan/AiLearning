package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.core.application.usecase.MapFailureToUserMessageUseCase
import io.artemkopan.ai.core.domain.model.AgentState
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.AgentOperationFailedDto
import io.artemkopan.ai.sharedcontract.AgentWsServerMessageDto
import io.artemkopan.ai.sharedcontract.TaskStateSnapshotDto
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
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
        sendError(session, message, requestId)
    }

    open suspend fun sendTaskStateSnapshot(
        session: DefaultWebSocketServerSession,
        payload: TaskStateSnapshotDto,
    ) {
        session.send(
            Frame.Text(json.encodeToString(AgentWsServerMessageDto.serializer(), payload)),
        )
    }

    open suspend fun broadcastTaskStateSnapshot(userScope: String, payload: TaskStateSnapshotDto) {
        sessionRegistry.broadcast(
            userScope = userScope,
            text = json.encodeToString(AgentWsServerMessageDto.serializer(), payload),
        )
    }

    open suspend fun broadcastPhaseChanged(
        userScope: String,
        agentId: String,
        taskId: String,
        fromPhase: TaskPhase,
        toPhase: TaskPhase,
        reason: String,
    ) {
        val payload = mapper.toPhaseChangedDto(agentId, taskId, fromPhase, toPhase, reason)
        sessionRegistry.broadcast(
            userScope = userScope,
            text = json.encodeToString(AgentWsServerMessageDto.serializer(), payload),
        )
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
