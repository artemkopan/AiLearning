package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.backend.agent.ws.AgentWsProcessingRegistry
import io.artemkopan.ai.core.application.usecase.StopAgentMessageUseCase
import io.artemkopan.ai.sharedcontract.StopAgentMessageCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class StopAgentMessageResolver(
    private val stopAgentMessageUseCase: StopAgentMessageUseCase,
    private val processingRegistry: AgentWsProcessingRegistry,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageResolver<StopAgentMessageCommandDto> {
    override val messageType = StopAgentMessageCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: StopAgentMessageCommandDto): Result<Unit> {
        processingRegistry.requestStop(context.userScope, message.agentId, message.messageId)
        return stopAgentMessageUseCase.execute(context.userScope, message.agentId, message.messageId)
            .onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            .map { }
    }
}
