package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.backend.agent.ws.AgentWsProcessingRegistry
import io.artemkopan.ai.core.application.model.StopAgentMessageCommand
import io.artemkopan.ai.core.application.usecase.StopAgentMessageUseCase
import io.artemkopan.ai.sharedcontract.StopAgentMessageCommandDto

class StopAgentMessageWsUseCase(
    private val processingRegistry: AgentWsProcessingRegistry,
    private val stopAgentMessageUseCase: StopAgentMessageUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<StopAgentMessageCommandDto> {
    override val messageType = StopAgentMessageCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: StopAgentMessageCommandDto): Result<Unit> {
        processingRegistry.requestStop(context.userScope, message.agentId, message.messageId)
        stopAgentMessageUseCase.execute(
            context.userScope,
            StopAgentMessageCommand(
                agentId = message.agentId,
                messageId = message.messageId,
            )
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
