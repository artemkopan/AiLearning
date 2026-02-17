package io.artemkopan.ai.core.domain.model

data class Prompt(val value: String)

data class ModelId(val value: String)

data class Temperature(val value: Double)

data class LlmGenerationInput(
    val prompt: Prompt,
    val modelId: ModelId,
    val temperature: Temperature,
)

data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

data class LlmGeneration(
    val text: String,
    val provider: String,
    val model: String,
    val usage: TokenUsage?,
)
