package io.artemkopan.ai.core.data.client

data class NetworkGenerateRequest(
    val prompt: String,
    val model: String,
    val temperature: Double,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
    val systemInstruction: String? = null,
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

data class NetworkEmbedRequest(
    val text: String,
    val model: String,
)

data class NetworkEmbedResponse(
    val values: List<Double>,
    val provider: String,
    val model: String,
)
