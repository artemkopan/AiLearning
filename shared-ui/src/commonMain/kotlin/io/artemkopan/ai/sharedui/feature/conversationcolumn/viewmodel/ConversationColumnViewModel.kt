package io.artemkopan.ai.sharedui.feature.conversationcolumn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.artemkopan.ai.sharedui.core.session.AgentId
import io.artemkopan.ai.sharedui.core.session.AgentSessionStore
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.ConversationColumnUiModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ConversationColumnViewModel(
    private val agentId: AgentId,
    private val sessionStore: AgentSessionStore,
) : ViewModel() {

    val state: StateFlow<ConversationColumnUiModel> = combine(
        sessionStore.observeAgent(agentId),
        sessionStore.sessionState,
    ) { slice, session ->
        ConversationColumnUiModel(
            agent = slice?.agent,
            allAgents = session.agentOrder.mapNotNull { id -> session.agents[id] },
            queuedMessages = slice?.queuedMessages.orEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ConversationColumnUiModel(),
    )

    fun onMessageInputChanged(value: String) {
        sessionStore.updateDraftMessage(agentId, value)
    }

    fun onSubmit() {
        sessionStore.submitMessage(agentId)
    }

    fun onStopQueue() {
        sessionStore.stopQueue(agentId)
    }

    fun onCreateBranch(checkpointMessageId: String, fallbackMessageId: String) {
        sessionStore.createBranch(
            agentId = agentId,
            checkpointMessageId = checkpointMessageId,
            name = "branch-${fallbackMessageId.takeLast(6)}",
        )
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
