package io.artemkopan.ai.backend.agent.ws.resolver

import io.artemkopan.ai.backend.agent.ws.AgentWsMapper
import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.core.application.model.GenerateCommand
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.TaskPhase
import io.artemkopan.ai.sharedcontract.RejectPlanCommandDto
import org.koin.core.annotation.Factory

@Factory(binds = [AgentWsMessageResolver::class])
class RejectPlanResolver(
    private val transitionTaskPhaseUseCase: TransitionTaskPhaseUseCase,
    private val getActiveTaskUseCase: GetActiveTaskUseCase,
    private val getAgentStateUseCase: GetAgentStateUseCase,
    private val generateTextUseCase: GenerateTextUseCase,
    private val completeAgentMessageUseCase: CompleteAgentMessageUseCase,
    private val failAgentMessageUseCase: FailAgentMessageUseCase,
    private val outboundService: AgentWsOutboundService,
    private val mapper: AgentWsMapper,
) : AgentWsMessageResolver<RejectPlanCommandDto> {
    override val messageType = RejectPlanCommandDto::class

    override suspend fun execute(context: AgentWsMessageContext, message: RejectPlanCommandDto): Result<Unit> {
        val task = getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()
            ?: return Result.failure(IllegalArgumentException("No active task found"))

        val state = getAgentStateUseCase.execute(context.userScope).getOrElse { return Result.failure(it) }
        val agent = state.agents.find { it.id.value == message.agentId }
            ?: return Result.failure(IllegalArgumentException("Agent not found"))
        val processingMsg = agent.messages.lastOrNull { it.role == AgentMessageRole.ASSISTANT }
            ?: return Result.failure(IllegalArgumentException("No assistant message to update"))

        transitionTaskPhaseUseCase.execute(
            context.userScope, message.agentId, message.taskId, TaskPhase.Failed,
            "User rejected: ${message.reason.take(100)}",
        ).getOrElse { return Result.failure(it) }

        val rejectionContext = if (message.reason.isNotBlank()) {
            "The user rejected the previous plan. Reason: ${message.reason}\n\n"
        } else {
            "The user rejected the previous plan.\n\n"
        }

        val conversationPrompt = agent.messages.joinToString("\n\n") { "${it.role.name}: ${it.text}" } +
            "\n\nSystem: $rejectionContext" +
            "Please revise your plan based on the user's feedback.\n\nAssistant:"

        val command = GenerateCommand(
            prompt = conversationPrompt,
            model = agent.model.ifBlank { "deepseek-chat" },
            temperature = agent.temperature.toDoubleOrNull() ?: 0.7,
            maxOutputTokens = agent.maxOutputTokens.toIntOrNull(),
            systemInstruction = REVISION_SYSTEM_PROMPT +
                InvariantsPromptBuilder.buildInvariantsBlock(agent.invariants),
        )

        val result = generateTextUseCase.execute(command).getOrElse { e ->
            failAgentMessageUseCase.execute(context.userScope, message.agentId, processingMsg.id.value, e.message ?: "Revision failed")
            getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
            return Result.success(Unit)
        }

        completeAgentMessageUseCase.execute(
            context.userScope,
            message.agentId,
            processingMsg.id.value,
            text = result.text,
            provider = result.provider,
            model = result.model,
            usageInputTokens = result.usage?.inputTokens,
            usageOutputTokens = result.usage?.outputTokens,
            usageTotalTokens = result.usage?.totalTokens,
        ).getOrElse { return Result.failure(it) }

        getAgentStateUseCase.execute(context.userScope).onSuccess { outboundService.broadcastSnapshot(context.userScope, it) }
        getActiveTaskUseCase.execute(context.userScope, message.agentId).getOrNull()?.let {
            outboundService.broadcastTaskStateSnapshot(context.userScope, mapper.toTaskStateSnapshot(it))
        }
        return Result.success(Unit)
    }
}

private const val REVISION_SYSTEM_PROMPT =
    """You are a helpful assistant. The user rejected your previous plan. Revise your approach based on their feedback. Respond with a new plan as a JSON object: {"stage": "planning", "goal": "brief goal", "plan": ["step1", "step2", ...], "requires_user_confirmation": true, "question_for_user": "optional question"}"""
