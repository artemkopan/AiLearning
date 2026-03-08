package io.artemkopan.ai.core.application.model

import io.artemkopan.ai.core.domain.model.LlmResponseFormat

data class GenerateCommand(
    val prompt: String,
    val model: String?,
    val temperature: Double?,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
    val agentMode: String? = null,
    val responseFormat: LlmResponseFormat = LlmResponseFormat.TEXT,
    /** When set, overrides agent mode for system instruction (e.g. task phase-specific prompts). */
    val systemInstructionOverride: String? = null,
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
