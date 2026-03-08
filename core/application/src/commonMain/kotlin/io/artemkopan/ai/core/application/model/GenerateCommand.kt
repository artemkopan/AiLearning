package io.artemkopan.ai.core.application.model

import io.artemkopan.ai.core.domain.model.LlmResponseFormat

data class GenerateCommand(
    val prompt: String,
    val model: String,
    val temperature: Double = 0.7,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
    val systemInstruction: String? = null,
    val responseFormat: LlmResponseFormat = LlmResponseFormat.TEXT,
)
