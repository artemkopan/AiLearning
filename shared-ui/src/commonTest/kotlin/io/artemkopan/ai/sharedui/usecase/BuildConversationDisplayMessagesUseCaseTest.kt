package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedcontract.RollingSummaryContextConfigDto
import io.artemkopan.ai.sharedui.core.session.AgentMessageState
import io.artemkopan.ai.sharedui.core.session.QueuedDraftSnapshot
import io.artemkopan.ai.sharedui.core.session.QueuedMessageState
import io.artemkopan.ai.sharedui.core.session.QueuedMessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildConversationDisplayMessagesUseCaseTest {
    private val useCase = BuildConversationDisplayMessagesUseCase()

    @Test
    fun `appends queued local messages and marks them`() {
        val serverMessage = AgentMessageState(
            id = "server-1",
            role = AgentMessageRoleDto.ASSISTANT,
            text = "ok",
            status = "done",
            createdAt = 1L,
        )
        val queued = QueuedMessageState(
            id = "local-1",
            text = "queued",
            status = QueuedMessageStatus.QUEUED,
            createdAt = 2L,
            draftSnapshot = QueuedDraftSnapshot(
                model = "",
                maxOutputTokens = "",
                temperature = "",
                stopSequences = "",
                agentMode = AgentMode.DEFAULT,
                contextConfig = RollingSummaryContextConfigDto(),
            ),
        )

        val result = useCase(
            messages = listOf(serverMessage),
            queuedMessages = listOf(queued),
        )

        assertEquals(2, result.size)
        assertEquals("server-1", result[0].id)
        assertEquals("local-1", result[1].id)
        assertTrue(result[1].isQueuedLocal)
        assertEquals("queued", result[1].message.status)
    }
}
