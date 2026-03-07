package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.core.application.usecase.task.TransitionTaskPhaseUseCase
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.TransitionTaskPhaseCommandDto
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class TransitionTaskPhaseWsUseCase(
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
) : AgentWsMessageUseCase<TransitionTaskPhaseCommandDto> {

    override val messageType: KClass<TransitionTaskPhaseCommandDto> = TransitionTaskPhaseCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: TransitionTaskPhaseCommandDto,
    ): Result<Unit> {
        return transitionTaskPhaseUseCase.execute(
            userId = context.userScope,
            taskId = message.taskId,
            fromPhase = TaskPhase.valueOf(message.fromPhase.uppercase()),
            targetPhase = TaskPhase.valueOf(message.targetPhase.uppercase()),
            reason = message.reason,
        )
    }
}
