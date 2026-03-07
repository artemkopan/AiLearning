package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.core.application.usecase.task.CreateTaskUseCase
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.core.domain.model.TaskStep
import io.artemkopan.ai.core.domain.model.TaskStepStatus
import io.artemkopan.ai.sharedcontract.AgentWsServerMessageDto
import io.artemkopan.ai.sharedcontract.CreateTaskCommandDto
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Factory
import kotlin.reflect.KClass

@Factory(binds = [AgentWsMessageUseCase::class])
class CreateTaskWsUseCase(
    private val createTaskUseCase: CreateTaskUseCase,
    private val mapper: AgentWsMapper,
    private val json: Json,
) : AgentWsMessageUseCase<CreateTaskCommandDto> {

    override val messageType: KClass<CreateTaskCommandDto> = CreateTaskCommandDto::class

    override suspend fun execute(
        context: AgentWsMessageContext,
        message: CreateTaskCommandDto,
    ): Result<Unit> {
        val domainSteps = message.steps.mapIndexed { index, dto ->
            TaskStep(
                index = index,
                phase = TaskPhase.valueOf(dto.phase.uppercase()),
                description = dto.description,
                expectedAction = dto.expectedAction,
                status = TaskStepStatus.PENDING,
                result = "",
            )
        }

        val task = createTaskUseCase.execute(
            userId = context.userScope,
            agentId = message.agentId,
            title = message.title,
            steps = domainSteps,
        ).getOrElse { return Result.failure(it) }

        val payload = mapper.toTaskStateSnapshot(task)
        context.session.send(
            Frame.Text(json.encodeToString(AgentWsServerMessageDto.serializer(), payload))
        )
        return Result.success(Unit)
    }
}
