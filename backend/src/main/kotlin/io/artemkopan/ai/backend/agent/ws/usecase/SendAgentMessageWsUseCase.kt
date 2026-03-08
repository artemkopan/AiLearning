package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.backend.agent.ws.AgentWsProcessingRegistry
import io.artemkopan.ai.core.application.model.SendAgentMessageCommand
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.application.usecase.shortcut.*
import io.artemkopan.ai.core.application.usecase.task.CreateTaskUseCase
import io.artemkopan.ai.core.application.usecase.task.GetActiveTaskUseCase
import io.artemkopan.ai.core.application.usecase.task.TransitionTaskPhaseUseCase
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.AgentRepository
import io.artemkopan.ai.core.domain.repository.TaskRepository
import io.artemkopan.ai.sharedcontract.SendAgentMessageCommandDto
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Factory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.random.Random

@Factory(binds = [AgentWsMessageUseCase::class])
class SendAgentMessageWsUseCase(
    private val processingRegistry: AgentWsProcessingRegistry,
    private val startAgentMessageUseCase: StartAgentMessageUseCase,
    private val completeAgentMessageUseCase: CompleteAgentMessageUseCase,
    private val failAgentMessageUseCase: FailAgentMessageUseCase,
    private val generateTextUseCase: GenerateTextUseCase,
    private val parseStatsShortcutTokensUseCase: ParseStatsShortcutTokensUseCase,
    private val parseMemoryLayerShortcutTokenUseCase: ParseMemoryLayerShortcutTokenUseCase,
    private val resolveStatsShortcutsUseCase: ResolveStatsShortcutsUseCase,
    private val switchAgentMemoryLayerUseCase: SwitchAgentMemoryLayerUseCase,
    private val agentRepository: AgentRepository,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
    private val taskRepository: TaskRepository,
    private val parsePhaseResponseUseCase: ParsePhaseResponseUseCase,
    private val mapper: AgentWsMapper,
    private val outboundService: AgentWsOutboundService,
    private val logger: Logger = LoggerFactory.getLogger(SendAgentMessageWsUseCase::class.java),
) : AgentWsMessageUseCase<SendAgentMessageCommandDto> {
    override val messageType = SendAgentMessageCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: SendAgentMessageCommandDto): Result<Unit> {
        if (processingRegistry.isAgentBusy(context.userScope, message.agentId)) {
            outboundService.sendError(
                session = context.session,
                message = "This agent already has a processing message.",
                requestId = message.requestId,
            )
            return Result.success(Unit)
        }

        if (tryHandleStatsShortcutCommand(context, message)) {
            return Result.success(Unit)
        }
        if (tryHandleMemoryLayerShortcutCommand(context, message)) {
            return Result.success(Unit)
        }

        if (message.skipGeneration) {
            appendUserMessageOnly(context, message)
            return Result.success(Unit)
        }

        ensureActiveTaskExists(context, message)

        startAgentMessageUseCase.execute(
            context.userScope,
            SendAgentMessageCommand(
                agentId = message.agentId,
                text = message.text,
            )
        )
            .onSuccess { started ->
                outboundService.broadcastSnapshot(context.userScope, started.state)

                val job = processingRegistry.launch {
                    try {
                        val startedAt = System.currentTimeMillis()
                        val generation = generateTextUseCase.execute(started.generateCommand)
                        val latencyMs = System.currentTimeMillis() - startedAt
                        generation
                            .onSuccess { output ->
                                val currentTask = getActiveTaskUseCase.execute(context.userScope, started.agentId.value).getOrNull()
                                val (displayText, msgType) = formatPhaseOutput(currentTask, output.text)
                                completeAgentMessageUseCase.execute(
                                    context.userScope,
                                    CompleteAgentMessageCommand(
                                        agentId = started.agentId.value,
                                        messageId = started.messageId.value,
                                        output = output.copy(text = displayText),
                                        latencyMs = latencyMs,
                                        messageType = msgType,
                                    )
                                )
                                    .onSuccess { state ->
                                        outboundService.broadcastSnapshot(context.userScope, state)
                                        advanceTaskPhaseAfterCompletion(
                                            context = context,
                                            agentId = started.agentId.value,
                                            outputText = output.text,
                                        )
                                    }
                                    .onFailure { throwable ->
                                        markProcessingFailed(
                                            userScope = context.userScope,
                                            agentId = started.agentId.value,
                                            messageId = started.messageId.value,
                                            requestId = message.requestId,
                                        )
                                        outboundService.sendOperationFailure(context.session, throwable, message.requestId)
                                    }
                            }
                            .onFailure { throwable ->
                                if (!processingRegistry.isStopRequested(
                                        context.userScope,
                                        started.agentId.value,
                                        started.messageId.value,
                                    )
                                ) {
                                    markProcessingFailed(
                                        userScope = context.userScope,
                                        agentId = started.agentId.value,
                                        messageId = started.messageId.value,
                                        requestId = message.requestId,
                                    )
                                    outboundService.sendOperationFailure(context.session, throwable, message.requestId)
                                }
                            }
                    } catch (throwable: Throwable) {
                        val stopRequested = processingRegistry.isStopRequested(
                            userScope = context.userScope,
                            agentId = started.agentId.value,
                            messageId = started.messageId.value,
                        )
                        if (throwable is CancellationException && stopRequested) {
                            throw throwable
                        }
                        markProcessingFailed(
                            userScope = context.userScope,
                            agentId = started.agentId.value,
                            messageId = started.messageId.value,
                            requestId = message.requestId,
                        )
                        outboundService.sendOperationFailure(context.session, throwable, message.requestId)
                        if (throwable is CancellationException) {
                            throw throwable
                        }
                    } finally {
                        processingRegistry.clearProcessing(
                            userScope = context.userScope,
                            agentId = started.agentId.value,
                            messageId = started.messageId.value,
                        )
                    }
                }

                processingRegistry.registerProcessing(
                    userScope = context.userScope,
                    agentId = started.agentId.value,
                    messageId = started.messageId.value,
                    job = job,
                )
            }
            .onFailure { throwable -> outboundService.sendOperationFailure(context.session, throwable, message.requestId) }

        return Result.success(Unit)
    }

    private suspend fun advanceTaskPhaseAfterCompletion(
        context: AgentWsMessageContext,
        agentId: String,
        outputText: String,
    ) {
        val task = getActiveTaskUseCase.execute(context.userScope, agentId).getOrNull() ?: return
        if (task.currentPhase == TaskPhase.WAITING_FOR_APPROVAL || task.currentPhase == TaskPhase.DONE || task.currentPhase == TaskPhase.FAILED) return

        val userId = UserId(context.userScope)

        when (task.currentPhase) {
            TaskPhase.PLANNING -> {
                parsePhaseResponseUseCase.parsePlanningResponse(outputText).onSuccess {
                    val updatedTask = task.copy(planJson = outputText)
                    taskRepository.upsertTask(userId, updatedTask).getOrElse {
                        logger.warn("Failed to store plan: {}", it.message)
                    }
                }
            }
            TaskPhase.VALIDATION -> {
                val (nextPhase, stepIndex) = parsePhaseResponseUseCase.parseValidationResponse(outputText)
                    .map { validation ->
                        if (validation.success) TaskPhase.DONE to 3
                        else TaskPhase.FAILED to 3
                    }
                    .getOrElse { TaskPhase.DONE to 3 }
                val updatedTask = task.copy(validationJson = outputText)
                taskRepository.upsertTask(userId, updatedTask).getOrElse {
                    logger.warn("Failed to store validation: {}", it.message)
                }
                transitionTaskPhaseUseCase.execute(
                    userId = context.userScope,
                    taskId = task.id.value,
                    fromPhase = task.currentPhase,
                    targetPhase = nextPhase,
                    reason = "Validation completed",
                    newStepIndex = stepIndex,
                ).onSuccess {
                    broadcastTaskSnapshotAndMaybeTriggerValidation(context, agentId, task, nextPhase)
                }
                return
            }
            else -> {}
        }

        val (nextPhase, stepIndex) = when (task.currentPhase) {
            TaskPhase.PLANNING -> TaskPhase.WAITING_FOR_APPROVAL to 1
            TaskPhase.EXECUTION -> TaskPhase.VALIDATION to 2
            TaskPhase.VALIDATION -> TaskPhase.DONE to 3
            TaskPhase.DONE, TaskPhase.WAITING_FOR_APPROVAL, TaskPhase.FAILED -> return
        }

        transitionTaskPhaseUseCase.execute(
            userId = context.userScope,
            taskId = task.id.value,
            fromPhase = task.currentPhase,
            targetPhase = nextPhase,
            reason = "Assistant message completed",
            newStepIndex = stepIndex,
        ).onSuccess {
            broadcastTaskSnapshotAndMaybeTriggerValidation(context, agentId, task, nextPhase)
        }
    }

    private suspend fun broadcastTaskSnapshotAndMaybeTriggerValidation(
        context: AgentWsMessageContext,
        agentId: String,
        task: AgentTask,
        nextPhase: TaskPhase,
    ) {
        val updatedTask = getActiveTaskUseCase.execute(context.userScope, agentId).getOrNull()
        if (updatedTask != null) {
            outboundService.broadcastTaskStateSnapshot(
                userScope = context.userScope,
                payload = mapper.toTaskStateSnapshot(updatedTask),
            )
        }

        if (task.currentPhase == TaskPhase.EXECUTION) {
            triggerSystemPhaseGeneration(context, agentId, "[Validating results...]")
        }
    }

    suspend fun triggerSystemPhaseGeneration(
        context: AgentWsMessageContext,
        agentId: String,
        systemText: String,
    ) {
        if (processingRegistry.isAgentBusy(context.userScope, agentId)) {
            logger.warn("Cannot trigger system generation: agent is busy userScope={} agentId={}", context.userScope, agentId)
            return
        }

        startAgentMessageUseCase.execute(
            context.userScope,
            SendAgentMessageCommand(agentId = agentId, text = systemText),
        ).onSuccess { started ->
            outboundService.broadcastSnapshot(context.userScope, started.state)

            val job = processingRegistry.launch {
                try {
                    val startedAt = System.currentTimeMillis()
                    val generation = generateTextUseCase.execute(started.generateCommand)
                    val latencyMs = System.currentTimeMillis() - startedAt
                    generation
                        .onSuccess { output ->
                            val currentTask = getActiveTaskUseCase.execute(context.userScope, started.agentId.value).getOrNull()
                            val (displayText, msgType) = formatPhaseOutput(currentTask, output.text)
                            completeAgentMessageUseCase.execute(
                                context.userScope,
                                CompleteAgentMessageCommand(
                                    agentId = started.agentId.value,
                                    messageId = started.messageId.value,
                                    output = output.copy(text = displayText),
                                    latencyMs = latencyMs,
                                    messageType = msgType,
                                )
                            ).onSuccess { state ->
                                outboundService.broadcastSnapshot(context.userScope, state)
                                advanceTaskPhaseAfterCompletion(
                                    context = context,
                                    agentId = started.agentId.value,
                                    outputText = output.text,
                                )
                            }.onFailure { throwable ->
                                markProcessingFailed(context.userScope, started.agentId.value, started.messageId.value, null)
                                logger.warn("System generation complete failed: {}", throwable.message)
                            }
                        }
                        .onFailure { throwable ->
                            markProcessingFailed(context.userScope, started.agentId.value, started.messageId.value, null)
                            logger.warn("System generation failed: {}", throwable.message)
                        }
                } catch (throwable: Throwable) {
                    markProcessingFailed(context.userScope, started.agentId.value, started.messageId.value, null)
                    if (throwable is CancellationException) throw throwable
                    logger.warn("System generation error: {}", throwable.message)
                } finally {
                    processingRegistry.clearProcessing(context.userScope, started.agentId.value, started.messageId.value)
                }
            }

            processingRegistry.registerProcessing(
                userScope = context.userScope,
                agentId = started.agentId.value,
                messageId = started.messageId.value,
                job = job,
            )
        }.onFailure { throwable ->
            logger.warn("Failed to start system generation: {}", throwable.message)
        }
    }

    private suspend fun ensureActiveTaskExists(
        context: AgentWsMessageContext,
        message: SendAgentMessageCommandDto,
    ) {
        val activeTask = getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()
        if (activeTask != null) return

        val defaultSteps = listOf(
            TaskStep(0, TaskPhase.PLANNING, "Plan approach", "Analyze and plan", TaskStepStatus.PENDING),
            TaskStep(1, TaskPhase.EXECUTION, "Execute", "Implement", TaskStepStatus.PENDING),
            TaskStep(2, TaskPhase.VALIDATION, "Validate", "Verify", TaskStepStatus.PENDING),
            TaskStep(3, TaskPhase.DONE, "Complete", "Done", TaskStepStatus.PENDING),
        )
        val title = message.text.trim().take(MAX_TASK_TITLE_LENGTH).ifBlank { "Task" }

        createTaskUseCase.execute(
            userId = context.userScope,
            agentId = message.agentId,
            title = title,
            steps = defaultSteps,
        ).onSuccess { task ->
            outboundService.broadcastTaskStateSnapshot(
                userScope = context.userScope,
                payload = mapper.toTaskStateSnapshot(task),
            )
        }
    }

    private suspend fun markProcessingFailed(
        userScope: String,
        agentId: String,
        messageId: String,
        requestId: String?,
    ) {
        failAgentMessageUseCase.execute(
            userScope,
            FailAgentMessageCommand(
                agentId = agentId,
                messageId = messageId,
            )
        ).onSuccess { state ->
            outboundService.broadcastSnapshot(userScope, state)
        }.onFailure { throwable ->
            logger.warn(
                "Failed to mark processing as failed userScope={} agentId={} messageId={} requestId={} reason={}",
                userScope,
                agentId,
                messageId,
                requestId,
                throwable.message,
            )
        }
    }

    private suspend fun appendUserMessageOnly(
        context: AgentWsMessageContext,
        message: SendAgentMessageCommandDto,
    ) {
        val userId = UserId(context.userScope)
        val agentId = AgentId(message.agentId)
        val userMessage = AgentMessage(
            id = AgentMessageId("info-${System.currentTimeMillis()}"),
            role = AgentMessageRole.USER,
            text = message.text,
            status = STATUS_DONE,
            createdAt = 0L,
        )
        agentRepository.appendMessage(userId, agentId, userMessage)
            .onSuccess { state -> outboundService.broadcastSnapshot(context.userScope, state) }
            .onFailure { throwable ->
                outboundService.sendOperationFailure(context.session, throwable, message.requestId)
            }
    }

    private suspend fun tryHandleStatsShortcutCommand(
        context: AgentWsMessageContext,
        message: SendAgentMessageCommandDto,
    ): Boolean {
        val normalizedText = message.text.trim()
        val token = parseStatsShortcutTokensUseCase.execute(normalizedText).singleOrNull() ?: return false
        if (token.raw != normalizedText) return false

        val resolved = resolveStatsShortcutsUseCase.execute(UserId(context.userScope), listOf(token)).getOrElse { throwable ->
            outboundService.sendOperationFailure(context.session, throwable, message.requestId)
            return true
        }
        val responseText = resolved[token.raw]
            ?.takeIf { it.isNotBlank() }
            ?: NO_AGENT_STATS_MESSAGE

        val userId = UserId(context.userScope)
        val agentId = AgentId(message.agentId)
        val userMessage = AgentMessage(
            id = AgentMessageId("cmd-${createMessageId()}"),
            role = AgentMessageRole.USER,
            text = normalizedText,
            status = STATUS_DONE,
            createdAt = 0L,
        )
        val assistantMessage = AgentMessage(
            id = AgentMessageId("cmd-${createMessageId()}"),
            role = AgentMessageRole.ASSISTANT,
            text = responseText,
            status = STATUS_DONE,
            createdAt = 0L,
        )

        agentRepository.appendMessage(userId, agentId, userMessage).getOrElse { throwable ->
            outboundService.sendOperationFailure(context.session, throwable, message.requestId)
            return true
        }
        agentRepository.appendMessage(userId, agentId, assistantMessage)
            .onSuccess { state -> outboundService.broadcastSnapshot(context.userScope, state) }
            .onFailure { throwable -> outboundService.sendOperationFailure(context.session, throwable, message.requestId) }

        return true
    }

    private suspend fun tryHandleMemoryLayerShortcutCommand(
        context: AgentWsMessageContext,
        message: SendAgentMessageCommandDto,
    ): Boolean {
        val normalizedText = message.text.trim()
        val token = parseMemoryLayerShortcutTokenUseCase.execute(normalizedText) ?: return false

        switchAgentMemoryLayerUseCase.execute(
            userId = context.userScope,
            agentId = message.agentId,
            layer = token.layer,
        ).getOrElse { throwable ->
            outboundService.sendOperationFailure(context.session, throwable, message.requestId)
            return true
        }

        val userId = UserId(context.userScope)
        val agentId = AgentId(message.agentId)
        val userMessage = AgentMessage(
            id = AgentMessageId("cmd-${createMessageId()}"),
            role = AgentMessageRole.USER,
            text = normalizedText,
            status = STATUS_DONE,
            createdAt = 0L,
        )
        val assistantMessage = AgentMessage(
            id = AgentMessageId("cmd-${createMessageId()}"),
            role = AgentMessageRole.ASSISTANT,
            text = memoryLayerSwitchResponse(token.layer),
            status = STATUS_DONE,
            createdAt = 0L,
        )

        agentRepository.appendMessage(userId, agentId, userMessage).getOrElse { throwable ->
            outboundService.sendOperationFailure(context.session, throwable, message.requestId)
            return true
        }
        agentRepository.appendMessage(userId, agentId, assistantMessage)
            .onSuccess { state -> outboundService.broadcastSnapshot(context.userScope, state) }
            .onFailure { throwable -> outboundService.sendOperationFailure(context.session, throwable, message.requestId) }

        return true
    }

    private fun memoryLayerSwitchResponse(layer: MemoryLayerType): String {
        return when (layer) {
            MemoryLayerType.SHORT_TERM -> {
                "MEMORY LAYER SET TO SHORT-TERM ($SHORT_TERM_TOKEN). " +
                    "Responses now prioritize current dialogue turns from recent message history."
            }

            MemoryLayerType.WORKING -> {
                "MEMORY LAYER SET TO WORKING ($WORKING_TOKEN). " +
                    "Responses now prioritize current task data from rolling summaries plus recent dialogue."
            }

            MemoryLayerType.LONG_TERM -> {
                "MEMORY LAYER SET TO LONG-TERM ($LONG_TERM_TOKEN). " +
                    "Responses now prioritize persistent profile/decisions and retrieved knowledge."
            }
        }
    }

    private fun formatPhaseOutput(task: AgentTask?, rawText: String): Pair<String, AgentMessageType?> {
        if (task == null) return rawText to null
        return when (task.currentPhase) {
            TaskPhase.PLANNING -> {
                val display = parsePhaseResponseUseCase.parsePlanningResponse(rawText)
                    .map { parsePhaseResponseUseCase.formatPlanningForDisplay(it) }
                    .getOrDefault(rawText)
                display to AgentMessageType.REVIEW
            }
            TaskPhase.EXECUTION -> {
                val display = parsePhaseResponseUseCase.parseExecutionResponse(rawText)
                    .map { parsePhaseResponseUseCase.formatExecutionForDisplay(it) }
                    .getOrDefault(rawText)
                display to null
            }
            TaskPhase.VALIDATION -> {
                val display = parsePhaseResponseUseCase.parseValidationResponse(rawText)
                    .map { parsePhaseResponseUseCase.formatValidationForDisplay(it) }
                    .getOrDefault(rawText)
                display to null
            }
            else -> rawText to null
        }
    }

    private fun createMessageId(): String {
        val a = Random.nextLong().toULong().toString(16)
        val b = Random.nextLong().toULong().toString(16)
        return "$a$b"
    }
}

private const val STATUS_DONE = "done"
private const val NO_AGENT_STATS_MESSAGE = "No agent stats available."
private const val MAX_TASK_TITLE_LENGTH = 64
