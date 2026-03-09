package io.artemkopan.ai.sharedui.feature.configpanel.model

import io.artemkopan.ai.sharedcontract.ModelOptionDto

data class ConfigPanelUiModel(
    val model: String = "",
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val stopSequences: String = "",
    val models: List<ModelOptionDto> = emptyList(),
    val temperaturePlaceholder: String = "0.0 - 2.0",
    val invariantsText: String = "",
)
