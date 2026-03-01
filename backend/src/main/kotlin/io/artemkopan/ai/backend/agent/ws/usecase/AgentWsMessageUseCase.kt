package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.ktor.server.websocket.*
import kotlin.reflect.KClass

data class AgentWsMessageContext(
    val userScope: String,
    val session: DefaultWebSocketServerSession,
)

interface AgentWsMessageUseCase<T : AgentWsClientMessageDto> {
    val messageType: KClass<T>

    suspend fun execute(context: AgentWsMessageContext, message: T): Result<Unit>
}
