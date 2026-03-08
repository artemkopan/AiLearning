package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.sharedcontract.SubscribeAgentsDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class SubscribeAgentsResolver(
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageResolver<SubscribeAgentsDto> {
    override val messageType = SubscribeAgentsDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: SubscribeAgentsDto): Result<Unit> {
        return getAgentStateUseCase.execute(context.userScope)
            .onSuccess { outboundService.sendSnapshot(context.session, it) }
            .map { }
    }
}
