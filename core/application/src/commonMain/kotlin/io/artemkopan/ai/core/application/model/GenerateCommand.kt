package io.artemkopan.ai.core.application.model

data class GenerateCommand(
    val prompt: String,
    val model: String?,
    val temperature: Double?,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
    val agentMode: String? = null,
)

data class GenerateOutput(
    val text: String,
    val provider: String,
    val model: String,
    val usage: UsageOutput?,
)

data class UsageOutput(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)
