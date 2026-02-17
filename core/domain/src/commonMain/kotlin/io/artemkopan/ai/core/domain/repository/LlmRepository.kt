package io.artemkopan.ai.core.domain.repository

import io.artemkopan.ai.core.domain.model.LlmGeneration
import io.artemkopan.ai.core.domain.model.LlmGenerationInput

interface LlmRepository {
    suspend fun generate(input: LlmGenerationInput): Result<LlmGeneration>
}
