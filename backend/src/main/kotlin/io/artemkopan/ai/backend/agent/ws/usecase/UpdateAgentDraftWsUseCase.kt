package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.backend.agent.ws.toDomain
import io.artemkopan.ai.core.application.model.UpdateAgentDraftCommand
import io.artemkopan.ai.core.application.usecase.UpdateAgentDraftUseCase
import io.artemkopan.ai.sharedcontract.UpdateAgentDraftCommandDto

class UpdateAgentDraftWsUseCase(
    private val updateAgentDraftUseCase: UpdateAgentDraftUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<UpdateAgentDraftCommandDto> {
    override val messageType = UpdateAgentDraftCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: UpdateAgentDraftCommandDto): Result<Unit> {
        updateAgentDraftUseCase.execute(
            context.userScope,
            UpdateAgentDraftCommand(
                agentId = message.agentId,
                model = message.model,
                maxOutputTokens = message.maxOutputTokens,
                temperature = message.temperature,
                stopSequences = message.stopSequences,
                agentMode = message.agentMode.name.lowercase(),
                contextConfig = message.contextConfig.toDomain(),
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
