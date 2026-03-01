package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.model.SelectAgentCommand
import io.artemkopan.ai.core.application.usecase.SelectAgentUseCase
import io.artemkopan.ai.sharedcontract.SelectAgentCommandDto

class SelectAgentWsUseCase(
    private val selectAgentUseCase: SelectAgentUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<SelectAgentCommandDto> {
    override val messageType = SelectAgentCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: SelectAgentCommandDto): Result<Unit> {
        selectAgentUseCase.execute(
            context.userScope,
            SelectAgentCommand(message.agentId),
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
