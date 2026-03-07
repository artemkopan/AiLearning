package io.artemkopan.ai.sharedui.core.session

import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.artemkopan.ai.sharedcontract.SendAgentMessageCommandDto
import io.artemkopan.ai.sharedcontract.UpdateAgentDraftCommandDto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MessageQueueManager(
    private val scope: CoroutineScope,
    private val sessionState: StateFlow<SessionState>,
    private val getState: () -> SessionState,
    private val updateState: ((SessionState) -> SessionState) -> Unit,
    private val sendGateway: suspend (AgentWsClientMessageDto) -> Result<Unit>,
    private val showError: (title: String, message: String) -> Unit,
) {

    private val drainJobsByAgent = mutableMapOf<AgentId, Job>()
    private var queuedMessageCounter: Long = 0
    private var queuedMessageCreatedAtCounter: Long = 0

    fun enqueue(agentId: AgentId, message: QueuedMessageState) {
        updateState { current ->
            val updated = current.queuedByAgent[agentId].orEmpty() + message
            current.copy(queuedByAgent = current.queuedByAgent + (agentId to updated))
        }
        triggerQueueDrain(agentId)
    }

    fun clearQueue(agentId: AgentId) {
        clearQueuedMessages(agentId)
        drainJobsByAgent.remove(agentId)?.cancel()
    }

    fun stopQueue(agentId: AgentId) {
        clearQueuedMessages(agentId)
        drainJobsByAgent.remove(agentId)?.cancel()
    }

    fun triggerDrainForQueuedAgents() {
        getState().queuedByAgent.keys.forEach(::triggerQueueDrain)
    }

    fun cancelDrainsForMissingAgents(existingIds: Set<AgentId>) {
        val missing = drainJobsByAgent.keys.filterNot { existingIds.contains(it) }
        missing.forEach { id ->
            drainJobsByAgent.remove(id)?.cancel()
        }
    }

    fun nextQueuedMessageId(): String {
        queuedMessageCounter += 1
        return "$LOCAL_QUEUE_MESSAGE_ID_PREFIX$queuedMessageCounter"
    }

    fun nextQueuedMessageCreatedAt(): Long {
        queuedMessageCreatedAtCounter += 1
        return queuedMessageCreatedAtCounter
    }

    fun dispose() {
        drainJobsByAgent.values.forEach { it.cancel() }
        drainJobsByAgent.clear()
    }

    private fun triggerQueueDrain(agentId: AgentId) {
        if (getState().queuedByAgent[agentId].isNullOrEmpty()) return
        if (drainJobsByAgent[agentId]?.isActive == true) return

        val job = scope.launch {
            drainQueue(agentId)
        }
        drainJobsByAgent[agentId] = job
        job.invokeOnCompletion {
            drainJobsByAgent.remove(agentId)
        }
    }

    private suspend fun drainQueue(agentId: AgentId) {
        while (currentCoroutineContext().isActive) {
            val queued = getState().queuedByAgent[agentId].orEmpty().firstOrNull() ?: return
            waitUntilAgentReady(agentId) ?: return
            updateQueuedStatus(agentId, queued.id, QueuedMessageStatus.SENDING)
            sendQueuedMessage(agentId, queued)
                .onSuccess {
                    removeQueuedMessage(agentId, queued.id)
                    waitForProcessingCycle(agentId)
                }
                .onFailure { throwable ->
                    removeQueuedMessage(agentId, queued.id)
                    showError(
                        "Request Failed",
                        throwable.message ?: "Failed to send queued message.",
                    )
                }
        }
    }

    private suspend fun waitUntilAgentReady(agentId: AgentId): AgentState? {
        return sessionState
            .map { current ->
                val agent = current.agents[agentId]
                if (current.isConnected && agent != null && !agent.isLoading) {
                    agent
                } else {
                    null
                }
            }
            .distinctUntilChanged()
            .filterNotNull()
            .first()
    }

    private suspend fun waitForProcessingCycle(agentId: AgentId) {
        val enteredProcessing = withTimeoutOrNull(5_000L) {
            sessionState
                .map { current ->
                    val agent = current.agents[agentId]
                    current.isConnected && agent != null && agent.isLoading
                }
                .distinctUntilChanged()
                .first { it }
        } ?: false

        if (enteredProcessing) {
            waitUntilAgentReady(agentId)
        }
    }

    private suspend fun sendQueuedMessage(agentId: AgentId, queued: QueuedMessageState): Result<Unit> {
        val snapshot = queued.draftSnapshot
        val updateDraftResult = sendGateway(
            UpdateAgentDraftCommandDto(
                agentId = agentId.value,
                model = snapshot.model,
                maxOutputTokens = snapshot.maxOutputTokens,
                temperature = snapshot.temperature,
                stopSequences = snapshot.stopSequences,
                agentMode = snapshot.agentMode,
                contextConfig = snapshot.contextConfig,
            )
        )
        if (updateDraftResult.isFailure) return updateDraftResult

        return sendGateway(
            SendAgentMessageCommandDto(
                agentId = agentId.value,
                text = queued.text,
            )
        )
    }

    private fun updateQueuedStatus(agentId: AgentId, queuedId: String, status: QueuedMessageStatus) {
        updateState { current ->
            val queue = current.queuedByAgent[agentId].orEmpty()
            if (queue.isEmpty()) return@updateState current
            val updatedQueue = queue.map { queued ->
                if (queued.id == queuedId) queued.copy(status = status) else queued
            }
            current.copy(queuedByAgent = current.queuedByAgent + (agentId to updatedQueue))
        }
    }

    private fun removeQueuedMessage(agentId: AgentId, queuedId: String) {
        updateState { current ->
            val queue = current.queuedByAgent[agentId].orEmpty()
            if (queue.isEmpty()) return@updateState current
            val updatedQueue = queue.filterNot { queued -> queued.id == queuedId }
            val updatedMap = if (updatedQueue.isEmpty()) {
                current.queuedByAgent - agentId
            } else {
                current.queuedByAgent + (agentId to updatedQueue)
            }
            current.copy(queuedByAgent = updatedMap)
        }
    }

    private fun clearQueuedMessages(agentId: AgentId) {
        updateState { current ->
            if (!current.queuedByAgent.containsKey(agentId)) return@updateState current
            current.copy(queuedByAgent = current.queuedByAgent - agentId)
        }
    }
}

private const val LOCAL_QUEUE_MESSAGE_ID_PREFIX = "local-queue-"
