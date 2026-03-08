package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.usecase.task.GetActiveTaskUseCase
import io.artemkopan.ai.core.application.usecase.task.TransitionTaskPhaseUseCase
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.sharedcontract.AcceptPlanCommandDto
import org.koin.core.annotation.Factory
import kotlin.random.Random
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class AcceptPlanWsUseCase(
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val agentRepository: AgentRepository,
    private val mapper: AgentWsMapper,
    private val outboundService: AgentWsOutboundService,
) : AgentWsMessageUseCase<AcceptPlanCommandDto> {

    override val messageType: KClass<AcceptPlanCommandDto> = AcceptPlanCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: AcceptPlanCommandDto,
    ): Result<Unit> {
        transitionTaskPhaseUseCase.execute(
            userId = context.userScope,
            taskId = message.taskId,
            fromPhase = TaskPhase.WAITING_FOR_APPROVAL,
            targetPhase = TaskPhase.EXECUTION,
            reason = "User accepted plan",
            newStepIndex = 1,
        ).onSuccess {
            val updatedTask = getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()
            if (updatedTask != null) {
                outboundService.broadcastTaskStateSnapshot(
                    context.userScope,
                    mapper.toTaskStateSnapshot(updatedTask),
                )
            }

            val confirmationMessage = AgentMessage(
                id = AgentMessageId(createMessageId()),
                role = AgentMessageRole.ASSISTANT,
                text = "Plan accepted. Ready to execute. Confirm to proceed or edit.",
                status = "done",
                createdAt = 0L,
                messageType = AgentMessageType.EXECUTION_CONFIRMATION,
            )
            agentRepository.appendMessage(
                UserId(context.userScope),
                AgentId(message.agentId),
                confirmationMessage,
            ).onSuccess { state ->
                outboundService.broadcastSnapshot(context.userScope, state)
            }
        }.onFailure { throwable ->
            outboundService.sendOperationFailure(context.session, throwable, message.requestId)
        }

        return Result.success(Unit)
    }

    private fun createMessageId(): String {
        val a = Random.nextLong().toULong().toString(16)
        val b = Random.nextLong().toULong().toString(16)
        return "$a$b"
    }
}
