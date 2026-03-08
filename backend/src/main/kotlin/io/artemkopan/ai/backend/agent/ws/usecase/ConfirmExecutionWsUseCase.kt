package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.sharedcontract.ConfirmExecutionCommandDto
import io.artemkopan.ai.sharedcontract.SendAgentMessageCommandDto
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class ConfirmExecutionWsUseCase(
    private val sendAgentMessageWsUseCase: SendAgentMessageWsUseCase,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<ConfirmExecutionCommandDto> {

    override val messageType: KClass<ConfirmExecutionCommandDto> = ConfirmExecutionCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: ConfirmExecutionCommandDto,
    ): Result<Unit> {
        val text = message.text.takeIf { it.isNotBlank() } ?: "Execute the plan now."

        return sendAgentMessageWsUseCase.execute(
            context,
            SendAgentMessageCommandDto(
                agentId = message.agentId,
                text = text,
                requestId = message.requestId,
            ),
        )
    }
}
