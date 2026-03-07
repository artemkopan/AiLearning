package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.*
import io.artemkopan.ai.sharedui.core.session.*
import org.koin.core.annotation.Factory

@Factory
class CreateAgentActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke() {
        controller.sendCommand { CreateAgentCommandDto() }
    }
}

@Factory
class SelectAgentActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId) {
        controller.sendCommand { SelectAgentCommandDto(agentId = agentId.value) }
    }
}

@Factory
class CloseAgentActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId) {
        controller.clearQueue(agentId)
        controller.sendCommand { CloseAgentCommandDto(agentId = agentId.value) }
    }
}

@Factory
class SubmitFromActiveAgentActionUseCase(
    private val submitMessageActionUseCase: SubmitMessageActionUseCase,
    private val controller: AgentSessionController,
) {
    operator fun invoke() {
        controller.getState().activeAgentId?.let { submitMessageActionUseCase(it) }
    }
}

@Factory
class SelectNextAgentActionUseCase(
    private val controller: AgentSessionController,
    private val selectAgentActionUseCase: SelectAgentActionUseCase,
) {
    operator fun invoke() {
        val current = controller.getState()
        nextAgentId(current.agentOrder, current.activeAgentId)?.let { selectAgentActionUseCase(it) }
    }
}

@Factory
class SelectPreviousAgentActionUseCase(
    private val controller: AgentSessionController,
    private val selectAgentActionUseCase: SelectAgentActionUseCase,
) {
    operator fun invoke() {
        val current = controller.getState()
        previousAgentId(current.agentOrder, current.activeAgentId)?.let { selectAgentActionUseCase(it) }
    }
}

@Factory
class DismissErrorActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke() {
        controller.updateState { it.copy(errorDialog = null) }
    }
}

@Factory
class UpdateDraftMessageActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        controller.updateAgent(agentId) { it.copy(draftMessage = value) }
    }
}

@Factory
class SubmitMessageActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId) {
        val agent = controller.getState().agents[agentId] ?: return
        val text = agent.draftMessage.trim()
        if (text.isBlank()) {
            controller.showError(
                title = "Validation Error",
                message = "Message must not be blank.",
            )
            return
        }

        val queuedMessage = QueuedMessageState(
            id = controller.nextQueuedMessageId(),
            text = text,
            status = QueuedMessageStatus.QUEUED,
            createdAt = controller.nextQueuedMessageCreatedAt(),
            draftSnapshot = QueuedDraftSnapshot(
                model = agent.model,
                maxOutputTokens = agent.maxOutputTokens,
                temperature = agent.temperature,
                stopSequences = agent.stopSequences,
                agentMode = agent.agentMode,
                contextConfig = agent.contextConfig,
            ),
        )

        controller.updateAgent(agentId) { it.copy(draftMessage = "") }
        controller.enqueueMessage(agentId, queuedMessage)
    }
}

@Factory
class StopQueueActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId) {
        controller.stopQueue(agentId)

        val processingMessage = controller.getState()
            .agents[agentId]
            ?.messages
            ?.firstOrNull {
                it.role == AgentMessageRoleDto.ASSISTANT &&
                    it.status.equals(STATUS_PROCESSING, ignoreCase = true)
            } ?: return

        controller.sendCommand {
            StopAgentMessageCommandDto(
                agentId = agentId.value,
                messageId = processingMessage.id,
            )
        }
    }
}

@Factory
class CreateBranchActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, checkpointMessageId: String, name: String) {
        controller.sendCommand {
            CreateBranchCommandDto(
                agentId = agentId.value,
                checkpointMessageId = checkpointMessageId,
                branchName = name,
            )
        }
    }
}

@Factory
class UpdateModelActionUseCase(
    private val controller: AgentSessionController,
    private val normalizeModelUseCase: NormalizeModelUseCase,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        val normalized = normalizeModelUseCase(value, controller.getState().agentConfig)
        controller.updateAgent(agentId) { it.copy(model = normalized) }
        controller.sendDraftUpdate(agentId)
    }
}

@Factory
class UpdateMaxOutputTokensActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        controller.updateAgent(agentId) { it.copy(maxOutputTokens = value.filter { ch -> ch.isDigit() }) }
        controller.sendDraftUpdate(agentId)
    }
}

@Factory
class UpdateTemperatureActionUseCase(
    private val controller: AgentSessionController,
    private val filterTemperatureInputUseCase: FilterTemperatureInputUseCase,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        val filtered = filterTemperatureInputUseCase(value)
        controller.updateAgent(agentId) { it.copy(temperature = filtered) }
        controller.sendDraftUpdate(agentId)
    }
}

@Factory
class UpdateStopSequencesActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        controller.updateAgent(agentId) { it.copy(stopSequences = value) }
        controller.sendDraftUpdate(agentId)
    }
}

@Factory
class UpdateAgentModeActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: AgentMode) {
        controller.updateAgent(agentId) { it.copy(agentMode = value) }
        controller.sendDraftUpdate(agentId)
    }
}

@Factory
class UpdateContextStrategyActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        controller.updateAgent(agentId) { current ->
            if (current.contextConfig.locked) return@updateAgent current
            when (value) {
                STRATEGY_FULL_HISTORY -> current.copy(
                    contextConfig = FullHistoryContextConfigDto(locked = false)
                )

                STRATEGY_SLIDING_WINDOW -> current.copy(
                    contextConfig = SlidingWindowContextConfigDto(
                        windowSize = (current.contextConfig as? SlidingWindowContextConfigDto)?.windowSize
                            ?: DEFAULT_SLIDING_WINDOW_SIZE,
                        locked = false,
                    )
                )

                STRATEGY_STICKY_FACTS -> current.copy(
                    contextConfig = StickyFactsContextConfigDto(
                        recentMessagesN = (current.contextConfig as? StickyFactsContextConfigDto)?.recentMessagesN ?: 12,
                        locked = false,
                    )
                )

                STRATEGY_BRANCHING -> current.copy(
                    contextConfig = BranchingContextConfigDto(
                        recentMessagesN = (current.contextConfig as? BranchingContextConfigDto)?.recentMessagesN ?: 12,
                        locked = false,
                    )
                )

                else -> current.copy(
                    contextConfig = RollingSummaryContextConfigDto(
                        recentMessagesN = (current.contextConfig as? RollingSummaryContextConfigDto)?.recentMessagesN ?: 12,
                        summarizeEveryK = (current.contextConfig as? RollingSummaryContextConfigDto)?.summarizeEveryK ?: 10,
                        locked = false,
                    )
                )
            }
        }
        controller.sendDraftUpdate(agentId)
    }
}

@Factory
class UpdateContextRecentMessagesActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        controller.updateAgent(agentId) { current ->
            if (current.contextConfig.locked) return@updateAgent current
            val updated = when (val config = current.contextConfig) {
                is RollingSummaryContextConfigDto -> config.copy(recentMessagesN = parsed)
                is StickyFactsContextConfigDto -> config.copy(recentMessagesN = parsed)
                is BranchingContextConfigDto -> config.copy(recentMessagesN = parsed)
                else -> return@updateAgent current
            }
            current.copy(contextConfig = updated)
        }
        controller.sendDraftUpdate(agentId)
    }
}

@Factory
class UpdateContextSummarizeEveryActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        controller.updateAgent(agentId) { current ->
            val config = current.contextConfig as? RollingSummaryContextConfigDto ?: return@updateAgent current
            if (config.locked) return@updateAgent current
            current.copy(contextConfig = config.copy(summarizeEveryK = parsed))
        }
        controller.sendDraftUpdate(agentId)
    }
}

@Factory
class UpdateContextWindowSizeActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        controller.updateAgent(agentId) { current ->
            val config = current.contextConfig as? SlidingWindowContextConfigDto ?: return@updateAgent current
            if (config.locked) return@updateAgent current
            current.copy(contextConfig = config.copy(windowSize = parsed))
        }
        controller.sendDraftUpdate(agentId)
    }
}

@Factory
class SwitchBranchActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, branchId: String) {
        controller.sendCommand {
            SwitchBranchCommandDto(
                agentId = agentId.value,
                branchId = branchId,
            )
        }
    }
}

@Factory
class UpdateUserProfileActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(
        communicationStyle: String,
        responseFormat: String,
        restrictions: List<String>,
        customInstructions: String,
    ) {
        controller.sendCommand {
            UpdateUserProfileCommandDto(
                communicationStyle = communicationStyle,
                responseFormat = responseFormat,
                restrictions = restrictions,
                customInstructions = customInstructions,
            )
        }
    }
}

@Factory
class DeleteBranchActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, branchId: String) {
        controller.sendCommand {
            DeleteBranchCommandDto(
                agentId = agentId.value,
                branchId = branchId,
            )
        }
    }
}

private fun nextAgentId(order: List<AgentId>, activeAgentId: AgentId?): AgentId? {
    if (order.isEmpty()) return null
    val currentIndex = order.indexOf(activeAgentId).takeIf { it >= 0 } ?: 0
    return order[(currentIndex + 1) % order.size]
}

private fun previousAgentId(order: List<AgentId>, activeAgentId: AgentId?): AgentId? {
    if (order.isEmpty()) return null
    val currentIndex = order.indexOf(activeAgentId).takeIf { it >= 0 } ?: 0
    return order[(currentIndex - 1 + order.size) % order.size]
}

private const val STRATEGY_FULL_HISTORY = "full_history"
private const val STRATEGY_SLIDING_WINDOW = "sliding_window"
private const val STRATEGY_STICKY_FACTS = "sticky_facts"
private const val STRATEGY_BRANCHING = "branching"
