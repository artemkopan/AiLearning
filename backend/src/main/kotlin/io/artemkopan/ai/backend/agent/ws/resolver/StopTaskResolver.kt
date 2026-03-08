package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.backend.agent.ws.AgentWsProcessingRegistry
import io.artemkopan.ai.core.application.usecase.GetActiveTaskUseCase
import io.artemkopan.ai.core.application.usecase.GetAgentStateUseCase
import io.artemkopan.ai.core.application.usecase.TransitionTaskPhaseUseCase
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.StopTaskCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class StopTaskResolver(
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val processingRegistry: AgentWsProcessingRegistry,
    private val outboundService: AgentWsOutboundService,
    private val mapper: AgentWsMapper,
) : AgentWsMessageResolver<StopTaskCommandDto> {
    override val messageType = StopTaskCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: StopTaskCommandDto): Result<Unit> {
        val fromPhase = transitionTaskPhaseUseCase.execute(
            context.userScope, message.agentId, message.taskId,
            TaskPhase.Stopped, "User stopped task",
        ).getOrElse { return Result.failure(it) }

        outboundService.broadcastPhaseChanged(
            context.userScope, message.agentId, message.taskId,
            fromPhase, TaskPhase.Stopped, "User stopped task",
        )
        getAgentStateUseCase.execute(context.userScope).onSuccess {
            outboundService.broadcastSnapshot(context.userScope, it)
        }
        getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let {
            outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(it))
        }
        return Result.success(Unit)
    }
}
