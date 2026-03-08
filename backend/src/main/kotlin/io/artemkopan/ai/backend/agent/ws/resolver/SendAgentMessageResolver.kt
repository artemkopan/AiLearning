package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.backend.agent.ws.AgentWsProcessingRegistry
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.domain.model.AgentMessageType
import io.artemkopan.ai.core.domain.model.AgentTask
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.SendAgentMessageCommandDto
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class SendAgentMessageResolver(
    private val startAgentMessageUseCase: StartAgentMessageUseCase,
    private val generateTextUseCase: GenerateTextUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val completeAgentMessageUseCase: CompleteAgentMessageUseCase,
    private val failAgentMessageUseCase: FailAgentMessageUseCase,
    private val processingRegistry: AgentWsProcessingRegistry,
    private val outboundService: AgentWsOutboundService,
    private val mapper: AgentWsMapper,
    private val json: Json,
) : AgentWsMessageResolver<SendAgentMessageCommandDto> {
    override val messageType = SendAgentMessageCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: SendAgentMessageCommandDto): Result<Unit> {
        if (message.text.isBlank()) return Result.failure(IllegalArgumentException("Message text must not be blank"))
        if (message.skipGeneration) return Result.success(Unit)

        val activeTask = getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()
        val isAnsweringQuestion = activeTask != null && activeTask.currentPhase is TaskPhase.WaitingForUserInput

        val started = startAgentMessageUseCase.execute(context.userScope, message.agentId, message.text)
            .getOrElse { return Result.failure(it) }

        val job = processingRegistry.launch {
            if (isAnsweringQuestion) {
                runAnswerPipeline(context, started, message, activeTask!!)
            } else {
                runPipeline(context, started, message)
            }
        }
        processingRegistry.registerProcessing(context.userScope, message.agentId, started.messageId, job)
        job.invokeOnCompletion {
            processingRegistry.launch {
                processingRegistry.clearProcessing(context.userScope, message.agentId, started.messageId)
            }
        }
        return Result.success(Unit)
    }

    private suspend fun runPipeline(context: AgentWsMessageContext, started: StartedAgentMessage, message: SendAgentMessageCommandDto) {
        if (processingRegistry.isStopRequested(context.userScope, message.agentId, started.messageId)) return

        val taskResult = createTaskUseCase.execute(context.userScope, message.agentId, "Task ${message.text.take(30)}")
        val task = taskResult.getOrNull()
        if (task == null) {
            val err = taskResult.exceptionOrNull()
            failAgentMessageUseCase.execute(context.userScope, message.agentId, started.messageId, err?.message ?: "Failed to create task")
            getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            return
        }

        outboundService.broadcastPhaseChanged(
            userScope = context.userScope,
            agentId = message.agentId,
            taskId = task.id.value,
            fromPhase = TaskPhase.Planning,
            toPhase = TaskPhase.Planning,
            reason = "Task created",
        )
        outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(task))

        val genResult = generateTextUseCase.execute(started.generateCommand)
        if (genResult.isFailure) {
            failAgentMessageUseCase.execute(context.userScope, message.agentId, started.messageId, genResult.exceptionOrNull()?.message ?: "Generation failed")
            getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            return
        }

        val genValue = genResult.getOrThrow()
        val planText = genValue.text
        val planJson = extractPlanJson(planText)
        val updatedTask = task.copy(planJson = planJson)
        updateTaskUseCase.execute(context.userScope, updatedTask).getOrElse { return }

        val fromPhase = transitionTaskPhaseUseCase.execute(
            context.userScope, message.agentId, task.id.value,
            TaskPhase.WaitingForApproval, "Plan generated",
        ).getOrElse { return }

        outboundService.broadcastPhaseChanged(
            userScope = context.userScope,
            agentId = message.agentId,
            taskId = task.id.value,
            fromPhase = fromPhase,
            toPhase = TaskPhase.WaitingForApproval,
            reason = "Plan generated",
        )

        completeAgentMessageUseCase.execute(
            context.userScope,
            message.agentId,
            started.messageId,
            text = planText,
            provider = genValue.provider,
            model = genValue.model,
            usageInputTokens = genValue.usage?.inputTokens,
            usageOutputTokens = genValue.usage?.outputTokens,
            usageTotalTokens = genValue.usage?.totalTokens,
            messageType = AgentMessageType.REVIEW,
        ).onSuccess { state ->
            outboundService.broadcastSnapshot(context.userScope, state)
            getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let { activeTask ->
                outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(activeTask))
            }
        }
    }

    private suspend fun runAnswerPipeline(
        context: AgentWsMessageContext,
        started: StartedAgentMessage,
        message: SendAgentMessageCommandDto,
        existingTask: AgentTask,
    ) {
        if (processingRegistry.isStopRequested(context.userScope, message.agentId, started.messageId)) return

        val fromPhaseTransition = transitionTaskPhaseUseCase.execute(
            context.userScope, message.agentId, existingTask.id.value,
            TaskPhase.WaitingForApproval, "User answered question",
        ).getOrElse { fromPhase ->
            failAgentMessageUseCase.execute(context.userScope, message.agentId, started.messageId, "Failed to transition task")
            getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            return
        }

        outboundService.broadcastPhaseChanged(
            userScope = context.userScope,
            agentId = message.agentId,
            taskId = existingTask.id.value,
            fromPhase = fromPhaseTransition,
            toPhase = TaskPhase.WaitingForApproval,
            reason = "User answered question",
        )

        val genResult = generateTextUseCase.execute(started.generateCommand)
        if (genResult.isFailure) {
            failAgentMessageUseCase.execute(context.userScope, message.agentId, started.messageId, genResult.exceptionOrNull()?.message ?: "Generation failed")
            getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            return
        }

        val genValue = genResult.getOrThrow()
        val planText = genValue.text
        val planJson = extractPlanJson(planText)
        val currentTask = getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()
            ?: existingTask
        val updatedTask = currentTask.copy(planJson = planJson)
        updateTaskUseCase.execute(context.userScope, updatedTask).getOrElse { return }

        completeAgentMessageUseCase.execute(
            context.userScope,
            message.agentId,
            started.messageId,
            text = planText,
            provider = genValue.provider,
            model = genValue.model,
            usageInputTokens = genValue.usage?.inputTokens,
            usageOutputTokens = genValue.usage?.outputTokens,
            usageTotalTokens = genValue.usage?.totalTokens,
            messageType = AgentMessageType.REVIEW,
        ).onSuccess { state ->
            outboundService.broadcastSnapshot(context.userScope, state)
            getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let { activeTask ->
                outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(activeTask))
            }
        }
    }

    private fun extractPlanJson(text: String): String {
        val jsonStart = text.indexOf('{')
        if (jsonStart < 0) return fallbackPlanJson(text)
        var depth = 0
        for (i in jsonStart until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(jsonStart, i + 1)
                }
            }
        }
        return fallbackPlanJson(text)
    }

    private fun fallbackPlanJson(text: String): String {
        val escaped = json.encodeToString(text.take(200))
        return """{"plan":[$escaped],"question_for_user":""}"""
    }
}
