package io.artemkopan.ai.core.application.model

import io.artemkopan.ai.core.domain.model.CommunicationStyle
import io.artemkopan.ai.core.domain.model.ResponseFormat

data class UpdateUserProfileCommand(
    val communicationStyle: CommunicationStyle,
    val responseFormat: ResponseFormat,
    val restrictions: List<String>,
    val customInstructions: String,
)
