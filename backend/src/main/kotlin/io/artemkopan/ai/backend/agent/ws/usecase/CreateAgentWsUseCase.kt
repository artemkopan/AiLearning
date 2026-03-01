package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.CreateAgentUseCase
import io.artemkopan.ai.sharedcontract.CreateAgentCommandDto

class CreateAgentWsUseCase(
    private val createAgentUseCase: CreateAgentUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<CreateAgentCommandDto> {
    override val messageType = CreateAgentCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: CreateAgentCommandDto): Result<Unit> {
        createAgentUseCase.execute(context.userScope)
            .onSuccess { state -> outboundService.broadcastSnapshot(context.userScope, state) }
            .onFailure { throwable ->
                outboundService.sendOperationFailure(
                    session = context.session,
                    throwable = throwable,
                    requestId = message.requestId,
                )
            }
        return Result.success(Unit)
    }
}
