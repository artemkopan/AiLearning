package io.artemkopan.ai.sharedui.feature.settingscolumn.model

import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedui.core.session.AgentState

data class SettingsColumnUiModel(
    val agent: AgentState? = null,
    val agentConfig: AgentConfigDto? = null,
    val runtimeOutputTokensLabel: String = "n/a",
    val runtimeApiDurationLabel: String = "n/a",
)
