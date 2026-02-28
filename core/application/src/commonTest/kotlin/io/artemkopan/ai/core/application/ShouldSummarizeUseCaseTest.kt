package io.artemkopan.ai.core.application

import io.artemkopan.ai.core.application.usecase.context.ShouldSummarizeUseCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldSummarizeUseCaseTest {
    private val useCase = ShouldSummarizeUseCase()

    @Test
    fun `returns true when threshold reached`() {
        assertTrue(useCase.execute(messagesToCompressCount = 10, summarizeEveryK = 10))
    }

    @Test
    fun `returns false when below threshold`() {
        assertFalse(useCase.execute(messagesToCompressCount = 9, summarizeEveryK = 10))
    }

    @Test
    fun `returns false for non-positive threshold`() {
        assertFalse(useCase.execute(messagesToCompressCount = 10, summarizeEveryK = 0))
    }
}
