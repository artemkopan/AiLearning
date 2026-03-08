package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.task.GetActiveTaskUseCase
import io.artemkopan.ai.core.application.usecase.task.TransitionTaskPhaseUseCase
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.EditPlanCommandDto
import io.artemkopan.ai.sharedcontract.TaskStateSnapshotDto
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class EditPlanWsUseCase(
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val sendAgentMessageWsUseCase: Lazy<SendAgentMessageWsUseCase>,
    private val mapper: AgentWsMapper,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<EditPlanCommandDto> {

    override val messageType: KClass<EditPlanCommandDto> = EditPlanCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: EditPlanCommandDto): Result<Unit> {
        return transitionTaskPhaseUseCase.execute(
            userId = context.userScope,
            taskId = message.taskId,
            fromPhase = TaskPhase.WAITING_FOR_APPROVAL,
            targetPhase = TaskPhase.PLANNING,
            reason = "User requested plan edit: ${message.instructions.take(200)}",
            newStepIndex = 0,
        ).onSuccess {
            val updatedTask = getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()
            val snapshot = if (updatedTask != null) {
                mapper.toTaskStateSnapshot(updatedTask)
            } else {
                TaskStateSnapshotDto(agentId = message.agentId, task = null)
            }
            outboundService.broadcastTaskStateSnapshot(context.userScope, snapshot)

            val prompt = if (message.instructions.isNotBlank()) {
                "[Re-planning with edits: ${message.instructions}]"
            } else {
                "[Re-planning...]"
            }
            sendAgentMessageWsUseCase.value.triggerSystemPhaseGeneration(context, message.agentId, prompt)
        }.onFailure { throwable ->
            outboundService.sendOperationFailure(context.session, throwable, message.requestId)
        }
    }
}
