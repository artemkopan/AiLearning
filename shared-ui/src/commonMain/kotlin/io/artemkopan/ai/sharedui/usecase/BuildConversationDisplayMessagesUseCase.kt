package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedui.core.session.AgentMessageState
import io.artemkopan.ai.sharedui.core.session.QueuedMessageState
import io.artemkopan.ai.sharedui.core.session.QueuedMessageStatus
import io.artemkopan.ai.sharedui.feature.conversationcolumn.model.ConversationDisplayMessage
import org.koin.core.annotation.Factory

@Factory
class BuildConversationDisplayMessagesUseCase {
    operator fun invoke(
        messages: List<AgentMessageState>,
        queuedMessages: List<QueuedMessageState>,
    ): List<ConversationDisplayMessage> {
        val queuedIds = queuedMessages.map { it.id }.toSet()
        val queuedDisplay = queuedMessages.map { queued ->
            AgentMessageState(
                id = queued.id,
                role = AgentMessageRoleDto.USER,
                text = queued.text,
                status = when (queued.status) {
                    QueuedMessageStatus.QUEUED -> "queued"
                    QueuedMessageStatus.SENDING -> "sending"
                },
                createdAt = queued.createdAt,
            )
        }

        return (messages + queuedDisplay).map { message ->
            ConversationDisplayMessage(
                id = message.id,
                message = message,
                isQueuedLocal = queuedIds.contains(message.id),
            )
        }
    }
}
