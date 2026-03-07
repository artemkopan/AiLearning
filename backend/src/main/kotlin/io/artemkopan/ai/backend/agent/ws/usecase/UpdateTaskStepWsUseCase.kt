package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.core.application.usecase.task.UpdateTaskStepUseCase
import io.artemkopan.ai.core.domain.model.TaskStepStatus
import io.artemkopan.ai.sharedcontract.UpdateTaskStepCommandDto
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class UpdateTaskStepWsUseCase(
    private val updateTaskStepUseCase: UpdateTaskStepUseCase,
) : AgentWsMessageUseCase<UpdateTaskStepCommandDto> {

    override val messageType: KClass<UpdateTaskStepCommandDto> = UpdateTaskStepCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: UpdateTaskStepCommandDto,
    ): Result<Unit> {
        return updateTaskStepUseCase.execute(
            userId = context.userScope,
            taskId = message.taskId,
            stepIndex = message.stepIndex,
            status = TaskStepStatus.valueOf(message.status.uppercase()),
            result = message.result,
        )
    }
}
