package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.SelectAgentUseCase
import io.artemkopan.ai.sharedcontract.SelectAgentCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class SelectAgentResolver(
    private val selectAgentUseCase: SelectAgentUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageResolver<SelectAgentCommandDto> {
    override val messageType = SelectAgentCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: SelectAgentCommandDto): Result<Unit> {
        return selectAgentUseCase.execute(context.userScope, message.agentId)
            .onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            .map { }
    }
}
