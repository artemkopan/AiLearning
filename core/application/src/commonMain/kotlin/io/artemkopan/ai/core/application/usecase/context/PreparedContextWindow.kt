package io.artemkopan.ai.core.application.usecase.context

import io.artemkopan.ai.core.domain.model.AgentMessage

data class PreparedContextWindow(
    val summaryText: String,
    val recentMessages: List<AgentMessage>,
)
