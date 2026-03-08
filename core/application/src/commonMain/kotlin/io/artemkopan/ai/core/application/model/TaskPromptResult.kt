package io.artemkopan.ai.core.application.model

import io.artemkopan.ai.core.domain.model.LlmResponseFormat

data class TaskPromptResult(
    val taskStateSnippet: String,
    val phaseSystemInstruction: String?,
    val responseFormat: LlmResponseFormat,
)
