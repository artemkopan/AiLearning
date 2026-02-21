package io.artemkopan.ai.sharedcontract

import kotlinx.serialization.Serializable

@Serializable
data class GenerateRequestDto(
    val prompt: String,
    val model: String? = null,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
    val agentMode: AgentMode? = null,
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

@Serializable
data class ModelOptionDto(
    val id: String,
    val name: String,
    val provider: String,
)

@Serializable
data class ChatConfigDto(
    val models: List<ModelOptionDto>,
    val defaultModel: String,
    val temperatureMin: Double,
    val temperatureMax: Double,
    val defaultTemperature: Double,
)
