package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.GetActiveTaskUseCase
import io.artemkopan.ai.core.application.usecase.TransitionTaskPhaseUseCase
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.ResumeTaskCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class ResumeTaskResolver(
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val outboundService: AgentWsOutboundService,
    private val mapper: AgentWsMapper,
) : AgentWsMessageResolver<ResumeTaskCommandDto> {
    override val messageType = ResumeTaskCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: ResumeTaskCommandDto): Result<Unit> {
        val fromPhase = transitionTaskPhaseUseCase.execute(
            context.userScope, message.agentId, message.taskId,
            TaskPhase.Execution, "User resumed",
        ).getOrElse { return Result.failure(it) }

        outboundService.broadcastPhaseChanged(
            context.userScope, message.agentId, message.taskId,
            fromPhase, TaskPhase.Execution, "User resumed",
        )
        getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let {
            outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(it))
        }
        return Result.success(Unit)
    }
}
