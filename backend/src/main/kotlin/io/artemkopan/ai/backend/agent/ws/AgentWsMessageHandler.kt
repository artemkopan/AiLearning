package io.artemkopan.ai.backend.agent.ws

import io.artemkopan.ai.backend.agent.ws.usecase.AgentWsMessageContext
import io.artemkopan.ai.backend.agent.ws.usecase.AgentWsMessageUseCase
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.sharedcontract.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.slf4j.Logger
import kotlin.reflect.KClass

@Single
class AgentWsMessageHandler(
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val sessionRegistry: AgentWsSessionRegistry,
    private val outboundService: AgentWsOutboundService,
    private val handlersByMessageType: Map<KClass<out AgentWsClientMessageDto>, AgentWsMessageUseCase<out AgentWsClientMessageDto>>,
    private val json: Json,
    private val logger: Logger,
) {
    init {
        validateHandlerBindings()
    }

    suspend fun onConnected(userScope: String, session: DefaultWebSocketServerSession) {
        sessionRegistry.register(userScope, session)
        getAgentStateUseCase.execute(userScope)
            .onSuccess { state -> outboundService.sendSnapshot(session, state) }
            .onFailure { throwable ->
                outboundService.sendError(
                    session = session,
                    message = throwable.message ?: "Failed to load state",
                    requestId = null,
                )
            }
    }

    suspend fun onDisconnected(session: DefaultWebSocketServerSession) {
        sessionRegistry.unregister(session)
    }

    suspend fun onTextMessage(userScope: String, session: DefaultWebSocketServerSession, text: String) {
        val parsed = runCatching {
            json.decodeFromString(AgentWsClientMessageDto.serializer(), text)
        }.getOrElse { throwable ->
            outboundService.sendError(session, "Invalid message payload", null)
            logger.warn("WS invalid payload: {}", throwable.message)
            return
        }

        val handler = handlersByMessageType[parsed::class]
        if (handler == null) {
            logger.error("No WS handler registered for dtoType={}", parsed::class.qualifiedName)
            outboundService.sendError(
                session = session,
                message = "Unsupported message type",
                requestId = parsed.requestIdOrNull(),
            )
            return
        }

        dispatch(handler, AgentWsMessageContext(userScope, session), parsed)
            .onFailure { throwable ->
                logger.error(
                    "WS handler failed dtoType={} requestId={} reason={}",
                    parsed::class.qualifiedName,
                    parsed.requestIdOrNull(),
                    throwable.message,
                    throwable,
                )
                outboundService.sendOperationFailure(
                    session = session,
                    throwable = throwable,
                    requestId = parsed.requestIdOrNull(),
                )
            }
    }

    private fun validateHandlerBindings() {
        val expectedTypes = AgentWsClientMessageDto::class.sealedSubclasses.toSet()
        val registeredTypes = handlersByMessageType.keys.toSet()
        val missingTypes = expectedTypes - registeredTypes
        require(missingTypes.isEmpty()) {
            val missing = missingTypes.joinToString { type -> type.qualifiedName ?: type.toString() }
            "Missing WS message handlers for DTO types: $missing"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun dispatch(
        handler: AgentWsMessageUseCase<out AgentWsClientMessageDto>,
        context: AgentWsMessageContext,
        message: AgentWsClientMessageDto,
    ): Result<Unit> {
        return (handler as AgentWsMessageUseCase<AgentWsClientMessageDto>).execute(context, message)
    }
}

private fun AgentWsClientMessageDto.requestIdOrNull(): String? {
    return when (this) {
        is SubscribeAgentsDto -> requestId
        is CreateAgentCommandDto -> requestId
        is SelectAgentCommandDto -> requestId
        is UpdateAgentDraftCommandDto -> requestId
        is CloseAgentCommandDto -> requestId
        is SubmitAgentCommandDto -> requestId
        is SendAgentMessageCommandDto -> requestId
        is StopAgentMessageCommandDto -> requestId
        is CreateBranchCommandDto -> requestId
        is SwitchBranchCommandDto -> requestId
        is DeleteBranchCommandDto -> requestId
    }
}
