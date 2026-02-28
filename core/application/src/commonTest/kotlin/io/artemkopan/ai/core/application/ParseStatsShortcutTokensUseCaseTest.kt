package io.artemkopan.ai.core.application

import io.artemkopan.ai.core.application.usecase.shortcut.ParseStatsShortcutTokensUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseStatsShortcutTokensUseCaseTest {
    private val useCase = ParseStatsShortcutTokensUseCase()

    @Test
    fun `extracts agent stats shortcuts`() {
        val result = useCase.execute("Compare /agent-agent-1-stats and /agent-agent-2-stats")

        assertEquals(2, result.size)
        assertEquals("/agent-agent-1-stats", result[0].raw)
        assertEquals("agent-1", result[0].agentId)
        assertEquals("/agent-agent-2-stats", result[1].raw)
        assertEquals("agent-2", result[1].agentId)
    }

    @Test
    fun `deduplicates repeated shortcuts`() {
        val result = useCase.execute("/agent-agent-1-stats vs /agent-agent-1-stats")

        assertEquals(1, result.size)
        assertEquals("agent-1", result[0].agentId)
    }
}
