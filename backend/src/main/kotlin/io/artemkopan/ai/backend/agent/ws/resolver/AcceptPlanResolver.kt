package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.backend.agent.ws.AgentWsProcessingRegistry
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.AcceptPlanCommandDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class AcceptPlanResolver(
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val generateTextUseCase: GenerateTextUseCase,
    private val completeAgentMessageUseCase: CompleteAgentMessageUseCase,
    private val failAgentMessageUseCase: FailAgentMessageUseCase,
    private val setAgentProcessingUseCase: SetAgentProcessingUseCase,
    private val processingRegistry: AgentWsProcessingRegistry,
    private val outboundService: AgentWsOutboundService,
    private val mapper: AgentWsMapper,
    private val json: Json,
) : AgentWsMessageResolver<AcceptPlanCommandDto> {
    override val messageType = AcceptPlanCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: AcceptPlanCommandDto): Result<Unit> {
        val task = getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()
            ?: return Result.failure(IllegalArgumentException("No active task found"))

        val questionForUser = extractQuestionForUser(task.planJson)
        if (questionForUser.isNotBlank()) {
            val fromPhase = transitionTaskPhaseUseCase.execute(
                context.userScope, message.agentId, message.taskId,
                TaskPhase.WaitingForUserInput, "Question pending: $questionForUser",
            ).getOrElse { return Result.failure(it) }
            outboundService.broadcastPhaseChanged(
                context.userScope, message.agentId, message.taskId,
                fromPhase, TaskPhase.WaitingForUserInput, "Question pending",
            )
            getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let {
                outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(it))
            }
            return Result.success(Unit)
        }

        val state = getAgentStateUseCase.execute(context.userScope).getOrElse { return Result.failure(it) }
        val agent = state.agents.find { it.id.value == message.agentId }
            ?: return Result.failure(IllegalArgumentException("Agent not found"))
        val processingMsg = agent.messages.lastOrNull { it.role == AgentMessageRole.ASSISTANT }
            ?: return Result.failure(IllegalArgumentException("No assistant message to update"))

        setAgentProcessingUseCase.execute(context.userScope, message.agentId).onSuccess {
            outboundService.broadcastSnapshot(context.userScope, it)
        }

        val job = processingRegistry.launch {
            runExecutionPipeline(context, message, agent, processingMsg.id.value)
        }
        processingRegistry.registerProcessing(context.userScope, message.agentId, processingMsg.id.value, job)
        job.invokeOnCompletion {
            processingRegistry.launch {
                processingRegistry.clearProcessing(context.userScope, message.agentId, processingMsg.id.value)
            }
        }

        return Result.success(Unit)
    }

    private suspend fun runExecutionPipeline(
        context: AgentWsMessageContext,
        message: AcceptPlanCommandDto,
        agent: io.artemkopan.ai.core.domain.model.Agent,
        processingMsgId: String,
    ) {
        // 1. Transition -> EXECUTION, broadcast, THEN call LLM
        val fromExec = transitionTaskPhaseUseCase.execute(
            context.userScope, message.agentId, message.taskId,
            TaskPhase.Execution, "User approved",
        ).getOrElse { e ->
            failAgentMessageUseCase.execute(context.userScope, message.agentId, processingMsgId, e.message ?: "Transition failed")
            getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            return
        }
        outboundService.broadcastPhaseChanged(
            context.userScope, message.agentId, message.taskId,
            fromExec, TaskPhase.Execution, "User approved",
        )
        getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let {
            outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(it))
        }

        val conversationPrompt = agent.messages.joinToString("\n\n") { "${it.role.name}: ${it.text}" } + "\n\nAssistant:"
        val execCommand = GenerateCommand(
            prompt = conversationPrompt,
            model = agent.model.ifBlank { "deepseek-chat" },
            temperature = agent.temperature.toDoubleOrNull() ?: 0.7,
            maxOutputTokens = agent.maxOutputTokens.toIntOrNull(),
            systemInstruction = EXECUTION_SYSTEM_PROMPT +
                InvariantsPromptBuilder.buildInvariantsBlock(agent.invariants),
        )
        val execResult = generateTextUseCase.execute(execCommand).getOrElse { e ->
            failAgentMessageUseCase.execute(context.userScope, message.agentId, processingMsgId, e.message ?: "Execution failed")
            getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            return
        }

        val execText = execResult.text
        completeAgentMessageUseCase.execute(
            context.userScope,
            message.agentId,
            processingMsgId,
            text = execText,
            provider = execResult.provider,
            model = execResult.model,
            usageInputTokens = execResult.usage?.inputTokens,
            usageOutputTokens = execResult.usage?.outputTokens,
            usageTotalTokens = execResult.usage?.totalTokens,
        ).getOrElse { return }

        // 2. Transition -> VALIDATION, broadcast, THEN call validation LLM
        val fromVal = transitionTaskPhaseUseCase.execute(
            context.userScope, message.agentId, message.taskId,
            TaskPhase.Validation, "Execution complete",
        ).getOrElse { e ->
            getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            return
        }
        outboundService.broadcastPhaseChanged(
            context.userScope, message.agentId, message.taskId,
            fromVal, TaskPhase.Validation, "Execution complete",
        )
        getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let {
            outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(it))
        }

        val validationCommand = GenerateCommand(
            prompt = "Original request context and execution result. Verify the response is accurate and not hallucinated.\n\nExecution result:\n$execText",
            model = agent.model.ifBlank { "deepseek-chat" },
            temperature = 0.3,
            systemInstruction = VALIDATION_SYSTEM_PROMPT +
                InvariantsPromptBuilder.buildInvariantsBlock(agent.invariants),
        )
        val validationResult = generateTextUseCase.execute(validationCommand).getOrElse { e ->
            transitionTaskPhaseUseCase.execute(context.userScope, message.agentId, message.taskId, TaskPhase.Done, "Validation skipped")
            getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let {
                outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(it))
            }
            return
        }

        // 3. Transition -> DONE/FAILED, broadcast
        val validationText = validationResult.text.trim().lowercase()
        val validationPassed = validationText.startsWith("pass") || validationText == "yes"
        val finalPhase = if (validationPassed) TaskPhase.Done else TaskPhase.Failed
        val finalReason = if (validationPassed) "Validation passed" else "Validation failed: ${validationResult.text.take(100)}"

        val fromFinal = transitionTaskPhaseUseCase.execute(
            context.userScope, message.agentId, message.taskId,
            finalPhase, finalReason,
        ).getOrElse { return }

        outboundService.broadcastPhaseChanged(
            context.userScope, message.agentId, message.taskId,
            fromFinal, finalPhase, finalReason,
        )
        getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
        getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let {
            outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(it))
        }
    }

    private fun extractQuestionForUser(planJson: String): String {
        if (planJson.isBlank()) return ""
        return runCatching {
            json.decodeFromString<PlanQuestionDto>(planJson).questionForUser
        }.getOrDefault("")
    }
}

@Serializable
private data class PlanQuestionDto(
    @SerialName("question_for_user")
    val questionForUser: String = "",
)

private const val EXECUTION_SYSTEM_PROMPT = "Execute the approved plan. Provide a clear, helpful response to the user's request."
private const val VALIDATION_SYSTEM_PROMPT = "Review the execution result. Respond ONLY with PASS or FAIL on the first line, followed by a brief reason. Be concise."
