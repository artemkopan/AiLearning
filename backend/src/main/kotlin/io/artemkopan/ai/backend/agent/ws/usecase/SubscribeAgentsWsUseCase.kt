package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.sharedcontract.SubscribeAgentsDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageUseCase::class])
class SubscribeAgentsWsUseCase(
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<SubscribeAgentsDto> {
    override val messageType = SubscribeAgentsDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: SubscribeAgentsDto): Result<Unit> {
        getAgentStateUseCase.execute(context.userScope)
            .onSuccess { state -> outboundService.sendSnapshot(context.session, state) }
            .onFailure { throwable ->
                outboundService.sendError(
                    session = context.session,
                    message = throwable.message ?: "Failed to load state",
                    requestId = message.requestId,
                )
            }
        return Result.success(Unit)
    }
}
