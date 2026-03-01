package io.artemkopan.ai.sharedui.feature.settingscolumn.model

import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedui.core.session.AgentState

data class SettingsColumnUiModel(
    val agent: AgentState? = null,
    val agentConfig: AgentConfigDto? = null,
    val contextTotalTokensLabel: String = "n/a",
    val contextLeftLabel: String = "n/a",
)
