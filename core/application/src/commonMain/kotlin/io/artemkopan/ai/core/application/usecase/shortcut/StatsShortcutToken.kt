package io.artemkopan.ai.core.application.usecase.shortcut

data class StatsShortcutToken(
    val raw: String,
    val agentId: String,
    val allAgents: Boolean = false,
)
