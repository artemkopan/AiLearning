package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.CloseAgentUseCase
import io.artemkopan.ai.sharedcontract.CloseAgentCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class CloseAgentResolver(
    private val closeAgentUseCase: CloseAgentUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageResolver<CloseAgentCommandDto> {
    override val messageType = CloseAgentCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: CloseAgentCommandDto): Result<Unit> {
        return closeAgentUseCase.execute(context.userScope, message.agentId)
            .onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            .map { }
    }
}
