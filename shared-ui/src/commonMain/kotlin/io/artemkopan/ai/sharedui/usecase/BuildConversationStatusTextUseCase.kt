package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.core.session.AgentState
import io.artemkopan.ai.sharedui.core.session.QueuedMessageState
import org.koin.core.annotation.Factory

@Factory
class BuildConversationStatusTextUseCase {
    operator fun invoke(agent: AgentState, queuedMessages: List<QueuedMessageState>): String? {
        if (!agent.isLoading && queuedMessages.isEmpty()) return null
        return when {
            agent.isLoading && queuedMessages.isNotEmpty() -> "${agent.status} / queued ${queuedMessages.size}"
            agent.isLoading -> agent.status
            else -> "queued ${queuedMessages.size}"
        }
    }
}
