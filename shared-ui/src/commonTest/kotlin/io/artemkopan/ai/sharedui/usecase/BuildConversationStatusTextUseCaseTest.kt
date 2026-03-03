package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.AgentMessageRoleDto
import io.artemkopan.ai.sharedcontract.AgentMode
import io.artemkopan.ai.sharedcontract.RollingSummaryContextConfigDto
import io.artemkopan.ai.sharedui.core.session.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuildConversationStatusTextUseCaseTest {
    private val useCase = BuildConversationStatusTextUseCase()

    @Test
    fun `returns null when nothing is loading or queued`() {
        val status = useCase(agent = idleAgent(), queuedMessages = emptyList())
        assertNull(status)
    }

    @Test
    fun `returns combined status when loading and queued`() {
        val status = useCase(
            agent = loadingAgent(),
            queuedMessages = listOf(sampleQueuedMessage()),
        )

        assertEquals("processing / queued 1", status)
    }

    private fun idleAgent(): AgentState {
        return AgentState(
            id = AgentId("agent-1"),
            title = "A",
            status = "done",
        )
    }

    private fun loadingAgent(): AgentState {
        return AgentState(
            id = AgentId("agent-1"),
            title = "A",
            status = "processing",
            messages = listOf(
                AgentMessageState(
                    id = "m1",
                    role = AgentMessageRoleDto.ASSISTANT,
                    text = "...",
                    status = "processing",
                    createdAt = 1L,
                )
            ),
        )
    }

    private fun sampleQueuedMessage(): QueuedMessageState {
        return QueuedMessageState(
            id = "q1",
            text = "queued",
            status = QueuedMessageStatus.QUEUED,
            createdAt = 1L,
            draftSnapshot = QueuedDraftSnapshot(
                model = "",
                maxOutputTokens = "",
                temperature = "",
                stopSequences = "",
                agentMode = AgentMode.DEFAULT,
                contextConfig = RollingSummaryContextConfigDto(),
            ),
        )
    }
}
