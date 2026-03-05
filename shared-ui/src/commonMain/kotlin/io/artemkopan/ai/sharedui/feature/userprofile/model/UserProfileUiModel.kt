package io.artemkopan.ai.sharedui.feature.userprofile.model

data class UserProfileUiModel(
    val communicationStyle: String = "concise",
    val responseFormat: String = "markdown",
    val restrictions: String = "",
    val customInstructions: String = "",
    val presets: List<ProfilePreset> = DEFAULT_PRESETS,
    val isDirty: Boolean = false,
)

data class ProfilePreset(
    val name: String,
    val communicationStyle: String,
    val responseFormat: String,
    val restrictions: List<String>,
    val customInstructions: String,
)

val DEFAULT_PRESETS = listOf(
    ProfilePreset(
        name = "Concise Expert",
        communicationStyle = "concise",
        responseFormat = "markdown",
        restrictions = emptyList(),
        customInstructions = "",
    ),
    ProfilePreset(
        name = "Detailed Teacher",
        communicationStyle = "detailed",
        responseFormat = "structured",
        restrictions = listOf("avoid jargon"),
        customInstructions = "Explain concepts step by step",
    ),
    ProfilePreset(
        name = "Socratic Coach",
        communicationStyle = "socratic",
        responseFormat = "plain_text",
        restrictions = emptyList(),
        customInstructions = "Guide through questions, don't give direct answers",
    ),
    ProfilePreset(
        name = "Code Focused",
        communicationStyle = "concise",
        responseFormat = "code_focused",
        restrictions = listOf("minimal prose"),
        customInstructions = "Prioritize code examples",
    ),
)
