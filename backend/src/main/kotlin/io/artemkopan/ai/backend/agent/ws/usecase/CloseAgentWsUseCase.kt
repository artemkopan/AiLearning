package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.model.CloseAgentCommand
import io.artemkopan.ai.core.application.usecase.CloseAgentUseCase
import io.artemkopan.ai.sharedcontract.CloseAgentCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageUseCase::class])
class CloseAgentWsUseCase(
    private val closeAgentUseCase: CloseAgentUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<CloseAgentCommandDto> {
    override val messageType = CloseAgentCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: CloseAgentCommandDto): Result<Unit> {
        closeAgentUseCase.execute(
            context.userScope,
            CloseAgentCommand(message.agentId),
        )
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
