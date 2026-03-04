package io.artemkopan.ai.core.application

import io.artemkopan.ai.core.application.usecase.shortcut.MemoryLayerType
import io.artemkopan.ai.core.application.usecase.shortcut.ParseMemoryLayerShortcutTokenUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseMemoryLayerShortcutTokenUseCaseTest {
    private val useCase = ParseMemoryLayerShortcutTokenUseCase()

    @Test
    fun `parses supported memory tokens`() {
        assertEquals(
            MemoryLayerType.SHORT_TERM,
            useCase.execute("/memory-short-term")?.layer,
        )
        assertEquals(
            MemoryLayerType.WORKING,
            useCase.execute(" /memory-working ")?.layer,
        )
        assertEquals(
            MemoryLayerType.LONG_TERM,
            useCase.execute("/memory-long-term")?.layer,
        )
    }

    @Test
    fun `returns null for unsupported token`() {
        assertNull(useCase.execute("/memory-unknown"))
    }
}
