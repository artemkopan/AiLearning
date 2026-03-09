package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.domain.model.AgentId
import io.artemkopan.ai.core.domain.model.UserId
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.sharedcontract.UpdateAgentInvariantsCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class UpdateAgentInvariantsResolver(
    private val agentRepository: AgentRepository,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageResolver<UpdateAgentInvariantsCommandDto> {
    override val messageType = UpdateAgentInvariantsCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: UpdateAgentInvariantsCommandDto,
    ): Result<Unit> {
        val userId = UserId(context.userScope)
        val agentId = AgentId(message.agentId)
        return agentRepository.updateAgentInvariants(userId, agentId, message.invariants)
            .onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            .map { }
    }
}
