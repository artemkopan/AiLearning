package io.artemkopan.ai.core.data.client

data class NetworkGenerateRequest(
    val prompt: String,
    val model: String,
    val temperature: Double,
)

data class NetworkTokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

data class NetworkGenerateResponse(
    val text: String,
    val provider: String,
    val model: String,
    val usage: NetworkTokenUsage?,
)
