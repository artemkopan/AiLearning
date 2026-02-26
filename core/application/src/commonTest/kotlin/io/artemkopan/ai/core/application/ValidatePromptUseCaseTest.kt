package io.artemkopan.ai.core.application

import io.artemkopan.ai.core.application.usecase.ValidatePromptUseCase
import io.artemkopan.ai.core.application.usecase.EstimatePromptTokensUseCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatePromptUseCaseTest {
    private val useCase = ValidatePromptUseCase(estimatePromptTokensUseCase = EstimatePromptTokensUseCase())

    @Test
    fun `returns failure for blank prompt`() {
        val result = useCase.execute("   ", inputTokenLimit = 10)
        assertTrue(result.isFailure)
    }

    @Test
    fun `returns failure when estimated tokens exceed model limit`() {
        val result = useCase.execute("123456789", inputTokenLimit = 2)
        assertTrue(result.isFailure)
    }

    @Test
    fun `returns success for valid prompt`() {
        val result = useCase.execute("hello", inputTokenLimit = 2)
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().value.isBlank())
    }
}
