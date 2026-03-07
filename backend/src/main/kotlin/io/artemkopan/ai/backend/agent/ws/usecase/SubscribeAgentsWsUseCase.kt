package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.core.application.usecase.task.GetActiveTaskUseCase
import io.artemkopan.ai.sharedcontract.SubscribeAgentsDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageUseCase::class])
class SubscribeAgentsWsUseCase(
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val mapper: AgentWsMapper,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<SubscribeAgentsDto> {
    override val messageType = SubscribeAgentsDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: SubscribeAgentsDto): Result<Unit> {
        getAgentStateUseCase.execute(context.userScope)
            .onSuccess { state ->
                outboundService.sendSnapshot(context.session, state)
                restoreTaskStateForAgents(context, state.agents.map { it.id.value })
            }
            .onFailure { throwable ->
                outboundService.sendError(
                    session = context.session,
                    message = throwable.message ?: "Failed to load state",
                    requestId = message.requestId,
                )
            }
        return Result.success(Unit)
    }

    private suspend fun restoreTaskStateForAgents(context: AgentWsMessageContext, agentIds: List<String>) {
        agentIds.forEach { agentId ->
            getActiveTaskUseCase.execute(context.userScope, agentId).getOrNull()?.let { task ->
                outboundService.sendTaskStateSnapshot(
                    session = context.session,
                    payload = mapper.toTaskStateSnapshot(task),
                )
            }
        }
    }
}
