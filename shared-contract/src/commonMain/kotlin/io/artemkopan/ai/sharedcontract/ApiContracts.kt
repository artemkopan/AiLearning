package io.artemkopan.ai.sharedcontract

import kotlinx.serialization.SerialName
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
    val contextWindowTokens: Int,
)

@Serializable
data class ModelMetadataDto(
    val model: String,
    val provider: String,
    val inputTokenLimit: Int,
    val outputTokenLimit: Int,
)

@Serializable
data class AgentConfigDto(
    val models: List<ModelOptionDto>,
    val defaultModel: String,
    val defaultContextWindowTokens: Int = 1_000_000,
    val temperatureMin: Double,
    val temperatureMax: Double,
    val defaultTemperature: Double,
)

@Serializable
data class AgentResponseDto(
    val text: String,
    val provider: String,
    val model: String,
    val usage: TokenUsageDto? = null,
    val latencyMs: Long = 0,
)

@Serializable
enum class AgentMessageRoleDto {
    USER,
    ASSISTANT,
}

@Serializable
enum class AgentMessageTypeDto {
    @SerialName("text") TEXT,
    @SerialName("review") REVIEW,
    @SerialName("execution_confirmation") EXECUTION_CONFIRMATION,
}

@Serializable
data class AgentMessageDto(
    val id: String,
    val role: AgentMessageRoleDto,
    val text: String,
    val status: String,
    val createdAt: Long,
    val provider: String? = null,
    val model: String? = null,
    val usage: TokenUsageDto? = null,
    val latencyMs: Long? = null,
    val messageType: AgentMessageTypeDto = AgentMessageTypeDto.TEXT,
)

@Serializable
data class AgentDto(
    val id: String,
    val title: String,
    val model: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val stopSequences: String = "",
    val status: String,
    val messages: List<AgentMessageDto> = emptyList(),
)
