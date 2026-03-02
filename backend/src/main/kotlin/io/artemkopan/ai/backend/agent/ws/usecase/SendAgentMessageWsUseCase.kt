package io.artemkopan.ai.backend.agent.ws.usecase

import io.artemkopan.ai.backend.agent.ws.AgentWsOutboundService
import io.artemkopan.ai.backend.agent.ws.AgentWsProcessingRegistry
import io.artemkopan.ai.core.application.model.SendAgentMessageCommand
import io.artemkopan.ai.core.application.usecase.*
import io.artemkopan.ai.core.application.usecase.shortcut.ParseStatsShortcutTokensUseCase
import io.artemkopan.ai.core.application.usecase.shortcut.ResolveStatsShortcutsUseCase
import io.artemkopan.ai.core.domain.model.*
import io.artemkopan.ai.core.domain.repository.AgentRepository
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
    private val resolveStatsShortcutsUseCase: ResolveStatsShortcutsUseCase,
    private val agentRepository: AgentRepository,
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

        if (message.skipGeneration) {
            appendUserMessageOnly(context, message)
            return Result.success(Unit)
        }

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
                                completeAgentMessageUseCase.execute(
                                    context.userScope,
                                    CompleteAgentMessageCommand(
                                        agentId = started.agentId.value,
                                        messageId = started.messageId.value,
                                        output = output,
                                        latencyMs = latencyMs,
                                    )
                                )
                                    .onSuccess { state -> outboundService.broadcastSnapshot(context.userScope, state) }
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

    private fun createMessageId(): String {
        val a = Random.nextLong().toULong().toString(16)
        val b = Random.nextLong().toULong().toString(16)
        return "$a$b"
    }
}

private const val STATUS_DONE = "done"
private const val NO_AGENT_STATS_MESSAGE = "No agent stats available."
