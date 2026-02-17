package io.artemkopan.ai.core.application

import io.artemkopan.ai.core.application.usecase.ResolveGenerationOptionsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolveGenerationOptionsUseCaseTest {
    private val useCase = ResolveGenerationOptionsUseCase(defaultModel = "gemini-2.5-flash")

    @Test
    fun `uses defaults when values missing`() {
        val result = useCase.execute(model = null, temperature = null)
        assertTrue(result.isSuccess)
        val value = result.getOrThrow()
        assertEquals("gemini-2.5-flash", value.modelId.value)
        assertEquals(0.7, value.temperature.value)
    }

    @Test
    fun `fails when temperature out of range`() {
        val result = useCase.execute(model = null, temperature = 1.5)
        assertTrue(result.isFailure)
    }
}
