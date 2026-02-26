package io.artemkopan.ai.core.domain.repository

import io.artemkopan.ai.core.domain.model.LlmGeneration
import io.artemkopan.ai.core.domain.model.LlmGenerationInput
import io.artemkopan.ai.core.domain.model.LlmEmbedding
import io.artemkopan.ai.core.domain.model.LlmModelMetadata

interface LlmRepository {
    suspend fun generate(input: LlmGenerationInput): Result<LlmGeneration>
    suspend fun embed(text: String, model: String): Result<LlmEmbedding>
    suspend fun getModelMetadata(model: String): Result<LlmModelMetadata>
}
