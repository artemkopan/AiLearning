package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.CreateAgentUseCase
import io.artemkopan.ai.sharedcontract.CreateAgentCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class CreateAgentResolver(
    private val createAgentUseCase: CreateAgentUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageResolver<CreateAgentCommandDto> {
    override val messageType = CreateAgentCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: CreateAgentCommandDto): Result<Unit> {
        return createAgentUseCase.execute(context.userScope)
            .onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            .map { }
    }
}
