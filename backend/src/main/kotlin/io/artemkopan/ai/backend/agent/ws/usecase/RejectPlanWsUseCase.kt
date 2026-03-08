package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.task.GetActiveTaskUseCase
import io.artemkopan.ai.core.application.usecase.task.TransitionTaskPhaseUseCase
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.RejectPlanCommandDto
import io.artemkopan.ai.sharedcontract.TaskStateSnapshotDto
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class RejectPlanWsUseCase(
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val mapper: AgentWsMapper,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<RejectPlanCommandDto> {

    override val messageType: KClass<RejectPlanCommandDto> = RejectPlanCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: RejectPlanCommandDto,
    ): Result<Unit> {
        val task = getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()
        val fromPhase = task?.currentPhase ?: TaskPhase.WAITING_FOR_APPROVAL

        transitionTaskPhaseUseCase.execute(
            userId = context.userScope,
            taskId = message.taskId,
            fromPhase = fromPhase,
            targetPhase = TaskPhase.DONE,
            reason = "User rejected plan",
            newStepIndex = 3,
        ).onSuccess {
            outboundService.broadcastTaskStateSnapshot(
                context.userScope,
                TaskStateSnapshotDto(agentId = message.agentId, task = null),
            )
        }.onFailure { throwable ->
            outboundService.sendOperationFailure(context.session, throwable, message.requestId)
        }

        return Result.success(Unit)
    }
}
