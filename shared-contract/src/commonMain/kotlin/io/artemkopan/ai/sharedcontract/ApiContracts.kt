package io.artemkopan.ai.sharedcontract

import kotlinx.serialization.Serializable

@Serializable
data class GenerateRequestDto(
    val prompt: String,
    val model: String? = null,
    val temperature: Double? = null,
)

@Serializable
data class TokenUsageDto(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

@Serializable
data class GenerateResponseDto(
    val text: String,
    val provider: String,
    val model: String,
    val usage: TokenUsageDto? = null,
    val requestId: String,
    val latencyMs: Long,
)

@Serializable
data class ErrorResponseDto(
    val code: String,
    val message: String,
    val requestId: String,
)
