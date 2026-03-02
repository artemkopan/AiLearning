package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.sharedcontract.SubmitAgentCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageUseCase::class])
class SubmitAgentDeprecatedWsUseCase(
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<SubmitAgentCommandDto> {
    override val messageType = SubmitAgentCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: SubmitAgentCommandDto): Result<Unit> {
        outboundService.sendError(
            session = context.session,
            message = "submit_agent is deprecated. Use send_agent_message.",
            requestId = message.requestId,
        )
        return Result.success(Unit)
    }
}
