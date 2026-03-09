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
class UpdateModelActionUseCase(
    private val controller: AgentSessionController,
    private val normalizeModelUseCase: NormalizeModelUseCase,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        val normalized = normalizeModelUseCase(value, controller.getState().agentConfig)
        controller.updateAgent(agentId) { it.copy(model = normalized) }
    }
}

@Factory
class UpdateMaxOutputTokensActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        controller.updateAgent(agentId) { it.copy(maxOutputTokens = value.filter { ch -> ch.isDigit() }) }
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
    }
}

@Factory
class UpdateStopSequencesActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, value: String) {
        controller.updateAgent(agentId) { it.copy(stopSequences = value) }
    }
}

@Factory
class UpdateInvariantsActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, invariants: List<String>) {
        controller.updateAgent(agentId) { it.copy(invariants = invariants) }
        controller.sendCommand {
            UpdateAgentInvariantsCommandDto(
                agentId = agentId.value,
                invariants = invariants,
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

@Factory
class AcceptPlanActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId) {
        val task = controller.getState().taskByAgent[agentId] ?: return
        controller.sendCommand {
            AcceptPlanCommandDto(agentId = agentId.value, taskId = task.id)
        }
    }
}

@Factory
class RejectPlanActionUseCase(
    private val controller: AgentSessionController,
) {
    operator fun invoke(agentId: AgentId, reason: String = "") {
        val task = controller.getState().taskByAgent[agentId] ?: return
        controller.sendCommand {
            RejectPlanCommandDto(agentId = agentId.value, taskId = task.id, reason = reason)
        }
    }
}

