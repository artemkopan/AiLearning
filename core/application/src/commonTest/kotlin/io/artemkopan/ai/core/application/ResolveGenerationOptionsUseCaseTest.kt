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
        assertEquals(null, value.maxOutputTokens)
        assertEquals(null, value.stopSequences)
    }

    @Test
    fun `fails when temperature out of range`() {
        val result = useCase.execute(model = null, temperature = 1.5)
        assertTrue(result.isFailure)
    }

    @Test
    fun `accepts valid maxOutputTokens`() {
        val result = useCase.execute(model = null, temperature = null, maxOutputTokens = 100)
        assertTrue(result.isSuccess)
        assertEquals(100, result.getOrThrow().maxOutputTokens?.value)
    }

    @Test
    fun `fails when maxOutputTokens is zero`() {
        val result = useCase.execute(model = null, temperature = null, maxOutputTokens = 0)
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when maxOutputTokens is negative`() {
        val result = useCase.execute(model = null, temperature = null, maxOutputTokens = -1)
        assertTrue(result.isFailure)
    }

    @Test
    fun `accepts valid stopSequences`() {
        val result = useCase.execute(model = null, temperature = null, stopSequences = listOf("END", "STOP"))
        assertTrue(result.isSuccess)
        assertEquals(listOf("END", "STOP"), result.getOrThrow().stopSequences?.values)
    }

    @Test
    fun `filters blank stopSequences`() {
        val result = useCase.execute(model = null, temperature = null, stopSequences = listOf("  ", "", "END"))
        assertTrue(result.isSuccess)
        assertEquals(listOf("END"), result.getOrThrow().stopSequences?.values)
    }

    @Test
    fun `returns null stopSequences when all blank`() {
        val result = useCase.execute(model = null, temperature = null, stopSequences = listOf("  ", ""))
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrThrow().stopSequences)
    }
}
