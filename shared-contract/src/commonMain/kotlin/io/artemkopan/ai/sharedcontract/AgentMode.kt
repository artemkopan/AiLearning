package io.artemkopan.ai.sharedcontract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AgentMode {
    @SerialName("default")
    DEFAULT,

    @SerialName("engineer")
    ENGINEER,

    @SerialName("philosophic")
    PHILOSOPHIC,

    @SerialName("critic")
    CRITIC,
}
