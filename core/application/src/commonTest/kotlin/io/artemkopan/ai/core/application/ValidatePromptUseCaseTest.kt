package io.artemkopan.ai.core.application

import io.artemkopan.ai.core.application.usecase.ValidatePromptUseCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatePromptUseCaseTest {
    private val useCase = ValidatePromptUseCase(maxPromptLength = 10)

    @Test
    fun `returns failure for blank prompt`() {
        val result = useCase.execute("   ")
        assertTrue(result.isFailure)
    }

    @Test
    fun `returns failure for too long prompt`() {
        val result = useCase.execute("12345678901")
        assertTrue(result.isFailure)
    }

    @Test
    fun `returns success for valid prompt`() {
        val result = useCase.execute("hello")
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().value.isBlank())
    }
}
