package io.artemkopan.ai.core.application

import io.artemkopan.ai.core.application.model.AssistantMemoryModel
import io.artemkopan.ai.core.application.model.LongTermMemoryLayer
import io.artemkopan.ai.core.application.model.ShortTermMemoryLayer
import io.artemkopan.ai.core.application.model.WorkingMemoryLayer
import io.artemkopan.ai.core.application.usecase.BuildContextPromptUseCase
import io.artemkopan.ai.core.domain.model.AgentMessage
import io.artemkopan.ai.core.domain.model.AgentMessageId
import io.artemkopan.ai.core.domain.model.AgentMessageRole
import io.artemkopan.ai.core.domain.model.RetrievedContextChunk
import kotlin.test.Test
import kotlin.test.assertTrue

class BuildContextPromptUseCaseMemoryModelTest {
    private val useCase = BuildContextPromptUseCase()

    @Test
    fun `builds prompt with explicit memory layers`() {
        val prompt = useCase.execute(
            AssistantMemoryModel(
                shortTerm = ShortTermMemoryLayer(
                    dialogueTurns = listOf(
                        AgentMessage(
                            id = AgentMessageId("m1"),
                            role = AgentMessageRole.USER,
                            text = "Current task",
                            status = "done",
                            createdAt = 1L,
                        )
                    )
                ),
                working = WorkingMemoryLayer(
                    taskDataSummary = "Task plan summary",
                ),
                longTerm = LongTermMemoryLayer(
                    profileAndDecisions = "{\"decision\":\"prefer concise output\"}",
                    retrievedKnowledge = listOf(
                        RetrievedContextChunk(
                            messageId = AgentMessageId("m2"),
                            text = "Past decision context",
                            score = 0.9,
                            createdAt = 2L,
                        )
                    ),
                ),
            )
        )

        assertTrue(prompt.contains("WORKING MEMORY (CURRENT TASK DATA):"))
        assertTrue(prompt.contains("LONG-TERM MEMORY (PROFILE / DECISIONS):"))
        assertTrue(prompt.contains("LONG-TERM MEMORY (RETRIEVED KNOWLEDGE):"))
        assertTrue(prompt.contains("SHORT-TERM MEMORY (CURRENT DIALOGUE):"))
        assertTrue(prompt.contains("Current task"))
    }
}
